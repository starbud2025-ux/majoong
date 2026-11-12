package kr.pleosapp.getoff.core

import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.zone.ZoneMatcher
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneMatcherTest {
    private val center = GeoPoint(37.5665, 126.9780)
    private val circle = Zone("z1", "시청 앞", ZoneType.CROSSWALK, Geometry.Circle(center, 50.0))
    private val now = LocalDateTime.of(2026, 11, 9, 9, 0) // 월요일

    @Test fun `원 안은 매칭, 밖은 아님`() {
        assertEquals(1, ZoneMatcher.match(center, listOf(circle), now).size)
        val far = GeoPoint(37.5665 + 0.001, 126.9780) // 약 111m
        assertTrue(ZoneMatcher.match(far, listOf(circle), now).isEmpty())
    }

    @Test fun `폴리곤 안팎`() {
        val poly = Zone("z2", "정류장", ZoneType.BUS_STOP, Geometry.Polygon(listOf(
            GeoPoint(37.0, 127.0), GeoPoint(37.0, 127.001), GeoPoint(37.001, 127.001), GeoPoint(37.001, 127.0),
        )))
        assertTrue(ZoneMatcher.contains(poly.geometry, GeoPoint(37.0005, 127.0005)))
        assertFalse(ZoneMatcher.contains(poly.geometry, GeoPoint(37.002, 127.0005)))
    }

    @Test fun `시간대 밖이면 activeNow가 false`() {
        val weekdays = ActiveHours(setOf(DayOfWeek.MONDAY), 8 * 60, 20 * 60)
        val z = circle.copy(activeHours = weekdays)
        assertTrue(ZoneMatcher.match(center, listOf(z), now).single().activeNow)
        assertFalse(ZoneMatcher.match(center, listOf(z), now.withHour(21)).single().activeNow)
        assertFalse(ZoneMatcher.match(center, listOf(z), now.plusDays(1)).single().activeNow)
    }

    @Test fun `자정을 넘는 시간대`() {
        val night = ActiveHours(emptySet(), 22 * 60, 6 * 60)
        assertTrue(ZoneMatcher.isActive(night, now.withHour(23)))
        assertTrue(ZoneMatcher.isActive(night, now.withHour(5)))
        assertFalse(ZoneMatcher.isActive(night, now.withHour(12)))
    }

    @Test fun `시간 제한 없으면 항상 적용`() {
        assertTrue(ZoneMatcher.isActive(null, now))
    }
}
