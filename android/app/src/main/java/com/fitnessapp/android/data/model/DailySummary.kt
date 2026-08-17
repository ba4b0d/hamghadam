package com.fitnessapp.android.data.model

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import java.time.Instant

/**
 * One local day of health metrics aggregated from Health Connect.
 *
 * @param date           user's local calendar date, YYYY-MM-DD
 * @param tzOffsetMinutes minutes east of UTC at aggregation time (BE-C1 contract)
 * @param steps          total steps for the local day (StepsRecord REQUIRED)
 * @param sleepSeconds   total sleep seconds attributed to this day (optional, permission-gated)
 * @param avgHr          average resting/tracked heart rate bpm (optional, permission-gated)
 * @param sourceApps     distinct packages that wrote the underlying records (data origins)
 */
data class DailySummary(
    val date: String,
    val tzOffsetMinutes: Int,
    val steps: Long,
    val sleepSeconds: Long? = null,
    val avgHr: Double? = null,
    val sourceApps: List<String> = emptyList(),
) {
    /** BE-C1 POST /api/v1/daily body as a plain map (identity comes from the JWT, not the body). */
    fun toPayloadMap(): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "date" to date,
            "tz_offset" to tzOffsetMinutes,
            "steps" to steps,
            "source_apps" to sourceApps,
            "source" to "health_connect", // anti-cheat: device-generated data only
        )
        sleepSeconds?.let { payload["sleep_seconds"] = it }
        avgHr?.let { payload["avg_hr"] = it }
        return payload
    }
}

/**
 * Pure aggregation helpers over Health Connect records. Kept free of Android
 * framework dependencies so they are unit-testable on the JVM.
 */
object DailyAggregator {

    fun totalSteps(records: List<StepsRecord>): Long =
        records.sumOf { it.count }

    /**
     * Sleep attribution rule: a session belongs to the local day it ENDS in
     * (the "wake day"), and its full duration counts toward that day.
     * Sessions that end exactly at local midnight belong to that new day.
     */
    fun sleepSeconds(
        records: List<SleepSessionRecord>,
        dayStart: Instant,
        dayEnd: Instant,
    ): Long {
        return records
            .asSequence()
            .filter { session ->
                !session.endTime.isBefore(dayStart) && session.endTime.isBefore(dayEnd)
            }
            .sumOf { session ->
                val seconds = session.endTime.epochSecond - session.startTime.epochSecond
                seconds.coerceIn(0L, 24L * 3600L)
            }
    }

    /** Average of all non-zero HR samples in the day. Null when no valid samples. */
    fun averageHr(records: List<HeartRateRecord>): Double? {
        val samples = records
            .flatMap { it.samples }
            .map { it.beatsPerMinute }
            .filter { it > 0L }
        return if (samples.isEmpty()) null else samples.average()
    }

    /** Distinct source packages (data origins) for a set of records, sorted. */
    fun sourceApps(records: List<Record>): List<String> =
        sourceAppsFromPackageNames(records.mapNotNull { it.metadata?.dataOrigin?.packageName })

    /** Pure helper (unit-testable): dedupe + sort package names. */
    fun sourceAppsFromPackageNames(packages: List<String>): List<String> =
        packages.distinct().sorted()
}
