package kr.pleosapp.getoff.core.model

enum class Gear { P, D, R, N, Unknown }

enum class Seat(val label: String) {
    DRIVER("운전석"), PASSENGER("조수석"), REAR_LEFT("뒷좌석 왼쪽"), REAR_RIGHT("뒷좌석 오른쪽")
}

enum class DoorId(val label: String) {
    DRIVER("운전석"), PASSENGER("조수석"), REAR_LEFT("뒷좌석 왼쪽"), REAR_RIGHT("뒷좌석 오른쪽"), TRUNK("트렁크")
}

/** 창문·도어 공통. [Unknown]은 "정상"이 아니다(AC2.3). */
enum class OpenState { Open, Closed, Unknown }

enum class Occupancy { Occupied, Empty, Unknown }

/** 탑승·벨트 판단용 좌석 위치. 창문(Seat)과 달리 뒷줄 가운데가 있다. SDK의 SeatValue(ROW_n × LEFT/CENTER/RIGHT)에 대응. */
enum class SeatPos(val label: String, val isRear: Boolean) {
    DRIVER("운전석", false),
    PASSENGER("조수석", false),
    REAR_LEFT("뒷좌석 왼쪽", true),
    REAR_CENTER("뒷좌석 가운데", true),
    REAR_RIGHT("뒷좌석 오른쪽", true),
}

/** Unknown은 "착용"이 아니다(AC5.2). */
enum class BeltState { Buckled, Unbuckled, Unknown }

/** 정차 방향: 내리는 쪽(인도 쪽)이 어느 쪽인가. 설정으로 바꾼다. */
enum class CurbSide(val label: String, val rearDoor: DoorId) {
    RIGHT("오른쪽", DoorId.REAR_RIGHT),
    LEFT("왼쪽", DoorId.REAR_LEFT),
}

data class Warning(val code: String, val message: String)

data class ClimateState(
    val defrosterOn: Boolean? = null,
    val wiperActive: Boolean? = null,
    val sideMirrorHeatOn: Boolean? = null,
    val steeringHeatOn: Boolean? = null,
    val outsideTempC: Double? = null,
)

/** 기능 단위 지원 여부 조회 대상. 도메인별 `checkXXXCapabilities`에 대응. */
enum class Feature {
    WINDOW, DOOR, SAFETY, REAR_OCCUPANCY, SEAT_BELT, DEFROSTER, WIPER, SIDE_MIRROR_HEAT, STEERING_HEAT, TTS, LOCATION
}

sealed interface Capability {
    data object Supported : Capability
    data class Unsupported(val reason: String) : Capability
    data class PermissionDenied(val reason: String) : Capability
}

/** 점검 시점에 모은 값. 읽지 못한 값은 Unknown / null. */
data class VehicleSnapshot(
    val windows: Map<Seat, OpenState> = emptyMap(),
    val doors: Map<DoorId, OpenState> = emptyMap(),
    val rearOccupancy: Occupancy = Occupancy.Unknown,
    val warnings: List<Warning>? = null,
    val timedOut: Boolean = false,
)
