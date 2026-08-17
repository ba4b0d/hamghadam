package com.fitnessapp.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DashboardFormattersTest {

    @Test
    fun formatDuration_handlesHoursMinutesAndZero() {
        assertEquals("8h 30m", DashboardFormatters.formatDuration(8 * 3600L + 30 * 60L))
        assertEquals("45m", DashboardFormatters.formatDuration(45 * 60L))
        assertEquals("2h", DashboardFormatters.formatDuration(2 * 3600L))
        assertEquals("0m", DashboardFormatters.formatDuration(0L))
    }

    @Test
    fun formatRelativeTime_coversFreshToDays() {
        val now = 1_700_000_000_000L
        assertEquals("just now", DashboardFormatters.formatRelativeTime(now, now - 5_000L))
        assertEquals("12m ago", DashboardFormatters.formatRelativeTime(now, now - 12 * 60_000L))
        assertEquals("2h ago", DashboardFormatters.formatRelativeTime(now, now - 2 * 3_600_000L))
        assertEquals("3d ago", DashboardFormatters.formatRelativeTime(now, now - 3 * 86_400_000L))
        assertEquals("never", DashboardFormatters.formatRelativeTime(now, 0L))
    }

    @Test
    fun sourceLabel_knownAppsGetFriendlyNames() {
        assertEquals("Samsung Health", DashboardFormatters.sourceLabel("com.samsung.health"))
        assertEquals("Google Fit", DashboardFormatters.sourceLabel("com.google.android.apps.fitness"))
        assertEquals("Demo Data", DashboardFormatters.sourceLabel("com.fitness.explorer.datagenerator"))
    }

    @Test
    fun sourceLabel_unknownPackageFallsBackToHumanizedTail() {
        assertEquals("My Health App", DashboardFormatters.sourceLabel("com.example.my_health_app"))
        assertEquals("Something", DashboardFormatters.sourceLabel("com.unknown.Something"))
    }

    @Test
    fun sourceAttribution_dedupesSortsAndJoins() {
        val apps = listOf(
            "com.fitness.explorer.datagenerator",
            "com.samsung.health",
            "com.fitness.explorer.datagenerator",
        )
        assertEquals(
            "Demo Data · Samsung Health",
            DashboardFormatters.sourceAttribution(apps),
        )
        assertEquals("", DashboardFormatters.sourceAttribution(emptyList()))
    }

    @Test
    fun dayLabel_formatsWeekdayAndDay() {
        // 2026-08-16 is a Sunday.
        assertEquals("Sun 16", DashboardFormatters.dayLabel(LocalDate.of(2026, 8, 16)))
        assertEquals("Today", DashboardFormatters.dayLabel(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 16)))
    }

    @Test
    fun weekTotals_aggregatesOnlyPresentDays() {
        val week = listOf(
            null,
            DailySummary("2026-08-11", 210, 5000),
            DailySummary("2026-08-12", 210, 12000),
            null,
            DailySummary("2026-08-14", 210, 8000),
            null,
            DailySummary("2026-08-16", 210, 3000),
        )
        val totals = DashboardFormatters.weekTotals(week)
        assertEquals(28_000L, totals.totalSteps)
        assertEquals(12_000L, totals.bestDaySteps)
        assertEquals(4, totals.activeDays)
        assertEquals(true, totals.hasData)
        assertEquals(false, DashboardFormatters.weekTotals(List(7) { null }).hasData)
    }

    @Test
    fun weekSummaryDescription_listsEveryDayInOrder() {
        val today = LocalDate.of(2026, 8, 16)
        val week = listOf(
            null,
            DailySummary("2026-08-11", 210, 5000),
            null,
            null,
            null,
            null,
            DailySummary("2026-08-16", 210, 9000),
        )
        val desc = DashboardFormatters.weekSummaryDescription(week, today)
        assertEquals(true, desc.startsWith("Last 7 days —"))
        assertEquals(true, desc.contains("Mon 10: no data"))
        assertEquals(true, desc.contains("Tue 11: 5,000 steps"))
        assertEquals(true, desc.contains("Today: 9,000 steps"))
    }

    @Test
    fun formatCount_groupsThousands() {
        assertEquals("8,000", DashboardFormatters.formatCount(8000))
        assertEquals("0", DashboardFormatters.formatCount(0))
        assertEquals("12,340", DashboardFormatters.formatCount(12340))
    }
}
