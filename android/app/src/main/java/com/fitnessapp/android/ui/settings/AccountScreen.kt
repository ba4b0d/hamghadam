package com.fitnessapp.android.ui.settings

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.network.ApiResult
import com.fitnessapp.android.data.sync.SyncScheduler
import com.fitnessapp.android.ui.auth.LoginScreen
import com.fitnessapp.android.ui.profile.ProfileScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val email: String = "",
    val password: String = "",
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<FitnessApp>()).container

    private val _state = MutableStateFlow(
        AccountUiState(
            email = container.authStore.email.orEmpty(),
            signedIn = container.authStore.jwt != null,
        )
    )
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }

    fun register() {
        val s = _state.value
        if (s.email.isBlank() || s.password.length < 8) {
            _state.update { it.copy(message = "Email required; password ≥ 8 chars") }
            return
        }
        launchAuth(register = true)
    }

    fun login() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(message = "Email and password required") }
            return
        }
        launchAuth(register = false)
    }

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = container.apiClient.loginWithGoogle(idToken)
            when (result) {
                is ApiResult.Success -> {
                    container.authStore.jwt = result.value.token
                    result.value.email?.let { container.authStore.email = it }
                    container.authStore.userId = result.value.userId
                    result.value.displayName?.let { container.authStore.displayName = it }
                    result.value.avatarUrl?.let { container.authStore.avatarUrl = it }
                    result.value.bio?.let { container.authStore.bio = it }
                    container.authStore.authProvider = "google"
                    _state.update {
                        it.copy(
                            email = container.authStore.email.orEmpty(),
                            busy = false,
                            signedIn = true,
                            message = "Signed in with Google ✓",
                        )
                    }
                    SyncScheduler.syncNow(getApplication())
                    container.fcmTokenManager.registerIfSignedIn(force = true)
                }
                is ApiResult.Unauthorized -> _state.update { it.copy(busy = false, message = "Google Sign-In unauthorized: ${result.detail}") }
                is ApiResult.Validation -> _state.update { it.copy(busy = false, message = "Google validation error: ${result.detail}") }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, message = "Google Sign-In failed: ${result.detail}") }
                else -> _state.update { it.copy(busy = false, message = "Google Sign-In error") }
            }
        }
    }

    fun onGoogleAuthError(err: String) {
        _state.update { it.copy(busy = false, message = "Google Sign-In: $err") }
    }

    private fun launchAuth(register: Boolean) {
        viewModelScope.launch {
            val s = _state.value
            _state.update { it.copy(busy = true, message = null) }
            val tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
            val result = if (register) {
                container.apiClient.register(s.email.trim(), s.password, displayName = "Android user", tzOffset = tzOffset)
            } else {
                container.apiClient.login(s.email.trim(), s.password)
            }
            when (result) {
                is ApiResult.Success -> {
                    container.authStore.jwt = result.value.token
                    container.authStore.email = s.email.trim()
                    container.authStore.userId = result.value.userId
                    result.value.displayName?.let { container.authStore.displayName = it }
                    result.value.avatarUrl?.let { container.authStore.avatarUrl = it }
                    result.value.bio?.let { container.authStore.bio = it }
                    container.authStore.authProvider = "email"
                    _state.update {
                        it.copy(busy = false, signedIn = true, message = if (register) "Registered ✓" else "Signed in ✓")
                    }
                    SyncScheduler.syncNow(getApplication())
                    container.fcmTokenManager.registerIfSignedIn(force = true)
                }
                is ApiResult.Conflict -> {
                    // Email exists — fall back to login for convenience.
                    val login = container.apiClient.login(s.email.trim(), s.password)
                    if (login is ApiResult.Success) {
                        container.authStore.jwt = login.value.token
                        container.authStore.email = s.email.trim()
                        container.authStore.userId = login.value.userId
                        login.value.displayName?.let { container.authStore.displayName = it }
                        container.authStore.authProvider = "email"
                        _state.update { it.copy(busy = false, signedIn = true, message = "Registered before — signed in ✓") }
                        SyncScheduler.syncNow(getApplication())
                        container.fcmTokenManager.registerIfSignedIn(force = true)
                    } else {
                        _state.update { it.copy(busy = false, message = "Email exists — try signing in") }
                    }
                }
                is ApiResult.Unauthorized -> _state.update { it.copy(busy = false, message = "Bad credentials") }
                is ApiResult.Forbidden -> _state.update { it.copy(busy = false, message = "Access denied: ${result.detail}") }
                is ApiResult.Validation -> _state.update { it.copy(busy = false, message = "Validation error: ${result.detail}") }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, message = "Network: ${result.detail}") }
            }
        }
    }

    fun syncNow() {
        SyncScheduler.syncNow(getApplication())
        _state.update { it.copy(message = "Sync scheduled (see Dashboard for status)") }
    }

    fun signOut() {
        container.authStore.clearSession()
        _state.update { it.copy(signedIn = false, message = "Signed out") }
    }
}

@Composable
fun AccountScreen(viewModel: AccountViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    if (state.signedIn) {
        ProfileScreen(
            onSignOut = viewModel::signOut,
            onSyncNow = viewModel::syncNow,
        )
    } else {
        LoginScreen(
            email = state.email,
            password = state.password,
            onEmailChange = viewModel::onEmail,
            onPasswordChange = viewModel::onPassword,
            onSignIn = viewModel::login,
            onRegister = viewModel::register,
            onGoogleIdToken = viewModel::loginWithGoogle,
            onGoogleError = viewModel::onGoogleAuthError,
            busy = state.busy,
            message = state.message,
        )
    }
}
