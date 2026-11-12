package kr.pleosapp.getoff.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.signals.*
import kr.pleosapp.getoff.core.zone.ZoneHit
import kr.pleosapp.getoff.core.zone.ZoneMatcher
import java.time.LocalDateTime

data class Settings(
    val voice: Boolean = true,
    val zoneWarning: Boolean = true,
    val windows: Boolean = true,
    val doors: Boolean = true,
    val rear: Boolean = true,
    val routine: Boolean = true,
    /** 등·하원 픽업 모드 사용 여부 */
    val pickup: Boolean = true,
    /** 정차 방향(내리는 쪽). 안내 문구와 차도 쪽 문 경고에 쓴다. */
    val curbSide: CurbSide = CurbSide.RIGHT,
) {
    fun inspection() = InspectionOptions(windows = windows, doors = doors, rear = rear, warnings = true)
    val inspectionEnabled get() = windows || doors || rear
}

sealed interface AppState {
    /** 정차 전용: 기어가 P가 아니거나 주행 제한 중. 모든 조작·제어 중단. */
    data class Blocked(val notice: String? = null) : AppState
    /** 구역 대조·점검·계획 수립 중. */
    data object Checking : AppState
    data class ZoneWarning(val hit: ZoneHit) : AppState
    data class ExitChecklist(
        val items: List<ChecklistItem>,
        val acknowledged: Set<String> = emptySet(),
        val timedOut: Boolean = false,
    ) : AppState {
        val rearItem get() = items.firstOrNull { it.kind == ItemKind.REAR }
    }
    data class Idle(val message: String? = null) : AppState
    data class DepartPrep(val plan: List<PlannedStep>) : AppState {
        /** 실행 가능한 단계가 하나도 없으면 안내 모드(제어 없음). */
        val guideOnly get() = plan.none { it is PlannedStep.Run }
    }
    data class RoutineRunning(val plan: List<PlannedStep>, val outcomes: List<StepOutcome> = emptyList()) : AppState
    data class RoutineDone(val outcomes: List<StepOutcome>) : AppState
    /** 등·하원 픽업 모드. 승차(벨트 확인)와 하차(인도 쪽 문 안내)를 한 상태에서 실시간으로 갱신한다. */
    data class PickupActive(
        val boarding: BoardingReport,
        val openRearDoors: Set<DoorId>,
        val curbSide: CurbSide,
    ) : AppState {
        val phase get() = if (openRearDoors.isNotEmpty()) PickupPhase.Alighting else PickupPhase.Boarding
        /** 인도 쪽이 아닌(차도 쪽) 뒷문이 열렸다. */
        val wrongSideOpen get() = openRearDoors.any { it != curbSide.rearDoor }
    }
}

sealed interface UserAction {
    data object ExitRequested : UserAction              // "내릴게요"
    data object DismissZone : UserAction                // "알겠어요"
    data class Acknowledge(val itemId: String) : UserAction
    data object AllRearExited : UserAction              // "모두 내렸어요"
    data object DepartRequested : UserAction            // "출발 준비"
    data object ConsentRoutine : UserAction             // 서리 제거 시작 동의
    data object DeclineRoutine : UserAction
    data object Close : UserAction
    data object PickupStart : UserAction                // "픽업 모드"
    data object PickupDone : UserAction                 // "모두 내렸어요" → 마무리 점검
    data object PickupCancel : UserAction
}

