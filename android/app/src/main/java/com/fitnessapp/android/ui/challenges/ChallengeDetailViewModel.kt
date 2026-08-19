package com.fitnessapp.android.ui.challenges

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.model.Challenge
import com.fitnessapp.android.data.model.ChallengeFormatters
import com.fitnessapp.android.data.model.InviteInfo
import com.fitnessapp.android.data.model.Leaderboard
import com.fitnessapp.android.data.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChallengeDetailUiState(
    val loading: Boolean = false,
    val challenge: Challenge? = null,
    val leaderboard: Leaderboard? = null,
    val leaderboardLoading: Boolean = false,
    val error: String? = null,
    val isCreator: Boolean = false,
    val isParticipant: Boolean = false,
    val isSignedIn: Boolean = false,
    // join dialogs
    val joinDialogOpen: Boolean = false,
    val joinCode: String = "",
    val joinBusy: Boolean = false,
    val joinError: String? = null,
    // invite share
    val invite: InviteInfo? = null,
    val inviteBusy: Boolean = false,
    val inviteError: String? = null,
    val statusBusy: Boolean = false,
    val notice: String? = null,
)

/** Challenge detail: progress, participants, leaderboard, join/invite/status. */
class ChallengeDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val container get() = (getApplication<FitnessApp>()).container

    private val _state = MutableStateFlow(ChallengeDetailUiState())
    val state: StateFlow<ChallengeDetailUiState> = _state.asStateFlow()

    private var loadedChallengeId: Long? = null

    /**
     * [autoJoinCode] comes from a `fitnessapp://challenges/{id}/join?code=…`
     * deep link or a simulated push: join once the challenge is loaded.
     */
    fun load(challengeId: Long, autoJoinCode: String? = null) {
        if (loadedChallengeId == challengeId && _state.value.challenge != null) {
            refreshLeaderboard()
            return
        }
        loadedChallengeId = challengeId
        viewModelScope.launch {
            val token = container.authStore.jwt
            if (token == null) {
                _state.update { it.copy(isSignedIn = false, loading = false, error = "Sign in on the Account tab first") }
                return@launch
            }
            _state.update { it.copy(loading = true, isSignedIn = true, error = null) }
            when (val result = container.apiClient.getChallenge(token, challengeId)) {
                is ApiResult.Success -> {
                    val challenge = result.value
                    val userId = container.authStore.userId
                    _state.update {
                        it.copy(
                            loading = false,
                            challenge = challenge,
                            isCreator = challenge.creator.id == userId,
                            isParticipant = challenge.participants.any { p -> p.userId == userId },
                            error = null,
                        )
                    }
                    refreshLeaderboard()
                    if (!autoJoinCode.isNullOrBlank() && !_state.value.isParticipant) {
                        joinWithCode(autoJoinCode, silent = true)
                    }
                }
                is ApiResult.Unauthorized -> {
                    container.authStore.clearSession()
                    _state.update { it.copy(loading = false, isSignedIn = false, error = "Session expired — sign in again") }
                }
                else -> _state.update { it.copy(loading = false, error = describe(result)) }
            }
        }
    }

    fun refreshLeaderboard() {
        val token = container.authStore.jwt ?: return
        val id = loadedChallengeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(leaderboardLoading = true) }
            when (val result = container.apiClient.getLeaderboard(token, id)) {
                is ApiResult.Success -> _state.update { it.copy(leaderboard = result.value, leaderboardLoading = false) }
                else -> _state.update { it.copy(leaderboardLoading = false) }
            }
        }
    }

    /**
     * Manual "Refresh": reloads the challenge object (participants + totals)
     * and then the leaderboard, so the participants list and the ranked board
     * both reflect current server state after other users sync/join.
     */
    fun refreshAll() {
        val token = container.authStore.jwt ?: return
        val id = loadedChallengeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = container.apiClient.getChallenge(token, id)) {
                is ApiResult.Success -> {
                    val challenge = result.value
                    val userId = container.authStore.userId
                    _state.update {
                        it.copy(
                            loading = false,
                            challenge = challenge,
                            isCreator = challenge.creator.id == userId,
                            isParticipant = challenge.participants.any { p -> p.userId == userId },
                            error = null,
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    container.authStore.clearSession()
                    _state.update { it.copy(loading = false, isSignedIn = false, error = "Session expired — sign in again") }
                }
                else -> _state.update { it.copy(loading = false, error = describe(result)) }
            }
            refreshLeaderboard()
        }
    }

    // ------------------------------------------------------------------
    // Join
    // ------------------------------------------------------------------

    fun openJoinDialog() = _state.update { it.copy(joinDialogOpen = true, joinCode = "", joinError = null) }
    fun closeJoinDialog() = _state.update { it.copy(joinDialogOpen = false) }
    fun onJoinCode(v: String) = _state.update { it.copy(joinCode = v, joinError = null) }

    /** Join an open challenge (no code needed). Invite-only opens the code dialog. */
    fun join() {
        val challenge = _state.value.challenge ?: return
        if (challenge.inviteOnly) {
            openJoinDialog()
            return
        }
        joinWithCode(code = null)
    }

    fun submitJoinDialog() {
        val code = ChallengeFormatters.normalizeInviteCode(_state.value.joinCode)
        if (!ChallengeFormatters.isValidInviteCode(code)) {
            _state.update { it.copy(joinError = "Invite codes are 8 characters (A–H J–N P–Z 2–9)") }
            return
        }
        joinWithCode(code)
    }

    private fun joinWithCode(code: String?, silent: Boolean = false) {
        val token = container.authStore.jwt ?: return
        val id = loadedChallengeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(joinBusy = true, joinError = null) }
            when (val result = container.apiClient.joinChallenge(token, id, code)) {
                is ApiResult.Success -> {
                    val userId = container.authStore.userId
                    _state.update {
                        it.copy(
                            joinBusy = false,
                            joinDialogOpen = false,
                            challenge = result.value,
                            isParticipant = result.value.participants.any { p -> p.userId == userId },
                            notice = if (silent) null else "You joined ${result.value.title} 🎉",
                        )
                    }
                    refreshLeaderboard()
                }
                else -> _state.update {
                    it.copy(joinBusy = false, joinError = describe(result), notice = null)
                }
            }
        }
    }

    fun leave() {
        val token = container.authStore.jwt ?: return
        val id = loadedChallengeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(joinBusy = true, joinError = null) }
            when (val result = container.apiClient.leaveChallenge(token, id)) {
                is ApiResult.Success -> {
                    val userId = container.authStore.userId
                    _state.update {
                        it.copy(
                            joinBusy = false,
                            challenge = result.value,
                            isParticipant = result.value.participants.any { p -> p.userId == userId },
                            notice = "You left the challenge",
                        )
                    }
                    refreshLeaderboard()
                }
                is ApiResult.Failure -> {
                    // creator cannot leave — 400 from backend; surface politely
                    _state.update { it.copy(joinBusy = false, joinError = "The creator cannot leave (${result.detail})") }
                }
                else -> _state.update { it.copy(joinBusy = false, joinError = describe(result)) }
            }
        }
    }

    fun cancelChallenge(onCancelled: () -> Unit) {
        val token = container.authStore.jwt ?: return
        val id = loadedChallengeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(joinBusy = true, joinError = null) }
            when (val result = container.apiClient.cancelChallenge(token, id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(joinBusy = false, notice = "Challenge cancelled") }
                    onCancelled()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(joinBusy = false, joinError = "Failed to cancel challenge: ${result.detail}") }
                }
                else -> _state.update { it.copy(joinBusy = false, joinError = describe(result)) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Invites
    // ------------------------------------------------------------------

    fun createInvite(ttlHours: Int = 168) {
        val token = container.authStore.jwt ?: return
        val id = loadedChallengeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(inviteBusy = true, inviteError = null) }
            when (val result = container.apiClient.createInvite(token, id, ttlHours)) {
                is ApiResult.Success -> _state.update { it.copy(invite = result.value, inviteBusy = false) }
                else -> _state.update { it.copy(inviteBusy = false, inviteError = describe(result)) }
            }
        }
    }

    fun dismissInvite() = _state.update { it.copy(invite = null, inviteError = null) }

    // ------------------------------------------------------------------
    // Creator status controls (trigger FCM challenge_started/ended)
    // ------------------------------------------------------------------

    fun setStatus(newStatus: String) {
        val token = container.authStore.jwt ?: return
        val id = loadedChallengeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(statusBusy = true, notice = null) }
            when (val result = container.apiClient.updateChallengeStatus(token, id, newStatus)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(statusBusy = false, challenge = result.value, notice = "Challenge ${newStatus}") }
                    refreshLeaderboard()
                }
                else -> _state.update { it.copy(statusBusy = false, joinError = describe(result)) }
            }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    // ------------------------------------------------------------------

    private fun describe(result: ApiResult<*>): String = when (result) {
        is ApiResult.Unauthorized -> "Session expired — sign in again"
        is ApiResult.Forbidden -> "Forbidden: ${result.detail}"
        is ApiResult.Conflict -> result.detail
        is ApiResult.Validation -> "Validation error: ${result.detail}"
        is ApiResult.Failure -> "Network: ${result.detail}"
        else -> "Something went wrong"
    }
}