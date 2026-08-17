package com.fitnessapp.android.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.ExperimentalMatchmakingApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.matchmaking.MatchmakingRequest
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fitnessapp.android.data.model.DailyAggregator
import com.fitnessapp.android.data.model.DailySummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/** Health Connect availability, mirrored from the SDK status codes. */
enum class HcStatus {
    AVAILABLE,
    UNAVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    UNKNOWN,
}

/** Wraps the read-only Health Connect access used by the app (v1: no writes). */
class HealthConnectRepository(private val context: Context) {

    companion object {
        private const val TAG = "HealthRepo"
        const val PERM_READ_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        /**
         * v1 read types — STEPS ONLY (Play-ready scope).
         *
         * We must only request the READ_* types a live feature actually powers.
         * v1 ships step challenges only, so READ_STEPS is the sole permission we
         * request and surface as granted. Sleep & heart rate stay out of the
         * consent flow until a feature (sleep/HR challenge) uses them.
         *
         * Each requested READ_* maps to a function it powers (checklist 1.4):
         *   READ_STEPS -> step challenges + dashboard steps.
         */
        val REQUIRED_READ_TYPES: List<KClass<out Record>> = listOf(
            StepsRecord::class,
        )
    }

    private var client: HealthConnectClient? = null

    /** Permissions we will request from the Health Connect consent UI. */
    fun requiredReadPermissionStrings(): Set<String> =
        REQUIRED_READ_TYPES.map { HealthPermission.getReadPermission(it) }.toSet()

    fun status(): HcStatus {
        return try {
            when (HealthConnectClient.getSdkStatus(context)) {
                SDK_AVAILABLE -> HcStatus.AVAILABLE
                SDK_UNAVAILABLE -> HcStatus.UNAVAILABLE
                SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcStatus.PROVIDER_UPDATE_REQUIRED
                else -> HcStatus.UNKNOWN
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSdkStatus failed", e)
            HcStatus.UNKNOWN
        }
    }

    private fun clientOrNull(): HealthConnectClient? {
        if (client == null && status() == HcStatus.AVAILABLE) {
            client = HealthConnectClient.getOrCreate(context)
        }
        return client
    }

    suspend fun grantedPermissions(): Set<String> =
        clientOrNull()?.permissionController?.getGrantedPermissions() ?: emptySet()

    suspend fun missingReadPermissions(): Set<String> {
        val granted = grantedPermissions()
        return requiredReadPermissionStrings() - granted
    }

    suspend fun hasBackgroundReadPermission(): Boolean =
        PERM_READ_BACKGROUND in grantedPermissions()

    /**
     * Aggregate one local calendar day. Steps are read over [dayStart, dayEnd];
     * sleep sessions are attributed by wake day (see [DailyAggregator.sleepSeconds]).
     */
    suspend fun readDailySummary(day: LocalDate = LocalDate.now()): DailySummary? {
        val hc = clientOrNull() ?: return null
        val zone = ZoneId.systemDefault()
        val dayStart = day.atStartOfDay(zone).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
        val tzOffsetMinutes = zone.rules.getOffset(dayStart).totalSeconds / 60

        val granted = hc.permissionController.getGrantedPermissions()

        var steps: Long = 0
        var sleepSeconds: Long? = null
        var avgHr: Double? = null
        val allRecords = mutableListOf<Record>()

        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            val resp = hc.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                )
            )
            steps = DailyAggregator.totalSteps(resp.records)
            allRecords += resp.records
        }

        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            // Read a wide window so overnight sessions that END today are included.
            val resp = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        dayStart.minus(24, ChronoUnit.HOURS),
                        dayEnd,
                    ),
                )
            )
            sleepSeconds = DailyAggregator.sleepSeconds(resp.records, dayStart, dayEnd)
            allRecords += resp.records
        }

        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            val resp = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                )
            )
            avgHr = DailyAggregator.averageHr(resp.records)
            allRecords += resp.records
        }

        return DailySummary(
            date = day.toString(),
            tzOffsetMinutes = tzOffsetMinutes,
            steps = steps,
            sleepSeconds = sleepSeconds,
            avgHr = avgHr,
            sourceApps = DailyAggregator.sourceApps(allRecords),
        )
    }

    /** True when this Health Connect build exposes the Matchmaking feature at all. */
    @OptIn(ExperimentalMatchmakingApi::class)
    fun matchmakingFeatureAvailable(): Boolean {
        val hc = clientOrNull() ?: return false
        return try {
            hc.features.getFeatureStatus(HealthConnectFeatures.FEATURE_MATCHMAKING) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (e: Exception) {
            Log.w(TAG, "getFeatureStatus failed", e)
            false
        }
    }

    /** True when Health Connect offers a matchmaking screen for our data types. */
    @OptIn(ExperimentalMatchmakingApi::class)
    suspend fun matchmakingPossible(): Boolean {
        if (!matchmakingFeatureAvailable()) return false
        val hc = clientOrNull() ?: return false
        return try {
            val request = matchmakingRequest()
            hc.checkIfMatchmakingIsPossible(request).isMatchmakingPossible
        } catch (e: Exception) {
            Log.w(TAG, "checkIfMatchmakingIsPossible failed", e)
            false
        }
    }

    /**
     * Intent that opens the Health Connect "connect your watch/app" matchmaking
     * screen for the record types the app reads. Launch with an Activity context.
     */
    @OptIn(ExperimentalMatchmakingApi::class)
    suspend fun matchmakingIntent(): Intent? {
        if (!matchmakingFeatureAvailable()) return null
        val hc = clientOrNull() ?: return null
        return try {
            hc.createMatchmakingIntent(matchmakingRequest())
        } catch (e: Exception) {
            Log.w(TAG, "createMatchmakingIntent failed", e)
            null
        }
    }

    @OptIn(ExperimentalMatchmakingApi::class)
    private fun matchmakingRequest(): MatchmakingRequest =
        MatchmakingRequest(
            recordTypes = setOf(StepsRecord::class),
            includedDataSources = emptySet(),
            excludedDataSources = emptySet(),
        )
}
