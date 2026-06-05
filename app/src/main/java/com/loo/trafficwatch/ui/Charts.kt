package com.loo.trafficwatch.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loo.trafficwatch.data.SeriesPoint
import com.loo.trafficwatch.data.TrafficCategory
import com.loo.trafficwatch.data.UsageRow
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt

private val ChartPalette = listOf(
    Color(0xFF1B7F79),
    Color(0xFFD46A4C),
    Color(0xFF6256A7),
    Color(0xFF2F80C0),
    Color(0xFFE0A928),
    Color(0xFF3C8D5B),
    Color(0xFF9A5D82),
    Color(0xFF56616B),
)

@Composable
fun LineChart(
    points: List<SeriesPoint>,
    modifier: Modifier = Modifier,
    unitLabel: String = "蜂窝流量",
    style: TimeChartStyle = TimeChartStyle.LINE,
) {
    val chartKey = points.joinToString("|") { "${it.bucketStartMillis}:${it.totalBytes}" }
    var revealTarget by remember(chartKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(chartKey) {
        revealTarget = 1f
    }
    val reveal by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = tween(520),
        label = "line-reveal",
    )
    ChartFrame(modifier = modifier.height(230.dp), empty = points.isEmpty()) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val maxValue = niceMax(points.maxOfOrNull { it.totalBytes } ?: 1L).toFloat()
            val left = 70f
            val right = size.width - 14f
            val top = 26f
            val bottom = size.height - 42f
            val plotWidth = (right - left).coerceAtLeast(1f)
            val plotHeight = (bottom - top).coerceAtLeast(1f)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(91, 97, 101)
                textSize = 24f
            }
            val rightLabelPaint = Paint(labelPaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            val axisColor = Color(0xFFBCC4BD)
            val gridColor = Color(0xFFE6EAE3)

            drawContext.canvas.nativeCanvas.drawText(unitLabel, left, 18f, labelPaint)

            val gridCount = 4
            repeat(gridCount + 1) { step ->
                val ratio = step / gridCount.toFloat()
                val y = bottom - ratio * plotHeight
                drawLine(
                    color = if (step == 0) axisColor else gridColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = if (step == 0) 2.4f else 1.4f,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    formatBytes((maxValue * ratio).toLong()),
                    left - 8f,
                    y + 8f,
                    rightLabelPaint,
                )
            }
            drawLine(axisColor, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
            val firstLabel = points.firstOrNull()?.bucketStartMillis?.let(::formatAxisTime).orEmpty()
            val lastLabel = points.lastOrNull()?.bucketStartMillis?.let(::formatAxisTime).orEmpty()
            drawContext.canvas.nativeCanvas.drawText(firstLabel, left, size.height - 8f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText(lastLabel, right, size.height - 8f, rightLabelPaint)

            fun xFor(index: Int): Float =
                if (points.size == 1) left + plotWidth / 2f else left + (index.toFloat() / points.lastIndex) * plotWidth

            fun yFor(bytes: Long): Float =
                bottom - (bytes.toFloat() / maxValue).coerceIn(0f, 1f) * plotHeight

            val visibleLastX = left + plotWidth * reveal

            val linePath = Path()
            points.forEachIndexed { index, point ->
                val x = xFor(index)
                if (x > visibleLastX) return@forEachIndexed
                val y = yFor(point.totalBytes)
                if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }

            if (style == TimeChartStyle.BARS) {
                val barWidth = (plotWidth / points.size.coerceAtLeast(1) * 0.72f).coerceIn(10f, 42f)
                points.forEachIndexed { index, point ->
                    val x = xFor(index)
                    if (x <= visibleLastX) {
                        val y = yFor(point.totalBytes)
                        drawRoundRect(
                            color = Color(0xFF35557F),
                            topLeft = Offset(x - barWidth / 2f, y),
                            size = Size(barWidth, bottom - y),
                            cornerRadius = CornerRadius(5f, 5f),
                        )
                    }
                }
                return@Canvas
            }

            if (points.size > 1) {
                val areaPath = Path().apply {
                    moveTo(xFor(0), bottom)
                    points.forEachIndexed { index, point ->
                        val x = xFor(index)
                        if (x <= visibleLastX) lineTo(x, yFor(point.totalBytes))
                    }
                    lineTo(visibleLastX.coerceIn(left, right), bottom)
                    close()
                }
                drawPath(areaPath, color = Color(0xFF1B7F79).copy(alpha = 0.14f))
            }

            drawPath(
                path = linePath,
                color = Color(0xFF1B7F79),
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )

            points.forEachIndexed { index, point ->
                val x = xFor(index)
                if (x <= visibleLastX && (points.size <= 24 || index == 0 || index == points.lastIndex)) {
                    drawCircle(
                        color = Color.White,
                        radius = 6.5f,
                        center = Offset(x, yFor(point.totalBytes)),
                    )
                    drawCircle(
                        color = Color(0xFF1B7F79),
                        radius = 4.3f,
                        center = Offset(x, yFor(point.totalBytes)),
                    )
                }
            }
        }
    }
}

