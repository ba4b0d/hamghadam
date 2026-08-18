package com.fitnessapp.android.data.network

import com.fitnessapp.android.data.model.Challenge
import com.fitnessapp.android.data.model.ChallengeCodec
import com.fitnessapp.android.data.model.DailySummary
import com.fitnessapp.android.data.model.DailySummaryCodec
import com.fitnessapp.android.data.model.FcmRegistration
import com.fitnessapp.android.data.model.FriendProfile
import com.fitnessapp.android.data.model.Friendship
import com.fitnessapp.android.data.model.InviteInfo
import com.fitnessapp.android.data.model.Leaderboard
import com.fitnessapp.android.data.model.PendingFriendRequest
import com.fitnessapp.android.data.model.SocialCodec
import com.fitnessapp.android.data.model.UserProfile
import com.fitnessapp.android.data.model.UserPublicProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val value: T, val httpCode: Int) : ApiResult<T>()
    data class Unauthorized(val detail: String) : ApiResult<Nothing>()
    data class Forbidden(val detail: String) : ApiResult<Nothing>()
    data class Conflict(val detail: String) : ApiResult<Nothing>()
    data class Validation(val detail: String) : ApiResult<Nothing>()
    data class Failure(val detail: String) : ApiResult<Nothing>()
}

data class AuthSession(
    val token: String,
    val expiresIn: Int,
    val userId: Int? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val authProvider: String? = null,
)

