package com.fitnessapp.android.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailySummaryCodecTest {

    private val full = DailySummary(
        date = "2026-08-16",
        tzOffsetMinutes = 210,
        steps = 8000,
        sleepSeconds = 43_200,
        avgHr = 70.67,
        sourceApps = listOf("com.samsung.health", "com.fitness.explorer.datagenerator"),
    )

    @Test
    fun toJson_fromJson_roundTripsAllFields() {
        val json = DailySummaryCodec.toJson(full)
        val decoded = DailySummaryCodec.fromJson(json)!!

        assertEquals(full.date, decoded.date)
        assertEquals(full.tzOffsetMinutes, decoded.tzOffsetMinutes)
        assertEquals(full.steps, decoded.steps)
        assertEquals(full.sleepSeconds, decoded.sleepSeconds)
        assertEquals(full.avgHr!!, decoded.avgHr!!, 1e-9)
        assertEquals(full.sourceApps, decoded.sourceApps)
    }

    @Test
    fun fromJson_toleratesMissingOptionalMetrics() {
        val json = JSONObject()
            .put("date", "2026-08-15")
            .put("tz_offset", 210)
            .put("steps", 3200)
            .put("source_apps", emptyJSONArray())
        val decoded = DailySummaryCodec.fromJson(json)!!
        assertEquals(3200L, decoded.steps)
        assertNull(decoded.sleepSeconds)
        assertNull(decoded.avgHr)
        assertEquals(emptyList<String>(), decoded.sourceApps)
    }

    @Test
    fun fromJson_acceptsBackendShapeWithNullOptional() {
        // BE-C1 DailyOut may serialize missing metrics as JSON null.
        val json = JSONObject(
            """{"date":"2026-08-14","tz_offset":210,"steps":0,"sleep_seconds":null,"avg_hr":null,"source_apps":[]}"""
        )
        val decoded = DailySummaryCodec.fromJson(json)!!
        assertEquals(0L, decoded.steps)
        assertNull(decoded.sleepSeconds)
        assertNull(decoded.avgHr)
    }

    @Test
    fun fromJson_returnsNullWhenNoDate() {
        assertNull(DailySummaryCodec.fromJson(JSONObject().put("steps", 1)))
    }

    @Test
    fun encodeList_decodeList_roundTripsAndSkipsCorrupt() {
        val rows = listOf(
            full,
            full.copy(date = "2026-08-15", steps = 3200, sleepSeconds = null),
        )
        val raw = DailySummaryCodec.encodeList(rows)
        val decoded = DailySummaryCodec.decodeList(raw)
        assertEquals(2, decoded.size)
        // Codec preserves array order; date sorting is the cache's job.
        assertEquals("2026-08-16", decoded[0].date)
        assertEquals("2026-08-15", decoded[1].date)
        assertEquals(8000L, decoded[0].steps)
    }

    @Test
    fun decodeList_handlesBlankAndGarbage() {
        assertEquals(emptyList<DailySummary>(), DailySummaryCodec.decodeList(""))
        assertEquals(emptyList<DailySummary>(), DailySummaryCodec.decodeList("not json at all"))
        assertEquals(emptyList<DailySummary>(), DailySummaryCodec.decodeList("[{\"steps\": 1}]")) // no date -> dropped
    }

    private fun emptyJSONArray() = org.json.JSONArray()
}
