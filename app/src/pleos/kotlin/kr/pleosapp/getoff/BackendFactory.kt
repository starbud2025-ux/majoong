package kr.pleosapp.getoff

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.signals.*

/**
 * Pleos Vehicle SDK 2.3.2 어댑터. 시그니처는 Maven에서 받은 AAR(javap)로 확인했다(docs/03 §10).
 *
 * 확인되지 않은 것은 추측하지 않고 Unknown/NotSupported로 degrade한다.
 *  - 도어·창문 위치 Int의 의미: 문서에 없어 `check*PositionCapabilities`의 minValue를 "닫힘"으로 본다(에뮬레이터 검증 필요).
 *  - 사이드미러·핸들 열선: 단계 값(Int) 의미 미확인 → 제어하지 않고 수동 안내.
 *  - FusedLocation: SDK 신청 불가(Coming Soon) → 위치 없음(AC1.3 경로).
 *  - CarUxRestrictions: android.car 접근 경로 미확인 → 기어가 P가 아니면 차단(절대 규칙 1의 기어 조건).
 */
fun createBackend(app: Application): Backend {
    val vehicle = Vehicle(app).also { it.initialize() }
    val signals = PleosSignals(vehicle)
    return Backend(
        name = "pleos(Vehicle 2.3.2)",
        signals = signals,
        ux = object : UxRestrictions {
            // Unknown(읽기 실패·미지원)도 차단: 확인되지 않으면 조작을 허용하지 않는다.
            override val drivingBlocked: Flow<Boolean> = signals.gear.map { it != Gear.P }
        },
        location = object : LocationSource { override val location = flowOf<GeoPoint?>(null) },
        climate = PleosClimate(vehicle),
    )
}

private const val READ_TIMEOUT_MS = 3_000L

/** 콜백 한 번을 suspend로. 실패·타임아웃은 null. */
private suspend fun <T> await(block: (onOk: (T?) -> Unit, onFail: (Exception) -> Unit) -> Unit): T? {
    val d = CompletableDeferred<T?>()
    try {
        block({ d.complete(it) }, { d.complete(null) })
    } catch (e: Exception) {
        d.complete(null)
    }
    return withTimeoutOrNull(READ_TIMEOUT_MS) { d.await() }
}

private class PleosSignals(private val v: Vehicle) : VehicleSignals {

    // ---- 기어 -------------------------------------------------------------------------
    override val gear: Flow<Gear> = callbackFlow {
        val l = object : GearSelectionListener {
            override fun onSelected(p0: VehicleGear?) { trySend(p0?.toGear() ?: Gear.Unknown) }
            override fun onFailed(p0: Exception) { trySend(Gear.Unknown) }
        }
        trySend(Gear.Unknown) // 읽기 전에는 차단 상태로 시작
        try {
            v.getGear().registerGearSelection(l)
            v.getGear().getCurrentGearSelectionState({ trySend(it?.toGear() ?: Gear.Unknown) }, { trySend(Gear.Unknown) })
        } catch (e: Exception) { trySend(Gear.Unknown) }
        awaitClose { runCatching { v.getGear().unregisterGearSelection(l) } }
    }.conflate()

