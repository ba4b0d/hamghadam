package com.fitnessapp.android.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for [DailySummary] — used by the local cache (SharedPreferences)
 * and for parsing BE-C1 GET /api/v1/daily responses.
 *
 * Pure JVM (org.json) so it is unit-testable without a device.
 */
object DailySummaryCodec {

    const val KEY_DATE = "date"
    const val KEY_TZ_OFFSET = "tz_offset"
    const val KEY_STEPS = "steps"
    const val KEY_SLEEP_SECONDS = "sleep_seconds"
    const val KEY_AVG_HR = "avg_hr"
    const val KEY_SOURCE_APPS = "source_apps"
    const val KEY_SOURCE = "source"

    fun toJson(summary: DailySummary): JSONObject = JSONObject().apply {
        put(KEY_DATE, summary.date)
        put(KEY_TZ_OFFSET, summary.tzOffsetMinutes)
        put(KEY_STEPS, summary.steps)
        summary.sleepSeconds?.let { put(KEY_SLEEP_SECONDS, it) }
        summary.avgHr?.let { put(KEY_AVG_HR, it) }
        put(KEY_SOURCE_APPS, JSONArray(summary.sourceApps))
    }

    /** Tolerant reader: missing optional metrics become null (BE-C1 shape). */
    fun fromJson(json: JSONObject): DailySummary? {
        val date = json.optString(KEY_DATE).takeIf { it.isNotEmpty() } ?: return null
        return DailySummary(
            date = date,
            tzOffsetMinutes = json.optInt(KEY_TZ_OFFSET, 0),
            steps = json.optLong(KEY_STEPS, 0L),
            sleepSeconds = if (json.has(KEY_SLEEP_SECONDS) && !json.isNull(KEY_SLEEP_SECONDS)) json.optLong(KEY_SLEEP_SECONDS) else null,
            avgHr = if (json.has(KEY_AVG_HR) && !json.isNull(KEY_AVG_HR)) json.optDouble(KEY_AVG_HR) else null,
            sourceApps = json.optJSONArray(KEY_SOURCE_APPS)
                ?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() } }
                ?: emptyList(),
        )
    }

    /** Encode a list of rows (oldest first) into a JSON array string. */
    fun encodeList(rows: List<DailySummary>): String =
        JSONArray(rows.map { toJson(it) }).toString()

    /** Decode a list-of-rows JSON string; unknown/corrupt entries are dropped. */
    fun decodeList(raw: String): List<DailySummary> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .mapNotNull { fromJson(arr.optJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
