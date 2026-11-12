package kr.pleosapp.getoff.core.engine

import kr.pleosapp.getoff.core.model.*

data class SeatStatus(val seat: SeatPos, val occupancy: Occupancy, val belt: BeltState)

enum class BoardingStatus {
    /** 탑승자가 아직 없다. */
    Waiting,
    /** 탑승자 중 벨트를 안 맨 사람이 있다. */
    Unbuckled,
    /** 읽지 못한 값이 있어 확정할 수 없다(Unknown은 정상이 아니다, AC5.2). */
    NeedsCheck,
    /** 탑승자 전원 벨트 착용이 확인됨. */
    Ready,
}

/** 뒷줄 좌석별 판정. 좌석을 하나씩 따로 본다(AC5.2). */
data class BoardingReport(val seats: List<SeatStatus>, val degraded: Boolean = false) {
    val occupied get() = seats.filter { it.occupancy == Occupancy.Occupied }
    val unbuckled get() = occupied.filter { it.belt == BeltState.Unbuckled }

    val status: BoardingStatus
        get() = when {
            unbuckled.isNotEmpty() -> BoardingStatus.Unbuckled
            // 미지원 차량이거나 값을 못 읽으면 "준비 완료"를 말하지 않는다(AC5.3, AC5.6)
            degraded || seats.any { it.occupancy == Occupancy.Unknown } || occupied.any { it.belt == BeltState.Unknown } ->
                BoardingStatus.NeedsCheck
            occupied.isEmpty() -> BoardingStatus.Waiting
            else -> BoardingStatus.Ready
        }
}

enum class PickupPhase { Boarding, Alighting }

object PickupEvaluator {
    val rearDoors = listOf(DoorId.REAR_LEFT, DoorId.REAR_RIGHT)
    val rearSeats = SeatPos.entries.filter { it.isRear }

    fun boarding(
        occupancy: Map<SeatPos, Occupancy>,
        belts: Map<SeatPos, BeltState>,
        degraded: Boolean,
    ) = BoardingReport(
        rearSeats.map { SeatStatus(it, occupancy[it] ?: Occupancy.Unknown, belts[it] ?: BeltState.Unknown) },
        degraded,
    )

    fun openRearDoors(doors: Map<DoorId, OpenState>): Set<DoorId> =
        rearDoors.filter { doors[it] == OpenState.Open }.toSet()

    /** 상태에서 말할 문장. 같은 문장이 반복되면 호출자가 다시 말하지 않는다. */
    fun voice(s: AppState.PickupActive): String? = when (s.phase) {
        PickupPhase.Alighting ->
            if (s.wrongSideOpen) Phrases.pickupWrongSide(s.curbSide) else Phrases.pickupCurb(s.curbSide)
        PickupPhase.Boarding -> when (s.boarding.status) {
            BoardingStatus.Unbuckled -> Phrases.pickupUnbuckled(s.boarding.unbuckled.map { it.seat })
            BoardingStatus.NeedsCheck -> Phrases.PICKUP_CHECK
            BoardingStatus.Ready -> Phrases.PICKUP_BELTS_OK
            BoardingStatus.Waiting -> null
        }
    }
}
