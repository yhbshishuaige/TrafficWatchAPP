package com.loo.trafficwatch.monitor

import android.content.Context
import com.loo.trafficwatch.data.SettingsRepository
import com.loo.trafficwatch.data.TrafficDatabase
import com.loo.trafficwatch.data.TrafficLogLevel

class TrafficSampler(context: Context) {
    private val appContext = context.applicationContext
    private val database = TrafficDatabase(appContext)
    private val settings = SettingsRepository(appContext)
    private val reader = NetworkStatsReader(appContext)
    private val subscriptionResolver = SubscriptionResolver(appContext, settings)

    fun sampleNow(
        source: SampleSource,
        requireMonitoringEnabled: Boolean,
        writeSuccessLog: Boolean,
    ): SampleResult = synchronized(sampleLock) {
        val now = System.currentTimeMillis()
        if (requireMonitoringEnabled && !settings.monitoringEnabled) {
            return@synchronized SampleResult(
                status = SampleStatus.SKIPPED,
                message = "${source.label}跳过：监测开关已关闭",
                totalBytes = 0L,
                sampleCount = 0,
                startMillis = now,
                endMillis = now,
            )
        }

        if (!PermissionUtils.hasUsageAccess(appContext)) {
            val message = "${source.label}失败：未开启使用情况访问权限"
            database.insertLog(TrafficLogLevel.ERROR, message, now)
            return@synchronized SampleResult(
                status = SampleStatus.ERROR,
                message = message,
                totalBytes = 0L,
                sampleCount = 0,
                startMillis = now,
                endMillis = now,
            )
        }

        val previous = settings.lastSampleAtMillis
        val start = when {
            previous <= 0L -> now - SAMPLE_INTERVAL_MILLIS
            now <= previous -> now - MIN_QUERY_WINDOW_MILLIS
            now - previous > MAX_BACKFILL_MILLIS -> now - MAX_BACKFILL_MILLIS
            else -> previous
        }.coerceAtMost(now - 1L)
        val slot = subscriptionResolver.currentDataSlot()

        val result = runCatching {
            val samples = reader.readMobileAppUsage(start, now, slot)
            database.insertSamples(samples)

            val total = samples.sumOf { it.totalBytes }
            if (total > 0L) {
                settings.lastSampleAtMillis = now
            } else if (previous <= 0L) {
                settings.lastSampleAtMillis = start
            }
            val activeSlot = subscriptionResolver.activeSlotName()
            val message = if (total > 0L) {
                "${source.label}成功：新增 ${formatBytes(total)}，${samples.size} 条记录 · $activeSlot"
            } else {
                "${source.label}完成：系统暂未返回新增蜂窝流量，已保留时间窗口 · $activeSlot"
            }
            SampleResult(
                status = if (total > 0L) SampleStatus.SUCCESS else SampleStatus.NO_DATA,
                message = message,
                totalBytes = total,
                sampleCount = samples.size,
                startMillis = start,
                endMillis = now,
            )
        }.getOrElse { error ->
            SampleResult(
                status = SampleStatus.ERROR,
                message = "${source.label}失败：${error.message ?: error.javaClass.simpleName}",
                totalBytes = 0L,
                sampleCount = 0,
                startMillis = start,
                endMillis = now,
            )
        }

        if (writeSuccessLog || result.status == SampleStatus.ERROR) {
            database.insertLog(result.status.logLevel, result.message, now)
        }
        result
    }

    fun close() {
        database.close()
    }

    companion object {
        private val sampleLock = Any()
        private const val MIN_QUERY_WINDOW_MILLIS = 10_000L
        private const val SAMPLE_INTERVAL_MILLIS = 60_000L
        private const val MAX_BACKFILL_MILLIS = 2 * 60 * 60_000L
    }
}

enum class SampleSource(val label: String) {
    MANUAL("手动刷新"),
    SCHEDULED("后台采样"),
}

enum class SampleStatus(val logLevel: TrafficLogLevel) {
    SUCCESS(TrafficLogLevel.SUCCESS),
    NO_DATA(TrafficLogLevel.INFO),
    SKIPPED(TrafficLogLevel.WARNING),
    ERROR(TrafficLogLevel.ERROR),
}

data class SampleResult(
    val status: SampleStatus,
    val message: String,
    val totalBytes: Long,
    val sampleCount: Int,
    val startMillis: Long,
    val endMillis: Long,
)

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
        "%.1f%s".format(value, units[index])
    }
}