    private fun VehicleGear.toGear() = when (this) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4,
        VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9 -> Gear.D
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        else -> Gear.Unknown
    }

    // ---- 도어·창문 --------------------------------------------------------------------
    private val doorValues = mapOf(
        DoorId.DRIVER to DoorValue.ROW_1_LEFT, DoorId.PASSENGER to DoorValue.ROW_1_RIGHT,
        DoorId.REAR_LEFT to DoorValue.ROW_2_LEFT, DoorId.REAR_RIGHT to DoorValue.ROW_2_RIGHT,
        DoorId.TRUNK to DoorValue.REAR,
    )
    private val windowValues = mapOf(
        Seat.DRIVER to WindowValue.ROW_1_LEFT, Seat.PASSENGER to WindowValue.ROW_1_RIGHT,
        Seat.REAR_LEFT to WindowValue.ROW_2_LEFT, Seat.REAR_RIGHT to WindowValue.ROW_2_RIGHT,
    )

    private suspend fun doorMin(a: DoorArea): Int? =
        await<Map<DoorArea, AreaBounds<Int>>> { ok, fail -> v.getDoor().checkDoorPositionCapabilities({ _, m -> ok(m) }, { fail(it) }) }
            ?.get(a)?.minValue

    private suspend fun windowMin(a: WindowArea): Int? =
        await<Map<WindowArea, AreaBounds<Int>>> { ok, fail -> v.getWindow().checkWindowPositionCapabilities({ _, m -> ok(m) }, { fail(it) }) }
            ?.get(a)?.minValue

    private fun openOf(pos: Int?, min: Int?): OpenState =
        if (pos == null || min == null) OpenState.Unknown else if (pos > min) OpenState.Open else OpenState.Closed

    override val doors: Flow<Map<DoorId, OpenState>> = combineMap(DoorId.entries.map { id ->
        callbackFlow {
            val area = DoorArea(doorValues.getValue(id))
            var min: Int? = null
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(p0: Int?) { trySend(id to openOf(p0, min)) }
                override fun onFailed(p0: Exception) { trySend(id to OpenState.Unknown) }
            }
            trySend(id to OpenState.Unknown)
            try {
                min = doorMin(area)
                v.getDoor().registerDoorPosition(area, l)
                v.getDoor().getCurrentDoorPosition(area, { trySend(id to openOf(it, min)) }, { trySend(id to OpenState.Unknown) })
            } catch (e: Exception) { trySend(id to OpenState.Unknown) }
            awaitClose { runCatching { v.getDoor().unregisterDoorPosition(area, l) } }
        }
    })

    override val windows: Flow<Map<Seat, OpenState>> = combineMap(Seat.entries.map { s ->
        callbackFlow {
            val area = WindowArea(windowValues.getValue(s))
            var min: Int? = null
            val l = object : WindowPositionListener {
                override fun onWindowPositionUpdated(p0: Int?) { trySend(s to openOf(p0, min)) }
                override fun onFailed(p0: Exception) { trySend(s to OpenState.Unknown) }
            }
            trySend(s to OpenState.Unknown)
            try {
                min = windowMin(area)
                v.getWindow().registerWindowPosition(area, l)
                v.getWindow().getCurrentWindowPosition(area, { trySend(s to openOf(it, min)) }, { trySend(s to OpenState.Unknown) })
            } catch (e: Exception) { trySend(s to OpenState.Unknown) }
            awaitClose { runCatching { v.getWindow().unregisterWindowPosition(area, l) } }
        }
    })

    // ---- 좌석 탑승·벨트 ---------------------------------------------------------------
    private val seatValues = mapOf(
        SeatPos.DRIVER to SeatValue.ROW_1_LEFT, SeatPos.PASSENGER to SeatValue.ROW_1_RIGHT,
        SeatPos.REAR_LEFT to SeatValue.ROW_2_LEFT, SeatPos.REAR_CENTER to SeatValue.ROW_2_CENTER,
        SeatPos.REAR_RIGHT to SeatValue.ROW_2_RIGHT,
    )

    override val seatOccupancy: Flow<Map<SeatPos, Occupancy>> = combineMap(SeatPos.entries.filter { it.isRear }.map { p ->
        callbackFlow {
            val area = SeatArea(seatValues.getValue(p))
            fun occ(b: Boolean?) = when (b) { true -> Occupancy.Occupied; false -> Occupancy.Empty; null -> Occupancy.Unknown }
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(p0: Boolean?) { trySend(p to occ(p0)) }
                override fun onFailed(p0: Exception) { trySend(p to Occupancy.Unknown) }
            }
            trySend(p to Occupancy.Unknown)
            try {
                v.getSeat().registerSeatOccupancy(area, l)
                v.getSeat().getCurrentSeatOccupancy(area, { trySend(p to occ(it)) }, { trySend(p to Occupancy.Unknown) })
            } catch (e: Exception) { trySend(p to Occupancy.Unknown) }
            awaitClose { runCatching { v.getSeat().unregisterSeatOccupancy(area, l) } }
        }
    })

    override val rearOccupancy: Flow<Occupancy> = seatOccupancy.map { m ->
        val vals = m.values
        when {
            vals.any { it == Occupancy.Occupied } -> Occupancy.Occupied
            vals.any { it == Occupancy.Unknown } -> Occupancy.Unknown
            else -> Occupancy.Empty
        }
    }

    override val seatBelts: Flow<Map<SeatPos, BeltState>> = combineMap(SeatPos.entries.filter { it.isRear }.map { p ->
        callbackFlow {
            val area = SeatArea(seatValues.getValue(p))
            fun belt(b: Boolean?) = when (b) { true -> BeltState.Buckled; false -> BeltState.Unbuckled; null -> BeltState.Unknown }
            val l = object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(p0: Boolean?) { trySend(p to belt(p0)) }
                override fun onFailed(p0: Exception) { trySend(p to BeltState.Unknown) }
            }
            trySend(p to BeltState.Unknown)
            try {
                v.getSeat().registerSeatBeltBuckledState(area, l)
                v.getSeat().getCurrentSeatBeltBuckledState(area, { trySend(p to belt(it)) }, { trySend(p to BeltState.Unknown) })
            } catch (e: Exception) { trySend(p to BeltState.Unknown) }
            awaitClose { runCatching { v.getSeat().unregisterSeatBeltBuckledState(area, l) } }
        }
    })

    // ---- 경고등 -----------------------------------------------------------------------
    // 텔테일 조회(Safety.registerTelltales 등)는 PRIVATE_CAR_SAFETY가 필요하고 일반 참가자에게 개방되지 않을 가능성이 커서
    // 사용하지 않는다(사용자 결정 2026-11-09). 값을 내보내지 않으므로 점검은 "차량 경고 정보는 확인 불가"로 표시한다.
    override val warnings: Flow<List<Warning>> = emptyFlow()

    override val climate: Flow<ClimateState> = flowOf(ClimateState())

    // ---- 기능별 지원 여부 -------------------------------------------------------------
    override suspend fun capability(feature: Feature): Capability {
        fun ok() = Capability.Supported
        fun no(r: String) = Capability.Unsupported(r)
        return when (feature) {
            Feature.DOOR -> await<Map<DoorArea, AreaBounds<Int>>> { o, f -> v.getDoor().checkDoorPositionCapabilities({ _, m -> o(m) }, { f(it) }) }
                ?.let { if (it.isEmpty()) no("이 차량은 도어 위치를 제공하지 않아요") else ok() } ?: no("도어 지원 여부를 확인하지 못했어요")
            Feature.WINDOW -> await<Map<WindowArea, AreaBounds<Int>>> { o, f -> v.getWindow().checkWindowPositionCapabilities({ _, m -> o(m) }, { f(it) }) }
                ?.let { if (it.isEmpty()) no("이 차량은 창문 위치를 제공하지 않아요") else ok() } ?: no("창문 지원 여부를 확인하지 못했어요")
            Feature.REAR_OCCUPANCY -> await<List<SeatArea>> { o, f -> v.getSeat().checkSeatOccupancyCapabilities({ _, l -> o(l) }, { f(it) }) }
                ?.let { l -> if (l.any { a -> a.values.any { it == SeatValue.ROW_2_LEFT || it == SeatValue.ROW_2_RIGHT } }) ok() else no("뒷좌석 탑승 감지를 제공하지 않아요") }
                ?: no("뒷좌석 탑승 감지 지원 여부를 확인하지 못했어요")
            Feature.SEAT_BELT -> await<List<SeatArea>> { o, f -> v.getSeat().checkSeatBeltBuckledCapabilities({ _, l -> o(l) }, { f(it) }) }
                ?.let { l -> if (l.any { a -> a.values.any { it == SeatValue.ROW_2_LEFT || it == SeatValue.ROW_2_RIGHT } }) ok() else no("뒷좌석 벨트 감지를 제공하지 않아요") }
                ?: no("벨트 감지 지원 여부를 확인하지 못했어요")
            Feature.SAFETY -> no("경고등 조회 퍼미션(PRIVATE_CAR_SAFETY)을 사용하지 않아요")
            Feature.DEFROSTER -> await<Boolean> { o, f ->
                v.getHVAC().checkHvacDefrosterCapabilities({ c, l -> o(c.accessType.canWrite() && l.any { it.values.contains(WindowValue.FRONT_WINDSHIELD) }) }, { f(it) })
            }?.let { if (it) ok() else no("앞유리 제습 제어를 사용할 수 없어요") } ?: no("제습 제어 지원 여부를 확인하지 못했어요")
            Feature.WIPER -> await<Boolean> { o, f ->
                v.getWiper().checkWiperSwitchCapabilities({ c, m -> o(c.accessType.canWrite() && m.values.any { WiperSwitch.MIST in it }) }, { f(it) })
            }?.let { if (it) ok() else no("와이퍼 제어를 사용할 수 없어요") } ?: no("와이퍼 제어 지원 여부를 확인하지 못했어요")
            // 열선: 단계 값(Int)의 의미가 문서에 없어 제어하지 않는다(수동 안내).
            Feature.SIDE_MIRROR_HEAT, Feature.STEERING_HEAT -> no("열선 단계 값을 확인하지 못해 직접 켜 주셔야 해요")
            Feature.TTS -> ok() // 플랫폼 TextToSpeech(TtsSpeaker). TTS SDK는 신청 불가(Coming Soon)
            Feature.LOCATION -> no("위치 SDK를 사용할 수 없어요")
        }
    }

    private fun AccessType.canWrite() = this == AccessType.WRITE || this == AccessType.READ_WRITE
}

