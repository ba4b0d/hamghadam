package com.fitnessapp.android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.fitnessapp.android.data.HcStatus
import com.fitnessapp.android.data.model.DailySummary
import com.fitnessapp.android.ui.dashboard.DashboardContent
import com.fitnessapp.android.ui.dashboard.DashboardUiState
import com.fitnessapp.android.ui.theme.FitnessAppTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Compose UI tests for the dashboard states — no ViewModel/DI required because
 * DashboardContent is stateless.
 */
class DashboardUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 8, 16)

    private fun emptyState() = DashboardUiState(
        hcStatus = HcStatus.AVAILABLE,
        grantedPermissions = setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_HEART_RATE",
        ),
        missingPermissions = emptySet(),
        matchmakingPossible = true,
        summary = null,
    )

    @Test
    fun emptyState_showsConnectWatchHero() {
        compose.setContent {
            FitnessAppTheme {
                DashboardContent(
                    state = emptyState(),
                    today = today,
                    onGrant = {},
                    onConnect = {},
                    onRefresh = {},
                    onSyncNow = {},
                    syncLine = null,
                )
            }
        }
        compose.onNodeWithText("Dashboard").assertIsDisplayed()
        compose.onNodeWithText("Connect your watch").assertIsDisplayed()
        compose.onNodeWithText("Connect now").assertIsDisplayed()
        compose.onNodeWithText("Last 7 days").assertDoesNotExist()
    }

    @Test
    fun dataState_showsTodayCardWeekStripAndAttribution() {
        val summary = DailySummary(
            date = "2026-08-16",
            tzOffsetMinutes = 210,
            steps = 8000,
            sleepSeconds = 43_200,
            avgHr = 71.0,
            sourceApps = listOf("com.samsung.health", "com.fitness.explorer.datagenerator"),
        )
        val week = (0 until 7).map { i ->
            if (i == 6) summary else summary.copy(
                date = today.minusDays((6 - i).toLong()).toString(),
                steps = (1000L * (i + 1)),
            )
        }
        compose.setContent {
            FitnessAppTheme {
                DashboardContent(
                    state = emptyState().copy(
                        summary = summary,
                        week = week,
                    ),
                    today = today,
                    onGrant = {},
                    onConnect = {},
                    onRefresh = {},
                    onSyncNow = {},
                    syncLine = "Last sync: 2026-08-16 (2h ago)",
                )
            }
        }
        // Steps hero AND the chart's today-bar label both show 8,000.
        compose.onAllNodesWithText("8,000").assertCountEquals(2)
        compose.onNodeWithText("12h").assertExists()            // sleep
        compose.onNodeWithText("71 bpm").assertExists()         // avg HR
        compose.onNodeWithText("Demo Data · Samsung Health").assertExists() // attribution
        compose.onNodeWithText("Last 7 days").assertExists()    // week strip
        compose.onNodeWithText("Last sync: 2026-08-16 (2h ago)").assertExists()
        compose.onNodeWithText("Connect your watch").assertDoesNotExist() // not in empty mode
    }
}
