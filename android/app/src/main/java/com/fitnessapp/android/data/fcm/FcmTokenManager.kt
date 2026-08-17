package com.fitnessapp.android.data.fcm

import android.content.Context
import android.util.Log
import com.fitnessapp.android.data.network.ApiClient
import com.fitnessapp.android.data.network.ApiResult
import com.fitnessapp.android.data.network.AuthStore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the device's FCM registration token and keeps the backend in sync
 * (POST /users/me/fcm-token, BE-C3).
 */
class FcmTokenManager(
    private val context: Context,
    private val authStore: AuthStore,
    private val apiClient: ApiClient,
) {

    val firebaseAvailable: Boolean
        get() = try {
            val apps = FirebaseApp.getApps(context)
            Log.d("FcmTokenManager", "FirebaseApp apps count: ${apps.size}")
            apps.isNotEmpty()
        } catch (e: Exception) {
            Log.e("FcmTokenManager", "Error checking FirebaseApp availability", e)
            false
        }

    /** Returns the current token — Firebase's when configured, else dev:<uuid>. */
    fun obtainToken(): String {
        val cached = authStore.fcmToken
        if (!cached.isNullOrBlank()) {
            if (!firebaseAvailable || !cached.startsWith("dev:")) {
                Log.d("FcmTokenManager", "Using cached FCM token: ${cached.take(30)}...")
                return cached
            }
        }
        val token = try {
            if (firebaseAvailable) {
                val realToken = Tasks.await(FirebaseMessaging.getInstance().token, 15, TimeUnit.SECONDS)
                Log.d("FcmTokenManager", "Obtained real FCM token: ${realToken.take(30)}...")
                realToken
            } else {
                val devTok = "dev:${UUID.randomUUID()}"
                Log.d("FcmTokenManager", "Firebase not available, generated dev token: $devTok")
                devTok
            }
        } catch (e: Exception) {
            val devTok = "dev:${UUID.randomUUID()}"
            Log.e("FcmTokenManager", "Failed to obtain Firebase token, falling back to dev token", e)
            devTok
        }
        authStore.fcmToken = token
        return token
    }

    /**
     * Push the token to the backend when signed in. [force] re-registers even
     * when the last attempt succeeded (token refresh / new session).
     */
    suspend fun registerIfSignedIn(force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val jwt = authStore.jwt ?: run {
                Log.d("FcmTokenManager", "No JWT, skipping FCM registration")
                return@withContext false
            }
            if (authStore.fcmTokenRegistered && !force) {
                Log.d("FcmTokenManager", "FCM token already registered")
                return@withContext true
            }
            val token = obtainToken()
            val ok = when (val res = apiClient.registerFcmToken(jwt, token)) {
                is ApiResult.Success -> {
                    Log.d("FcmTokenManager", "FCM token successfully registered with backend")
                    true
                }
                is ApiResult.Unauthorized -> {
                    Log.w("FcmTokenManager", "FCM token registration unauthorized, clearing session")
                    authStore.clearSession()
                    false
                }
                else -> {
                    Log.e("FcmTokenManager", "FCM token registration failed: $res")
                    false
                }
            }
            authStore.fcmTokenRegistered = ok
            ok
        }

    /** FirebaseMessagingService.onNewToken — store + re-register, fire-and-forget. */
    fun onNewToken(newToken: String) {
        if (newToken.isBlank()) return
        Log.d("FcmTokenManager", "onNewToken received: ${newToken.take(30)}...")
        authStore.fcmToken = newToken
        authStore.fcmTokenRegistered = false
        Thread { kotlinx.coroutines.runBlocking { registerIfSignedIn(force = true) } }.start()
    }
}
