package com.fitnessapp.android.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codecs for User Profile and Friendship social endpoints.
 */
object SocialCodec {

    fun userProfileFromJson(json: JSONObject?): UserProfile? {
        if (json == null) return null
        return UserProfile(
            id = json.optInt("id", -1),
            email = json.optString("email", ""),
            displayName = if (json.isNull("display_name")) null else json.optString("display_name"),
            bio = if (json.isNull("bio")) null else json.optString("bio"),
            avatarUrl = if (json.isNull("avatar_url")) null else json.optString("avatar_url"),
            location = if (json.isNull("location")) null else json.optString("location"),
            premium = json.optBoolean("premium", false),
            tzOffset = if (json.isNull("tz_offset")) null else json.optInt("tz_offset"),
            authProvider = json.optString("auth_provider", "email"),
            createdAt = if (json.isNull("created_at")) null else json.optString("created_at"),
        )
    }

    fun userPublicProfileFromJson(json: JSONObject?): UserPublicProfile? {
        if (json == null) return null
        return UserPublicProfile(
            id = json.optInt("id", -1),
            displayName = if (json.isNull("display_name")) null else json.optString("display_name"),
            avatarUrl = if (json.isNull("avatar_url")) null else json.optString("avatar_url"),
            bio = if (json.isNull("bio")) null else json.optString("bio"),
            location = if (json.isNull("location")) null else json.optString("location"),
            friendshipStatus = json.optString("friendship_status", "NONE"),
        )
    }

    fun userPublicListFromText(text: String?): List<UserPublicProfile> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = try {
            JSONArray(text)
        } catch (_: Exception) {
            try {
                JSONObject(text).optJSONArray("items") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
        }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { userPublicProfileFromJson(it) }
        }
    }

    fun friendProfileFromJson(json: JSONObject?): FriendProfile? {
        if (json == null) return null
        return FriendProfile(
            id = json.optInt("id", -1),
            email = if (json.isNull("email")) null else json.optString("email"),
            displayName = if (json.isNull("display_name")) null else json.optString("display_name"),
            avatarUrl = if (json.isNull("avatar_url")) null else json.optString("avatar_url"),
            bio = if (json.isNull("bio")) null else json.optString("bio"),
            location = if (json.isNull("location")) null else json.optString("location"),
            todaySteps = json.optInt("today_steps", 0),
        )
    }

    fun friendListFromText(text: String?): List<FriendProfile> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = try {
            JSONArray(text)
        } catch (_: Exception) {
            try {
                JSONObject(text).optJSONArray("items") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
        }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { friendProfileFromJson(it) }
        }
    }

    fun pendingRequestFromJson(json: JSONObject?): PendingFriendRequest? {
        if (json == null) return null
        val requesterJson = json.optJSONObject("requester")
        val requester = userPublicProfileFromJson(requesterJson) ?: UserPublicProfile(
            id = json.optInt("requester_id", -1),
            displayName = "User",
        )
        return PendingFriendRequest(
            requestId = json.optInt("request_id", json.optInt("id", -1)),
            requester = requester,
            createdAt = if (json.isNull("created_at")) null else json.optString("created_at"),
        )
    }

    fun pendingRequestListFromText(text: String?): List<PendingFriendRequest> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = try {
            JSONArray(text)
        } catch (_: Exception) {
            try {
                JSONObject(text).optJSONArray("items") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
        }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { pendingRequestFromJson(it) }
        }
    }

    fun friendshipFromJson(json: JSONObject?): Friendship? {
        if (json == null) return null
        return Friendship(
            id = json.optInt("id", -1),
            requesterId = json.optInt("requester_id", -1),
            addresseeId = json.optInt("addressee_id", -1),
            status = json.optString("status", "PENDING"),
            createdAt = if (json.isNull("created_at")) null else json.optString("created_at"),
            updatedAt = if (json.isNull("updated_at")) null else json.optString("updated_at"),
        )
    }

    fun avatarUploadResponseFromJson(json: JSONObject?): String? {
        if (json == null) return null
        return if (json.isNull("avatar_url")) null else json.optString("avatar_url")
    }
}
