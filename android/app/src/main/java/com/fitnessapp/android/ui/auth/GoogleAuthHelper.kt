package com.fitnessapp.android.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.fitnessapp.android.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed class GoogleAuthResult {
    data class Success(val idToken: String) : GoogleAuthResult()
    data object Cancelled : GoogleAuthResult()
    data class Error(val message: String) : GoogleAuthResult()
}

class GoogleAuthHelper(
    private val context: Context,
    private val serverClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun getGoogleIdToken(): GoogleAuthResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context,
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthResult.Success(googleIdTokenCredential.idToken)
            } else {
                GoogleAuthResult.Error("Unexpected credential type: ${credential::class.java.simpleName}")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleAuthResult.Error(e.message ?: "Google Sign-In failed")
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.message ?: "Google Sign-In error")
        }
    }
}
