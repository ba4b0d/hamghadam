package com.fitnessapp.android.data.model

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class DailyAggregatorTest {

    private val originA = DataOrigin("com.samsung.health")
    private val originB = DataOrigin("com.google.android.apps.fitness")

    private fun metadata(origin: DataOrigin) = Metadata.autoRecorded(
        device = Device(type = Device.TYPE_PHONE),
    )

    @Test
    fun totalSteps_sumsAllRecordsInDay() {
        val start = Instant.parse("2026-08-16T00:00:00Z")
        val end = Instant.parse("2026-08-16T23:59:59Z")
        val records = listOf(
            StepsRecord(start, ZoneOffset.UTC, end, ZoneOffset.UTC, 5000, metadata(originA)),
            StepsRecord(end.minusSeconds(60), ZoneOffset.UTC, end, ZoneOffset.UTC, 3200, metadata(originA)),
        )
        assertEquals(8200L, DailyAggregator.totalSteps(records))
    }

    @Test
    fun sleepSeconds_attributesSessionToWakeDay() {
        // Session 23:00 prev day -> 07:00 today ends inside today: counted fully.
        val dayStart = Instant.parse("2026-08-16T00:00:00Z")
        val dayEnd = Instant.parse("2026-08-17T00:00:00Z")
        val overnight = SleepSessionRecord(
            startTime = dayStart.minus(Duration.ofHours(1)),
            startZoneOffset = ZoneOffset.UTC,
            endTime = dayStart.plus(Duration.ofHours(7)),
            endZoneOffset = ZoneOffset.UTC,
            stages = emptyList(),
            metadata = metadata(originA),
        )
        assertEquals(8 * 3600L, DailyAggregator.sleepSeconds(listOf(overnight), dayStart, dayEnd))
    }

    @Test
    fun sleepSeconds_excludesSessionEndingNextDay() {
        val dayStart = Instant.parse("2026-08-16T00:00:00Z")
        val dayEnd = Instant.parse("2026-08-17T00:00:00Z")
        val crossing = SleepSessionRecord(
            startTime = dayStart.plus(Duration.ofHours(20)),
            startZoneOffset = ZoneOffset.UTC,
            endTime = dayEnd.plus(Duration.ofHours(6)),
            endZoneOffset = ZoneOffset.UTC,
            stages = emptyList(),
            metadata = metadata(originA),
        )
        assertEquals(0L, DailyAggregator.sleepSeconds(listOf(crossing), dayStart, dayEnd))
    }

    @Test
    fun averageHr_averagesValidSamples_andReturnsNullWhenEmpty() {
        val t = Instant.parse("2026-08-16T09:00:00Z")
        val samples = listOf(
            HeartRateRecord.Sample(t, 65),
            HeartRateRecord.Sample(t.plusSeconds(1), 72),
            HeartRateRecord.Sample(t.plusSeconds(2), 75),
        )
        val record = HeartRateRecord(
            startTime = t,
            startZoneOffset = ZoneOffset.UTC,
            endTime = t.plusSeconds(4),
            endZoneOffset = ZoneOffset.UTC,
            samples = samples,
            metadata = metadata(originB),
        )
        assertEquals((65.0 + 72.0 + 75.0) / 3.0, DailyAggregator.averageHr(listOf(record))!!, 1e-9)
        assertNull(DailyAggregator.averageHr(emptyList()))
    }

    @Test
    fun sourceApps_dedupesAndSortsPackageNames() {
        assertEquals(
            listOf("com.google.android.apps.fitness", "com.samsung.health"),
            DailyAggregator.sourceAppsFromPackageNames(
                listOf("com.samsung.health", "com.google.android.apps.fitness", "com.samsung.health"),
            ),
        )
        // On-device the dataOrigin comes from record metadata; the record-level
        // mapper simply extracts packageName (may be empty in pure-JVM tests).
        assertEquals(emptyList<String>(), DailyAggregator.sourceApps(emptyList()))
    }
}
