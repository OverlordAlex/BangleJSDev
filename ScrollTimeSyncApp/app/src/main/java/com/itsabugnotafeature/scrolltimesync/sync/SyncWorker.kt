package com.itsabugnotafeature.scrolltimesync.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.itsabugnotafeature.scrolltimesync.MainActivity
import com.itsabugnotafeature.scrolltimesync.R
import com.itsabugnotafeature.scrolltimesync.ble.BleSyncService
import com.itsabugnotafeature.scrolltimesync.data.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("scrolltimesync", Context.MODE_PRIVATE)
        val deviceAddress = prefs.getString("device_address", null)

        if (deviceAddress == null) {
            return@withContext Result.failure()
        }

        val intent = Intent(applicationContext, BleSyncService::class.java).apply {
            putExtra(BleSyncService.EXTRA_DEVICE_ADDRESS, deviceAddress)
            putExtra(BleSyncService.EXTRA_TRIGGER_TYPE, "AUTOMATIC")
        }
        applicationContext.startForegroundService(intent)

        if (LocalDate.now().dayOfWeek == DayOfWeek.MONDAY) {
            try {
                val repository = HealthRepository.getInstance(applicationContext)
                val summary = repository.generateWeeklySummary()
                if (summary != null) {
                    val trendStr = when {
                        summary.restingBpmTrend < -1 -> "down ${(-summary.restingBpmTrend).toInt()}"
                        summary.restingBpmTrend > 1 -> "up ${summary.restingBpmTrend.toInt()}"
                        else -> "steady"
                    }
                    val avgSleepH = summary.avgSleepMinutes / 60
                    val avgSleepM = summary.avgSleepMinutes % 60
                    val text = "Resting HR ${summary.avgRestingBpm.toInt()} avg ($trendStr), " +
                            "%,d steps, ${avgSleepH}h ${avgSleepM}m avg sleep".format(summary.totalSteps)
                    postWeeklySummaryNotification(text)
                }
            } catch (_: Exception) { }
        }

        Result.success()
    }

    private fun postWeeklySummaryNotification(text: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WEEKLY_CHANNEL_ID, "Weekly Summary",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(applicationContext, WEEKLY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Weekly Summary")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(WEEKLY_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WEEKLY_CHANNEL_ID = "weekly_summary"
        private const val WEEKLY_NOTIFICATION_ID = 2001
    }
}
