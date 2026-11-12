package kr.pleosapp.getoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.pleosapp.getoff.core.fake.FakeVehicle
import kr.pleosapp.getoff.core.model.*

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) = OutlinedButton(onClick = onClick) { Text(label) }

/** 심사용 3분 데모(docs/02)를 에뮬레이터 없이 재현하는 조작판. fake 빌드에만 포함된다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(v: FakeVehicle) {
    val inZone = GeoPoint(37.5665, 126.9780)
    val outZone = GeoPoint(37.5800, 126.9900)
    FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DemoButton("① 구역 안 P 정차") { v.locationState.value = inZone; v.gearState.value = Gear.P }
        DemoButton("구역 밖 P 정차") { v.locationState.value = outZone; v.gearState.value = Gear.P }
        DemoButton("② 조수석 창문 열기") { v.windowState.value = v.windowState.value + (Seat.PASSENGER to OpenState.Open) }
        DemoButton("뒷좌석 탑승") { v.setRear(Occupancy.Occupied) }
        DemoButton("뒷좌석 감지 불가") { v.setRear(Occupancy.Unknown) }
        DemoButton("창문 닫기·하차") {
            v.windowState.value = Seat.entries.associateWith { OpenState.Closed }
            v.setRear(Occupancy.Empty)
        }
        // 등·하원 픽업 모드 시나리오 (홈에서 "픽업 모드"를 켠 뒤 사용)
        DemoButton("[픽업] 아이 2명 탑승(오른쪽 벨트 미착용)") {
            v.seatOccupancyState.value = v.seatOccupancyState.value +
                (SeatPos.REAR_LEFT to Occupancy.Occupied) + (SeatPos.REAR_RIGHT to Occupancy.Occupied)
            v.seatBeltState.value = v.seatBeltState.value +
                (SeatPos.REAR_LEFT to BeltState.Buckled) + (SeatPos.REAR_RIGHT to BeltState.Unbuckled)
        }
        DemoButton("[픽업] 벨트 착용") {
            v.seatBeltState.value = v.seatBeltState.value.mapValues { BeltState.Buckled }
        }
        DemoButton("[픽업] 벨트 읽기 실패") {
            v.seatBeltState.value = v.seatBeltState.value.mapValues { BeltState.Unknown }
        }
        DemoButton("[픽업] 뒷문 오른쪽 열림") {
            v.doorState.value = v.doorState.value + (DoorId.REAR_RIGHT to OpenState.Open)
        }
        DemoButton("[픽업] 뒷문 왼쪽 열림") {
            v.doorState.value = v.doorState.value + (DoorId.REAR_LEFT to OpenState.Open)
        }
        DemoButton("[픽업] 뒷문 닫음") {
            v.doorState.value = v.doorState.value + (DoorId.REAR_LEFT to OpenState.Closed) + (DoorId.REAR_RIGHT to OpenState.Closed)
        }
        DemoButton("[픽업] 벨트 미지원 차량") { v.capabilities[Feature.SEAT_BELT] = Capability.Unsupported("차량 미지원") }
        DemoButton("핸들 열선 미지원") { v.capabilities[Feature.STEERING_HEAT] = Capability.Unsupported("차량 미지원") }
        DemoButton("와이퍼 확인 실패") { v.stepResults[RoutineStep.WIPER_ONCE] = StepResult.Unverified }
        DemoButton("모두 지원·성공") { v.capabilities.clear(); v.stepResults.clear() }
        DemoButton("④ 주행 시작(D)") { v.gearState.value = Gear.D }
    }
}
