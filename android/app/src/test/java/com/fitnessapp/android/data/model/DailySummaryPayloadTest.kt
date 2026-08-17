package com.fitnessapp.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySummaryPayloadTest {

    @Test
    fun payload_matchesBeC1Contract_shape() {
        val summary = DailySummary(
            date = "2026-08-16",
            tzOffsetMinutes = -420,
            steps = 12345,
            sleepSeconds = 28800,
            avgHr = 70.5,
            sourceApps = listOf("com.samsung.health"),
        )
        val payload = summary.toPayloadMap()

        assertEquals("2026-08-16", payload["date"])
        assertEquals(-420, payload["tz_offset"])
        assertEquals(12345L, payload["steps"])
        assertEquals(28800L, payload["sleep_seconds"])
        assertEquals(70.5, payload["avg_hr"])
        assertEquals(listOf("com.samsung.health"), payload["source_apps"])
        assertEquals("health_connect", payload["source"])
        // BE-C1: identity comes from the JWT — no user field in the body.
        assertFalse("payload must not contain a user field", payload.containsKey("user"))
    }

    @Test
    fun payload_omitsOptionalFieldsWhenNull() {
        val summary = DailySummary(
            date = "2026-08-16",
            tzOffsetMinutes = 210,
            steps = 0,
            sourceApps = emptyList(),
        )
        val payload = summary.toPayloadMap()
        assertNull(payload["sleep_seconds"])
        assertNull(payload["avg_hr"])
        assertEquals(0L, payload["steps"])
        assertTrue((payload["source_apps"] as List<*>).isEmpty())
    }
}
