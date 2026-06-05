package com.loo.trafficwatch.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.loo.trafficwatch.MainActivity
import com.loo.trafficwatch.R
import com.loo.trafficwatch.data.SettingsRepository
import com.loo.trafficwatch.data.TrafficDatabase
import com.loo.trafficwatch.widget.TrafficWidgetProvider
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TrafficMonitorService : Service() {
    private lateinit var database: TrafficDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var reader: NetworkStatsReader
    private lateinit var subscriptionResolver: SubscriptionResolver
    private var executor: ScheduledExecutorService? = null

    override fun onCreate() {
        super.onCreate()
        database = TrafficDatabase(this)
        settings = SettingsRepository(this)
        reader = NetworkStatsReader(this)
        subscriptionResolver = SubscriptionResolver(this, settings)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.monitoringEnabled = false
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                settings.monitoringEnabled = true
                startForeground(NOTIFICATION_ID, buildNotification("正在等待下一次采样"))
                startMonitoring()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (executor != null) return
        settings.lastSampleAtMillis = System.currentTimeMillis()
        executor = Executors.newSingleThreadScheduledExecutor().also { scheduler ->
            scheduler.scheduleAtFixedRate(
                { sampleOnce() },
                SAMPLE_INTERVAL_MILLIS,
                SAMPLE_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun stopMonitoring() {
        executor?.shutdownNow()
        executor = null
        settings.lastSampleAtMillis = 0L
    }

    private fun sampleOnce() {
        if (!settings.monitoringEnabled) return
        withShortWakeLock {
            val now = System.currentTimeMillis()
            val previous = settings.lastSampleAtMillis.takeIf { it > 0L } ?: now
            val start = if (now - previous > MAX_BACKFILL_MILLIS) now - SAMPLE_INTERVAL_MILLIS else previous
            val slot = subscriptionResolver.currentDataSlot()

            val samples = reader.readMobileAppUsage(start, now, slot)
            database.insertSamples(samples)
            settings.lastSampleAtMillis = now

            val total = samples.sumOf { it.totalBytes }
            val message = if (total > 0L) {
                "本分钟 ${formatBytes(total)} · ${subscriptionResolver.activeSlotName()}"
            } else {
                "本分钟无蜂窝流量 · ${subscriptionResolver.activeSlotName()}"
            }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
            TrafficWidgetProvider.refresh(this)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_traffic)
            .setContentTitle("Traffic Monitoring")
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent())
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, TrafficMonitorService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun withShortWakeLock(block: () -> Unit) {
        val powerManager = getSystemService(PowerManager::class.java)
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:traffic-sample")
        try {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            block()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "流量监测",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示流量观察后台采样状态"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_STOP = "com.loo.trafficwatch.action.STOP"
        private const val CHANNEL_ID = "traffic_watch_monitor"
        private const val NOTIFICATION_ID = 1106
        private const val SAMPLE_INTERVAL_MILLIS = 60_000L
        private const val MAX_BACKFILL_MILLIS = 5 * 60_000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 20_000L

        fun start(context: Context): Boolean {
            val intent = Intent(context, TrafficMonitorService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (_: RuntimeException) {
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, TrafficMonitorService::class.java).setAction(ACTION_STOP))
            } catch (_: RuntimeException) {
                SettingsRepository(context).monitoringEnabled = false
                TrafficWidgetProvider.refresh(context)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "${bytes}B"
    } else {
        String.format("%.1f%s", value, units[index])
    }
}
