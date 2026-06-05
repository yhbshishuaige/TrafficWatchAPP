package com.loo.trafficwatch.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.ZoneId

class TrafficDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE traffic_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                captured_at INTEGER NOT NULL,
                window_start INTEGER NOT NULL,
                window_end INTEGER NOT NULL,
                sim_slot INTEGER NOT NULL,
                uid INTEGER NOT NULL,
                package_name TEXT,
                app_label TEXT NOT NULL,
                category TEXT NOT NULL,
                rx_bytes INTEGER NOT NULL,
                tx_bytes INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_samples_time ON traffic_samples(window_start, window_end)")
        db.execSQL("CREATE INDEX idx_samples_uid_time ON traffic_samples(uid, window_start)")
        db.execSQL("CREATE INDEX idx_samples_sim_time ON traffic_samples(sim_slot, window_start)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Keep existing rows across app updates. Future schema changes should add
        // ALTER TABLE steps here instead of rebuilding the table.
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_samples_time ON traffic_samples(window_start, window_end)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_samples_uid_time ON traffic_samples(uid, window_start)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_samples_sim_time ON traffic_samples(sim_slot, window_start)")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    fun insertSamples(samples: List<TrafficSample>) {
        if (samples.isEmpty()) return
        writableDatabase.runInTransaction {
            samples.forEach { sample ->
                insert(TABLE_SAMPLES, null, sample.toValues())
            }
        }
    }

    fun dashboardStats(nowMillis: Long = System.currentTimeMillis()): DashboardStats {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val todayBytes = sumBytes(todayStart, nowMillis)
        val monthBytes = sumBytes(monthStart, nowMillis)
        val hotspotMonthBytes = sumBytes(monthStart, nowMillis, category = TrafficCategory.HOTSPOT)
        val simMonthBytes = (1..2).associateWith { slot ->
            sumBytes(monthStart, nowMillis, simSlot = slot)
        }
        val top = usageRows(monthStart, nowMillis, limit = 24)
            .firstOrNull { it.category == TrafficCategory.APP }

        return DashboardStats(
            todayBytes = todayBytes,
            monthBytes = monthBytes,
            hotspotMonthBytes = hotspotMonthBytes,
            simMonthBytes = simMonthBytes,
            topAppLabel = top?.label ?: "暂无",
            topAppBytes = top?.totalBytes ?: 0L,
        )
    }

    fun usageRows(
        startMillis: Long,
        endMillis: Long,
        simSlot: Int? = null,
        limit: Int = 50,
    ): List<UsageRow> {
        val args = mutableListOf(startMillis.toString(), endMillis.toString())
        val simWhere = if (simSlot != null) {
            args += simSlot.toString()
            " AND sim_slot = ?"
        } else {
            ""
        }

        val sql = """
            SELECT uid, package_name, app_label, category,
                   SUM(rx_bytes) AS rx_total,
                   SUM(tx_bytes) AS tx_total
            FROM traffic_samples
            WHERE window_start >= ? AND window_end <= ?$simWhere
            GROUP BY uid, package_name, app_label, category
            ORDER BY (rx_total + tx_total) DESC
            LIMIT $limit
        """.trimIndent()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).useRows { cursor ->
            UsageRow(
                uid = cursor.getInt(0),
                packageName = cursor.getStringOrNull(1),
                label = cursor.getString(2),
                category = TrafficCategory.valueOf(cursor.getString(3)),
                rxBytes = cursor.getLong(4),
                txBytes = cursor.getLong(5),
            )
        }
    }

    fun series(
        startMillis: Long,
        endMillis: Long,
        bucketMillis: Long,
        simSlot: Int? = null,
    ): List<SeriesPoint> {
        val args = mutableListOf(startMillis.toString(), endMillis.toString())
        val simWhere = if (simSlot != null) {
            args += simSlot.toString()
            " AND sim_slot = ?"
        } else {
            ""
        }

        val sql = """
            SELECT ((window_start / $bucketMillis) * $bucketMillis) AS bucket_start,
                   SUM(rx_bytes) AS rx_total,
                   SUM(tx_bytes) AS tx_total
            FROM traffic_samples
            WHERE window_start >= ? AND window_end <= ?$simWhere
            GROUP BY bucket_start
            ORDER BY bucket_start ASC
        """.trimIndent()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).useRows { cursor ->
            SeriesPoint(
                bucketStartMillis = cursor.getLong(0),
                rxBytes = cursor.getLong(1),
                txBytes = cursor.getLong(2),
            )
        }
    }

    fun uidSeries(
        uid: Int,
        startMillis: Long,
        endMillis: Long,
        bucketMillis: Long,
        simSlot: Int? = null,
    ): List<SeriesPoint> {
        val args = mutableListOf(startMillis.toString(), endMillis.toString(), uid.toString())
        val simWhere = if (simSlot != null) {
            args += simSlot.toString()
            " AND sim_slot = ?"
        } else {
            ""
        }

        val sql = """
            SELECT ((window_start / $bucketMillis) * $bucketMillis) AS bucket_start,
                   SUM(rx_bytes) AS rx_total,
                   SUM(tx_bytes) AS tx_total
            FROM traffic_samples
            WHERE window_start >= ? AND window_end <= ? AND uid = ?$simWhere
            GROUP BY bucket_start
            ORDER BY bucket_start ASC
        """.trimIndent()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).useRows { cursor ->
            SeriesPoint(
                bucketStartMillis = cursor.getLong(0),
                rxBytes = cursor.getLong(1),
                txBytes = cursor.getLong(2),
            )
        }
    }

    fun monthlySeries(
        startMillis: Long,
        endMillis: Long,
        simSlot: Int? = null,
    ): List<SeriesPoint> {
        val args = mutableListOf(startMillis.toString(), endMillis.toString())
        val simWhere = if (simSlot != null) {
            args += simSlot.toString()
            " AND sim_slot = ?"
        } else {
            ""
        }

        val sql = """
            SELECT strftime('%Y-%m', window_start / 1000, 'unixepoch', 'localtime') AS month_key,
                   MIN(window_start) AS bucket_start,
                   SUM(rx_bytes) AS rx_total,
                   SUM(tx_bytes) AS tx_total
            FROM traffic_samples
            WHERE window_start >= ? AND window_end <= ?$simWhere
            GROUP BY month_key
            ORDER BY month_key ASC
        """.trimIndent()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).useRows { cursor ->
            SeriesPoint(
                bucketStartMillis = cursor.getLong(1),
                rxBytes = cursor.getLong(2),
                txBytes = cursor.getLong(3),
            )
        }
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_SAMPLES, null, null)
    }

    private fun sumBytes(
        startMillis: Long,
        endMillis: Long,
        category: TrafficCategory? = null,
        simSlot: Int? = null,
    ): Long {
        val args = mutableListOf(startMillis.toString(), endMillis.toString())
        val categoryWhere = if (category != null) {
            args += category.name
            " AND category = ?"
        } else {
            ""
        }
        val simWhere = if (simSlot != null) {
            args += simSlot.toString()
            " AND sim_slot = ?"
        } else {
            ""
        }
        val sql = """
            SELECT COALESCE(SUM(rx_bytes + tx_bytes), 0)
            FROM traffic_samples
            WHERE window_start >= ? AND window_end <= ?$categoryWhere$simWhere
        """.trimIndent()
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun TrafficSample.toValues(): ContentValues = ContentValues().apply {
        put("captured_at", capturedAtMillis)
        put("window_start", windowStartMillis)
        put("window_end", windowEndMillis)
        put("sim_slot", simSlot)
        put("uid", uid)
        put("package_name", packageName)
        put("app_label", appLabel)
        put("category", category.name)
        put("rx_bytes", rxBytes)
        put("tx_bytes", txBytes)
    }

    companion object {
        private const val DATABASE_NAME = "traffic_watch.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SAMPLES = "traffic_samples"
    }
}

private inline fun SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

private inline fun <T> Cursor.useRows(mapper: (Cursor) -> T): List<T> = use { cursor ->
    buildList {
        while (cursor.moveToNext()) {
            add(mapper(cursor))
        }
    }
}

private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
