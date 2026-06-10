package com.loo.trafficwatch

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List as ListIcon
import androidx.compose.material.icons.rounded.BarChart as BarChartIcon
import androidx.compose.material.icons.rounded.Delete as DeleteIcon
import androidx.compose.material.icons.rounded.Remove as RemoveIcon
import androidx.compose.material.icons.rounded.PieChart as PieChartIcon
import androidx.compose.material.icons.rounded.PlayArrow as PlayArrowIcon
import androidx.compose.material.icons.rounded.Add as AddIcon
import androidx.compose.material.icons.rounded.Refresh as RefreshIcon
import androidx.compose.material.icons.rounded.Save as SaveIcon
import androidx.compose.material.icons.rounded.Settings as SettingsIcon
import androidx.compose.material.icons.rounded.SimCard as SimCardIcon
import androidx.compose.material.icons.rounded.Stop as StopIcon
import androidx.compose.material.icons.rounded.Timeline as TimelineIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loo.trafficwatch.data.SimProfile
import com.loo.trafficwatch.data.TrafficCategory
import com.loo.trafficwatch.data.TrafficLogEntry
import com.loo.trafficwatch.data.TrafficLogLevel
import com.loo.trafficwatch.data.UsageRow
import com.loo.trafficwatch.ui.AppSeriesRange
import com.loo.trafficwatch.ui.LineChart
import com.loo.trafficwatch.ui.PieChart
import com.loo.trafficwatch.ui.TrafficTheme
import com.loo.trafficwatch.ui.TrafficUiState
import com.loo.trafficwatch.ui.TrafficViewModel
import com.loo.trafficwatch.ui.TreemapChart
import com.loo.trafficwatch.ui.TimeChartStyle
import com.loo.trafficwatch.ui.formatAxisTime
import com.loo.trafficwatch.ui.formatBytes
import com.loo.trafficwatch.ui.formatMonth
import com.loo.trafficwatch.ui.formatShortTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: TrafficViewModel = viewModel()
            val state = viewModel.uiState
            val context = LocalContext.current
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { viewModel.refresh() }
            val phoneStateLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { viewModel.refresh() }

            LaunchedEffect(Unit) {
                while (true) {
                    viewModel.refresh()
                    delay(30_000)
                }
            }

            TrafficTheme {
                TrafficApp(
                    state = state,
                    onRefresh = viewModel::manualRefresh,
                    onOpenUsageSettings = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestPhoneState = {
                        phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    },
                    onRequestBatteryOptimization = {
                        val packageUri = Uri.parse("package:$packageName")
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                        }
                        try {
                            startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    onMonitoringChange = { enabled ->
                        if (enabled && !state.hasUsageAccess) {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } else {
                            if (enabled && !state.hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (enabled && !state.hasPhoneState) {
                                phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            }
                            viewModel.setMonitoringEnabled(enabled)
                        }
                    },
                    loadAppSeries = viewModel::appSeries,
                    onSaveSimProfile = viewModel::saveSimProfile,
                    onFallbackSlotChange = viewModel::setFallbackActiveSlot,
                    onClearAllData = viewModel::clearAllData,
                    onOpenAppSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        startActivity(intent)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrafficApp(
    state: TrafficUiState,
    onRefresh: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestPhoneState: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    loadAppSeries: (Int, AppSeriesRange) -> List<com.loo.trafficwatch.data.SeriesPoint>,
    onSaveSimProfile: (SimProfile) -> Unit,
    onFallbackSlotChange: (Int) -> Unit,
    onClearAllData: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val tabs = listOf(
        TabSpec("总览", Icons.Rounded.PieChartIcon),
        TabSpec("App", Icons.AutoMirrored.Rounded.ListIcon),
        TabSpec("趋势", Icons.Rounded.TimelineIcon),
        TabSpec("设置", Icons.Rounded.SettingsIcon),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    var showLogs by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "流量观察",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { showLogs = true }) {
                        Icon(Icons.AutoMirrored.Rounded.ListIcon, contentDescription = "日志")
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.RefreshIcon, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> OverviewTab(
                        state = state,
                        onOpenUsageSettings = onOpenUsageSettings,
                        onMonitoringChange = onMonitoringChange,
                    )
                    1 -> AppsTab(
                        rows = state.usageRows,
                        loadSeries = loadAppSeries,
                    )
                    2 -> TrendsTab(state)
                    3 -> SettingsTab(
                        state = state,
                        onOpenUsageSettings = onOpenUsageSettings,
                        onRequestNotifications = onRequestNotifications,
                        onRequestPhoneState = onRequestPhoneState,
                        onRequestBatteryOptimization = onRequestBatteryOptimization,
                        onSaveSimProfile = onSaveSimProfile,
                        onFallbackSlotChange = onFallbackSlotChange,
                        onClearAllData = onClearAllData,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }
            }
        }
    }

    if (showLogs) {
        LogDialog(
            logs = state.logs,
            onDismiss = { showLogs = false },
        )
    }
}

@Composable
private fun LogDialog(
    logs: List<TrafficLogEntry>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("刷新日志") },
        text = {
            if (logs.isEmpty()) {
                Text("暂无日志。点击右上角刷新后，这里会显示采样结果。")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    logs.forEach { log ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(9.dp)
                                    .background(logLevelColor(log.level), RoundedCornerShape(50)),
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = formatAxisTime(log.timestampMillis),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun OverviewTab(
    state: TrafficUiState,
    onOpenUsageSettings: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
) {
    var showPieDetails by remember { mutableStateOf(false) }

    ScreenColumn {
        MonitorCard(
            state = state,
            onOpenUsageSettings = onOpenUsageSettings,
            onMonitoringChange = onMonitoringChange,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("今日", formatBytes(state.stats.todayBytes), Modifier.weight(1f))
            MetricTile("本月", formatBytes(state.stats.monthBytes), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val sim1 = state.simProfiles.getOrNull(0)?.label?.ifBlank { "卡1" } ?: "卡1"
            val sim2 = state.simProfiles.getOrNull(1)?.label?.ifBlank { "卡2" } ?: "卡2"
            MetricTile("$sim1 本月", formatBytes(state.stats.simMonthBytes[1] ?: 0L), Modifier.weight(1f))
            MetricTile("$sim2 本月", formatBytes(state.stats.simMonthBytes[2] ?: 0L), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("热点共享", formatBytes(state.stats.hotspotMonthBytes), Modifier.weight(1f))
            MetricTile(state.stats.topAppLabel, formatBytes(state.stats.topAppBytes), Modifier.weight(1f))
        }

        SectionHeader(
            title = "本月占比",
            icon = Icons.Rounded.PieChartIcon,
            actionText = "详情",
            onAction = { showPieDetails = true },
        )
        PieChart(state.usageRows, Modifier.fillMaxWidth())

        SectionHeader("树状视图", Icons.Rounded.BarChartIcon)
        TreemapChart(state.usageRows, Modifier.fillMaxWidth())
    }

    if (showPieDetails) {
        AlertDialog(
            onDismissRequest = { showPieDetails = false },
            title = { Text("本月占比") },
            text = {
                Text(
                    text = "这个图统计本月已记录的蜂窝流量，按 App、系统服务、热点共享等来源分组。圆环面积越大，表示这个来源在本月总流量中的占比越高；下方图例显示具体名称、流量大小和百分比。热点流量会单独显示为“热点共享”，不会归入某个 App。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPieDetails = false }) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun AppsTab(
    rows: List<UsageRow>,
    loadSeries: (Int, AppSeriesRange) -> List<com.loo.trafficwatch.data.SeriesPoint>,
) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var selectedApp by remember { mutableStateOf<UsageRow?>(null) }
    var selectedRange by remember { mutableStateOf(AppSeriesRange.TODAY) }

    ScreenColumn {
        SectionHeader("本月 App 流量", Icons.AutoMirrored.Rounded.ListIcon)
        TabRow(selectedTabIndex = selectedMode) {
            Tab(
                selected = selectedMode == 0,
                onClick = { selectedMode = 0 },
                text = { Text("列表") },
            )
            Tab(
                selected = selectedMode == 1,
                onClick = { if (selectedApp != null) selectedMode = 1 },
                enabled = selectedApp != null,
                text = { Text("柱状") },
            )
        }

        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240)) { width -> direction * width } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(240)) { width -> -direction * width } + fadeOut(tween(180)))
            },
            label = "app-inner-tabs",
        ) { mode ->
            if (mode == 0) {
                AppListPanel(
                    rows = rows,
                    onAppClick = { row ->
                        selectedApp = row
                        selectedMode = 1
                    },
                )
            } else {
                AppSeriesPanel(
                    row = selectedApp,
                    selectedRange = selectedRange,
                    onRangeChange = { selectedRange = it },
                    loadSeries = loadSeries,
                )
            }
        }
    }
}

@Composable
private fun TrendsTab(state: TrafficUiState) {
    var hourZoom by remember { mutableIntStateOf(0) }
    var weekZoom by remember { mutableIntStateOf(0) }
    val hourOptions = listOf(60 to "60分钟前", 30 to "30分钟前", 15 to "15分钟前")
    val weekOptions = listOf(7 * 24 to "7天前", 3 * 24 to "3天前", 24 to "24小时前")
    val hourWindow = hourOptions[hourZoom.coerceIn(hourOptions.indices)]
    val weekWindow = weekOptions[weekZoom.coerceIn(weekOptions.indices)]
    val hourPoints = state.lastHour.takeLast(hourWindow.first)
    val weekPoints = state.lastWeek.takeLast(weekWindow.first)

    ScreenColumn {
        SectionHeader("最近一小时", Icons.Rounded.TimelineIcon)
        ZoomControls(
            label = "显示 ${hourWindow.first} 分钟",
            canZoomIn = hourZoom < hourOptions.lastIndex,
            canZoomOut = hourZoom > 0,
            onZoomIn = { hourZoom = (hourZoom + 1).coerceAtMost(hourOptions.lastIndex) },
            onZoomOut = { hourZoom = (hourZoom - 1).coerceAtLeast(0) },
        )
        LineChart(
            points = hourPoints,
            modifier = Modifier.fillMaxWidth(),
            unitLabel = "每分钟流量",
        )
        TrendFooter(state.lastHour.lastOrNull()?.bucketStartMillis?.let { "更新到 ${formatShortTime(it)}" } ?: "等待采样")

        SectionHeader("最近一周", Icons.Rounded.TimelineIcon)
        ZoomControls(
            label = "显示 ${weekWindow.second} 至现在",
            canZoomIn = weekZoom < weekOptions.lastIndex,
            canZoomOut = weekZoom > 0,
            onZoomIn = { weekZoom = (weekZoom + 1).coerceAtMost(weekOptions.lastIndex) },
            onZoomOut = { weekZoom = (weekZoom - 1).coerceAtLeast(0) },
        )
        LineChart(
            points = weekPoints,
            modifier = Modifier.fillMaxWidth(),
            unitLabel = "每小时流量",
        )
        TrendFooter("按小时聚合")

        SectionHeader("最近十二个月", Icons.Rounded.BarChartIcon)
        MonthlyBars(state.monthly)
    }
}

@Composable
private fun AppListPanel(
    rows: List<UsageRow>,
    onAppClick: (UsageRow) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyBlock("暂无 App 统计")
    } else {
        val maxBytes = rows.maxOf { it.totalBytes }.coerceAtLeast(1L)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                UsageRowItem(
                    row = row,
                    maxBytes = maxBytes,
                    onClick = { onAppClick(row) },
                )
            }
        }
    }
}

@Composable
private fun AppSeriesPanel(
    row: UsageRow?,
    selectedRange: AppSeriesRange,
    onRangeChange: (AppSeriesRange) -> Unit,
    loadSeries: (Int, AppSeriesRange) -> List<com.loo.trafficwatch.data.SeriesPoint>,
) {
    if (row == null) {
        EmptyBlock("先在列表里选择一个 App")
        return
    }

    val points = loadSeries(row.uid, selectedRange)
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(categoryColor(row.category), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (row.category) {
                        TrafficCategory.HOTSPOT -> "热"
                        TrafficCategory.SYSTEM -> "系"
                        else -> row.label.take(1).uppercase()
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "本月 ${formatBytes(row.totalBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AppSeriesRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeChange(range) },
                label = { Text(range.title) },
            )
        }
    }

    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${row.label} 流量柱状图",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "真实采样时间 · 按小时聚合",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = selectedRange.title,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        LineChart(
            points = points,
            modifier = Modifier.fillMaxWidth(),
            unitLabel = "${row.label} 流量",
            style = TimeChartStyle.BARS,
            showUnitLabel = false,
            framed = false,
        )
    }
}

