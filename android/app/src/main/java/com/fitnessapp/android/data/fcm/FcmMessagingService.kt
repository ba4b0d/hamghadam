package com.fitnessapp.android.data.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitnessapp.android.FitnessApp

/**
 * Firebase Cloud Messaging service (BE-C3). `onMessageReceived` handles
 * data-only payloads `{type, challenge_id, deep_link}` and routes them via
 * [NotificationRouter]; `onNewToken` re-registers the token with the backend.
 *
 * Without a Firebase project (no google-services.json) this service simply
 * never receives messages — the same routing path can be exercised with the
 * debug simulator [DebugFcmSimulatorReceiver] (ACTION_SIMULATE_PUSH).
 */
class FcmMessagingService : com.google.firebase.messaging.FirebaseMessagingService() {

    override fun onMessageReceived(message: com.google.firebase.messaging.RemoteMessage) {
        super.onMessageReceived(message)
        val container = (application as FitnessApp).container
        container.notificationRouter.handleData(message.data)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val container = (application as FitnessApp).container
        container.fcmTokenManager.onNewToken(token)
    }
}

/**
 * Debug-only push simulator: broadcasts the same `data` map a real FCM
 * message would carry, without needing a Firebase project.
 *
 * Usage (tester / adb):
 *   adb shell am broadcast -a com.fitnessapp.android.action.SIMULATE_PUSH \
 *     --es deep_link "fitnessapp://challenges/1/join?code=ABCDEFGH" \
 *     --es type challenge_started --es challenge_id 1 --es body "…"
 */
class DebugFcmSimulatorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SIMULATE_PUSH) return
        val container = (context.applicationContext as FitnessApp).container
        val data = HashMap<String, String>()
        intent.extras?.keySet()?.forEach { key -> intent.getStringExtra(key)?.let { data[key] = it } }
        container.notificationRouter.handleData(data)
    }

    companion object {
        const val ACTION_SIMULATE_PUSH = "com.fitnessapp.android.action.SIMULATE_PUSH"
    }
}
