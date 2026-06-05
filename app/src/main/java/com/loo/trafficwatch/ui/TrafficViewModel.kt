package com.loo.trafficwatch.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.loo.trafficwatch.data.DashboardStats
import com.loo.trafficwatch.data.SeriesPoint
import com.loo.trafficwatch.data.SettingsRepository
import com.loo.trafficwatch.data.SimProfile
import com.loo.trafficwatch.data.TrafficDatabase
import com.loo.trafficwatch.data.UsageRow
import com.loo.trafficwatch.monitor.PermissionUtils
import com.loo.trafficwatch.monitor.SubscriptionResolver
import com.loo.trafficwatch.monitor.TrafficMonitorService
import com.loo.trafficwatch.widget.TrafficWidgetProvider
import java.time.LocalDate
import java.time.ZoneId

data class TrafficUiState(
    val monitoringEnabled: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasPhoneState: Boolean = false,
    val hasNotifications: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val fallbackActiveSlot: Int = 1,
    val activeSlotName: String = "卡1",
    val simProfiles: List<SimProfile> = listOf(SimProfile(1), SimProfile(2)),
    val stats: DashboardStats = DashboardStats(),
    val usageRows: List<UsageRow> = emptyList(),
    val lastHour: List<SeriesPoint> = emptyList(),
    val lastWeek: List<SeriesPoint> = emptyList(),
    val monthly: List<SeriesPoint> = emptyList(),
)

class TrafficViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val settings = SettingsRepository(application)
    private val database = TrafficDatabase(application)
    private val subscriptionResolver = SubscriptionResolver(application, settings)

    var uiState by mutableStateOf(TrafficUiState())
        private set

    init {
        if (settings.monitoringEnabled && PermissionUtils.hasUsageAccess(app)) {
            TrafficMonitorService.start(app)
        }
        refresh()
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val yearStart = LocalDate.now(zone).minusMonths(11).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val hourAgo = now - ONE_HOUR
        val weekAgo = now - SEVEN_DAYS

        uiState = TrafficUiState(
            monitoringEnabled = settings.monitoringEnabled,
            hasUsageAccess = PermissionUtils.hasUsageAccess(app),
            hasPhoneState = PermissionUtils.hasPhoneState(app),
            hasNotifications = PermissionUtils.hasNotifications(app),
            isIgnoringBatteryOptimizations = PermissionUtils.isIgnoringBatteryOptimizations(app),
            fallbackActiveSlot = settings.fallbackActiveSlot,
            activeSlotName = subscriptionResolver.activeSlotName(),
            simProfiles = settings.getSimProfiles(),
            stats = database.dashboardStats(now),
            usageRows = database.usageRows(monthStart, now, limit = 24),
            lastHour = database.series(hourAgo, now, ONE_MINUTE),
            lastWeek = database.series(weekAgo, now, ONE_HOUR),
            monthly = database.monthlySeries(yearStart, now),
        )
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        settings.monitoringEnabled = enabled
        if (enabled) {
            TrafficMonitorService.start(app)
        } else {
            TrafficMonitorService.stop(app)
        }
        TrafficWidgetProvider.refresh(app)
        refresh()
    }

    fun saveSimProfile(profile: SimProfile) {
        settings.saveSimProfile(profile)
        refresh()
    }

    fun setFallbackActiveSlot(slot: Int) {
        settings.fallbackActiveSlot = slot.coerceIn(1, 2)
        refresh()
    }

    fun clearAllData() {
        database.clearAll()
        TrafficWidgetProvider.refresh(app)
        refresh()
    }

    fun appSeries(uid: Int, range: AppSeriesRange): List<SeriesPoint> {
        val now = System.currentTimeMillis()
        val start = now - range.durationMillis
        return database.uidSeries(
            uid = uid,
            startMillis = start,
            endMillis = now,
            bucketMillis = range.bucketMillis,
        )
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }

    companion object {
        private const val ONE_MINUTE = 60_000L
        private const val ONE_HOUR = 60 * ONE_MINUTE
        private const val SEVEN_DAYS = 7 * 24 * ONE_HOUR
    }
}

enum class AppSeriesRange(
    val title: String,
    val durationMillis: Long,
    val bucketMillis: Long,
) {
    TODAY("今天", 24 * 60 * 60_000L, 60 * 60_000L),
    WEEK("最近一周", 7 * 24 * 60 * 60_000L, 60 * 60_000L),
}
