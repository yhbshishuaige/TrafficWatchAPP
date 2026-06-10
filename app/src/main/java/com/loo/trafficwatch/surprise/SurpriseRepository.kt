package com.loo.trafficwatch.surprise

import android.content.Context
import androidx.core.content.edit
import com.loo.trafficwatch.data.DashboardStats
import com.loo.trafficwatch.data.SeriesPoint
import com.loo.trafficwatch.data.TrafficCategory
import com.loo.trafficwatch.data.UsageRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId

enum class BadgeKind {
    NORMAL,
    EASTER_EGG,
}

data class BadgeDefinition(
    val id: String,
    val title: String,
    val description: String,
    val requirement: String,
    val kind: BadgeKind,
)

data class BadgeState(
    val definition: BadgeDefinition,
    val unlocked: Boolean,
    val unlockedAtMillis: Long,
)

data class BadgeUnlock(
    val title: String,
    val message: String,
)

data class SurpriseQuote(
    val text: String,
    val footnote: String,
)

data class FlowPersonality(
    val title: String,
    val subtitle: String,
    val body: String,
    val highlight: String,
)

data class SpecialMoment(
    val title: String,
    val message: String,
    val sparkleText: String,
)

data class SurpriseUiState(
    val badges: List<BadgeState> = emptyList(),
    val hiddenRoomUnlocked: Boolean = false,
    val quote: SurpriseQuote = SurpriseQuotes.first(),
    val quoteViews: Int = 0,
    val weeklyPersonality: FlowPersonality = FlowPersonality(
        title = "等待信号型",
        subtitle = "还在收集这一周的线索",
        body = "多记录几次流量之后，这里会给你一份轻轻的总结。",
        highlight = "暂无周数据",
    ),
    val monthlyPersonality: FlowPersonality = FlowPersonality(
        title = "月光待机型",
        subtitle = "这个月还很安静",
        body = "数据正在慢慢变成故事，等它多一点，惊喜也会更准一点。",
        highlight = "暂无月数据",
    ),
    val specialMoment: SpecialMoment? = null,
) {
    val unlockedNormalCount: Int
        get() = badges.count { it.unlocked && it.definition.kind == BadgeKind.NORMAL }

    val normalCount: Int
        get() = badges.count { it.definition.kind == BadgeKind.NORMAL }

    val unlockedEasterEggCount: Int
        get() = badges.count { it.unlocked && it.definition.kind == BadgeKind.EASTER_EGG }

    val easterEggCount: Int
        get() = badges.count { it.definition.kind == BadgeKind.EASTER_EGG }
}

class SurpriseRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun state(
        stats: DashboardStats,
        usageRows: List<UsageRow>,
        lastWeek: List<SeriesPoint>,
        monthly: List<SeriesPoint>,
        monitoringEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): SurpriseUiState {
        unlockAutomaticBadges(stats, usageRows, lastWeek, monthly, monitoringEnabled, nowMillis)
        val unlocked = unlockedBadgeIds()
        val quoteViews = prefs.getInt(KEY_QUOTE_VIEWS, 0)
        val quoteIndex = prefs.getInt(KEY_QUOTE_INDEX, 0)
        return SurpriseUiState(
            badges = BadgeCatalog.all.map { definition ->
                BadgeState(
                    definition = definition,
                    unlocked = definition.id in unlocked,
                    unlockedAtMillis = prefs.getLong("${KEY_BADGE_TIME_PREFIX}${definition.id}", 0L),
                )
            },
            hiddenRoomUnlocked = prefs.getBoolean(KEY_HIDDEN_ROOM_UNLOCKED, false),
            quote = SurpriseQuotes.at(quoteIndex),
            quoteViews = quoteViews,
            weeklyPersonality = buildWeeklyPersonality(lastWeek),
            monthlyPersonality = buildMonthlyPersonality(stats, usageRows),
            specialMoment = specialMoment(nowMillis),
        )
    }

    fun recordTitleTap(nowMillis: Long = System.currentTimeMillis()): BadgeUnlock? {
        val firstTap = prefs.getLong(KEY_TITLE_TAP_FIRST, 0L)
        val previousCount = prefs.getInt(KEY_TITLE_TAP_COUNT, 0)
        val withinWindow = firstTap > 0L && nowMillis - firstTap <= TITLE_TAP_WINDOW_MILLIS
        val nextCount = if (withinWindow) previousCount + 1 else 1
        prefs.edit {
            putLong(KEY_TITLE_TAP_FIRST, if (withinWindow) firstTap else nowMillis)
            putInt(KEY_TITLE_TAP_COUNT, nextCount)
        }
        if (nextCount < TITLE_TAP_TARGET) return null

        prefs.edit {
            putBoolean(KEY_HIDDEN_ROOM_UNLOCKED, true)
            putLong(KEY_TITLE_TAP_FIRST, 0L)
            putInt(KEY_TITLE_TAP_COUNT, 0)
        }
        return unlockBadge(
            id = "secret_door",
            nowMillis = nowMillis,
            message = "你敲开了一扇不写在说明书里的门。欢迎来到流量观察的心事角落。",
        )
    }

    fun recordHiddenQuoteSeen(nowMillis: Long = System.currentTimeMillis()): BadgeUnlock? {
        val views = prefs.getInt(KEY_QUOTE_VIEWS, 0) + 1
        val nextIndex = (prefs.getInt(KEY_QUOTE_INDEX, 0) + 1) % SurpriseQuotes.size
        prefs.edit {
            putInt(KEY_QUOTE_VIEWS, views)
            putInt(KEY_QUOTE_INDEX, nextIndex)
        }
        return when (views) {
            8 -> unlockBadge(
                id = "quote_collector",
                nowMillis = nowMillis,
                message = "你认真读完了很多句小话。被认真对待的，不止是流量，还有今天的你。",
            )

            18 -> unlockBadge(
                id = "soft_reader",
                nowMillis = nowMillis,
                message = "你把这里翻成了一个小小的秘密书架。愿每次打开，都有一句话接住你。",
            )

            else -> null
        }
    }

    fun recordAppFocus(
        row: UsageRow,
        monthBytes: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): BadgeUnlock? {
        val storedUid = prefs.getInt(KEY_FOCUS_UID, Int.MIN_VALUE)
        val firstTap = prefs.getLong(KEY_FOCUS_FIRST, 0L)
        val previousCount = prefs.getInt(KEY_FOCUS_COUNT, 0)
        val sameWindow = storedUid == row.uid && firstTap > 0L && nowMillis - firstTap <= APP_FOCUS_WINDOW_MILLIS
        val nextCount = if (sameWindow) previousCount + 1 else 1
        prefs.edit {
            putInt(KEY_FOCUS_UID, row.uid)
            putLong(KEY_FOCUS_FIRST, if (sameWindow) firstTap else nowMillis)
            putInt(KEY_FOCUS_COUNT, nextCount)
            putString(KEY_FOCUS_LABEL, row.label)
        }

        if (nextCount >= APP_FOCUS_TARGET) {
            unlockBadge(
                id = "looking_for_you",
                nowMillis = nowMillis,
                message = "你真的很在乎 ${row.label} 呢，一分钟内看了它 $nextCount 次。念念不忘，数据也会有回响。",
            )?.let { return it }
        }

        val ratio = if (monthBytes > 0L) row.totalBytes.toDouble() / monthBytes.toDouble() else 0.0
        if (row.category == TrafficCategory.APP && row.totalBytes >= 300L * MB && ratio >= 0.55) {
            return unlockBadge(
                id = "chosen_app",
                nowMillis = nowMillis,
                message = "${row.label} 这个月拿到了很多关注。喜欢当然可以明显一点，流量已经替你承认了。",
            )
        }
        return null
    }

    fun recordSurpriseVisit(nowMillis: Long = System.currentTimeMillis()): BadgeUnlock? {
        val moment = specialMoment(nowMillis) ?: return recordLateNightVisit(nowMillis)
        return when {
            moment.title.contains("周五") -> unlockBadge(
                id = "friday_breeze",
                nowMillis = nowMillis,
                message = "周五晚上的你值得被温柔对待。工作先放一放，网络也慢慢来。",
            )

            moment.title.contains("新年") -> unlockBadge(
                id = "new_year_signal",
                nowMillis = nowMillis,
                message = "新的一年也会有新的连接。愿重要的消息都来得刚刚好。",
            )

            else -> recordLateNightVisit(nowMillis)
        }
    }

    private fun recordLateNightVisit(nowMillis: Long): BadgeUnlock? {
        val localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        if (localTime.hour !in 0..4) return null
        return unlockBadge(
            id = "night_listener",
            nowMillis = nowMillis,
            message = "这么晚还在看数据，辛苦啦。夜里也有人替你把细小的变化记下来。",
        )
    }

    private fun unlockAutomaticBadges(
        stats: DashboardStats,
        usageRows: List<UsageRow>,
        lastWeek: List<SeriesPoint>,
        monthly: List<SeriesPoint>,
        monitoringEnabled: Boolean,
        nowMillis: Long,
    ) {
        if (monitoringEnabled) unlockBadge("watcher_on", nowMillis)
        if (stats.monthBytes > 0L || usageRows.isNotEmpty()) unlockBadge("first_signal", nowMillis)
        if (usageRows.any { it.category == TrafficCategory.APP }) unlockBadge("app_spotter", nowMillis)
        if (stats.todayBytes in 1 until 50L * MB) unlockBadge("light_step", nowMillis)
        if (stats.hotspotMonthBytes >= 100L * MB) unlockBadge("hotspot_keeper", nowMillis)
        if ((stats.simMonthBytes[1] ?: 0L) > 0L && (stats.simMonthBytes[2] ?: 0L) > 0L) {
            unlockBadge("dual_sim_balance", nowMillis)
        }
        if (lastWeek.count { it.totalBytes > 0L } >= 12) unlockBadge("week_rhythm", nowMillis)
        if (monthly.count { it.totalBytes > 0L } >= 2) unlockBadge("month_memory", nowMillis)
        val topApp = usageRows.firstOrNull { it.category == TrafficCategory.APP }
        if (topApp != null && stats.monthBytes > 0L) {
            val ratio = topApp.totalBytes.toDouble() / stats.monthBytes.toDouble()
            if (topApp.totalBytes >= 300L * MB && ratio >= 0.7) {
                unlockBadge("one_app_ocean", nowMillis)
            }
        }
    }

    private fun buildWeeklyPersonality(lastWeek: List<SeriesPoint>): FlowPersonality {
        val activePoints = lastWeek.filter { it.totalBytes > 0L }
        if (activePoints.isEmpty()) {
            return FlowPersonality(
                title = "静静待机型",
                subtitle = "这一周像把手机轻轻合上",
                body = "没有明显的流量波动，也是一种很会照顾注意力的生活方式。",
                highlight = "等待更多周数据",
            )
        }
        val total = activePoints.sumOf { it.totalBytes }
        val peak = activePoints.maxOf { it.totalBytes }
        val peakRatio = peak.toDouble() / total.coerceAtLeast(1L).toDouble()
        return when {
            peakRatio >= 0.45 -> FlowPersonality(
                title = "灵感暴雨型",
                subtitle = "一段时间集中冲浪，然后漂亮收手",
                body = "你的网络节奏很像突然打开一扇窗：来得热烈，结束得也干脆。",
                highlight = "周峰值 ${(peakRatio * 100).toInt()}%",
            )

            activePoints.size >= 72 -> FlowPersonality(
                title = "稳定在线型",
                subtitle = "每天都有一点细小的连接",
                body = "你不是猛冲型，更像把生活同步得很均匀。这样的节奏让人很安心。",
                highlight = "活跃时段 ${activePoints.size} 个",
            )

            total < 300L * MB -> FlowPersonality(
                title = "轻装漫游型",
                subtitle = "用得刚刚好，不多拿一点注意力",
                body = "这一周你像在网络里散步，走过、看见、然后把时间还给自己。",
                highlight = "周流量 ${formatCompactBytes(total)}",
            )

            else -> FlowPersonality(
                title = "松弛冲浪型",
                subtitle = "有热闹，也有空白",
                body = "你的流量曲线有自己的呼吸感。该连接时连接，该安静时安静。",
                highlight = "周流量 ${formatCompactBytes(total)}",
            )
        }
    }

    private fun buildMonthlyPersonality(stats: DashboardStats, usageRows: List<UsageRow>): FlowPersonality {
        if (stats.monthBytes <= 0L) {
            return FlowPersonality(
                title = "月光待机型",
                subtitle = "这个月还没留下太多脚印",
                body = "等数据慢慢多起来，这里会变成一份更像你的月度小结。",
                highlight = "暂无月数据",
            )
        }
        val topApp = usageRows.firstOrNull { it.category == TrafficCategory.APP }
        val topRatio = topApp?.let { it.totalBytes.toDouble() / stats.monthBytes.coerceAtLeast(1L).toDouble() } ?: 0.0
        val hotspotRatio = stats.hotspotMonthBytes.toDouble() / stats.monthBytes.coerceAtLeast(1L).toDouble()
        return when {
            stats.monthBytes < 500L * MB -> FlowPersonality(
                title = "省流诗人型",
                subtitle = "这个月的每一 MB 都挺有分寸",
                body = "你把网络用得很轻，像只拿走刚好需要的那一束光。",
                highlight = "本月 ${formatCompactBytes(stats.monthBytes)}",
            )

            hotspotRatio >= 0.35 -> FlowPersonality(
                title = "人间基站型",
                subtitle = "你把连接分享给了身边的人",
                body = "热点不是冷冰冰的数字，是你把网络递出去的一小段慷慨。",
                highlight = "热点占比 ${(hotspotRatio * 100).toInt()}%",
            )

            topApp != null && topRatio >= 0.55 -> FlowPersonality(
                title = "专注偏爱型",
                subtitle = "${topApp.label} 成了这个月的主角",
                body = "专一有时候不是计划出来的，是时间和流量一起投票选出来的。",
                highlight = "${topApp.label} ${(topRatio * 100).toInt()}%",
            )

            usageRows.count { it.category == TrafficCategory.APP } >= 10 -> FlowPersonality(
                title = "好奇巡游型",
                subtitle = "很多 App 都被你轻轻路过",
                body = "你这个月的网络足迹很丰富，像在不同窗口之间收集一点点世界。",
                highlight = "App 数 ${usageRows.count { it.category == TrafficCategory.APP }}",
            )

            else -> FlowPersonality(
                title = "均衡掌舵型",
                subtitle = "这个月的流量没有失控",
                body = "你把连接保持在舒服的位置。没有过分紧绷，也没有一路狂飙。",
                highlight = "本月 ${formatCompactBytes(stats.monthBytes)}",
            )
        }
    }

    private fun specialMoment(nowMillis: Long): SpecialMoment? {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        if (local.dayOfWeek == DayOfWeek.FRIDAY && local.hour in 18..23) {
            return SpecialMoment(
                title = "周五晚风模式",
                message = "下班后的网络也可以慢一点。今天的流量，被允许带一点自由的味道。",
                sparkleText = "晚风已上线",
            )
        }
        val today = local.toLocalDate()
        val monthDay = MonthDay.from(today)
        return when {
            monthDay == MonthDay.of(1, 1) -> SpecialMoment(
                title = "新年信号",
                message = "新的一年，愿你收到的每一条消息都刚好值得打开。",
                sparkleText = "新年快乐",
            )

            monthDay == MonthDay.of(5, 1) -> SpecialMoment(
                title = "劳动节小憩",
                message = "辛苦的人也要有暂停键。今天的连接，先服务于快乐。",
                sparkleText = "休息一下",
            )

            monthDay == MonthDay.of(10, 1) -> SpecialMoment(
                title = "假期漫游",
                message = "愿路上的信号稳定，愿想见的人都能联系上。",
                sparkleText = "假期在线",
            )

            isSpringWindow(today) -> SpecialMoment(
                title = "春节附近",
                message = "如果今天有很多问候穿过网络，希望它们都带着热乎乎的心意。",
                sparkleText = "团圆信号",
            )

            else -> null
        }
    }

    private fun isSpringWindow(date: LocalDate): Boolean {
        val day = MonthDay.from(date)
        return day >= MonthDay.of(1, 20) && day <= MonthDay.of(2, 20)
    }

    private fun unlockBadge(id: String, nowMillis: Long, message: String? = null): BadgeUnlock? {
        val definition = BadgeCatalog.byId(id) ?: return null
        val current = unlockedBadgeIds()
        if (id in current) return null
        prefs.edit {
            putStringSet(KEY_UNLOCKED_BADGES, current + id)
            putLong("${KEY_BADGE_TIME_PREFIX}$id", nowMillis)
        }
        return BadgeUnlock(
            title = definition.title,
            message = message ?: definition.description,
        )
    }

    private fun unlockedBadgeIds(): Set<String> =
        prefs.getStringSet(KEY_UNLOCKED_BADGES, emptySet()).orEmpty().toSet()

    companion object {
        private const val PREFS_NAME = "traffic_watch_surprises"
        private const val KEY_UNLOCKED_BADGES = "unlocked_badges"
        private const val KEY_BADGE_TIME_PREFIX = "badge_time_"
        private const val KEY_HIDDEN_ROOM_UNLOCKED = "hidden_room_unlocked"
        private const val KEY_TITLE_TAP_FIRST = "title_tap_first"
        private const val KEY_TITLE_TAP_COUNT = "title_tap_count"
        private const val KEY_QUOTE_VIEWS = "quote_views"
        private const val KEY_QUOTE_INDEX = "quote_index"
        private const val KEY_FOCUS_UID = "focus_uid"
        private const val KEY_FOCUS_FIRST = "focus_first"
        private const val KEY_FOCUS_COUNT = "focus_count"
        private const val KEY_FOCUS_LABEL = "focus_label"
        private const val TITLE_TAP_TARGET = 7
        private const val TITLE_TAP_WINDOW_MILLIS = 6_000L
        private const val APP_FOCUS_TARGET = 7
        private const val APP_FOCUS_WINDOW_MILLIS = 60_000L
        private const val MB = 1024L * 1024L
    }
}