enum class TimeChartStyle {
    LINE,
    BARS,
}

@Composable
fun PieChart(
    rows: List<UsageRow>,
    modifier: Modifier = Modifier,
) {
    val visibleRows = compactRows(rows, limit = 8)
    val total = visibleRows.sumOf { it.totalBytes }.coerceAtLeast(1L)
    var selectedIndex by remember(visibleRows) { mutableIntStateOf(-1) }
    val selectedRow = visibleRows.getOrNull(selectedIndex)
    val selectedBoost by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(220),
        label = "pie-selection",
    )
    ChartFrame(modifier = modifier.height(360.dp), empty = visibleRows.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .pointerInput(visibleRows) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = minOf(size.width, size.height) / 2f
                            val distance = sqrt(
                                (offset.x - center.x) * (offset.x - center.x) +
                                    (offset.y - center.y) * (offset.y - center.y),
                            )
                            if (distance > radius) {
                                selectedIndex = -1
                                return@detectTapGestures
                            }
                            val rawAngle = Math.toDegrees(atan2(offset.y - center.y, offset.x - center.x).toDouble()).toFloat()
                            val chartAngle = (rawAngle + 450f) % 360f
                            var cursor = 0f
                            val hit = visibleRows.indexOfFirst { row ->
                                val sweep = 360f * (row.totalBytes.toFloat() / total.toFloat())
                                val matched = chartAngle >= cursor && chartAngle < cursor + sweep
                                cursor += sweep
                                matched
                            }
                            selectedIndex = if (hit == selectedIndex) -1 else hit
                        }
                    },
            ) {
                val diameter = minOf(size.width, size.height)
                val baseLeft = (size.width - diameter) / 2
                val baseTop = (size.height - diameter) / 2
                var startAngle = -90f
                visibleRows.forEachIndexed { index, row ->
                    val sweep = 360f * (row.totalBytes.toFloat() / total.toFloat())
                    val boost = if (selectedIndex == index) selectedBoost else 0f
                    val centerAngle = Math.toRadians((startAngle + sweep / 2f).toDouble())
                    val offset = 10f * boost
                    val sizeBoost = 8f * boost
                    val left = baseLeft - sizeBoost / 2f + cos(centerAngle).toFloat() * offset
                    val top = baseTop - sizeBoost / 2f + sin(centerAngle).toFloat() * offset
                    drawArc(
                        color = chartColorFor(row, index).copy(alpha = if (selectedIndex < 0 || selectedIndex == index) 1f else 0.42f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(left, top),
                        size = Size(diameter + sizeBoost, diameter + sizeBoost),
                    )
                    startAngle += sweep
                }
                drawCircle(
                    color = Color.White,
                    radius = diameter * 0.29f,
                    center = Offset(size.width / 2, size.height / 2),
                )

                val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(30, 35, 38)
                    textAlign = Paint.Align.CENTER
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val subPaint = Paint(centerPaint).apply {
                    color = android.graphics.Color.rgb(91, 97, 101)
                    textSize = 20f
                    typeface = Typeface.DEFAULT
                }
                val centerTitle = selectedRow?.label ?: "本月总量"
                val centerValue = selectedRow?.let {
                    "${formatBytes(it.totalBytes)} · %.1f%%".format(it.totalBytes * 100.0 / total)
                } ?: formatBytes(total)
                drawContext.canvas.nativeCanvas.drawText(centerTitle.take(8), size.width / 2, size.height / 2 - 4f, subPaint)
                drawContext.canvas.nativeCanvas.drawText(centerValue, size.width / 2, size.height / 2 + 28f, centerPaint)
            }

            visibleRows.take(6).forEachIndexed { index, row ->
                LegendRow(
                    row = row,
                    color = chartColorFor(row, index),
                    total = total,
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = if (selectedIndex == index) -1 else index },
                )
            }
            if (visibleRows.size > 6) {
                Text(
                    text = "还有 ${visibleRows.size - 6} 项在图中合并展示",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun TreemapChart(
    rows: List<UsageRow>,
    modifier: Modifier = Modifier,
) {
    val visibleRows = compactRows(rows, limit = 10)
    val total = visibleRows.sumOf { it.totalBytes }.coerceAtLeast(1L)
    ChartFrame(modifier = modifier.height(280.dp), empty = visibleRows.isEmpty()) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            var cursorX = 0f
            var cursorY = 0f
            var remainingWidth = size.width
            var remainingHeight = size.height
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 27f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(230, 255, 255, 255)
                textSize = 22f
            }

            visibleRows.forEachIndexed { index, row ->
                val remainingTotal = visibleRows
                    .drop(index)
                    .sumOf { it.totalBytes }
                    .coerceAtLeast(1L)
                    .toFloat()
                val rect = if (remainingWidth >= remainingHeight) {
                    val width = max(24f, remainingWidth * (row.totalBytes.toFloat() / remainingTotal))
                    Rect(cursorX, cursorY, (cursorX + width).coerceAtMost(size.width), cursorY + remainingHeight)
                } else {
                    val height = max(24f, remainingHeight * (row.totalBytes.toFloat() / remainingTotal))
                    Rect(cursorX, cursorY, cursorX + remainingWidth, (cursorY + height).coerceAtMost(size.height))
                }

                val gutter = 4f
                val display = Rect(
                    rect.left + gutter,
                    rect.top + gutter,
                    (rect.right - gutter).coerceAtLeast(rect.left + gutter),
                    (rect.bottom - gutter).coerceAtLeast(rect.top + gutter),
                )
                drawRoundRect(
                    color = chartColorFor(row, index),
                    topLeft = Offset(display.left, display.top),
                    size = Size(display.width, display.height),
                    cornerRadius = CornerRadius(10f, 10f),
                )

                if (display.width > 92f && display.height > 48f) {
                    val canvas = drawContext.canvas.nativeCanvas
                    canvas.save()
                    canvas.clipRect(display.left, display.top, display.right, display.bottom)
                    canvas.drawText(row.label.take(10), display.left + 12f, display.top + 32f, namePaint)
                    if (display.width > 140f && display.height > 78f) {
                        val percent = row.totalBytes * 100.0 / total
                        canvas.drawText(
                            "${formatBytes(row.totalBytes)} · %.1f%%".format(percent),
                            display.left + 12f,
                            display.top + 62f,
                            valuePaint,
                        )
                    }
                    canvas.restore()
                }

                if (remainingWidth >= remainingHeight) {
                    cursorX = rect.right
                    remainingWidth = (size.width - cursorX).coerceAtLeast(0f)
                } else {
                    cursorY = rect.bottom
                    remainingHeight = (size.height - cursorY).coerceAtLeast(0f)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(
    row: UsageRow,
    color: Color,
    total: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val percent = row.totalBytes * 100.0 / total.coerceAtLeast(1L)
    val background by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(180),
        label = "legend-bg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .pointerInput(row) {
                detectTapGestures { onClick() }
            }
            .animateContentSize(tween(180))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = row.label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "${formatBytes(row.totalBytes)} · %.1f%%".format(percent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartFrame(
    modifier: Modifier = Modifier,
    empty: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (empty) {
            Text(
                text = "暂无数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        } else {
            content()
        }
    }
}

fun chartColorFor(row: UsageRow, index: Int): Color = when (row.category) {
    TrafficCategory.HOTSPOT -> Color(0xFF56616B)
    TrafficCategory.SYSTEM -> Color(0xFF8A8E91)
    TrafficCategory.UNKNOWN -> Color(0xFF6256A7)
    else -> ChartPalette[index % ChartPalette.size]
}

private fun compactRows(rows: List<UsageRow>, limit: Int): List<UsageRow> {
    val positives = rows.filter { it.totalBytes > 0 }
    if (positives.size <= limit) return positives
    val head = positives.take((limit - 1).coerceAtLeast(1))
    val tail = positives.drop(head.size)
    return head + UsageRow(
        uid = -999,
        packageName = null,
        label = "其他",
        category = TrafficCategory.UNKNOWN,
        rxBytes = tail.sumOf { it.rxBytes },
        txBytes = tail.sumOf { it.txBytes },
    )
}

private fun niceMax(value: Long): Long {
    if (value <= 0L) return 1L
    val units = listOf(1L, 2L, 5L)
    var scale = 1L
    while (scale * 10L < value) scale *= 10L
    return units
        .map { it * scale }
        .firstOrNull { it >= value }
        ?: 10L * scale
}