/** Thin OkHttp client for the BE-C1 API (register/login/daily ingest). */
class ApiClient(private val baseUrlProvider: () -> String) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val ok = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun base() = baseUrlProvider().trimEnd('/')

    suspend fun register(email: String, password: String, displayName: String? = null, tzOffset: Int? = null): ApiResult<AuthSession> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
                displayName?.let { put("display_name", it) }
                tzOffset?.let { put("tz_offset", it) }
            }
            val request = Request.Builder()
                .url("${base()}/auth/register")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            executeAuth(request)
        }

    suspend fun login(email: String, password: String): ApiResult<AuthSession> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("email", email)
                .put("password", password)
            val request = Request.Builder()
                .url("${base()}/auth/login")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            executeAuth(request)
        }

    suspend fun loginWithGoogle(idToken: String): ApiResult<AuthSession> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("id_token", idToken)
            val request = Request.Builder()
                .url("${base()}/auth/google")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            executeAuth(request)
        }

    suspend fun postDaily(token: String, summary: DailySummary): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val body = JSONObject(summary.toPayloadMap())
            val request = Request.Builder()
                .url("${base()}/daily")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            try {
                ok.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when (resp.code) {
                        in 200..299 -> ApiResult.Success(Unit, resp.code)
                        401 -> ApiResult.Unauthorized(parseDetail(text) ?: "Unauthorized")
                        422 -> ApiResult.Validation(parseDetail(text) ?: "Validation error")
                        else -> ApiResult.Failure("HTTP ${resp.code}: ${text.take(200)}")
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        }

    /**
     * BE-C1 GET /api/v1/daily?date=YYYY-MM-DD — read back one day's row.
     * Returns Failure("HTTP 404 …") when the server has no row for that date.
     */
    suspend fun getDaily(token: String, date: String): ApiResult<DailySummary> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/daily?date=$date")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            try {
                ok.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when (resp.code) {
                        in 200..299 -> {
                            val summary = DailySummaryCodec.fromJson(JSONObject(text))
                            if (summary == null) ApiResult.Failure("Unreadable daily row") else ApiResult.Success(summary, resp.code)
                        }
                        401 -> ApiResult.Unauthorized(parseDetail(text) ?: "Unauthorized")
                        404 -> ApiResult.Failure("HTTP 404: no row for $date")
                        else -> ApiResult.Failure("HTTP ${resp.code}: ${text.take(200)}")
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        }

    /**
     * BE-C1 GET /api/v1/daily/range?from=YYYY-MM-DD&to=YYYY-MM-DD — rows for a
     * date window, oldest first. Missing days are simply absent from [List].
     */
    suspend fun getDailyRange(token: String, from: String, to: String): ApiResult<List<DailySummary>> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/daily/range?from=$from&to=$to")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            try {
                ok.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when (resp.code) {
                        in 200..299 -> {
                            val items = JSONObject(text).optJSONArray("items")
                            val rows = if (items == null) emptyList()
                            else (0 until items.length()).mapNotNull { DailySummaryCodec.fromJson(items.optJSONObject(it)) }
                            ApiResult.Success(rows, resp.code)
                        }
                        401 -> ApiResult.Unauthorized(parseDetail(text) ?: "Unauthorized")
                        404 -> ApiResult.Success(emptyList(), resp.code)
                        else -> ApiResult.Failure("HTTP ${resp.code}: ${text.take(200)}")
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        }

    // ------------------------------------------------------------------
    // BE-C2 / BE-C3 — challenges, leaderboards, invites, FCM registration
    // ------------------------------------------------------------------

    suspend fun listChallenges(token: String): ApiResult<List<Challenge>> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/challenges")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> ApiResult.Success(ChallengeCodec.challengeListFromText(r.text), r.code)
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun getChallenge(token: String, id: Long): ApiResult<Challenge> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/challenges/$id")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val challenge = try {
                        ChallengeCodec.challengeFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (challenge == null) ApiResult.Failure("Unreadable challenge payload") else ApiResult.Success(challenge, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun createChallenge(
        token: String,
        title: String,
        startsAtIso: String,
        endsAtIso: String,
        metric: String = "steps",
        inviteOnly: Boolean = false,
        maxParticipants: Int? = null,
    ): ApiResult<Challenge> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("title", title)
                put("starts_at", startsAtIso)
                put("ends_at", endsAtIso)
                put("metric", metric)
                put("invite_only", inviteOnly)
                maxParticipants?.let { put("max_participants", it) }
            }
            val request = Request.Builder()
                .url("${base()}/challenges")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val challenge = try {
                        ChallengeCodec.challengeFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (challenge == null) ApiResult.Failure("Unreadable challenge payload") else ApiResult.Success(challenge, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    /** POST /challenges/{id}/join?code=... — 403 expired/invalid, 409 full/ended/duplicate. */
    suspend fun joinChallenge(token: String, id: Long, code: String? = null): ApiResult<Challenge> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("${base()}/challenges/$id/join")
                if (!code.isNullOrBlank()) append("?code=").append(code.trim())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val challenge = try {
                        ChallengeCodec.challengeFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (challenge == null) ApiResult.Failure("Unreadable challenge payload") else ApiResult.Success(challenge, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun leaveChallenge(token: String, id: Long): ApiResult<Challenge> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/challenges/$id/leave")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val challenge = try {
                        ChallengeCodec.challengeFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (challenge == null) ApiResult.Failure("Unreadable challenge payload") else ApiResult.Success(challenge, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    /** PATCH /challenges/{id}/status — creator-only; draft→active→ended only. */
    suspend fun updateChallengeStatus(token: String, id: Long, status: String): ApiResult<Challenge> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("status", status)
            val request = Request.Builder()
                .url("${base()}/challenges/$id/status")
                .addHeader("Authorization", "Bearer $token")
                .patch(body.toString().toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val challenge = try {
                        ChallengeCodec.challengeFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (challenge == null) ApiResult.Failure("Unreadable challenge payload") else ApiResult.Success(challenge, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun getLeaderboard(token: String, id: Long): ApiResult<Leaderboard> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/challenges/$id/leaderboard")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val board = try {
                        ChallengeCodec.leaderboardFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (board == null) ApiResult.Failure("Unreadable leaderboard payload") else ApiResult.Success(board, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    /** POST /challenges/{id}/invites {ttl_hours} → 201 InviteInfo (code + deep link). */
    suspend fun createInvite(token: String, id: Long, ttlHours: Int = 168): ApiResult<InviteInfo> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("ttl_hours", ttlHours)
            val request = Request.Builder()
                .url("${base()}/challenges/$id/invites")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val invite = try {
                        ChallengeCodec.inviteFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (invite == null) ApiResult.Failure("Unreadable invite payload") else ApiResult.Success(invite, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    /** POST /users/me/fcm-token — upsert on devices (key user_id+token). */
    suspend fun registerFcmToken(token: String, fcmToken: String, platform: String = "android"): ApiResult<FcmRegistration> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("token", fcmToken)
                .put("platform", platform)
            val request = Request.Builder()
                .url("${base()}/users/me/fcm-token")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val reg = try {
                        ChallengeCodec.fcmRegistrationFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (reg == null) ApiResult.Failure("Unreadable FCM response") else ApiResult.Success(reg, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    // ------------------------------------------------------------------
    // V1.2 Social — profile, bio, avatar upload, search, friends
    // ------------------------------------------------------------------

    suspend fun getMe(token: String): ApiResult<UserProfile> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/users/me")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val user = try {
                        SocialCodec.userProfileFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (user == null) ApiResult.Failure("Unreadable user profile") else ApiResult.Success(user, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun updateBio(token: String, bio: String?): ApiResult<UserProfile> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                if (bio == null) put("bio", JSONObject.NULL) else put("bio", bio)
            }
            val request = Request.Builder()
                .url("${base()}/users/me/bio")
                .addHeader("Authorization", "Bearer $token")
                .patch(body.toString().toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val user = try {
                        SocialCodec.userProfileFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (user == null) ApiResult.Failure("Unreadable user profile") else ApiResult.Success(user, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun updateProfile(
        token: String,
        displayName: String? = null,
        bio: String? = null,
        location: String? = null,
        tzOffset: Int? = null,
    ): ApiResult<UserProfile> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                displayName?.let { put("display_name", it) }
                bio?.let { put("bio", it) }
                location?.let { put("location", it) }
                tzOffset?.let { put("tz_offset", it) }
            }
            val request = Request.Builder()
                .url("${base()}/users/me")
                .addHeader("Authorization", "Bearer $token")
                .patch(body.toString().toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val user = try {
                        SocialCodec.userProfileFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (user == null) ApiResult.Failure("Unreadable user profile") else ApiResult.Success(user, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun uploadAvatar(
        token: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        filename: String = "avatar.jpg",
    ): ApiResult<String> =
        withContext(Dispatchers.IO) {
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    imageBytes.toRequestBody(mimeType.toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url("${base()}/users/me/avatar")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val avatarUrl = try {
                        SocialCodec.avatarUploadResponseFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (avatarUrl == null) ApiResult.Failure("Unreadable avatar response") else ApiResult.Success(avatarUrl, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun searchUsers(token: String, query: String): ApiResult<List<UserPublicProfile>> =
        withContext(Dispatchers.IO) {
            val encodedQ = URLEncoder.encode(query.trim(), "UTF-8")
            val request = Request.Builder()
                .url("${base()}/users/search?q=$encodedQ")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> ApiResult.Success(SocialCodec.userPublicListFromText(r.text), r.code)
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun sendFriendRequest(token: String, targetUserId: Int): ApiResult<Friendship> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("target_user_id", targetUserId)
            val request = Request.Builder()
                .url("${base()}/friends/request")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val friendship = try {
                        SocialCodec.friendshipFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (friendship == null) ApiResult.Failure("Unreadable friendship payload") else ApiResult.Success(friendship, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun acceptFriendRequest(token: String, requestId: Int): ApiResult<Friendship> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/friends/accept/$requestId")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val friendship = try {
                        SocialCodec.friendshipFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (friendship == null) ApiResult.Failure("Unreadable friendship payload") else ApiResult.Success(friendship, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun rejectFriendRequest(token: String, requestId: Int): ApiResult<Friendship> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/friends/reject/$requestId")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMedia))
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> {
                    val friendship = try {
                        SocialCodec.friendshipFromJson(JSONObject(r.text))
                    } catch (_: Exception) {
                        null
                    }
                    if (friendship == null) ApiResult.Failure("Unreadable friendship payload") else ApiResult.Success(friendship, r.code)
                }
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun deleteFriend(token: String, friendId: Int): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/friends/$friendId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            try {
                ok.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when (resp.code) {
                        in 200..299 -> ApiResult.Success(Unit, resp.code)
                        401 -> ApiResult.Unauthorized(parseDetail(text) ?: "Unauthorized")
                        404 -> ApiResult.Failure("Friend not found")
                        else -> ApiResult.Failure("HTTP ${resp.code}: ${text.take(200)}")
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        }

    suspend fun listFriends(token: String): ApiResult<List<FriendProfile>> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/friends")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> ApiResult.Success(SocialCodec.friendListFromText(r.text), r.code)
                is Raw.Error -> r.asApiResult()
            }
        }

    suspend fun listPendingRequests(token: String): ApiResult<List<PendingFriendRequest>> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${base()}/friends/requests/pending")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            when (val r = executeJson(request)) {
                is Raw.Success -> ApiResult.Success(SocialCodec.pendingRequestListFromText(r.text), r.code)
                is Raw.Error -> r.asApiResult()
            }
        }

    // ------------------------------------------------------------------
    // Raw helpers
    // ------------------------------------------------------------------

    private fun executeJson(request: Request): Raw =
        try {
            ok.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code in 200..299) Raw.Success(resp.code, text) else Raw.Error(resp.code, text)
            }
        } catch (e: IOException) {
            Raw.Error(-1, e.message ?: "Network error")
        }

    private fun executeAuth(request: Request): ApiResult<AuthSession> {
        return try {
            ok.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when (resp.code) {
                    in 200..299 -> {
                        val json = JSONObject(text)
                        val user = json.optJSONObject("user")
                        ApiResult.Success(
                            AuthSession(
                                token = json.getString("access_token"),
                                expiresIn = json.optInt("expires_in", 0),
                                userId = user?.optInt("id"),
                                email = user?.optString("email"),
                                displayName = user?.let { if (it.isNull("display_name")) null else it.optString("display_name") },
                                avatarUrl = user?.let { if (it.isNull("avatar_url")) null else it.optString("avatar_url") },
                                bio = user?.let { if (it.isNull("bio")) null else it.optString("bio") },
                                authProvider = user?.optString("auth_provider", "email"),
                            ),
                            resp.code,
                        )
                    }
                    401 -> ApiResult.Unauthorized(parseDetail(text) ?: "Bad credentials")
                    403 -> ApiResult.Forbidden(parseDetail(text) ?: "Forbidden")
                    409 -> ApiResult.Conflict(parseDetail(text) ?: "Email already registered")
                    422 -> ApiResult.Validation(parseDetail(text) ?: "Validation error")
                    else -> ApiResult.Failure("HTTP ${resp.code}: ${text.take(200)}")
                }
            }
        } catch (e: IOException) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    private fun parseDetail(text: String): String? =
        try {
            JSONObject(text).optString("detail").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
}

private sealed class Raw {
    abstract val code: Int
    abstract val text: String
    data class Success(override val code: Int, override val text: String) : Raw()
    data class Error(override val code: Int, override val text: String) : Raw()
}

private fun Raw.asApiResult(): ApiResult<Nothing> {
    if (code < 0) return ApiResult.Failure(text.take(200)) // network-level error
    val detail = try {
        JSONObject(text).optString("detail").takeIf { it.isNotEmpty() } ?: "HTTP $code"
    } catch (_: Exception) {
        "HTTP $code"
    }
    return when (code) {
        401 -> ApiResult.Unauthorized(detail)
        403 -> ApiResult.Forbidden(detail)
        404 -> ApiResult.Failure("HTTP 404: ${detail.take(160)}")
        409 -> ApiResult.Conflict(detail)
        422 -> ApiResult.Validation(detail)
        else -> ApiResult.Failure("HTTP $code: ${text.take(200)}")
    }
}
