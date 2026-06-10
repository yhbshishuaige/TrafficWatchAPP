package com.loo.trafficwatch.monitor

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import com.loo.trafficwatch.data.HOTSPOT_UID
import com.loo.trafficwatch.data.TrafficCategory
import com.loo.trafficwatch.data.TrafficSample

class NetworkStatsReader(private val context: Context) {
    private val packageManager = context.packageManager
    private val statsManager = context.getSystemService(NetworkStatsManager::class.java)

    fun readMobileAppUsage(
        startMillis: Long,
        endMillis: Long,
        simSlot: Int,
    ): List<TrafficSample> {
        if (!PermissionUtils.hasUsageAccess(context)) return emptyList()
        if (endMillis <= startMillis) return emptyList()

        val stats = try {
            @Suppress("DEPRECATION")
            statsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startMillis,
                endMillis,
            )
        } catch (error: SecurityException) {
            throw IllegalStateException("读取系统流量统计被拒绝", error)
        } catch (error: RuntimeException) {
            throw IllegalStateException("系统流量统计暂不可用", error)
        }

        return stats.useBuckets { bucket ->
            val total = bucket.rxBytes + bucket.txBytes
            if (total <= 0L) return@useBuckets null

            val category = categoryForUid(bucket.uid)
            val identity = identityForUid(bucket.uid, category)
            TrafficSample(
                capturedAtMillis = System.currentTimeMillis(),
                windowStartMillis = startMillis,
                windowEndMillis = endMillis,
                simSlot = simSlot,
                uid = bucket.uid,
                packageName = identity.packageName,
                appLabel = identity.label,
                category = category,
                rxBytes = bucket.rxBytes,
                txBytes = bucket.txBytes,
            )
        }
    }

    private fun categoryForUid(uid: Int): TrafficCategory = when {
        uid == HOTSPOT_UID || uid == NetworkStats.Bucket.UID_TETHERING -> TrafficCategory.HOTSPOT
        uid == Process.SYSTEM_UID || uid == NetworkStats.Bucket.UID_REMOVED -> TrafficCategory.SYSTEM
        uid < 0 -> TrafficCategory.UNKNOWN
        else -> TrafficCategory.APP
    }

    private fun identityForUid(uid: Int, category: TrafficCategory): UidIdentity = when (category) {
        TrafficCategory.HOTSPOT -> UidIdentity(null, "热点共享")
        TrafficCategory.SYSTEM -> UidIdentity(null, if (uid == NetworkStats.Bucket.UID_REMOVED) "已卸载应用" else "系统服务")
        TrafficCategory.UNKNOWN -> UidIdentity(null, "未知来源")
        TrafficCategory.APP -> {
            val packages = packageManager.getPackagesForUid(uid).orEmpty()
            val primaryPackage = packages.firstOrNull()
            val label = primaryPackage
                ?.let { packageName -> loadLabel(packageName) }
                ?: "UID $uid"
            UidIdentity(primaryPackage, label)
        }
    }

    private fun loadLabel(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private data class UidIdentity(
        val packageName: String?,
        val label: String,
    )
}

private inline fun <T : Any> NetworkStats.useBuckets(mapper: (NetworkStats.Bucket) -> T?): List<T> = use { stats ->
    val bucket = NetworkStats.Bucket()
    buildList {
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            mapper(bucket)?.let { add(it) }
        }
    }
}
