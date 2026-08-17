package com.fitnessapp.android.data.model

/**
 * Domain models for the BE-C2/BE-C3 challenges API. Field names mirror the
 * backend Pydantic schemas exactly (ChallengeOut, LeaderboardOut,
 * LeaderboardEntryOut, DailyEntryOut, InviteOut, UserBriefOut,
 * ParticipantProgressOut). Dates are ISO-8601 strings as sent by the server
 * (UTC instants); `daily[].date` and leaderboard `as_of` are YYYY-MM-DD.
 */
data class CreatorBrief(
    val id: Int,
    val displayName: String?,
)

data class ParticipantProgress(
    val userId: Int,
    val displayName: String?,
    val isCreator: Boolean,
    val joinedAt: String,
    /** Sum of the challenge metric over the window (as of server today). */
    val total: Double,
)

data class Challenge(
    val id: Int,
    val title: String,
    val metric: String,
    val startsAt: String,
    val endsAt: String,
    val status: String, // draft | active | ended
    val inviteOnly: Boolean,
    val maxParticipants: Int?,
    val creator: CreatorBrief,
    val createdAt: String,
    val updatedAt: String,
    val participants: List<ParticipantProgress>,
) {
    val isEnded: Boolean get() = status == "ended"
    val isActive: Boolean get() = status == "active"
    val isDraft: Boolean get() = status == "draft"

    /** Display label for the metric (v1 playable: steps). */
    val metricLabel: String
        get() = when (metric) {
            "steps" -> "Steps"
            "sleep_seconds" -> "Sleep (h)"
            "avg_hr" -> "Avg HR"
            else -> metric
        }
}

data class DailyEntry(
    val date: String, // YYYY-MM-DD
    val value: Double,
)

data class LeaderboardEntry(
    val rank: Int,
    val userId: Int,
    val displayName: String?,
    val total: Double,
    val daily: List<DailyEntry>,
    val isMe: Boolean,
)

data class Leaderboard(
    val challengeId: Int,
    val metric: String,
    val status: String,
    val asOf: String,
    val entries: List<LeaderboardEntry>,
) {
    val me: LeaderboardEntry?
        get() = entries.firstOrNull { it.isMe }

    val myRank: Int?
        get() = me?.rank

    val myTotal: Double?
        get() = me?.total
}

data class InviteInfo(
    val challengeId: Int,
    val code: String,
    val expiresAt: String,
    val deepLink: String,
    val createdAt: String,
)

data class FcmRegistration(
    val status: String,
    val token: String,
    val platform: String,
    val registeredAt: String,
)
