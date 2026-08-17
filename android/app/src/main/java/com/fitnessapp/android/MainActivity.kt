package com.fitnessapp.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitnessapp.android.ui.MainScreen
import com.fitnessapp.android.ui.theme.FitnessAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        routeExternalIntent(intent)
        setContent {
            FitnessAppTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeExternalIntent(intent)
    }

    /**
     * Forward `fitnessapp://challenges/…` VIEW intents (adb, notification tap,
     * browser) into the app's navigation via [com.fitnessapp.android.AppContainer.externalDeepLinks].
     */
    private fun routeExternalIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "fitnessapp") {
            (application as FitnessApp).container.externalDeepLinks.value = data
        }
    }
}