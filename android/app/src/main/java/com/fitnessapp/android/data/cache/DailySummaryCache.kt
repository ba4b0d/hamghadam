package com.fitnessapp.android.data.cache

import com.fitnessapp.android.data.model.DailySummary
import com.fitnessapp.android.data.model.DailySummaryCodec
import java.time.LocalDate

/**
 * Local cache of [DailySummary] rows, keyed by local date.
 *
 * The dashboard week strip reads from here first (offline + fast); the sync
 * worker and "Sync now" write here after a successful POST. The server is only
 * consulted for days still missing from the cache.
 */
interface DailySummaryCache {
    /** Persist one row (upsert by date). */
    fun save(row: DailySummary)

    fun get(date: String): DailySummary?

    /** All cached rows (oldest first). */
    fun loadAll(): List<DailySummary>

    /** Rows whose date falls within [startInclusive, endInclusive]. */
    fun range(startInclusive: LocalDate, endInclusive: LocalDate): List<DailySummary>

    fun clear()
}

/**
 * SharedPreferences-backed cache: rows are stored as one JSON array under a
 * single key. Fine for v1 volume (a few hundred days max).
 */
class PrefsDailySummaryCache(
    private val prefs: android.content.SharedPreferences,
    private val codec: DailySummaryCodec = DailySummaryCodec,
) : DailySummaryCache {

    override fun save(row: DailySummary) {
        val rows = loadAll().filterNot { it.date == row.date } + row
        prefs.edit().putString(KEY_ROWS, codec.encodeList(rows.sortedBy { it.date })).apply()
    }

    override fun get(date: String): DailySummary? = loadAll().firstOrNull { it.date == date }

    override fun loadAll(): List<DailySummary> =
        codec.decodeList(prefs.getString(KEY_ROWS, null) ?: "")

    override fun range(startInclusive: LocalDate, endInclusive: LocalDate): List<DailySummary> =
        loadAll().filter { row ->
            val d = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: return@filter false
            !d.isBefore(startInclusive) && !d.isAfter(endInclusive)
        }

    override fun clear() {
        prefs.edit().remove(KEY_ROWS).apply()
    }

    companion object {
        private const val KEY_ROWS = "daily_summary_rows_v1"
    }
}

/** Test double — same semantics, no persistence. */
class InMemoryDailySummaryCache : DailySummaryCache {
    private val rows = LinkedHashMap<String, DailySummary>()

    override fun save(row: DailySummary) {
        rows[row.date] = row
    }

    override fun get(date: String): DailySummary? = rows[date]

    override fun loadAll(): List<DailySummary> = rows.values.sortedBy { it.date }

    override fun range(startInclusive: LocalDate, endInclusive: LocalDate): List<DailySummary> {
        val start = startInclusive.toString()
        val end = endInclusive.toString()
        return loadAll().filter { it.date >= start && it.date <= end }
    }

    override fun clear() {
        rows.clear()
    }
}
