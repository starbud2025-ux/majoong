package kr.pleosapp.getoff.core.signals

import kotlinx.coroutines.flow.Flow
import kr.pleosapp.getoff.core.model.*

/** 차량 → 앱. 콜백/Flow 기반이며 폴링하지 않는다. 읽지 못한 값은 Unknown. */
interface VehicleSignals {
    val gear: Flow<Gear>
    val windows: Flow<Map<Seat, OpenState>>
    val doors: Flow<Map<DoorId, OpenState>>
    val rearOccupancy: Flow<Occupancy>
    /** 좌석별 탑승(뒷줄 개별 판단). 맵에 없는 좌석은 Unknown으로 취급한다. */
    val seatOccupancy: Flow<Map<SeatPos, Occupancy>>
    /** 좌석별 안전벨트. 맵에 없는 좌석은 Unknown으로 취급한다. */
    val seatBelts: Flow<Map<SeatPos, BeltState>>
    val warnings: Flow<List<Warning>>
    val climate: Flow<ClimateState>
    suspend fun capability(feature: Feature): Capability
}

/** 앱 → 차량 제어(F4). 상태를 확인한 뒤 결과를 반환한다. */
interface ClimateController {
    suspend fun apply(step: RoutineStep): StepResult
}

interface LocationSource { val location: Flow<GeoPoint?> }
interface Speaker { suspend fun speak(text: String) }
interface UxRestrictions { val drivingBlocked: Flow<Boolean> }
interface ZoneRepository { suspend fun zonesNear(p: GeoPoint, radiusM: Int): List<Zone> }
