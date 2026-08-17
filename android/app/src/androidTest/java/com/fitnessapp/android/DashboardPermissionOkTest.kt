package com.fitnessapp.android

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnessapp.android.data.HcStatus
import com.fitnessapp.android.data.HealthConnectRepository
import com.fitnessapp.android.data.cache.PrefsDailySummaryCache
import com.fitnessapp.android.data.model.DailySummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Instrumented, permission-ok path: on a device where Health Connect is
 * installed AND the app holds the v1 READ_STEPS grant, the repository must
 * return a real today aggregate and the local cache must round-trip.
 *
 * Skips cleanly when Health Connect is unavailable or the permission is missing,
 * so CI/emulators without grants don't fail — they simply don't exercise it.
 */
@RunWith(AndroidJUnit4::class)
class DashboardPermissionOkTest {

    private lateinit var context: Context
    private lateinit var repo: HealthConnectRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = HealthConnectRepository(context)
    }

    private fun stepsReadPermissionGranted(): Boolean = runBlocking {
        HealthPermission.getReadPermission(StepsRecord::class) in repo.grantedPermissions()
    }

    @Test
    fun permissionOkPath_readsRealTodayAggregate() = runBlocking {
        assumeTrue("Health Connect must be available", repo.status() == HcStatus.AVAILABLE)
        assumeTrue("READ_STEPS must be granted", stepsReadPermissionGranted())

        val today = LocalDate.now()
        val summary = repo.readDailySummary(today)

        assertNotNull("readDailySummary must return a row with permissions granted", summary)
        summary!!

        assertEquals("Row must be dated today", today.toString(), summary.date)
        assertTrue("Steps must be non-negative", summary.steps >= 0)
        assertTrue("tz_offset must be within ±14h", summary.tzOffsetMinutes in -840..840)
        assertNotNull("Source list must never be null", summary.sourceApps)
        // Real Health Connect data carries a data origin package for each record:
        // whenever any step/sleep/HR record exists, attribution must name a source.
        assertTrue(
            "Attribution should list at least one source app when records exist",
            summary.steps == 0L && summary.sourceApps.isEmpty() || summary.sourceApps.isNotEmpty(),
        )
    }

    @Test
    fun permissionOkPath_cacheRoundTripsOnDevice() {
        val cache = PrefsDailySummaryCache(
            context.getSharedPreferences("fitness_app_daily_cache_test", Context.MODE_PRIVATE),
        )
        cache.clear()
        val row = DailySummary(
            date = LocalDate.now().toString(),
            tzOffsetMinutes = 210,
            steps = 8000,
            sleepSeconds = 43_200,
            avgHr = 71.0,
            sourceApps = listOf("com.samsung.health"),
        )
        cache.save(row)

        assertEquals(row.steps, cache.get(row.date)!!.steps)
        assertEquals(1, cache.loadAll().size)
        assertEquals(
            1,
            cache.range(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)).size,
        )
        cache.clear()
    }
}
