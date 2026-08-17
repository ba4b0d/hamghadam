package com.fitnessapp.android.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessapp.android.data.model.DailySummary
import com.fitnessapp.android.data.model.DashboardFormatters
import com.fitnessapp.android.ui.theme.AccentOrange
import com.fitnessapp.android.ui.theme.SoftBlue
import com.fitnessapp.android.ui.theme.SoftBlueLight
import java.time.LocalDate

/**
 * Minimal steps bar chart for the last 7 days. Pure Canvas — no chart library.
 * Days without data render a dashed baseline; today's bar is highlighted.
 * Exposes a screen-reader description of the whole week.
 */
@Composable
fun WeekStepsChart(
    week: List<DailySummary?>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val description = DashboardFormatters.weekSummaryDescription(week, today)
    val maxSteps = week.mapNotNull { it?.steps }.maxOrNull() ?: 0L
    val barColor = SoftBlue
    val todayColor = AccentOrange
    val emptyBaselineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEachIndexed { index, row ->
            val date = today.minusDays((week.size - 1 - index).toLong())
            val steps = row?.steps ?: 0L
            val fraction = if (maxSteps > 0) (steps.toFloat() / maxSteps.toFloat()) else 0f
            val isToday = date == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (steps > 0) DashboardFormatters.formatCount(steps) else "",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(120.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (steps > 0) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawBar(
                                fraction = fraction,
                                color = if (isToday) todayColor else barColor,
                                highlight = isToday,
                            )
                        }
                    } else {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawEmptyBaseline(color = emptyBaselineColor)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DashboardFormatters.dayLabel(date, today),
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun DrawScope.drawBar(fraction: Float, color: Color, highlight: Boolean) {
    val barWidth = size.width * 0.5f
    val barHeight = size.height * fraction.coerceIn(0.04f, 1f)
    val left = (size.width - barWidth) / 2f
    val top = size.height - barHeight
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
    )
    if (highlight) {
        drawRect(
            color = SoftBlueLight,
            topLeft = Offset(left, 0f),
            size = Size(barWidth, size.height),
        )
    }
}

/** Dashed baseline so empty days read as "no data" rather than a gap. */
private fun DrawScope.drawEmptyBaseline(color: Color) {
    val barWidth = size.width * 0.5f
    val left = (size.width - barWidth) / 2f
    val y = size.height - 2.dp.toPx()
    val dash = 6.dp.toPx()
    val gap = 4.dp.toPx()
    var x = left
    while (x < left + barWidth) {
        drawRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(minOf(dash, left + barWidth - x), 2.dp.toPx()),
        )
        x += dash + gap
    }
}

@Composable
fun WeekLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(14.dp)
                .height(8.dp)
                .background(SoftBlue, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text("Steps", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .width(14.dp)
                .height(8.dp)
                .background(AccentOrange, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text("Today", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
