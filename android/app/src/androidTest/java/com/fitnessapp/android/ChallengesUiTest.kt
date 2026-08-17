package com.fitnessapp.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fitnessapp.android.data.model.Challenge
import com.fitnessapp.android.data.model.CreatorBrief
import com.fitnessapp.android.data.model.DailyEntry
import com.fitnessapp.android.data.model.Leaderboard
import com.fitnessapp.android.data.model.LeaderboardEntry
import com.fitnessapp.android.data.model.ParticipantProgress
import com.fitnessapp.android.ui.challenges.ChallengeDetailContent
import com.fitnessapp.android.ui.challenges.ChallengeDetailUiState
import com.fitnessapp.android.ui.challenges.ChallengesContent
import com.fitnessapp.android.ui.challenges.ChallengesUiState
import com.fitnessapp.android.ui.theme.FitnessAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the challenges screens — stateless content only (no
 * ViewModel/DI/network), same pattern as DashboardUiTest.
 */
class ChallengesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun challenge(
        id: Int,
        title: String,
        status: String,
        inviteOnly: Boolean = false,
        participants: List<ParticipantProgress> = listOf(
            ParticipantProgress(1, "Alice", true, "2026-08-14T10:00:00Z", 5000.0)
        ),
    ) = Challenge(
        id = id,
        title = title,
        metric = "steps",
        startsAt = "2026-08-15T00:00:00Z",
        endsAt = "2026-08-17T23:59:59Z",
        status = status,
        inviteOnly = inviteOnly,
        maxParticipants = null,
        creator = CreatorBrief(1, "Alice"),
        createdAt = "2026-08-14T10:00:00Z",
        updatedAt = "2026-08-14T10:00:00Z",
        participants = participants,
    )

    @Test
    fun notSignedIn_showsSignInCard() {
        compose.setContent {
            FitnessAppTheme {
                ChallengesContent(
                    state = ChallengesUiState(isSignedIn = false),
                    myUserId = null,
                    onRetry = {},
                    onCreate = {},
                    onJoinLink = {},
                    onOpenChallenge = {},
                    onSignInHint = {},
                )
            }
        }
        compose.onNodeWithText("Challenges").assertIsDisplayed()
        compose.onNodeWithText("Sign in to see your challenges").assertIsDisplayed()
    }

    @Test
    fun loading_showsSpinner() {
        compose.setContent {
            FitnessAppTheme {
                ChallengesContent(
                    state = ChallengesUiState(isSignedIn = true, loading = true),
                    myUserId = 1,
                    onRetry = {},
                    onCreate = {},
                    onJoinLink = {},
                    onOpenChallenge = {},
                    onSignInHint = {},
                )
            }
        }
        compose.onNodeWithTag("challenges_loading").assertIsDisplayed()
    }

    @Test
    fun empty_showsBrandEmptyState() {
        compose.setContent {
            FitnessAppTheme {
                ChallengesContent(
                    state = ChallengesUiState(isSignedIn = true),
                    myUserId = 1,
                    onRetry = {},
                    onCreate = {},
                    onJoinLink = {},
                    onOpenChallenge = {},
                    onSignInHint = {},
                )
            }
        }
        compose.onNodeWithText("No challenges yet").assertIsDisplayed()
        compose.onNodeWithText("Create a challenge").assertIsDisplayed()
    }

    @Test
    fun error_showsRetryAndRetryFires() {
        var retried = false
        compose.setContent {
            FitnessAppTheme {
                ChallengesContent(
                    state = ChallengesUiState(isSignedIn = true, loading = false, error = "Network: boom"),
                    myUserId = 1,
                    onRetry = { retried = true },
                    onCreate = {},
                    onJoinLink = {},
                    onOpenChallenge = {},
                    onSignInHint = {},
                )
            }
        }
        compose.onNodeWithText("Couldn't load challenges").assertIsDisplayed()
        compose.onNodeWithText("Network: boom").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun list_showsSectionCardsAndMyTotal() {
        val mine = ParticipantProgress(1, "Alice", true, "2026-08-14T10:00:00Z", 8000.0)
        val bob = ParticipantProgress(2, "Bob", false, "2026-08-15T08:00:00Z", 12000.0)
        val active = challenge(1, "Weekend 10k", "active", participants = listOf(mine, bob))
        val upcoming = challenge(2, "Next week", "draft", inviteOnly = true, participants = listOf(mine))
        val ended = challenge(3, "Done deal", "ended", participants = listOf(mine.copy(total = 7000.0)))

        compose.setContent {
            FitnessAppTheme {
                ChallengesContent(
                    state = ChallengesUiState(isSignedIn = true, challenges = listOf(active, upcoming, ended), myUserId = 1),
                    myUserId = 1,
                    onRetry = {},
                    onCreate = {},
                    onJoinLink = {},
                    onOpenChallenge = {},
                    onSignInHint = {},
                )
            }
        }
        compose.onAllNodesWithText("Active").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Weekend 10k").assertIsDisplayed()
        compose.onAllNodesWithText("Upcoming").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Next week").assertIsDisplayed()
        compose.onAllNodesWithText("Ended").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Done deal").assertIsDisplayed()
        // my total on the active card (user 1): 8,000 → "8.0K"
        compose.onAllNodesWithText("8.0K").onFirst().assertIsDisplayed()
        // participant counts
        compose.onNodeWithText("2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun leaderboard_showsRankedRowsAndMeHighlight() {
        val challenge = challenge(1, "Weekend 10k", "active")
        val board = Leaderboard(
            challengeId = 1,
            metric = "steps",
            status = "active",
            asOf = "2026-08-17",
            entries = listOf(
                LeaderboardEntry(1, 2, "Bob", 8000.0, listOf(DailyEntry("2026-08-15", 8000.0)), isMe = false),
                LeaderboardEntry(2, 1, "Alice", 5000.0, listOf(DailyEntry("2026-08-16", 5000.0)), isMe = true),
            ),
        )
        compose.setContent {
            FitnessAppTheme {
                ChallengeDetailContent(
                    state = ChallengeDetailUiState(
                        loading = false,
                        challenge = challenge,
                        leaderboard = board,
                        isCreator = true,
                        isParticipant = true,
                        isSignedIn = true,
                    ),
                    challengeId = 1,
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
        compose.onNodeWithText("Weekend 10k").assertIsDisplayed()
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithText("Alice (you)").assertIsDisplayed()
        compose.onNodeWithText("8.0K").assertIsDisplayed()   // leader total
        compose.onAllNodesWithText("5.0K").onFirst().assertIsDisplayed()   // my total
    }

    @Test
    fun detail_showsInviteCodeDialogForInviteOnly() {
        val challenge = challenge(1, "Private run", "active", inviteOnly = true)
        compose.setContent {
            FitnessAppTheme {
                ChallengeDetailContent(
                    state = ChallengeDetailUiState(
                        loading = false,
                        challenge = challenge,
                        isParticipant = false,
                        isSignedIn = true,
                        joinDialogOpen = true,
                        joinCode = "R7NRX32",
                        joinError = "Invite codes are 8 characters (A–H J–N P–Z 2–9)",
                    ),
                    challengeId = 1,
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
        compose.onNodeWithText("Enter invite code").assertIsDisplayed()
        compose.onNodeWithText("Join with invite code").assertIsDisplayed()
    }

    @Test
    fun detail_creatorSeesInviteAndStatusActions() {
        val challenge = challenge(1, "My run", "draft")
        var invited = false
        var started = false
        compose.setContent {
            FitnessAppTheme {
                ChallengeDetailContent(
                    state = ChallengeDetailUiState(
                        loading = false,
                        challenge = challenge,
                        isCreator = true,
                        isParticipant = true,
                        isSignedIn = true,
                    ),
                    challengeId = 1,
                    onBack = {},
                    onRetry = {},
                    onJoin = {},
                    onOpenJoinDialog = {},
                    onJoinCodeChange = {},
                    onSubmitJoinDialog = {},
                    onDismissJoinDialog = {},
                    onLeave = {},
                    onInvite = { invited = true },
                    onDismissInvite = {},
                    onStartNow = { started = true },
                    onEndNow = {},
                    onRefreshLeaderboard = {},
                    onShareInvite = {},
                    onCopyInvite = {},
                    onClearNotice = {},
                )
            }
        }
        compose.onNodeWithText("Invite friends").performClick()
        assertTrue(invited)
        compose.onNodeWithText("Start now").performClick()
        assertTrue(started)
    }
}