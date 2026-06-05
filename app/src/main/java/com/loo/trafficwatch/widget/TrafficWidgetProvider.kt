package com.loo.trafficwatch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.loo.trafficwatch.MainActivity
import com.loo.trafficwatch.R
import com.loo.trafficwatch.data.SettingsRepository
import com.loo.trafficwatch.data.TrafficDatabase
import com.loo.trafficwatch.monitor.TrafficMonitorService
import com.loo.trafficwatch.ui.formatBytes

class TrafficWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_MONITORING) {
            val settings = SettingsRepository(context)
            val enabled = !settings.monitoringEnabled
            settings.monitoringEnabled = enabled
            if (enabled) {
                TrafficMonitorService.start(context)
            } else {
                TrafficMonitorService.stop(context)
            }
            refresh(context)
        }
    }

    companion object {
        private const val ACTION_TOGGLE_MONITORING = "com.loo.trafficwatch.widget.TOGGLE_MONITORING"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TrafficWidgetProvider::class.java)
            updateAll(context, manager, manager.getAppWidgetIds(component))
        }

        private fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val settings = SettingsRepository(context)
            val stats = TrafficDatabase(context).use { it.dashboardStats() }
            ids.forEach { id ->
                manager.updateAppWidget(id, buildViews(context, settings.monitoringEnabled, stats.todayBytes))
            }
        }

        private fun buildViews(context: Context, enabled: Boolean, todayBytes: Long): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_traffic)
            views.setTextViewText(R.id.widget_today_value, formatBytes(todayBytes))
            views.setTextViewText(R.id.widget_status, if (enabled) "Monitoring" else "Paused")
            views.setTextViewText(R.id.widget_toggle, if (enabled) "Stop" else "Start")
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.widget_toggle, toggleIntent(context))
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                30,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )

        private fun toggleIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                31,
                Intent(context, TrafficWidgetProvider::class.java).setAction(ACTION_TOGGLE_MONITORING),
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )

        private fun immutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
