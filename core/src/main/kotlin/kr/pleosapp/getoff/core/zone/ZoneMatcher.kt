package kr.pleosapp.getoff.core.zone

import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.signals.ZoneRepository
import java.time.LocalDateTime
import kotlin.math.*

data class ZoneHit(val zone: Zone, val distanceM: Double, val activeNow: Boolean)

object ZoneMatcher {
    private const val EARTH_M = 6_371_000.0

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_M * asin(sqrt(h))
    }

    fun contains(g: Geometry, p: GeoPoint): Boolean = when (g) {
        is Geometry.Circle -> distanceM(g.center, p) <= g.radiusM
        is Geometry.Polygon -> inPolygon(g.points, p)
    }

    /** 레이 캐스팅. 도심 규모(수백 m)에서는 평면 근사로 충분하다. */
    private fun inPolygon(pts: List<GeoPoint>, p: GeoPoint): Boolean {
        if (pts.size < 3) return false
        var inside = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) &&
                p.lng < (b.lng - a.lng) * (p.lat - a.lat) / (b.lat - a.lat) + a.lng
            ) inside = !inside
            j = i
        }
        return inside
    }

    /** [hours]가 null이면 시간 제한 없음(항상 적용). 자정을 넘는 구간의 새벽 부분은 전날 요일 기준. */
    fun isActive(hours: ActiveHours?, now: LocalDateTime): Boolean {
        if (hours == null) return true
        val minute = now.hour * 60 + now.minute
        val wraps = hours.startMinute > hours.endMinute
        val inRange = if (!wraps) minute in hours.startMinute until hours.endMinute
        else minute >= hours.startMinute || minute < hours.endMinute
        val day = if (wraps && minute < hours.endMinute) now.dayOfWeek.minus(1) else now.dayOfWeek
        return inRange && (hours.days.isEmpty() || day in hours.days)
    }

    fun distanceToZone(g: Geometry, p: GeoPoint): Double = when (g) {
        is Geometry.Circle -> max(0.0, distanceM(g.center, p) - g.radiusM)
        is Geometry.Polygon -> if (inPolygon(g.points, p)) 0.0 else g.points.minOf { distanceM(it, p) }
    }

    /** 현재 위치가 들어 있는 구역. 시간대 밖이면 activeNow=false로 반환하며 경고 여부는 호출자가 정한다. */
    fun match(p: GeoPoint, zones: List<Zone>, now: LocalDateTime): List<ZoneHit> =
        zones.filter { contains(it.geometry, p) }
            .map { ZoneHit(it, distanceToZone(it.geometry, p), isActive(it.activeHours, now)) }
            .sortedBy { it.distanceM }
}

/** 메모리 구역 저장소. 데이터 로딩(JSON 등)은 app 쪽에서 한다. */
class InMemoryZoneRepository(private val zones: List<Zone>) : ZoneRepository {
    override suspend fun zonesNear(p: GeoPoint, radiusM: Int): List<Zone> =
        zones.filter { ZoneMatcher.distanceToZone(it.geometry, p) <= radiusM }
}
