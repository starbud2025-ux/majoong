package kr.pleosapp.getoff.core.engine

import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.zone.ZoneHit

/** UI·TTS 문구 모음. 확정 표현을 피한다(AC1.4). */
object Phrases {
    const val LOCATION_UNKNOWN = "위치를 확인하지 못했어요"
    const val ALL_CLEAR = "모두 정상이에요"
    const val REAR_GENERIC = "뒷좌석을 확인하세요"
    const val REAR_OCCUPIED = "뒷좌석에 탑승자가 있어요. 함께 내렸는지 확인하세요"
    const val PASSENGER_TIP = "내릴 때 주변 차와 자전거를 살펴보세요"
    const val BLOCKED = "정차 후 이용하세요"
    const val ROUTINE_DONE = "시야가 확보되면 출발하세요"
    const val SETTINGS_KEPT = "이미 바꾼 설정은 그대로 두었어요"
    const val WARNINGS_UNKNOWN = "차량 경고 정보는 확인 불가예요"

    fun zoneWarning(hit: ZoneHit): String {
        val hours = hit.zone.activeHours?.let { " (${it.label()})" } ?: ""
        return "여기는 ${hit.zone.type.label} 근처라 주정차 금지 구역일 수 있어요$hours"
    }

    fun windowOpen(seat: Seat) = "${seat.label} 창문이 열려 있어요"
    fun windowUnknown(seat: Seat) = "${seat.label} 창문은 확인 불가예요"
    fun doorOpen(door: DoorId) = "${door.label} 도어가 열려 있어요"
    fun doorUnknown(door: DoorId) = "${door.label} 도어는 확인 불가예요"
    fun warning(w: Warning) = "차량 경고: ${w.message}"

    // 픽업 모드. 확정 표현을 피하고, 벨트는 "확인했어요"까지만 말한다.
    const val PICKUP_BELTS_OK = "뒷좌석 안전벨트를 모두 확인했어요"
    const val PICKUP_CHECK = "뒷좌석 안전벨트를 확인하세요"
    fun pickupUnbuckled(seats: List<SeatPos>) = "${seats.joinToString(", ") { it.label }} 안전벨트를 확인하세요"
    fun pickupCurb(side: CurbSide) = "${side.label} 문으로 내리세요"
    fun pickupWrongSide(side: CurbSide) = "차도 쪽 문이에요. ${side.label} 문으로 내려 주세요"

    fun routineSkip(step: RoutineStep, reason: String) = "${step.label}: 건너뜀 ($reason). ${step.manualHint}"
}
