package kr.pleosapp.getoff.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kr.pleosapp.getoff.core.engine.*
import kr.pleosapp.getoff.core.fake.FakeVehicle
import kr.pleosapp.getoff.core.fake.RecordingSpeaker
import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.zone.InMemoryZoneRepository
import java.time.LocalDateTime
import kotlin.test.*

/** advanceUntilIdle()이 기다려 주도록 foreground 스케줄러에 붙이되, 종료 시 backgroundScope가 취소해 준다. */
private fun TestScope.controllerScope() =
    CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext[Job]))

@OptIn(ExperimentalCoroutinesApi::class)
class AppControllerTest {
    private val spot = GeoPoint(37.5665, 126.9780)
    private val zone = Zone("z1", "시청 앞", ZoneType.CROSSWALK, Geometry.Circle(spot, 50.0))

    private class Rig(scope: TestScope, zones: List<Zone>, val settings: MutableStateFlow<Settings> = MutableStateFlow(Settings())) {
        val v = FakeVehicle()
        val speaker = RecordingSpeaker()
        val c = AppController(
            v, v, v, InMemoryZoneRepository(zones), speaker, v, settings, scope.controllerScope(),
            clock = { LocalDateTime.of(2026, 11, 9, 9, 0) },
        )
        val state get() = c.state.value
    }

    private suspend fun TestScope.parked(rig: Rig) {
        rig.v.gearState.value = Gear.P
        advanceUntilIdle()
    }

    @Test fun `AC1_1 구역 안에서 P 전환하면 3초 안에 경고`() = runTest {
        val r = Rig(this, listOf(zone))
        r.v.locationState.value = spot
        advanceUntilIdle()
        assertIs<AppState.Blocked>(r.state)
        r.v.gearState.value = Gear.P
        advanceTimeBy(3_000)
        assertIs<AppState.ZoneWarning>(r.state)
        advanceUntilIdle()
        assertTrue(r.speaker.spoken.single().contains("일 수 있어요")) // AC1.4
        assertFalse(r.speaker.spoken.single().contains("금지 구역입니다"))
    }

    @Test fun `AC1_2 구역 밖이면 경고 없이 점검으로`() = runTest {
        val r = Rig(this, listOf(zone))
        r.v.locationState.value = GeoPoint(37.58, 126.99)
        parked(r)
        assertEquals(AppState.Idle(Phrases.ALL_CLEAR), r.state)
        assertEquals(listOf(Phrases.ALL_CLEAR), r.speaker.spoken)
    }

    @Test fun `AC1_3 위치를 못 얻으면 한 번만 안내하고 점검 진행`() = runTest {
        val r = Rig(this, listOf(zone))
        parked(r)
        assertEquals(1, r.speaker.spoken.count { it == Phrases.LOCATION_UNKNOWN })
        assertIs<AppState.Idle>(r.state)
    }

    @Test fun `경고를 닫으면 하차 점검으로`() = runTest {
        val r = Rig(this, listOf(zone))
        r.v.locationState.value = spot
        r.v.windowState.value = r.v.windowState.value + (Seat.PASSENGER to OpenState.Open)
        parked(r)
        r.c.onAction(UserAction.DismissZone)
        advanceUntilIdle()
        val s = assertIs<AppState.ExitChecklist>(r.state)
        assertEquals("조수석 창문이 열려 있어요", s.items.single().text)
    }

    @Test fun `체크리스트 항목을 모두 확인하면 Idle`() = runTest {
        val r = Rig(this, emptyList())
        r.v.windowState.value = r.v.windowState.value + (Seat.PASSENGER to OpenState.Open)
        r.v.setRear(Occupancy.Occupied)
        parked(r)
        assertEquals(2, assertIs<AppState.ExitChecklist>(r.state).items.size)
        r.c.onAction(UserAction.Acknowledge("window:PASSENGER"))
        advanceUntilIdle()
        assertIs<AppState.ExitChecklist>(r.state)
        r.c.onAction(UserAction.AllRearExited)
        advanceUntilIdle()
        assertEquals(AppState.Idle(), r.state)
    }

    @Test fun `정차 전용 - 주행 시작하면 차단되고 조작은 무시된다`() = runTest {
        val r = Rig(this, emptyList())
        r.v.windowState.value = r.v.windowState.value + (Seat.PASSENGER to OpenState.Open)
        parked(r)
        r.v.gearState.value = Gear.D
        advanceUntilIdle()
        assertIs<AppState.Blocked>(r.state)
        r.c.onAction(UserAction.Acknowledge("window:PASSENGER"))
        r.c.onAction(UserAction.DepartRequested)
        advanceUntilIdle()
        assertIs<AppState.Blocked>(r.state)
    }

    @Test fun `정차 전용 - 기어를 못 읽거나 UX 제한이 걸려도 차단`() = runTest {
        val r = Rig(this, emptyList())
        parked(r)
        r.v.gearState.value = Gear.Unknown
        advanceUntilIdle()
        assertIs<AppState.Blocked>(r.state)
        r.v.gearState.value = Gear.P
        advanceUntilIdle()
        assertIs<AppState.Idle>(r.state)
        r.v.restrictedState.value = true
        advanceUntilIdle()
        assertIs<AppState.Blocked>(r.state)
    }

    @Test fun `점검 설정을 모두 끄면 바로 Idle`() = runTest {
        val r = Rig(this, emptyList(), MutableStateFlow(Settings(windows = false, doors = false, rear = false)))
        r.v.windowState.value = r.v.windowState.value + (Seat.PASSENGER to OpenState.Open)
        parked(r)
        assertEquals(AppState.Idle(), r.state)
    }

