package com.onixgarage.phone.worker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onixgarage.core.domain.AlertLevel
import com.onixgarage.core.domain.ServicePredictor
import com.onixgarage.phone.di.ServiceLocator
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ServiceAlertWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext as Application)

        val db = ServiceLocator.db
        val records = db.serviceRecordDao().getAll().first()
        if (records.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val lastFuel = db.fuelLoadDao().getLastFullFill()
        val anchorKm = lastFuel?.odometerKm ?: 0
        val anchorMs = lastFuel?.timestampMs ?: 0L
        val accum = db.dailyTripDao().getTotalKmInPeriod(anchorMs, now)
        val odometer = anchorKm + accum.toInt()

        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val km30d = db.dailyTripDao().getTotalKmInPeriod(cal.timeInMillis, now)
        val avgDailyKm = km30d / 30.0

        val alerts = records
            .map { ServicePredictor.compute(it, odometer, avgDailyKm, now) }
            .filter { it.alert != AlertLevel.GREEN }

        if (alerts.isNotEmpty()) {
            ensureChannel()
            val redCount = alerts.count { it.alert == AlertLevel.RED }
            val title = if (redCount > 0) "Service overdue" else "Service due soon"
            val body = alerts.joinToString(", ") { it.record.serviceType.replace("_", " ") }
            notify(title, body)
        }

        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = applicationContext.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Service Alerts", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
    }

    private fun notify(title: String, body: String) {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        mgr.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "onixgarage_service_alerts"
        private const val NOTIFICATION_ID = 2001
        private const val WORK_NAME = "service_alert_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceAlertWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
