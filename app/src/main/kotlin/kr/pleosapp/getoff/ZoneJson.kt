package kr.pleosapp.getoff

import kr.pleosapp.getoff.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** assets/restricted_zones_sample.json 파서. 형식이 틀린 구역은 건너뛰고 나머지를 쓴다. */
object ZoneJson {
    fun parse(json: String): List<Zone> {
        val arr = JSONObject(json).optJSONArray("zones") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { runCatching { zone(arr.getJSONObject(it)) }.getOrNull() }
    }

    private fun zone(o: JSONObject): Zone {
        val geometry = if (o.has("circle")) {
            o.getJSONObject("circle").let {
                Geometry.Circle(GeoPoint(it.getDouble("lat"), it.getDouble("lng")), it.getDouble("radiusM"))
            }
        } else {
            val a = o.getJSONArray("polygon")
            Geometry.Polygon((0 until a.length()).map { i ->
                a.getJSONArray(i).let { GeoPoint(it.getDouble(0), it.getDouble(1)) }
            })
        }
        return Zone(
            id = o.getString("id"),
            name = o.getString("name"),
            type = runCatching { ZoneType.valueOf(o.getString("type")) }.getOrDefault(ZoneType.OTHER),
            geometry = geometry,
            activeHours = o.optJSONObject("activeHours")?.let(::hours),
        )
    }

    private fun hours(h: JSONObject): ActiveHours {
        val days: JSONArray = h.optJSONArray("days") ?: JSONArray()
        val set = (0 until days.length()).map { i ->
            DayOfWeek.entries.first { it.name.startsWith(days.getString(i).uppercase()) }
        }.toSet()
        return ActiveHours(set, minute(h.getString("start")), minute(h.getString("end")))
    }

    private fun minute(hhmm: String): Int = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
}