    @Test fun `음성을 끄면 말하지 않는다`() = runTest {
        val r = Rig(this, emptyList(), MutableStateFlow(Settings(voice = false)))
        parked(r)
        assertTrue(r.speaker.spoken.isEmpty())
    }

    // ---- F4 ----

    private suspend fun TestScope.toIdle(r: Rig) { parked(r); assertIs<AppState.Idle>(r.state) }

    @Test fun `AC4_1 동의 전에는 제어하지 않는다`() = runTest {
        val r = Rig(this, emptyList())
        toIdle(r)
        r.c.onAction(UserAction.DepartRequested)
        advanceUntilIdle()
        assertIs<AppState.DepartPrep>(r.state)
        assertTrue(r.v.applied.isEmpty())
        r.c.onAction(UserAction.DeclineRoutine)
        advanceUntilIdle()
        assertTrue(r.v.applied.isEmpty())
        assertIs<AppState.Idle>(r.state)
    }

    @Test fun `동의하면 순서대로 실행하고 결과를 보고`() = runTest {
        val r = Rig(this, emptyList())
        toIdle(r)
        r.c.onAction(UserAction.DepartRequested); advanceUntilIdle()
        r.c.onAction(UserAction.ConsentRoutine); advanceUntilIdle()
        val done = assertIs<AppState.RoutineDone>(r.state)
        assertEquals(RoutineStep.entries.toList(), r.v.applied.toList())
        assertTrue(done.outcomes.all { it.succeeded })
        assertEquals(Phrases.ROUTINE_DONE, r.speaker.spoken.last())
    }

    @Test fun `AC4_2 확인 실패 항목은 성공으로 표시하지 않는다`() = runTest {
        val r = Rig(this, emptyList())
        r.v.stepResults[RoutineStep.WIPER_ONCE] = StepResult.Unverified
        r.v.stepResults[RoutineStep.STEERING_HEAT] = StepResult.Failed("응답 없음")
        toIdle(r)
        r.c.onAction(UserAction.DepartRequested); advanceUntilIdle()
        r.c.onAction(UserAction.ConsentRoutine); advanceUntilIdle()
        val done = assertIs<AppState.RoutineDone>(r.state)
        val ok = done.outcomes.filter { it.succeeded }.map { it.step }
        assertEquals(listOf(RoutineStep.DEFROSTER, RoutineStep.SIDE_MIRROR_HEAT), ok)
    }

    @Test fun `AC4_4 미지원 항목은 건너뛰고 이유를 보여 준다`() = runTest {
        val r = Rig(this, emptyList())
        r.v.capabilities[Feature.STEERING_HEAT] = Capability.Unsupported("차량 미지원")
        toIdle(r)
        r.c.onAction(UserAction.DepartRequested); advanceUntilIdle()
        val prep = assertIs<AppState.DepartPrep>(r.state)
        assertEquals(PlannedStep.Skip(RoutineStep.STEERING_HEAT, "차량 미지원"), prep.plan.last())
        r.c.onAction(UserAction.ConsentRoutine); advanceUntilIdle()
        assertFalse(RoutineStep.STEERING_HEAT in r.v.applied)
        val done = assertIs<AppState.RoutineDone>(r.state)
        assertEquals("차량 미지원", done.outcomes.last().skippedReason)
    }

    @Test fun `퍼미션 미승인이면 안내 모드 - 동의해도 제어하지 않는다`() = runTest {
        val r = Rig(this, emptyList())
        RoutineStep.entries.forEach { r.v.capabilities[it.feature] = Capability.PermissionDenied("제어 퍼미션 미승인") }
        toIdle(r)
        r.c.onAction(UserAction.DepartRequested); advanceUntilIdle()
        assertTrue(assertIs<AppState.DepartPrep>(r.state).guideOnly)
        r.c.onAction(UserAction.ConsentRoutine); advanceUntilIdle()
        assertTrue(r.v.applied.isEmpty())
        assertIs<AppState.DepartPrep>(r.state)
    }

    @Test fun `AC4_3 루틴 중 주행 시작하면 즉시 중단하고 되돌리지 않고 알린다`() = runTest {
        val r = Rig(this, emptyList())
        r.v.stepDelayMs = 1_000
        toIdle(r)
        r.c.onAction(UserAction.DepartRequested); advanceUntilIdle()
        r.c.onAction(UserAction.ConsentRoutine)
        advanceTimeBy(1_500) // 1단계 끝, 2단계 진행 중
        assertIs<AppState.RoutineRunning>(r.state)
        r.v.gearState.value = Gear.D
        advanceUntilIdle()
        assertEquals(AppState.Blocked(Phrases.SETTINGS_KEPT), r.state)
        assertEquals(listOf(RoutineStep.DEFROSTER), r.v.applied) // 더 진행되지 않음
        assertTrue(Phrases.SETTINGS_KEPT in r.speaker.spoken)
    }

    @Test fun `제어 중 예외는 숨기지 않고 실패로 보고`() = runTest {
        val r = Rig(this, emptyList())
        val boom = object : kr.pleosapp.getoff.core.signals.ClimateController {
            override suspend fun apply(step: RoutineStep): StepResult = error("IPC 끊김")
        }
        val c = AppController(r.v, r.v, r.v, InMemoryZoneRepository(emptyList()), r.speaker, boom, r.settings, controllerScope(),
            clock = { LocalDateTime.of(2026, 11, 9, 9, 0) })
        r.v.gearState.value = Gear.P; advanceUntilIdle()
        c.onAction(UserAction.DepartRequested); advanceUntilIdle()
        c.onAction(UserAction.ConsentRoutine); advanceUntilIdle()
        val done = assertIs<AppState.RoutineDone>(c.state.value)
        assertTrue(done.outcomes.all { it.result == StepResult.Failed("IPC 끊김") })
    }
}
