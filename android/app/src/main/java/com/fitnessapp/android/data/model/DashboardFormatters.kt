package com.fitnessapp.android.data.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure formatting helpers for the dashboard. All functions are free of Android
 * framework dependencies so they are unit-testable on the JVM.
 */
object DashboardFormatters {

    private val SOURCE_LABELS = mapOf(
        "com.samsung.health" to "Samsung Health",
        "com.samsung.android.service.health" to "Samsung Health",
        "com.samsung.shealth" to "Samsung Health",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.fitness.explorer.datagenerator" to "Demo Data",
        "com.google.android.wearable.app" to "Wear OS",
        "com.fitexplorer" to "Demo Data",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.xiaomi.hm.health" to "Mi Fitness",
        "com.huawei.health" to "Huawei Health",
    )

    /** Friendly app label for a Health Connect data-origin package. */
    fun sourceLabel(packageName: String): String =
        SOURCE_LABELS[packageName]
            ?: packageName.substringAfterLast('.').replace('_', ' ')
                .split(' ')
                .joinToString(" ") { word ->
                    word.replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString()
                    }
                }
                .ifBlank { packageName }

    /** "Samsung Health · Google Fit" — deduped, in stable sorted order. */
    fun sourceAttribution(sourceApps: List<String>): String =
        sourceApps.distinct().sorted().joinToString(" · ") { sourceLabel(it) }

    /** "Mon 16", "Tue 17" — short weekday + day-of-month. */
    fun dayLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH))

    /** "Today" for the current date, else [dayLabel]. */
    fun dayLabel(date: LocalDate, today: LocalDate): String =
        if (date == today) "Today" else dayLabel(date)

    /** 8h 30m / 45m / 0m. */
    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /** "just now", "12m ago", "2h ago", "3d ago", else the local date/time. */
    fun formatRelativeTime(nowMillis: Long, atMillis: Long): String {
        if (atMillis <= 0L) return "never"
        val diff = nowMillis - atMillis
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            diff < 7L * 86_400_000L -> "${diff / 86_400_000L}d ago"
            else -> {
                val zoned = java.time.Instant.ofEpochMilli(atMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                zoned.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH))
            }
        }
    }

    /** "8,000 steps · 8000 steps today" hero sub-line. */
    fun stepsHeroSub(steps: Long): String = "${steps} steps today"

    /** Screen-reader description for the 7-day bar chart. */
    fun weekSummaryDescription(week: List<DailySummary?>, today: LocalDate): String {
        val parts = week.mapIndexed { index, row ->
            val date = today.minusDays((week.size - 1 - index).toLong())
            val value = row?.steps?.let { "${formatCount(it)} steps" } ?: "no data"
            "${dayLabel(date, today)}: $value"
        }
        return "Last 7 days — " + parts.joinToString(", ")
    }

    /** "Total this week" / "Best day" aggregation used by the week strip header. */
    data class WeekTotals(val totalSteps: Long, val bestDaySteps: Long, val activeDays: Int) {
        val hasData: Boolean get() = activeDays > 0
    }

    fun weekTotals(week: List<DailySummary?>): WeekTotals {
        var total = 0L
        var best = 0L
        var active = 0
        week.forEach { row ->
            row?.let {
                total += it.steps
                if (it.steps > best) best = it.steps
                if (it.steps > 0) active++
            }
        }
        return WeekTotals(totalSteps = total, bestDaySteps = best, activeDays = active)
    }

    /** "12,340" with grouping separators. */
    fun formatCount(value: Long): String = String.format(Locale.US, "%,d", value)
}
