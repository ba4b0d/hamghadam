package com.fitnessapp.android.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * org.json codecs for the challenges API responses (pure; unit-tested).
 * Tolerant of missing optional fields the same way DailySummaryCodec is:
 * unknown/missing keys fall back to safe defaults instead of crashing.
 */
object ChallengeCodec {

    fun challengeFromJson(json: JSONObject): Challenge {
        val participants = json.optJSONArray("participants")
        return Challenge(
            id = json.getInt("id"),
            title = json.getString("title"),
            metric = json.optString("metric", "steps"),
            startsAt = json.optString("starts_at", ""),
            endsAt = json.optString("ends_at", ""),
            status = json.optString("status", "draft"),
            inviteOnly = json.optBoolean("invite_only", false),
            maxParticipants = if (json.isNull("max_participants")) null else json.optInt("max_participants", -1).takeIf { it >= 0 },
            creator = creatorFromJson(json.optJSONObject("creator")),
            createdAt = json.optString("created_at", ""),
            updatedAt = json.optString("updated_at", ""),
            participants = if (participants == null) emptyList()
            else (0 until participants.length()).mapNotNull { i ->
                participantFromJson(participants.optJSONObject(i))
            },
        )
    }

    fun creatorFromJson(json: JSONObject?): CreatorBrief {
        if (json == null) return CreatorBrief(id = -1, displayName = null)
        return CreatorBrief(
            id = json.optInt("id", -1),
            displayName = if (json.isNull("display_name")) null else json.optString("display_name"),
        )
    }

    fun participantFromJson(json: JSONObject?): ParticipantProgress? {
        if (json == null) return null
        return ParticipantProgress(
            userId = json.optInt("user_id", -1),
            displayName = if (json.isNull("display_name")) null else json.optString("display_name"),
            isCreator = json.optBoolean("is_creator", false),
            joinedAt = json.optString("joined_at", ""),
            total = json.optDouble("total", 0.0),
        )
    }

    /**
     * GET /challenges returns a **bare JSON array** (newest first). Parse the
     * raw response text: array first, then a wrapped `{items: [...]}` shape.
     */
    fun challengeListFromText(text: String?): List<Challenge> {
        if (text.isNullOrBlank()) return emptyList()
        val arr: JSONArray = try {
            JSONArray(text)
        } catch (_: Exception) {
            try {
                JSONObject(text).optJSONArray("items") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
        }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { challengeFromJson(it) }
        }
    }

    fun leaderboardFromJson(json: JSONObject?): Leaderboard? {
        if (json == null) return null
        val entriesArr = json.optJSONArray("entries") ?: return null
        val entries = (0 until entriesArr.length()).mapNotNull { i ->
            leaderboardEntryFromJson(entriesArr.optJSONObject(i))
        }
        return Leaderboard(
            challengeId = json.optInt("challenge_id", -1),
            metric = json.optString("metric", "steps"),
            status = json.optString("status", "draft"),
            asOf = json.optString("as_of", ""),
            entries = entries,
        )
    }

    fun leaderboardEntryFromJson(json: JSONObject?): LeaderboardEntry? {
        if (json == null) return null
        val dailyArr = json.optJSONArray("daily")
        val daily = if (dailyArr == null) emptyList()
        else (0 until dailyArr.length()).mapNotNull { i ->
            val d = dailyArr.optJSONObject(i) ?: return@mapNotNull null
            DailyEntry(
                date = d.optString("date", ""),
                value = d.optDouble("value", 0.0),
            )
        }
        return LeaderboardEntry(
            rank = json.optInt("rank", 0),
            userId = json.optInt("user_id", -1),
            displayName = if (json.isNull("display_name")) null else json.optString("display_name"),
            total = json.optDouble("total", 0.0),
            daily = daily,
            isMe = json.optBoolean("is_me", false),
        )
    }

    fun inviteFromJson(json: JSONObject?): InviteInfo? {
        if (json == null) return null
        return InviteInfo(
            challengeId = json.optInt("challenge_id", -1),
            code = json.optString("code", ""),
            expiresAt = json.optString("expires_at", ""),
            deepLink = json.optString("deep_link", ""),
            createdAt = json.optString("created_at", ""),
        )
    }

    fun fcmRegistrationFromJson(json: JSONObject?): FcmRegistration? {
        if (json == null) return null
        return FcmRegistration(
            status = json.optString("status", "ok"),
            token = json.optString("token", ""),
            platform = json.optString("platform", "android"),
            registeredAt = json.optString("registered_at", ""),
        )
    }
}
