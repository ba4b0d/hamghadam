package com.fitnessapp.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.fitnessapp.android.data.model.UserPublicProfile
import com.fitnessapp.android.ui.auth.LoginCard
import com.fitnessapp.android.ui.profile.PublicProfileDialog
import com.fitnessapp.android.ui.theme.FitnessAppTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end UI verification tests for V1.2 Social features:
 * - Google Sign-In button on Login Screen
 * - Public Profile dialog modal
 */
class V12SocialUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun captureScreenshot(filename: String) {
        try {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val dir = File("/sdcard/Download")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            if (file.exists()) {
                file.delete()
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun loginCard_displaysGoogleSignInButton() {
        compose.setContent {
            FitnessAppTheme {
                LoginCard(
                    email = "test@example.com",
                    password = "password123",
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSignIn = {},
                    onRegister = {},
                    onGoogleSignIn = {},
                    busy = false,
                    message = null,
                )
            }
        }

        compose.onNodeWithText("Continue with Google").assertIsDisplayed()
        captureScreenshot("v12_google_login_card.png")
    }

    @Test
    fun publicProfileDialog_displaysUserProfileModal() {
        val publicUser = UserPublicProfile(
            id = 5,
            displayName = "David Sprinter",
            bio = "100m specialist",
            location = "Tabriz",
            friendshipStatus = "NONE",
        )

        compose.setContent {
            FitnessAppTheme {
                PublicProfileDialog(
                    user = publicUser,
                    onDismiss = {},
                    onSendFriendRequest = {},
                    isActionLoading = false,
                    todaySteps = 10500,
                )
            }
        }

        compose.onNodeWithText("David Sprinter").assertIsDisplayed()
        compose.onNodeWithText("100m specialist").assertIsDisplayed()
        captureScreenshot("v12_public_profile_dialog.png")
    }
}
