package com.fitnessapp.android.data.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.fitnessapp.android.MainActivity
import com.fitnessapp.android.R
import com.fitnessapp.android.data.model.ChallengeDeepLink
import com.fitnessapp.android.data.model.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes incoming challenge push payloads (BE-C3 `data` map:
 * `{type, challenge_id, deep_link}`):
 *
 *  - app in foreground → emit the deep link on [pendingRoute]; MainScreen
 *    observes it and navigates immediately.
 *  - app in background → post a notification whose tap re-opens the app
 *    through the same deep-link URI (MainActivity VIEW intent).
 *
 * The exact same entry point is used by FcmMessagingService and by the
 * debug push simulator (ACTION_SIMULATE_PUSH), so the routing path is
 * verifiable without a Firebase project.
 */
class NotificationRouter(
    private val context: Context,
    private val isForeground: () -> Boolean,
) {
    private val _pendingRoute = MutableStateFlow<ChallengeDeepLink?>(null)
    val pendingRoute: StateFlow<ChallengeDeepLink?> = _pendingRoute.asStateFlow()

    fun consumeRoute(): ChallengeDeepLink? {
        val current = _pendingRoute.value
        if (current != null) _pendingRoute.value = null
        return current
    }

    /** Handle a data-only FCM payload (or the simulated equivalent). */
    fun handleData(data: Map<String, String>) {
        val deepLinkText = data["deep_link"] ?: return
        val link = ChallengeDeepLink.parse(deepLinkText) ?: return
        val type = data["type"] ?: ""

        if (isForeground()) {
            _pendingRoute.value = link
        } else {
            postNotification(
                title = titleFor(type, link),
                body = bodyFor(type, data, link),
                deepLink = link,
            )
        }
    }

    private fun titleFor(type: String, link: ChallengeDeepLink): String = when (type) {
        "challenge_started" -> "Challenge started"
        "challenge_ended" -> "Challenge ended"
        "beat_you" -> "You've been overtaken!"
        else -> "Challenge update"
    }

    private fun bodyFor(type: String, data: Map<String, String>, link: ChallengeDeepLink): String {
        data["body"]?.let { if (it.isNotBlank()) return it }
        return when (type) {
            "challenge_started" -> "Your challenge #${link.challengeId()} is live — steps count now."
            "challenge_ended" -> "Challenge #${link.challengeId()} finished. See the final results."
            "beat_you" -> "Someone just passed you in challenge #${link.challengeId()}."
            else -> "Tap to open challenge #${link.challengeId()}."
        }
    }

    private fun postNotification(title: String, body: String, deepLink: ChallengeDeepLink) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "challenge_updates"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Challenge updates", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink.toUri())).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            deepLink.challengeId().toInt().hashCode(),
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_challenge)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            nm.notify(deepLink.challengeId().toInt().hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — data-only routing still works in foreground.
        }
    }
}

private fun ChallengeDeepLink.challengeId(): Long = when (this) {
    is ChallengeDeepLink.Detail -> challengeId
    is ChallengeDeepLink.Join -> challengeId
    is ChallengeDeepLink.Leaderboard -> challengeId
}
