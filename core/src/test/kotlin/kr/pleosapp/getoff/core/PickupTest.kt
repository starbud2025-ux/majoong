package kr.pleosapp.getoff.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kr.pleosapp.getoff.core.engine.*
import kr.pleosapp.getoff.core.fake.FakeVehicle
import kr.pleosapp.getoff.core.fake.RecordingSpeaker
import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.zone.InMemoryZoneRepository
import kotlin.test.*

private fun TestScope.scope() =
    CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext[Job]))

class PickupEvaluatorTest {
    private fun occ(vararg p: Pair<SeatPos, Occupancy>) = PickupEvaluator.rearSeats.associateWith { Occupancy.Empty } + p
    private fun belts(vararg p: Pair<SeatPos, BeltState>) = PickupEvaluator.rearSeats.associateWith { BeltState.Buckled } + p

    @Test fun `AC5_2 좌석을 따로 판단한다 - 오른쪽만 안 맨 경우`() {
        val r = PickupEvaluator.boarding(
            occ(SeatPos.REAR_LEFT to Occupancy.Occupied, SeatPos.REAR_RIGHT to Occupancy.Occupied),
            belts(SeatPos.REAR_RIGHT to BeltState.Unbuckled), degraded = false,
        )
        assertEquals(BoardingStatus.Unbuckled, r.status)
        assertEquals(listOf(SeatPos.REAR_RIGHT), r.unbuckled.map { it.seat })
    }

    @Test fun `AC5_2 벨트를 못 읽으면 착용으로 보지 않는다`() {
        val r = PickupEvaluator.boarding(
            occ(SeatPos.REAR_LEFT to Occupancy.Occupied), belts(SeatPos.REAR_LEFT to BeltState.Unknown), degraded = false,
        )
        assertEquals(BoardingStatus.NeedsCheck, r.status)
    }

    @Test fun `탑승 값을 못 읽은 좌석이 있으면 Ready가 아니다`() {
        val r = PickupEvaluator.boarding(occ(SeatPos.REAR_CENTER to Occupancy.Unknown, SeatPos.REAR_LEFT to Occupancy.Occupied), belts(), false)
        assertEquals(BoardingStatus.NeedsCheck, r.status)
    }

    @Test fun `AC5_3 탑승자 전원 착용이 확인돼야 Ready`() {
        val r = PickupEvaluator.boarding(occ(SeatPos.REAR_LEFT to Occupancy.Occupied, SeatPos.REAR_CENTER to Occupancy.Occupied), belts(), false)
        assertEquals(BoardingStatus.Ready, r.status)
    }

    @Test fun `탑승자가 없으면 Waiting`() {
        assertEquals(BoardingStatus.Waiting, PickupEvaluator.boarding(occ(), belts(), false).status)
    }

    @Test fun `AC5_6 미지원 차량은 Ready를 말하지 않는다`() {
        val r = PickupEvaluator.boarding(occ(SeatPos.REAR_LEFT to Occupancy.Occupied), belts(), degraded = true)
        assertEquals(BoardingStatus.NeedsCheck, r.status)
    }

    @Test fun `정차 방향에 따라 차도 쪽 문을 구분한다`() {
        fun s(open: DoorId, side: CurbSide) =
            AppState.PickupActive(PickupEvaluator.boarding(occ(), belts(), false), setOf(open), side)
        assertFalse(s(DoorId.REAR_RIGHT, CurbSide.RIGHT).wrongSideOpen)
        assertTrue(s(DoorId.REAR_LEFT, CurbSide.RIGHT).wrongSideOpen)
        assertFalse(s(DoorId.REAR_LEFT, CurbSide.LEFT).wrongSideOpen)
        assertTrue(s(DoorId.REAR_RIGHT, CurbSide.LEFT).wrongSideOpen)
        assertEquals(PickupPhase.Alighting, s(DoorId.REAR_RIGHT, CurbSide.RIGHT).phase)
    }

