package com.fitnessapp.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.fitnessapp.android.data.HcStatus
import com.fitnessapp.android.data.HeartRateSummary
import com.fitnessapp.android.data.model.Challenge
import com.fitnessapp.android.data.model.CreatorBrief
import com.fitnessapp.android.data.model.DailySummary
import com.fitnessapp.android.data.model.Leaderboard
import com.fitnessapp.android.data.model.LeaderboardEntry
import com.fitnessapp.android.data.model.ParticipantProgress
import com.fitnessapp.android.ui.challenges.ChallengeDetailContent
import com.fitnessapp.android.ui.challenges.ChallengeDetailUiState
import com.fitnessapp.android.ui.challenges.ChallengesContent
import com.fitnessapp.android.ui.challenges.ChallengesUiState
import com.fitnessapp.android.ui.dashboard.DashboardContent
import com.fitnessapp.android.ui.dashboard.DashboardUiState
import com.fitnessapp.android.ui.hr.HrTestContent
import com.fitnessapp.android.ui.hr.HrTestUiState
import com.fitnessapp.android.ui.theme.FitnessAppTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * End-to-end UI verification tests for V1.1 Sleep & Heart Rate features.
 * Captures UI evidence screenshots to /sdcard/Download/ for QA reporting.
 */
class V11SleepHrUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 8, 18)

    private fun captureScreenshot(filename: String) {
        try {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val dir = File("/sdcard/Download")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            if (file.exists()) {
                file.delete()
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun dashboard_displaysSleepAndHrWidgets() {
        val summary = DailySummary(
            date = "2026-08-18",
            tzOffsetMinutes = 210,
            steps = 9500,
            sleepSeconds = 27_000, // 7h 30m
            avgHr = 68.0,
            sourceApps = listOf("com.samsung.health"),
        )
        val state = DashboardUiState(
            hcStatus = HcStatus.AVAILABLE,
            grantedPermissions = setOf(
                "android.permission.health.READ_STEPS",
                "android.permission.health.READ_SLEEP",
                "android.permission.health.READ_HEART_RATE",
            ),
            missingPermissions = emptySet(),
            matchmakingPossible = true,
            summary = summary,
            week = listOf(summary),
        )

        compose.setContent {
            FitnessAppTheme {
                DashboardContent(
                    state = state,
                    today = today,
                    onGrant = {},
                    onConnect = {},
                    onRefresh = {},
                    onSyncNow = {},
                    onOpenHrTest = {},
                    syncLine = "Last sync: Today 08:30 AM",
                )
            }
        }

        compose.onNodeWithText("Dashboard").assertIsDisplayed()
        compose.onNodeWithText("7h 30m").assertIsDisplayed()
        compose.onNodeWithText("68 bpm").assertIsDisplayed()
        compose.onNodeWithText("Data access").assertIsDisplayed()
        compose.onNodeWithText("All permissions granted").assertIsDisplayed()

        captureScreenshot("v11_dashboard_sleep_hr.png")
    }

    @Test
    fun hrTestScreen_displaysBpmSummaryAndRecords() {
        val summary = HeartRateSummary(
            avgBpm = 72,
            minBpm = 58,
            maxBpm = 115,
            count = 42,
        )
        val state = HrTestUiState(
            loading = false,
            hcStatus = HcStatus.AVAILABLE,
            hasPermission = true,
            summary = summary,
            records = emptyList(),
        )

        compose.setContent {
            FitnessAppTheme {
                HrTestContent(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onGrantPermission = {},
                )
            }
        }

        compose.onNodeWithText("Heart Rate Test").assertIsDisplayed()
        compose.onNodeWithText("24h BPM Summary").assertIsDisplayed()
        compose.onNodeWithText("72").assertIsDisplayed()
        compose.onNodeWithText("58").assertIsDisplayed()
        compose.onNodeWithText("115").assertIsDisplayed()
        compose.onNodeWithText("42 total samples").assertIsDisplayed()

        captureScreenshot("v11_hr_test_screen.png")
    }

    @Test
    fun challenges_displaysSleepAndHrMetrics() {
        val sleepChallenge = Challenge(
            id = 1,
            title = "7-Day Sleep Duration Challenge",
            metric = "sleep_seconds",
            startsAt = "2026-08-18T00:00:00Z",
            endsAt = "2026-08-25T23:59:59Z",
            status = "active",
            inviteOnly = false,
            maxParticipants = 10,
            creator = CreatorBrief(1, "Alice"),
            createdAt = "2026-08-18T00:00:00Z",
            updatedAt = "2026-08-18T00:00:00Z",
            participants = listOf(
                ParticipantProgress(1, "Alice", true, "2026-08-18T00:00:00Z", 54000.0) // 15.0h
            ),
        )
        val hrChallenge = Challenge(
            id = 2,
            title = "Resting HR Target Challenge",
            metric = "avg_hr",
            startsAt = "2026-08-18T00:00:00Z",
            endsAt = "2026-08-25T23:59:59Z",
            status = "active",
            inviteOnly = false,
            maxParticipants = 5,
            creator = CreatorBrief(2, "Bob"),
            createdAt = "2026-08-18T00:00:00Z",
            updatedAt = "2026-08-18T00:00:00Z",
            participants = listOf(
                ParticipantProgress(1, "Alice", false, "2026-08-18T00:00:00Z", 64.0), // 64 bpm
                ParticipantProgress(2, "Bob", true, "2026-08-18T00:00:00Z", 68.0)
            ),
        )

        val state = ChallengesUiState(
            isSignedIn = true,
            loading = false,
            challenges = listOf(sleepChallenge, hrChallenge),
        )

        compose.setContent {
            FitnessAppTheme {
                ChallengesContent(
                    state = state,
                    myUserId = 1,
                    onRetry = {},
                    onCreate = {},
                    onJoinLink = {},
                    onOpenChallenge = {},
                    onSignInHint = {},
                )
            }
        }

        compose.onNodeWithText("7-Day Sleep Duration Challenge").assertIsDisplayed()
        compose.onNodeWithText("Resting HR Target Challenge").assertIsDisplayed()
        compose.onNodeWithText("15.0h").assertIsDisplayed()
        compose.onNodeWithText("64 bpm").assertIsDisplayed()

        captureScreenshot("v11_challenges_list.png")
    }

    @Test
    fun challengeDetail_displaysSleepLeaderboard() {
        val sleepChallenge = Challenge(
            id = 1,
            title = "7-Day Sleep Duration Challenge",
            metric = "sleep_seconds",
            startsAt = "2026-08-18T00:00:00Z",
            endsAt = "2026-08-25T23:59:59Z",
            status = "active",
            inviteOnly = false,
            maxParticipants = 10,
            creator = CreatorBrief(1, "Alice"),
            createdAt = "2026-08-18T00:00:00Z",
            updatedAt = "2026-08-18T00:00:00Z",
            participants = listOf(
                ParticipantProgress(1, "Alice", true, "2026-08-18T00:00:00Z", 54000.0),
                ParticipantProgress(2, "Bob", true, "2026-08-18T00:00:00Z", 46800.0)
            ),
        )

        val leaderboard = Leaderboard(
            challengeId = 1,
            metric = "sleep_seconds",
            status = "active",
            asOf = "2026-08-18",
            entries = listOf(
                LeaderboardEntry(rank = 1, userId = 1, displayName = "Alice", total = 54000.0, daily = emptyList(), isMe = true),
                LeaderboardEntry(rank = 2, userId = 2, displayName = "Bob", total = 46800.0, daily = emptyList(), isMe = false),
            ),
        )

        val state = ChallengeDetailUiState(
            loading = false,
            challenge = sleepChallenge,
            leaderboard = leaderboard,
        )

        compose.setContent {
            FitnessAppTheme {
                ChallengeDetailContent(
                    state = state,
                    challengeId = 1L,
                    onBack = {},
                    onRetry = {},
                    onJoin = {},
                    onOpenJoinDialog = {},
                    onJoinCodeChange = {},
                    onSubmitJoinDialog = {},
                    onDismissJoinDialog = {},
                    onLeave = {},
                    onInvite = {},
                    onDismissInvite = {},
                    onStartNow = {},
                    onEndNow = {},
                    onRefreshLeaderboard = {},
                    onShareInvite = {},
                    onCopyInvite = {},
                    onClearNotice = {},
                )
            }
        }

        compose.onNodeWithText("7-Day Sleep Duration Challenge").assertIsDisplayed()
        compose.onNodeWithText("Leaderboard").assertIsDisplayed()
        compose.onAllNodesWithText("15.0h").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("13.0h").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Alice (you)").assertIsDisplayed()
        compose.onNodeWithText("Bob").assertIsDisplayed()

        captureScreenshot("v11_challenge_leaderboard_sleep.png")
    }
}