/** 키-값 flow 여러 개를 하나의 Map flow로. 각 항목이 한 번씩 방출된 뒤부터 방출한다(모두 초기 Unknown을 먼저 보낸다). */
private inline fun <reified K, V> combineMap(flows: List<Flow<Pair<K, V>>>): Flow<Map<K, V>> =
    combine(flows) { it.toMap() }

private class PleosClimate(private val v: Vehicle) : ClimateController {
    override suspend fun apply(step: RoutineStep): StepResult = when (step) {
        RoutineStep.DEFROSTER -> defroster()
        RoutineStep.WIPER_ONCE -> wiperOnce()
        RoutineStep.SIDE_MIRROR_HEAT, RoutineStep.STEERING_HEAT -> StepResult.NotSupported
    }

    private suspend fun defroster(): StepResult {
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val sent = await<Boolean> { ok, fail -> v.getHVAC().setHvacDefroster(area, true, { _, on -> ok(on) }, { fail(it) }) }
            ?: return StepResult.Failed("제습 명령 응답이 없어요")
        if (!sent) return StepResult.Failed("차량이 제습을 켜지 않았어요")
        // 명령 응답과 별개로 실제 상태를 다시 읽어 확인한다(AC4.2).
        val state = await<Boolean> { ok, fail -> v.getHVAC().getHvacDefrosterState(area, { ok(it) }, { fail(it) }) }
        return when (state) { true -> StepResult.Success; false -> StepResult.Failed("제습이 켜지지 않았어요"); null -> StepResult.Unverified }
    }

    private suspend fun wiperOnce(): StepResult {
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        await<WiperSwitch> { ok, fail -> v.getWiper().setWiperSwitch(area, WiperSwitch.MIST, { _, sw -> ok(sw) }, { fail(it) }) }
            ?: return StepResult.Failed("와이퍼 명령 응답이 없어요")
        // 1회 동작은 곧 끝나므로 동작 상태로 성공을 단정할 수 없다 → 확인 불가로 보고(AC4.2).
        return StepResult.Unverified
    }
}
