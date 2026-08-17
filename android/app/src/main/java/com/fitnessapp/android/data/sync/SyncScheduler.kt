package com.fitnessapp.android.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Schedules the daily Health Connect → backend sync. */
object SyncScheduler {

    private const val PERIODIC_WORK_NAME = "daily-sync-periodic"
    private const val NOW_WORK_NAME = "daily-sync-now"

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Idempotent periodic sync (24h + 6h flex). WorkManager persists this across
     * reboots (RECEIVE_BOOT_COMPLETED declared). Re-scheduling with UPDATE keeps
     * the existing schedule when parameters are unchanged.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailySyncWorker>(24, TimeUnit.HOURS, 6, TimeUnit.HOURS)
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * One-shot foreground sync (button / post-permission / app open). Carries a
     * `trigger=foreground` flag so the worker reads Health Connect even when
     * READ_HEALTH_DATA_IN_BACKGROUND is not granted (the app is on screen, so a
     * foreground read is allowed on API 35+).
     */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailySyncWorker>()
            .setConstraints(constraints())
            .setInputData(workDataOf(KEY_TRIGGER to TRIGGER_FOREGROUND))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    const val KEY_TRIGGER = "trigger"
    const val TRIGGER_FOREGROUND = "foreground"
}