/** 앱 전체 흐름. 모든 전이는 단일 이벤트 루프에서 처리한다. [scope]는 단일 스레드 디스패처를 쓸 것. */
class AppController(
    private val signals: VehicleSignals,
    private val ux: UxRestrictions,
    private val location: LocationSource,
    private val zones: ZoneRepository,
    private val speaker: Speaker,
    climate: ClimateController,
    private val settings: StateFlow<Settings>,
    private val scope: CoroutineScope,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    private val locationTimeoutMs: Long = 2_000,
    private val zoneRadiusM: Int = 200,
    inspectTimeoutMs: Long = 3_000,
) {
    private sealed interface Event {
        data class BlockedChanged(val blocked: Boolean) : Event
        data class User(val action: UserAction) : Event
    }

    private val inspector = ExitInspector(signals, inspectTimeoutMs)
    private val runner = RoutineRunner(signals, climate)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val _state = MutableStateFlow<AppState>(AppState.Blocked())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var work: Job? = null

    init {
        // 기어≠P 이거나 UX 제한이면 차단. 기어를 읽지 못해도(Unknown) 차단 쪽으로 판단한다.
        scope.launch {
            combine(signals.gear, ux.drivingBlocked) { gear, restricted -> gear != Gear.P || restricted }
                .distinctUntilChanged()
                .collect { events.trySend(Event.BlockedChanged(it)) }
        }
        scope.launch { for (e in events) handle(e) }
    }

    fun onAction(a: UserAction) { events.trySend(Event.User(a)) }

    private fun handle(e: Event) = when (e) {
        is Event.BlockedChanged -> onBlockedChanged(e.blocked)
        is Event.User -> if (_state.value is AppState.Blocked) Unit else onUser(e.action)
    }

    private fun onBlockedChanged(blocked: Boolean) {
        val cur = _state.value
        if (blocked) {
            work?.cancel()
            val wasRunning = cur is AppState.RoutineRunning
            _state.value = AppState.Blocked(if (wasRunning) Phrases.SETTINGS_KEPT else null)
            if (wasRunning) say(Phrases.SETTINGS_KEPT) // AC4.3: 되돌리지 않고 알린다
        } else if (cur is AppState.Blocked) {
            _state.value = AppState.Checking
            work = scope.launch { onParked() }
        }
    }

    private suspend fun onParked() {
        if (settings.value.zoneWarning && !checkZone()) return
        runInspection()
    }

    /** @return true면 계속 진행, false면 경고 화면에서 사용자 응답을 기다린다. */
    private suspend fun checkZone(): Boolean {
        val p = withTimeoutOrNull(locationTimeoutMs) { location.location.first { it != null } }
        if (p == null) {
            say(Phrases.LOCATION_UNKNOWN) // AC1.3: 한 번만
            return true
        }
        val now = clock()
        val hit = ZoneMatcher.match(p, zones.zonesNear(p, zoneRadiusM), now).firstOrNull { it.activeNow } ?: return true
        _state.value = AppState.ZoneWarning(hit)
        say(Phrases.zoneWarning(hit))
        return false
    }

    private suspend fun runInspection() {
        val s = settings.value
        if (!s.inspectionEnabled) { _state.value = AppState.Idle(); return }
        _state.value = AppState.Checking
        val report = inspector.inspect(s.inspection())
        if (report.allClear) {
            _state.value = AppState.Idle(Phrases.ALL_CLEAR)
            say(Phrases.ALL_CLEAR)
        } else {
            _state.value = AppState.ExitChecklist(report.items, timedOut = report.timedOut)
            say(ExitInspector.summary(report))
        }
    }

    private fun onUser(a: UserAction) {
        val cur = _state.value
        when (a) {
            UserAction.DismissZone -> if (cur is AppState.ZoneWarning) launchWork { runInspection() }
            UserAction.ExitRequested -> if (cur is AppState.Idle || cur is AppState.ExitChecklist) launchWork { runInspection() }
            is UserAction.Acknowledge -> acknowledge(a.itemId)
            UserAction.AllRearExited -> acknowledge("rear")
            UserAction.DepartRequested ->
                if (cur is AppState.Idle && settings.value.routine) {
                    _state.value = AppState.Checking
                    launchWork { _state.value = AppState.DepartPrep(runner.plan()) }
                }
            UserAction.ConsentRoutine -> if (cur is AppState.DepartPrep && !cur.guideOnly) startRoutine(cur.plan) // AC4.1
            // AC5.1: 사용자가 켜기 전에는 아무 안내도 하지 않는다. 설정에서 끄면 시작하지 않는다.
            UserAction.PickupStart -> if (cur is AppState.Idle && settings.value.pickup) startPickup()
            UserAction.PickupDone -> if (cur is AppState.PickupActive) launchWork { runInspection() } // 남은 탑승자·창문·도어 마무리 확인
            UserAction.PickupCancel -> if (cur is AppState.PickupActive) { work?.cancel(); _state.value = AppState.Idle() }
            UserAction.DeclineRoutine, UserAction.Close ->
                if (cur is AppState.DepartPrep || cur is AppState.RoutineDone) _state.value = AppState.Idle()
        }
    }

    private fun acknowledge(id: String) {
        val cur = _state.value as? AppState.ExitChecklist ?: return
        if (cur.items.none { it.id == id }) return
        val acked = cur.acknowledged + id
        _state.value =
            if (cur.items.all { it.id in acked }) AppState.Idle() else cur.copy(acknowledged = acked)
    }

    private fun startRoutine(plan: List<PlannedStep>) {
        _state.value = AppState.RoutineRunning(plan)
        launchWork {
            val outcomes = runner.run(plan) { o ->
                _state.update { s -> if (s is AppState.RoutineRunning) s.copy(outcomes = s.outcomes + o) else s }
            }
            _state.value = AppState.RoutineDone(outcomes)
            say(Phrases.ROUTINE_DONE)
        }
    }

    /**
     * 픽업 모드: 좌석 탑승·벨트·도어 신호를 구독해 상태를 실시간 갱신한다(폴링 없음).
     * 한 번도 오지 않은 신호는 빈 맵(=Unknown)으로 시작해 "확인 불가"로 남는다.
     * 개인정보: 좌석 점유 불리언만 메모리에서 쓰고 저장·전송하지 않는다(AC5.5).
     */
    private fun startPickup() {
        _state.value = AppState.Checking
        launchWork {
            val degraded = signals.capability(Feature.REAR_OCCUPANCY) !is Capability.Supported ||
                signals.capability(Feature.SEAT_BELT) !is Capability.Supported // AC5.6
            var last: String? = null
            combine(
                signals.seatOccupancy.onStart { emit(emptyMap()) },
                signals.seatBelts.onStart { emit(emptyMap()) },
                signals.doors.onStart { emit(emptyMap()) },
            ) { occ, belts, doors ->
                AppState.PickupActive(
                    boarding = PickupEvaluator.boarding(occ, belts, degraded),
                    openRearDoors = PickupEvaluator.openRearDoors(doors),
                    curbSide = settings.value.curbSide,
                )
            }.collect { s ->
                _state.value = s
                val msg = PickupEvaluator.voice(s)
                if (msg != null && msg != last) say(msg)
                last = msg
            }
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        work?.cancel()
        work = scope.launch { block() }
    }

    private fun say(text: String) {
        if (settings.value.voice) scope.launch { speaker.speak(text) }
    }
}

val StepOutcome.succeeded get() = result == StepResult.Success
