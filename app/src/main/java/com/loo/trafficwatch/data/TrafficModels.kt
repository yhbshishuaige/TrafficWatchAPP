package com.loo.trafficwatch.data

const val HOTSPOT_UID = -5

enum class TrafficCategory {
    APP,
    HOTSPOT,
    SYSTEM,
    UNKNOWN,
}

data class SimProfile(
    val slot: Int,
    val phoneNumber: String = "",
    val carrier: String = "",
    val label: String = "卡$slot",
)

data class TrafficSample(
    val capturedAtMillis: Long,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val simSlot: Int,
    val uid: Int,
    val packageName: String?,
    val appLabel: String,
    val category: TrafficCategory,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class UsageRow(
    val uid: Int,
    val packageName: String?,
    val label: String,
    val category: TrafficCategory,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class SeriesPoint(
    val bucketStartMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class DashboardStats(
    val todayBytes: Long = 0L,
    val monthBytes: Long = 0L,
    val hotspotMonthBytes: Long = 0L,
    val simMonthBytes: Map<Int, Long> = emptyMap(),
    val topAppLabel: String = "暂无",
    val topAppBytes: Long = 0L,
)
