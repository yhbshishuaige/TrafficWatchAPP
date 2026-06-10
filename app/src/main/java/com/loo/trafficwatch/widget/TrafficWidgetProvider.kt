package com.loo.trafficwatch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.widget.RemoteViews
import com.loo.trafficwatch.MainActivity
import com.loo.trafficwatch.R
import com.loo.trafficwatch.data.SettingsRepository
import com.loo.trafficwatch.data.TrafficDatabase
import com.loo.trafficwatch.data.UsageRow
import com.loo.trafficwatch.monitor.TrafficMonitorService
import com.loo.trafficwatch.ui.formatBytes
import java.time.LocalDate
import java.time.ZoneId

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
            val data = TrafficDatabase(context).use { database ->
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val stats = database.dashboardStats(now)
                val monthStart = LocalDate.now(zone)
                    .withDayOfMonth(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                WidgetData(
                    todayBytes = stats.todayBytes,
                    monthBytes = stats.monthBytes,
                    rows = database.usageRows(monthStart, now, limit = 50),
                )
            }
            ids.forEach { id ->
                manager.updateAppWidget(id, buildViews(context, settings.monitoringEnabled, data))
            }
        }

        private fun buildViews(context: Context, enabled: Boolean, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_traffic)
            val top = data.rows.firstOrNull()
            views.setTextViewText(R.id.widget_today_value, formatBytes(data.todayBytes))
            views.setTextViewText(R.id.widget_month_value, "本月 ${formatBytes(data.monthBytes)}")
            views.setTextViewText(
                R.id.widget_top_label,
                top?.let { "${it.label} ${formatBytes(it.totalBytes)}" } ?: "暂无应用数据",
            )
            views.setTextViewText(R.id.widget_status, if (enabled) "监测中" else "已暂停")
            views.setTextViewText(R.id.widget_toggle, if (enabled) "停止" else "开始")
            views.setImageViewBitmap(R.id.widget_pie, buildPieBitmap(data.rows, data.monthBytes))
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.widget_toggle, toggleIntent(context))
            return views
        }

        private fun buildPieBitmap(rows: List<UsageRow>, monthBytes: Long): Bitmap {
            val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val strokeWidth = 32f
            val rect = RectF(28f, 28f, 192f, 192f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.BUTT
            }
            val positiveRows = compactRows(rows)
            val total = positiveRows.sumOf { it.totalBytes }.coerceAtLeast(monthBytes).coerceAtLeast(0L)

            paint.color = Color.rgb(224, 227, 218)
            canvas.drawArc(rect, -90f, 360f, false, paint)

            if (total > 0L && positiveRows.isNotEmpty()) {
                var start = -90f
                positiveRows.forEachIndexed { index, row ->
                    val sweep = 360f * row.totalBytes.toFloat() / total.toFloat()
                    paint.color = WIDGET_PALETTE[index % WIDGET_PALETTE.size]
                    canvas.drawArc(rect, start, sweep, false, paint)
                    start += sweep
                }
            }

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(91, 97, 101)
                textAlign = Paint.Align.CENTER
                textSize = 20f
            }
            val valuePaint = Paint(labelPaint).apply {
                color = Color.rgb(30, 35, 38)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("本月", 110f, 105f, labelPaint)
            canvas.drawText(formatBytes(total), 110f, 132f, valuePaint)
            return bitmap
        }

        private fun compactRows(rows: List<UsageRow>): List<UsageRow> {
            val positives = rows.filter { it.totalBytes > 0L }
            if (positives.size <= 6) return positives
            val head = positives.take(5)
            val tailBytes = positives.drop(5).sumOf { it.totalBytes }
            return head + positives[5].copy(
                label = "Other",
                rxBytes = tailBytes,
                txBytes = 0L,
            )
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

        private val WIDGET_PALETTE = intArrayOf(
            Color.rgb(27, 127, 121),
            Color.rgb(86, 97, 107),
            Color.rgb(98, 86, 167),
            Color.rgb(47, 128, 192),
            Color.rgb(224, 169, 40),
            Color.rgb(60, 141, 91),
        )
    }
}

private data class WidgetData(
    val todayBytes: Long,
    val monthBytes: Long,
    val rows: List<UsageRow>,
)
