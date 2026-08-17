package com.fitnessapp.android.data.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.HealthConnectRepository
import com.fitnessapp.android.data.network.ApiResult
import com.fitnessapp.android.data.sync.SyncScheduler.KEY_TRIGGER
import com.fitnessapp.android.data.sync.SyncScheduler.TRIGGER_FOREGROUND
import java.time.LocalDate

/**
 * Daily sync worker: reads today's Health Connect aggregates and POSTs them to
 * BE-C1 /api/v1/daily (idempotent upsert keyed by user+date).
 *
 * Background-read gate: on Android 15+ (API 35) Health Connect requires
 * READ_HEALTH_DATA_IN_BACKGROUND for background reads. When it is not granted,
 * the worker intentionally skips the read and relies on the foreground sync
 * (app-open / manual "Sync now") — the card's documented fallback.
 */
class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "DailySync"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? FitnessApp ?: return Result.failure()
        val container = app.container
        val token = container.authStore.jwt
        if (token == null) {
            Log.i(TAG, "No JWT — skipping daily sync (user not signed in)")
            return Result.success()
        }

        val foregroundTrigger = inputData.getString(KEY_TRIGGER) == TRIGGER_FOREGROUND

        if (!foregroundTrigger && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val granted = container.healthRepository.grantedPermissions()
            if (HealthConnectRepository.PERM_READ_BACKGROUND !in granted) {
                Log.i(
                    TAG,
                    "READ_HEALTH_DATA_IN_BACKGROUND not granted — skipping background read (foreground fallback covers it)",
                )
                return Result.success()
            }
        }

        return try {
            val summary = container.healthRepository.readDailySummary(LocalDate.now())
                ?: return Result.failure()
            Log.i(TAG, "Summary: steps=${summary.steps} sleep=${summary.sleepSeconds} hr=${summary.avgHr} sources=${summary.sourceApps}")

            when (val result = container.apiClient.postDaily(token, summary)) {
                is ApiResult.Success -> {
                    container.authStore.recordSyncSuccess(summary.date)
                    container.dailyCache.save(summary)
                    Log.i(TAG, "Synced ${summary.date} -> HTTP ${result.httpCode}")
                    Result.success()
                }
                is ApiResult.Unauthorized -> {
                    Log.w(TAG, "Sync rejected (401) — clearing session")
                    container.authStore.clearSession()
                    Result.failure()
                }
                is ApiResult.Validation -> {
                    Log.e(TAG, "Sync rejected (422): ${result.detail}")
                    container.authStore.recordSyncError(result.detail)
                    Result.failure()
                }
                is ApiResult.Conflict -> Result.failure()
                is ApiResult.Forbidden -> {
                    Log.w(TAG, "Sync rejected (403): ${result.detail}")
                    Result.failure()
                }
                is ApiResult.Failure -> {
                    Log.w(TAG, "Sync failed: ${result.detail}")
                    container.authStore.recordSyncError(result.detail)
                    Result.retry()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Health Connect read denied in worker", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected worker error", e)
            Result.retry()
        }
    }
}
