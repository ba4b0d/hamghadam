package com.fitnessapp.android.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.model.UserProfile
import com.fitnessapp.android.data.model.UserStats
import com.fitnessapp.android.data.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProfileUiState(
    val user: UserProfile? = null,
    val stats: UserStats = UserStats(),
    val themeMode: String = "SYSTEM",
    val isLoading: Boolean = false,
    val isUpdatingBio: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<FitnessApp>()).container

    private val _state = MutableStateFlow(
        ProfileUiState(
            themeMode = container.authStore.themeMode,
            user = container.authStore.userId?.let {
                UserProfile(
                    id = it,
                    email = container.authStore.email.orEmpty(),
                    displayName = container.authStore.displayName,
                    bio = container.authStore.bio,
                    avatarUrl = container.authStore.avatarUrl,
                    authProvider = container.authStore.authProvider ?: "email",
                )
            }
        )
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    fun setThemeMode(mode: String) {
        container.authStore.themeMode = mode
        _state.update { it.copy(themeMode = mode) }
    }

    fun loadProfile() {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // 1. Load User Profile
            val profileResult = container.apiClient.getMe(token)
            if (profileResult is ApiResult.Success) {
                val u = profileResult.value
                container.authStore.displayName = u.displayName
                container.authStore.avatarUrl = u.avatarUrl
                container.authStore.bio = u.bio
                container.authStore.authProvider = u.authProvider
                _state.update { it.copy(user = u) }
            }

            // 2. Load Stats (Challenges won / participated + Step stats)
            var wonCount = 0
            var totalChallenges = 0
            val challengesResult = container.apiClient.listChallenges(token)
            if (challengesResult is ApiResult.Success) {
                totalChallenges = challengesResult.value.size
                val currentUserId = container.authStore.userId
                for (ch in challengesResult.value) {
                    if (ch.status == "ended") {
                        // Check if user won
                        val lbResult = container.apiClient.getLeaderboard(token, ch.id.toLong())
                        if (lbResult is ApiResult.Success) {
                            val topRank = lbResult.value.entries.firstOrNull()
                            if (topRank != null && topRank.userId == currentUserId && topRank.rank == 1) {
                                wonCount++
                            }
                        }
                    }
                }
            }

            // Calculate total steps (from daily cache or range)
            var totalSteps = 0L
            try {
                val today = LocalDate.now()
                val from = today.minusDays(30).toString()
                val rangeResult = container.apiClient.getDailyRange(token, from, today.toString())
                if (rangeResult is ApiResult.Success) {
                    totalSteps = rangeResult.value.sumOf { it.steps.toLong() }
                }
            } catch (_: Exception) {
                totalSteps = container.dailyCache.get(LocalDate.now().toString())?.steps?.toLong() ?: 0L
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    stats = UserStats(
                        totalSteps = totalSteps,
                        challengesWon = wonCount,
                        totalChallenges = totalChallenges,
                    ),
                )
            }
        }
    }

    fun updateBio(newBio: String) {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(isUpdatingBio = true, error = null, message = null) }
            val cleanBio = newBio.trim()
            val result = container.apiClient.updateBio(token, cleanBio)
            when (result) {
                is ApiResult.Success -> {
                    val updated = result.value
                    container.authStore.bio = updated.bio
                    _state.update {
                        it.copy(
                            isUpdatingBio = false,
                            user = updated,
                            message = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isUpdatingBio = false, error = "Failed to update bio: ${result.detail}") }
                }
                else -> {
                    _state.update { it.copy(isUpdatingBio = false, error = "Failed to update bio") }
                }
            }
        }
    }

    fun updateDisplayName(newName: String) {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null, message = null) }
            val cleanName = newName.trim()
            val result = container.apiClient.updateProfile(token, displayName = cleanName)
            when (result) {
                is ApiResult.Success -> {
                    val updated = result.value
                    container.authStore.displayName = updated.displayName
                    _state.update {
                        it.copy(
                            user = updated,
                            message = "Name updated successfully",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(error = "Failed to update name: ${result.detail}") }
                }
                else -> {
                    _state.update { it.copy(error = "Failed to update name") }
                }
            }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String = "image/jpeg", filename: String = "avatar.jpg") {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true, error = null, message = null) }
            val result = container.apiClient.uploadAvatar(token, imageBytes, mimeType, filename)
            when (result) {
                is ApiResult.Success -> {
                    val avatarUrl = result.value
                    container.authStore.avatarUrl = avatarUrl
                    _state.update {
                        val updatedUser = it.user?.copy(avatarUrl = avatarUrl)
                        it.copy(
                            isUploadingAvatar = false,
                            user = updatedUser,
                            message = "Avatar updated successfully",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isUploadingAvatar = false, error = "Avatar upload failed: ${result.detail}") }
                }
                else -> {
                    _state.update { it.copy(isUploadingAvatar = false, error = "Avatar upload failed") }
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