@Composable
private fun SettingsTab(
    state: TrafficUiState,
    onOpenUsageSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestPhoneState: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onSaveSimProfile: (SimProfile) -> Unit,
    onFallbackSlotChange: (Int) -> Unit,
    onClearAllData: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    ScreenColumn {
        SectionHeader("权限", Icons.Rounded.SettingsIcon)
        PermissionCard(
            title = "使用情况访问",
            granted = state.hasUsageAccess,
            actionText = "打开",
            onClick = onOpenUsageSettings,
        )
        PermissionCard(
            title = "通知",
            granted = state.hasNotifications,
            actionText = "授权",
            onClick = onRequestNotifications,
        )
        PermissionCard(
            title = "读取当前上网卡",
            granted = state.hasPhoneState,
            actionText = "授权",
            onClick = onRequestPhoneState,
        )
        PermissionCard(
            title = "后台电池限制",
            granted = state.isIgnoringBatteryOptimizations,
            actionText = "允许常驻",
            onClick = onRequestBatteryOptimization,
        )

        SectionHeader("双卡标签", Icons.Rounded.SimCardIcon)
        state.simProfiles.forEach { profile ->
            SimProfileEditor(profile = profile, onSave = onSaveSimProfile)
        }

        CardBlock {
            Text("无权限时归属到", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..2).forEach { slot ->
                    FilterChip(
                        selected = state.fallbackActiveSlot == slot,
                        onClick = { onFallbackSlotChange(slot) },
                        label = { Text("卡$slot") },
                    )
                }
            }
        }

        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("应用设置", fontWeight = FontWeight.Bold)
                    Text(
                        text = "通知和电池策略在系统设置中调整",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onOpenAppSettings) {
                    Icon(Icons.Rounded.SettingsIcon, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开")
                }
            }
        }

        Button(
            onClick = { showClearDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.DeleteIcon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("清空本地记录")
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空记录") },
            text = { Text("这会删除已经采集到的本地流量记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAllData()
                    },
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun MonitorCard(
    state: TrafficUiState,
    onOpenUsageSettings: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (state.monitoringEnabled) Icons.Rounded.StopIcon else Icons.Rounded.PlayArrowIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (state.monitoringEnabled) "监测中" else "已暂停",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.activeSlotName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = state.monitoringEnabled,
                onCheckedChange = onMonitoringChange,
                enabled = state.hasUsageAccess || state.monitoringEnabled,
            )
        }
        if (!state.hasUsageAccess) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenUsageSettings, modifier = Modifier.fillMaxWidth()) {
                Text("开启使用情况访问")
            }
        }
    }
}

