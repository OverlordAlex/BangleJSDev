package com.itsabugnotafeature.scrolltimesync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val WORK_NAME = "midnight_sync"

    fun schedule(context: Context) {
        val initialDelay = computeDelayUntilMidnight()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun triggerManualSync(context: Context, deviceAddress: String) {
        val intent = android.content.Intent(context, com.itsabugnotafeature.scrolltimesync.ble.BleSyncService::class.java).apply {
            putExtra(com.itsabugnotafeature.scrolltimesync.ble.BleSyncService.EXTRA_DEVICE_ADDRESS, deviceAddress)
            putExtra(com.itsabugnotafeature.scrolltimesync.ble.BleSyncService.EXTRA_TRIGGER_TYPE, "MANUAL")
        }
        context.startForegroundService(intent)
    }

    private fun computeDelayUntilMidnight(): Duration {
        val now = ZonedDateTime.now()
        var nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        if (now.toLocalTime().isBefore(LocalTime.MIDNIGHT.plusMinutes(5))) {
            nextMidnight = now.toLocalDate().atStartOfDay(now.zone)
        }
        return Duration.between(now, nextMidnight).let {
            if (it.isNegative) it.plusHours(24) else it
        }
    }
}