    @Test fun `문구 - 인도 쪽 안내와 차도 쪽 경고`() {
        assertEquals("오른쪽 문으로 내리세요", Phrases.pickupCurb(CurbSide.RIGHT))
        assertEquals("차도 쪽 문이에요. 왼쪽 문으로 내려 주세요", Phrases.pickupWrongSide(CurbSide.LEFT))
        assertEquals("뒷좌석 오른쪽 안전벨트를 확인하세요", Phrases.pickupUnbuckled(listOf(SeatPos.REAR_RIGHT)))
    }
}

class PickupFlowTest {
    private class Rig(scope: TestScope, settings: Settings = Settings()) {
        val v = FakeVehicle()
        val speaker = RecordingSpeaker()
        val settings = MutableStateFlow(settings)
        val c = AppController(v, v, v, InMemoryZoneRepository(emptyList()), speaker, v, this.settings, scope.scope())
        val state get() = c.state.value
    }

    private suspend fun TestScope.idle(r: Rig) {
        r.v.gearState.value = Gear.P
        advanceUntilIdle()
        assertIs<AppState.Idle>(r.state)
    }

    @Test fun `AC5_1 켜기 전에는 아무 안내도 하지 않는다`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false))
        r.v.setRear(Occupancy.Occupied)
        r.v.seatBeltState.value = r.v.seatBeltState.value + (SeatPos.REAR_RIGHT to BeltState.Unbuckled)
        idle(r)
        advanceUntilIdle()
        // 벨트를 안 맨 탑승자가 있어도, 픽업 모드를 켜지 않았으면 벨트 안내가 없다
        assertTrue(r.speaker.spoken.none { "안전벨트" in it })
        assertIs<AppState.Idle>(r.state)
    }

    @Test fun `AC5_1 설정에서 끄면 시작하지 않는다`() = runTest {
        val r = Rig(this, Settings(pickup = false, rear = false, doors = false, windows = false))
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        assertIs<AppState.Idle>(r.state)
    }

    @Test fun `시작하면 좌석별 상태를 보여주고 벨트 미착용을 말한다`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false))
        r.v.seatOccupancyState.value = r.v.seatOccupancyState.value + (SeatPos.REAR_RIGHT to Occupancy.Occupied)
        r.v.seatBeltState.value = r.v.seatBeltState.value + (SeatPos.REAR_RIGHT to BeltState.Unbuckled)
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        val s = assertIs<AppState.PickupActive>(r.state)
        assertEquals(BoardingStatus.Unbuckled, s.boarding.status)
        assertTrue(Phrases.pickupUnbuckled(listOf(SeatPos.REAR_RIGHT)) in r.speaker.spoken)
    }

    @Test fun `AC5_3 벨트를 매면 상태가 실시간으로 Ready가 된다`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false))
        r.v.seatOccupancyState.value = r.v.seatOccupancyState.value + (SeatPos.REAR_LEFT to Occupancy.Occupied)
        r.v.seatBeltState.value = r.v.seatBeltState.value + (SeatPos.REAR_LEFT to BeltState.Unbuckled)
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        assertEquals(BoardingStatus.Unbuckled, assertIs<AppState.PickupActive>(r.state).boarding.status)
        r.v.seatBeltState.value = r.v.seatBeltState.value + (SeatPos.REAR_LEFT to BeltState.Buckled)
        advanceUntilIdle()
        assertEquals(BoardingStatus.Ready, assertIs<AppState.PickupActive>(r.state).boarding.status)
        assertEquals(Phrases.PICKUP_BELTS_OK, r.speaker.spoken.last())
    }

    @Test fun `같은 안내는 반복해서 말하지 않는다`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false))
        r.v.seatOccupancyState.value = r.v.seatOccupancyState.value + (SeatPos.REAR_LEFT to Occupancy.Occupied)
        r.v.seatBeltState.value = r.v.seatBeltState.value + (SeatPos.REAR_LEFT to BeltState.Unbuckled)
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        // 관련 없는 신호(도어)가 바뀌어도 같은 문장을 다시 말하지 않는다
        r.v.doorState.value = r.v.doorState.value + (DoorId.TRUNK to OpenState.Open); advanceUntilIdle()
        assertEquals(1, r.speaker.spoken.count { it == Phrases.pickupUnbuckled(listOf(SeatPos.REAR_LEFT)) })
    }

    @Test fun `하차 - 인도 쪽 문은 안내, 차도 쪽 문은 경고 (설정 반영)`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false, curbSide = CurbSide.RIGHT))
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        r.v.doorState.value = r.v.doorState.value + (DoorId.REAR_RIGHT to OpenState.Open); advanceUntilIdle()
        assertEquals(Phrases.pickupCurb(CurbSide.RIGHT), r.speaker.spoken.last())
        r.v.doorState.value = r.v.doorState.value + (DoorId.REAR_RIGHT to OpenState.Closed) + (DoorId.REAR_LEFT to OpenState.Open); advanceUntilIdle()
        assertTrue(assertIs<AppState.PickupActive>(r.state).wrongSideOpen)
        assertEquals(Phrases.pickupWrongSide(CurbSide.RIGHT), r.speaker.spoken.last())
    }

    @Test fun `정차 방향을 왼쪽으로 설정하면 왼쪽 문이 인도 쪽`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false, curbSide = CurbSide.LEFT))
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        r.v.doorState.value = r.v.doorState.value + (DoorId.REAR_LEFT to OpenState.Open); advanceUntilIdle()
        assertFalse(assertIs<AppState.PickupActive>(r.state).wrongSideOpen)
        assertEquals(Phrases.pickupCurb(CurbSide.LEFT), r.speaker.spoken.last())
    }

    @Test fun `AC5_4 기어가 P를 벗어나면 즉시 종료한다`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false))
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        assertIs<AppState.PickupActive>(r.state)
        r.v.gearState.value = Gear.D; advanceUntilIdle()
        assertIs<AppState.Blocked>(r.state)
        // 이후 신호가 바뀌어도 화면이 되살아나지 않는다
        r.v.doorState.value = r.v.doorState.value + (DoorId.REAR_RIGHT to OpenState.Open); advanceUntilIdle()
        assertIs<AppState.Blocked>(r.state)
    }

    @Test fun `AC5_6 미지원 차량이면 준비 완료를 말하지 않고 일반 문구`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false))
        r.v.capabilities[Feature.SEAT_BELT] = Capability.Unsupported("차량 미지원")
        r.v.seatOccupancyState.value = r.v.seatOccupancyState.value + (SeatPos.REAR_LEFT to Occupancy.Occupied)
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        assertEquals(BoardingStatus.NeedsCheck, assertIs<AppState.PickupActive>(r.state).boarding.status)
        assertTrue(Phrases.PICKUP_BELTS_OK !in r.speaker.spoken)
        assertTrue(Phrases.PICKUP_CHECK in r.speaker.spoken)
    }

    @Test fun `모두 내렸어요 - 남은 탑승자를 마무리 점검에서 잡는다`() = runTest {
        val r = Rig(this, Settings(windows = false, doors = false))
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        r.v.setRear(Occupancy.Occupied) // 한 명이 아직 남아 있다
        r.c.onAction(UserAction.PickupDone); advanceUntilIdle()
        val s = assertIs<AppState.ExitChecklist>(r.state)
        assertEquals(Phrases.REAR_OCCUPIED, s.rearItem?.text)
    }

    @Test fun `취소하면 홈으로 돌아간다`() = runTest {
        val r = Rig(this, Settings(rear = false, doors = false, windows = false))
        idle(r)
        r.c.onAction(UserAction.PickupStart); advanceUntilIdle()
        r.c.onAction(UserAction.PickupCancel); advanceUntilIdle()
        assertEquals(AppState.Idle(), r.state)
    }
}