@Composable
private fun MetricTile(title: String, value: String, modifier: Modifier = Modifier) {
    CardBlock(modifier = modifier.animateContentSize(animationSpec = tween(240))) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UsageRowItem(
    row: UsageRow,
    maxBytes: Long,
    onClick: (() -> Unit)? = null,
) {
    val targetProgress = row.totalBytes.toFloat() / maxBytes.toFloat()
    val progress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(520),
        label = "usage-progress",
    )
    val tileColor by animateColorAsState(
        targetValue = categoryColor(row.category),
        animationSpec = tween(250),
        label = "usage-color",
    )
    CardBlock(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tileColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (row.category) {
                        TrafficCategory.HOTSPOT -> "热"
                        TrafficCategory.SYSTEM -> "系"
                        else -> row.label.take(1).uppercase()
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.label,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatBytes(row.totalBytes), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = tileColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ZoomControls(
    label: String,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        IconButton(onClick = onZoomOut, enabled = canZoomOut) {
            Icon(Icons.Rounded.RemoveIcon, contentDescription = "缩小")
        }
        IconButton(onClick = onZoomIn, enabled = canZoomIn) {
            Icon(Icons.Rounded.AddIcon, contentDescription = "放大")
        }
    }
}

@Composable
private fun MonthlyBars(points: List<com.loo.trafficwatch.data.SeriesPoint>) {
    CardBlock {
        if (points.isEmpty()) {
            EmptyBlock("暂无月度数据", padded = false)
            return@CardBlock
        }
        val maxBytes = points.maxOf { it.totalBytes }.coerceAtLeast(1L)
        points.forEach { point ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMonth(point.bucketStartMillis), modifier = Modifier.width(48.dp))
                    LinearProgressIndicator(
                        progress = { (point.totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatBytes(point.totalBytes), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    actionText: String,
    onClick: () -> Unit,
) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    text = if (granted) "已授权" else "未授权",
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onClick, enabled = !granted) {
                Text(if (granted) "完成" else actionText)
            }
        }
    }
}

@Composable
private fun SimProfileEditor(
    profile: SimProfile,
    onSave: (SimProfile) -> Unit,
) {
    var label by remember(profile) { mutableStateOf(profile.label) }
    var carrier by remember(profile) { mutableStateOf(profile.carrier) }
    var phone by remember(profile) { mutableStateOf(profile.phoneNumber) }

    CardBlock {
        Text("卡${profile.slot}", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("标签") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = carrier,
            onValueChange = { carrier = it },
            label = { Text("运营商") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("手机号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { onSave(profile.copy(label = label, carrier = carrier, phoneNumber = phone)) }) {
            Icon(Icons.Rounded.SaveIcon, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("保存")
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun CardBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
private fun EmptyBlock(text: String, padded: Boolean = true) {
    val modifier = if (padded) Modifier.fillMaxWidth().padding(24.dp) else Modifier.fillMaxWidth()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendFooter(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun categoryColor(category: TrafficCategory): Color = when (category) {
    TrafficCategory.HOTSPOT -> Color(0xFF56616B)
    TrafficCategory.SYSTEM -> Color(0xFF8A8E91)
    TrafficCategory.UNKNOWN -> Color(0xFF6256A7)
    TrafficCategory.APP -> Color(0xFF1B7F79)
}

private fun logLevelColor(level: TrafficLogLevel): Color = when (level) {
    TrafficLogLevel.SUCCESS -> Color(0xFF1B7F79)
    TrafficLogLevel.INFO -> Color(0xFF2F80C0)
    TrafficLogLevel.WARNING -> Color(0xFFE0A928)
    TrafficLogLevel.ERROR -> Color(0xFFD46A4C)
}

private data class TabSpec(
    val title: String,
    val icon: ImageVector,
)
