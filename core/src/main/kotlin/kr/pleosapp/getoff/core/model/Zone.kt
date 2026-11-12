package kr.pleosapp.getoff.core.model

import java.time.DayOfWeek

data class GeoPoint(val lat: Double, val lng: Double)

enum class ZoneType(val label: String) {
    HYDRANT("소화전 주변"),
    CROSSWALK("횡단보도 주변"),
    BUS_STOP("버스정류장 주변"),
    SCHOOL_ZONE("어린이보호구역"),
    OTHER("주정차 제한 구간"),
}

sealed interface Geometry {
    data class Circle(val center: GeoPoint, val radiusM: Double) : Geometry
    data class Polygon(val points: List<GeoPoint>) : Geometry
}

/** [days]가 비어 있으면 매일. 분 단위(0..1439). start > end 이면 자정을 넘는 구간. */
data class ActiveHours(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int) {
    fun label(): String = "%02d:%02d~%02d:%02d".format(startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60)
}

data class Zone(
    val id: String,
    val name: String,
    val type: ZoneType,
    val geometry: Geometry,
    val activeHours: ActiveHours? = null,
)
