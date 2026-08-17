package com.fitnessapp.android.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.network.ApiResult
import com.fitnessapp.android.data.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val email: String = "",
    val password: String = "",
    val baseUrl: String = "",
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<FitnessApp>()).container

    private val _state = MutableStateFlow(
        AccountUiState(
            email = container.authStore.email.orEmpty(),
            baseUrl = container.authStore.baseUrl,
            signedIn = container.authStore.jwt != null,
        )
    )
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }
    fun onBaseUrl(v: String) = _state.update { it.copy(baseUrl = v) }

    fun saveBaseUrl() {
        container.authStore.baseUrl = _state.value.baseUrl
        _state.update { it.copy(message = "Base URL saved") }
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Backend", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::onBaseUrl,
                    label = { Text("API base URL (incl. /api/v1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::saveBaseUrl, modifier = Modifier.fillMaxWidth()) {
                    Text("Save base URL")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (state.signedIn) "Session" else "Sign in / Register",
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.signedIn) {
                    Spacer(Modifier.height(8.dp))
                    Text("Signed in as ${state.email}")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::syncNow, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                            Spacer(Modifier.height(0.dp))
                            Text("Sync now")
                        }
                        OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.weight(1f)) {
                            Text("Sign out")
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmail,
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPassword,
                        label = { Text("Password (≥ 8 chars)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::login, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                            Text("Sign in")
                        }
                        OutlinedButton(onClick = viewModel::register, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                            Text("Register")
                        }
                    }
                }
                if (state.busy) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }

        state.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = if (msg.startsWith("Network") || msg.contains("failed") || msg.contains("error", ignoreCase = true))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
