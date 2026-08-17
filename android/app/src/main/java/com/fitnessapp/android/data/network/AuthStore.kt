package com.fitnessapp.android.data.network

import android.content.Context
import android.content.SharedPreferences

/** Durable app settings + JWT session. v1 stores the token in private prefs. */
class AuthStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fitness_app_auth", Context.MODE_PRIVATE)

    var jwt: String?
        get() = prefs.getString(KEY_JWT, null)
        set(value) = prefs.edit().putString(KEY_JWT, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /** Server-side user id (from the auth token response) — marks "me" rows. */
    var userId: Int?
        get() = if (prefs.contains(KEY_USER_ID)) prefs.getInt(KEY_USER_ID, -1) else null
        set(value) {
            val e = prefs.edit()
            if (value == null) e.remove(KEY_USER_ID) else e.putInt(KEY_USER_ID, value)
            e.apply()
        }

    var displayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    /** FCM/device registration token (Firebase token, or dev:… when Firebase is not configured). */
    var fcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    /** True once the token was successfully pushed to POST /users/me/fcm-token. */
    var fcmTokenRegistered: Boolean
        get() = prefs.getBoolean(KEY_FCM_REGISTERED, false)
        set(value) = prefs.edit().putBoolean(KEY_FCM_REGISTERED, value).apply()

    /** Backend base URL including /api/v1. Defaulted via BuildConfig. */
    var baseUrl: String
        get() {
            if (com.fitnessapp.android.BuildConfig.DEBUG) {
                if (prefs.contains(KEY_BASE_URL)) {
                    prefs.edit().remove(KEY_BASE_URL).apply()
                }
                return com.fitnessapp.android.BuildConfig.DEFAULT_BASE_URL
            }
            return prefs.getString(KEY_BASE_URL, com.fitnessapp.android.BuildConfig.DEFAULT_BASE_URL)
                ?: com.fitnessapp.android.BuildConfig.DEFAULT_BASE_URL
        }
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trimEnd('/')).apply()

    fun recordSyncSuccess(date: String, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_LAST_SYNC_DATE, date)
            .putLong(KEY_LAST_SYNC_AT, atMillis)
            .putString(KEY_LAST_SYNC_STATUS, "ok")
            .apply()
    }

    fun recordSyncError(message: String) {
        prefs.edit().putString(KEY_LAST_SYNC_STATUS, message.take(200)).apply()
    }

    fun lastSync(): Pair<String?, Long> = prefs.getString(KEY_LAST_SYNC_DATE, null) to prefs.getLong(KEY_LAST_SYNC_AT, 0L)

    fun lastSyncStatus(): String? = prefs.getString(KEY_LAST_SYNC_STATUS, null)

    fun clearSession() {
        prefs.edit()
            .remove(KEY_JWT)
            .remove(KEY_EMAIL)
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_FCM_REGISTERED)
            .apply()
    }

    companion object {
        val DEFAULT_BASE_URL get() = com.fitnessapp.android.BuildConfig.DEFAULT_BASE_URL
        private const val KEY_JWT = "jwt"
        private const val KEY_EMAIL = "email"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_FCM_REGISTERED = "fcm_token_registered"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_LAST_SYNC_DATE = "last_sync_date"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
    }
}
