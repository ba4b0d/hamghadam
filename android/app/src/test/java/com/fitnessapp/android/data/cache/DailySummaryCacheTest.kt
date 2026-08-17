package com.fitnessapp.android.data.cache

import com.fitnessapp.android.data.model.DailySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DailySummaryCacheTest {

    private fun row(date: String, steps: Long = 1000) = DailySummary(
        date = date,
        tzOffsetMinutes = 210,
        steps = steps,
        sourceApps = listOf("com.samsung.health"),
    )

    @Test
    fun save_get_loadAll_upsertsByDate() {
        val cache = InMemoryDailySummaryCache()
        cache.save(row("2026-08-15", 3200))
        cache.save(row("2026-08-16", 8000))
        cache.save(row("2026-08-15", 3400)) // upsert

        assertEquals(3400L, cache.get("2026-08-15")!!.steps)
        assertEquals(2, cache.loadAll().size)
        assertEquals(listOf("2026-08-15", "2026-08-16"), cache.loadAll().map { it.date })
        assertNull(cache.get("2026-08-14"))
    }

    @Test
    fun range_returnsOnlyWindowedDates() {
        val cache = InMemoryDailySummaryCache()
        cache.save(row("2026-08-09"))
        cache.save(row("2026-08-10"))
        cache.save(row("2026-08-11"))
        cache.save(row("2026-08-16"))
        cache.save(row("2026-08-17"))

        val rows = cache.range(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 16))
        assertEquals(listOf("2026-08-10", "2026-08-11", "2026-08-16"), rows.map { it.date })
    }

    @Test
    fun clear_removesEverything() {
        val cache = InMemoryDailySummaryCache()
        cache.save(row("2026-08-16"))
        cache.clear()
        assertEquals(0, cache.loadAll().size)
    }

    @Test
    fun emptyCache_returnsEmptyEverything() {
        val cache = InMemoryDailySummaryCache()
        assertEquals(emptyList<DailySummary>(), cache.loadAll())
        assertEquals(emptyList<DailySummary>(), cache.range(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 16)))
        assertNull(cache.get("2026-08-16"))
    }
}
