package com.fitnessapp.android.data.model

data class UserProfile(
    val id: Int,
    val email: String,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val location: String? = null,
    val premium: Boolean = false,
    val tzOffset: Int? = null,
    val authProvider: String = "email",
    val createdAt: String? = null,
)

data class UserPublicProfile(
    val id: Int,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val friendshipStatus: String = "NONE",
)

data class FriendProfile(
    val id: Int,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val todaySteps: Int = 0,
)

data class PendingFriendRequest(
    val requestId: Int,
    val requester: UserPublicProfile,
    val createdAt: String? = null,
)

data class Friendship(
    val id: Int,
    val requesterId: Int,
    val addresseeId: Int,
    val status: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class UserStats(
    val totalSteps: Long = 0L,
    val challengesWon: Int = 0,
    val totalChallenges: Int = 0,
)