private object BadgeCatalog {
    val all: List<BadgeDefinition> = listOf(
        BadgeDefinition(
            id = "first_signal",
            title = "第一束信号",
            description = "流量观察第一次记录到了属于你的网络足迹。",
            requirement = "记录到任意流量样本",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "watcher_on",
            title = "守望者上岗",
            description = "监测服务已经开启，接下来的细小变化会被好好记住。",
            requirement = "开启流量监测",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "app_spotter",
            title = "抓住主角",
            description = "本月已经看见至少一个 App 的流量记录。",
            requirement = "本月出现任意 App 流量",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "light_step",
            title = "轻装上阵",
            description = "今天的蜂窝流量很轻，像把注意力放回了自己手里。",
            requirement = "今日已有记录且低于 50MB",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "hotspot_keeper",
            title = "共享之光",
            description = "你把网络分享了出去，也把方便递给了别人。",
            requirement = "本月热点共享达到 100MB",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "dual_sim_balance",
            title = "双卡平衡术",
            description = "两张卡都留下了痕迹，生活里的连接被安排得很稳。",
            requirement = "两张卡本月都有流量记录",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "week_rhythm",
            title = "一周有回声",
            description = "最近一周的曲线已经有了节奏，数据开始像故事。",
            requirement = "最近一周至少 12 个时段有记录",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "month_memory",
            title = "月份收藏家",
            description = "不止一个月份被记住了，这个 App 正在陪你变成长期观察者。",
            requirement = "最近十二个月中至少两个月有记录",
            kind = BadgeKind.NORMAL,
        ),
        BadgeDefinition(
            id = "secret_door",
            title = "暗门被打开了",
            description = "你发现了标题背后的隐藏房间。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "quote_collector",
            title = "句子收集者",
            description = "你读了很多句小话，像在数据旁边收好了一些温柔。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "soft_reader",
            title = "温柔读者",
            description = "你把隐藏页翻成了一个小小的秘密书架。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "looking_for_you",
            title = "念念不忘",
            description = "有一个 App 在一分钟里被你反复查看，关心已经被看见啦。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "chosen_app",
            title = "偏爱很明显",
            description = "某个 App 收到了特别多的关注，流量替你悄悄盖了章。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "one_app_ocean",
            title = "一片海给了它",
            description = "这个月的大部分流量都流向了同一个 App，专注也可以很可爱。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "friday_breeze",
            title = "周五晚风",
            description = "周五夜晚打开惊喜页，像给这周按下一个轻轻的句号。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "new_year_signal",
            title = "新年第一束网",
            description = "新的一年也会有新的连接，重要的人都会找到你。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
        BadgeDefinition(
            id = "night_listener",
            title = "夜里也有人听见",
            description = "深夜打开惊喜页时，流量观察给你留了一盏小灯。",
            requirement = "？？？",
            kind = BadgeKind.EASTER_EGG,
        ),
    )

    fun byId(id: String): BadgeDefinition? = all.firstOrNull { it.id == id }
}

private object SurpriseQuotes {
    private val quotes = listOf(
        SurpriseQuote(
            text = "你今天打开这里，说明你有在认真照顾自己的世界。被记录的不是流量，是你和生活保持连接的方式。",
            footnote = "谢谢你来看看这些细小的变化。",
        ),
        SurpriseQuote(
            text = "有些消息晚一点来也没关系。真正重要的连接，通常不会因为几分钟的沉默就消失。",
            footnote = "愿你等到的是好消息。",
        ),
        SurpriseQuote(
            text = "你真的很在乎她或他呢。反复查看不是笨拙，是心里有一盏灯一直亮着。",
            footnote = "念念不忘，也要记得好好睡觉。",
        ),
        SurpriseQuote(
            text = "今天的流量如果很多，可能是世界刚好热闹；如果很少，也可能是你把时间留给了自己。",
            footnote = "两种都很好。",
        ),
        SurpriseQuote(
            text = "每一次刷新都像问一句：现在怎么样了？这个 App 会替你回答，也会悄悄记得你来过。",
            footnote = "你不是一个人在观察。",
        ),
        SurpriseQuote(
            text = "如果一个 App 占了很多流量，那大概是它陪你度过了一段很具体的时间。快乐也需要带宽。",
            footnote = "这很合理。",
        ),
        SurpriseQuote(
            text = "周五晚上适合把曲线看得松一点。今天已经努力过了，剩下的交给晚风。",
            footnote = "下班快乐。",
        ),
        SurpriseQuote(
            text = "省流不是亏待自己，而是把注意力留给更值得的地方。你有在好好选择。",
            footnote = "很厉害。",
        ),
        SurpriseQuote(
            text = "热点共享有一点像借伞：数字看起来普通，但里面有很真实的照顾。",
            footnote = "谢谢你的慷慨。",
        ),
        SurpriseQuote(
            text = "如果今天没有特别好的事发生，那至少这里有一句小话证明：你被认真欢迎过。",
            footnote = "欢迎你。",
        ),
    )

    val size: Int get() = quotes.size

    fun first(): SurpriseQuote = quotes.first()

    fun at(index: Int): SurpriseQuote = quotes[index.floorMod(quotes.size)]
}

private fun Int.floorMod(divisor: Int): Int {
    val result = this % divisor
    return if (result < 0) result + divisor else result
}

private fun formatCompactBytes(bytes: Long): String {
    val gb = 1024L * 1024L * 1024L
    val mb = 1024L * 1024L
    return when {
        bytes >= gb -> "%.1fGB".format(bytes.toDouble() / gb.toDouble())
        bytes >= mb -> "%.0fMB".format(bytes.toDouble() / mb.toDouble())
        bytes >= 1024L -> "%.0fKB".format(bytes.toDouble() / 1024.0)
        else -> "${bytes}B"
    }
}
