package kr.pleosapp.getoff.core.fake

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.signals.*

/**
 * SDK·에뮬레이터 없이 모든 시나리오를 재현하는 가짜 차량. 순수 Kotlin이라 단위 테스트와 데모 빌드가 함께 쓴다.
 * 값은 모두 MutableStateFlow라서 시나리오가 중간에 바꿀 수 있다.
 */
class FakeVehicle : VehicleSignals, ClimateController, LocationSource, UxRestrictions {
    val gearState = MutableStateFlow(Gear.D)
    val windowState = MutableStateFlow(Seat.entries.associateWith { OpenState.Closed })
    val doorState = MutableStateFlow(DoorId.entries.associateWith { OpenState.Closed })
    /** 뒷줄 좌석별 탑승/벨트. rearOccupancy는 여기서 파생된다. */
    val seatOccupancyState = MutableStateFlow(rearSeats.associateWith { Occupancy.Empty })
    val seatBeltState = MutableStateFlow(rearSeats.associateWith { BeltState.Buckled })
    val warningState = MutableStateFlow<List<Warning>>(emptyList())
    val climateState = MutableStateFlow(ClimateState())
    val locationState = MutableStateFlow<GeoPoint?>(null)
    val restrictedState = MutableStateFlow(false)

    /** true면 해당 신호는 영영 오지 않는다(타임아웃 시험용). */
    var hangWindows = false

    val capabilities = mutableMapOf<Feature, Capability>()
    val stepResults = mutableMapOf<RoutineStep, StepResult>()
    var stepDelayMs = 0L
    val applied = mutableListOf<RoutineStep>()

    override val gear: Flow<Gear> = gearState
    override val windows: Flow<Map<Seat, OpenState>> = flow {
        if (hangWindows) awaitCancellation() else emitAll(windowState)
    }
    override val doors: Flow<Map<DoorId, OpenState>> = doorState
    override val rearOccupancy: Flow<Occupancy> = seatOccupancyState.map { m ->
        val v = m.values
        when {
            v.any { it == Occupancy.Occupied } -> Occupancy.Occupied
            v.any { it == Occupancy.Unknown } -> Occupancy.Unknown
            else -> Occupancy.Empty
        }
    }
    override val seatOccupancy: Flow<Map<SeatPos, Occupancy>> = seatOccupancyState
    override val seatBelts: Flow<Map<SeatPos, BeltState>> = seatBeltState
    override val warnings: Flow<List<Warning>> = warningState
    override val climate: Flow<ClimateState> = climateState
    override val location: Flow<GeoPoint?> = locationState
    override val drivingBlocked: Flow<Boolean> = restrictedState

    /** 뒷줄 전체를 같은 값으로 설정하는 단축 함수(테스트·데모용). */
    fun setRear(o: Occupancy) { seatOccupancyState.value = rearSeats.associateWith { o } }

    override suspend fun capability(feature: Feature): Capability = capabilities[feature] ?: Capability.Supported

    override suspend fun apply(step: RoutineStep): StepResult {
        if (stepDelayMs > 0) delay(stepDelayMs)
        applied += step
        return stepResults[step] ?: StepResult.Success
    }
}

private val rearSeats = SeatPos.entries.filter { it.isRear }

/** 말한 문장을 기록만 한다. 데모에서는 화면 자막으로도 쓴다. */
class RecordingSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    override suspend fun speak(text: String) { spoken += text }
}
