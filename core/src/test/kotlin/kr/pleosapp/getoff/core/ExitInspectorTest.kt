package kr.pleosapp.getoff.core

import kotlinx.coroutines.test.runTest
import kr.pleosapp.getoff.core.engine.*
import kr.pleosapp.getoff.core.fake.FakeVehicle
import kr.pleosapp.getoff.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExitInspectorTest {
    private fun healthy() = VehicleSnapshot(
        windows = Seat.entries.associateWith { OpenState.Closed },
        doors = DoorId.entries.associateWith { OpenState.Closed },
        rearOccupancy = Occupancy.Empty,
        warnings = emptyList(),
    )

    @Test fun `AC2_1 이상 없으면 한 문장`() {
        val r = ExitInspector.evaluate(healthy())
        assertTrue(r.allClear)
        assertEquals("모두 정상이에요", ExitInspector.summary(r))
    }

    @Test fun `AC2_2 열린 창문은 좌석을 구분해서 말한다`() {
        val s = healthy().copy(windows = healthy().windows + (Seat.PASSENGER to OpenState.Open))
        val r = ExitInspector.evaluate(s)
        assertEquals(listOf("조수석 창문이 열려 있어요"), r.items.map { it.text })
    }

    @Test fun `AC2_3 읽지 못한 항목은 정상으로 보지 않는다`() {
        val s = healthy().copy(windows = healthy().windows + (Seat.REAR_LEFT to OpenState.Unknown), warnings = null)
        val r = ExitInspector.evaluate(s)
        assertTrue(r.items.all { it.status == ItemStatus.Unverifiable })
        assertEquals(2, r.items.size)
        assertTrue(!r.allClear)
    }

    @Test fun `AC3_1 탑승 감지 시에만 안내하고 감지 불가면 일반 문구`() {
        assertEquals(Phrases.REAR_OCCUPIED, ExitInspector.evaluate(healthy().copy(rearOccupancy = Occupancy.Occupied)).items.single().text)
        assertEquals(Phrases.REAR_GENERIC, ExitInspector.evaluate(healthy().copy(rearOccupancy = Occupancy.Unknown)).items.single().text)
        assertTrue(ExitInspector.evaluate(healthy()).allClear)
    }

    @Test fun `꺼 둔 점검 항목은 건너뛴다`() {
        val s = healthy().copy(rearOccupancy = Occupancy.Occupied)
        assertTrue(ExitInspector.evaluate(s, InspectionOptions(rear = false)).allClear)
    }

    @Test fun `AC2_4 신호가 안 오면 3초 타임아웃 후 부분 결과`() = runTest {
        val v = FakeVehicle().apply {
            hangWindows = true
            doorState.value = doorState.value + (DoorId.DRIVER to OpenState.Open)
        }
        val r = ExitInspector(v, 3_000).inspect()
        assertEquals(3_000, testScheduler.currentTime)
        assertTrue(r.timedOut)
        assertTrue(r.items.any { it.id == "door:DRIVER" && it.status == ItemStatus.Issue })
        assertTrue(r.items.any { it.kind == ItemKind.WINDOW && it.status == ItemStatus.Unverifiable })
    }

    @Test fun `값을 내보내지 않고 끝나는 신호는 기다리지 않고 확인 불가`() = runTest {
        // pleos 어댑터의 경고등처럼 미지원이라 emptyFlow인 경우: 3초를 기다리지 않는다.
        val base = FakeVehicle()
        val signals = object : kr.pleosapp.getoff.core.signals.VehicleSignals by base {
            override val warnings = kotlinx.coroutines.flow.emptyFlow<List<Warning>>()
        }
        val r = ExitInspector(signals, 3_000).inspect()
        assertEquals(0, testScheduler.currentTime)
        assertTrue(r.items.any { it.id == "warning:unknown" && it.status == ItemStatus.Unverifiable })
    }
}
