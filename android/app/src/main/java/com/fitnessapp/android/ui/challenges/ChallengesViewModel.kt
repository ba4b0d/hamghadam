package com.fitnessapp.android.ui.challenges

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.model.Challenge
import com.fitnessapp.android.data.model.ChallengeDeepLink
import com.fitnessapp.android.data.model.ChallengeFormatters
import com.fitnessapp.android.data.network.ApiResult
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChallengesUiState(
    val loading: Boolean = false,
    val challenges: List<Challenge> = emptyList(),
    val error: String? = null,
    val isSignedIn: Boolean = false,
    val myUserId: Int? = null,
    // create form
    val createOpen: Boolean = false,
    val createTitle: String = "",
    val createMetric: String = "steps", // steps | sleep_seconds | avg_hr
    val createStart: LocalDateTime? = null,
    val createEnd: LocalDateTime? = null,
    val createInviteOnly: Boolean = false,
    val createMaxParticipants: String = "",
    val createBusy: Boolean = false,
    val createError: String? = null,
    // join-by-invite-link dialog
    val joinOpen: Boolean = false,
    val joinInput: String = "",
    val joinBusy: Boolean = false,
    val joinError: String? = null,
) {
    val active: List<Challenge> get() = challenges.filter { it.isActive }
    val upcoming: List<Challenge> get() = challenges.filter { it.isDraft }
    val ended: List<Challenge> get() = challenges.filter { it.isEnded }
    val hasAny: Boolean get() = challenges.isNotEmpty()
}

/**
 * Challenges tab: list (active / upcoming / ended), create-challenge form,
 * join-by-invite-link dialog. Requests navigation via [navigateToChallenge].
 */
class ChallengesViewModel(app: Application) : AndroidViewModel(app) {

    private val container get() = (getApplication<FitnessApp>()).container

    private val _state = MutableStateFlow(ChallengesUiState())
    val state: StateFlow<ChallengesUiState> = _state.asStateFlow()

    private val _navigateToChallenge = MutableStateFlow<Long?>(null)
    val navigateToChallenge: StateFlow<Long?> = _navigateToChallenge.asStateFlow()

    init {
        refresh()
    }

    fun consumeNavigation() {
        _navigateToChallenge.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            val token = container.authStore.jwt
            if (token == null) {
                _state.update { it.copy(isSignedIn = false, loading = false, challenges = emptyList(), error = null, myUserId = null) }
                return@launch
            }
            _state.update { it.copy(loading = true, isSignedIn = true, error = null, myUserId = container.authStore.userId) }
            when (val result = container.apiClient.listChallenges(token)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, challenges = result.value, error = null) }
                }
                is ApiResult.Unauthorized -> {
                    container.authStore.clearSession()
                    _state.update { it.copy(loading = false, isSignedIn = false, challenges = emptyList(), error = "Session expired — sign in again") }
                }
                else -> {
                    _state.update { it.copy(loading = false, error = describe(result)) }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Create challenge form
    // ------------------------------------------------------------------

    fun openCreate() {
        val now = LocalDateTime.now()
        _state.update {
            it.copy(
                createOpen = true,
                createTitle = "",
                createMetric = "steps",
                createStart = now.plusHours(1).withMinute(0).withSecond(0).withNano(0),
                createEnd = now.plusDays(7).withMinute(0).withSecond(0).withNano(0),
                createInviteOnly = false,
                createMaxParticipants = "",
                createBusy = false,
                createError = null,
            )
        }
    }

    fun closeCreate() = _state.update { it.copy(createOpen = false) }
    fun onCreateTitle(v: String) = _state.update { it.copy(createTitle = v.take(120), createError = null) }
    fun onCreateMetric(v: String) = _state.update { it.copy(createMetric = v) }
    fun onCreateStart(v: LocalDateTime) = _state.update { it.copy(createStart = v, createError = null) }
    fun onCreateEnd(v: LocalDateTime) = _state.update { it.copy(createEnd = v, createError = null) }
    fun onCreateInviteOnly(v: Boolean) = _state.update { it.copy(createInviteOnly = v) }
    fun onCreateMaxParticipants(v: String) = _state.update { it.copy(createMaxParticipants = v.filter { it.isDigit() }.take(4)) }

    fun submitCreate() {
        val s = _state.value
        val title = s.createTitle.trim()
        val start = s.createStart ?: return
        val end = s.createEnd ?: return
        val maxP = s.createMaxParticipants.trim().toIntOrNull()
        val validation = ChallengeFormValidator.validate(title, start, end, maxP)
        if (validation != null) {
            _state.update { it.copy(createError = validation) }
            return
        }
        val token = container.authStore.jwt ?: run {
            _state.update { it.copy(createError = "Sign in on the Account tab first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(createBusy = true, createError = null) }
            when (val result = container.apiClient.createChallenge(
                token = token,
                title = title,
                startsAtIso = toUtcIso(start),
                endsAtIso = toUtcIso(end),
                metric = s.createMetric,
                inviteOnly = s.createInviteOnly,
                maxParticipants = maxP,
            )) {
                is ApiResult.Success -> {
                    _state.update { it.copy(createBusy = false, createOpen = false) }
                    refresh()
                    _navigateToChallenge.value = result.value.id.toLong()
                }
                else -> _state.update { it.copy(createBusy = false, createError = describe(result)) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Join by invite link (fitnessapp://challenges/{id}/join?code=...)
    // ------------------------------------------------------------------

    fun openJoin() = _state.update { it.copy(joinOpen = true, joinInput = "", joinBusy = false, joinError = null) }
    fun closeJoin() = _state.update { it.copy(joinOpen = false) }
    fun onJoinInput(v: String) = _state.update { it.copy(joinInput = v, joinError = null) }

    fun submitJoin() {
        val s = _state.value
        val token = container.authStore.jwt ?: run {
            _state.update { it.copy(joinError = "Sign in on the Account tab first") }
            return
        }
        val text = s.joinInput.trim()
        if (text.isBlank()) {
            _state.update { it.copy(joinError = "Paste the invite link you received") }
            return
        }
        val link = if (text.startsWith("fitnessapp://")) ChallengeDeepLink.parse(text) else null
        val join = asJoin(link) ?: run {
            _state.update {
                it.copy(joinError = "That doesn't look like an invite link. Use: fitnessapp://challenges/{id}/join?code=CODE")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(joinBusy = true, joinError = null) }
            when (val result = container.apiClient.joinChallenge(token, join.challengeId, join.code)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(joinBusy = false, joinOpen = false) }
                    refresh()
                    _navigateToChallenge.value = result.value.id.toLong()
                }
                else -> _state.update { it.copy(joinBusy = false, joinError = describe(result)) }
            }
        }
    }

    private fun asJoin(link: ChallengeDeepLink?): ChallengeDeepLink.Join? = link as? ChallengeDeepLink.Join

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun describe(result: ApiResult<*>): String = when (result) {
        is ApiResult.Unauthorized -> "Session expired — sign in again"
        is ApiResult.Forbidden -> "Invite invalid or expired (${result.detail})"
        is ApiResult.Conflict -> result.detail
        is ApiResult.Validation -> "Validation error: ${result.detail}"
        is ApiResult.Failure -> "Network: ${result.detail}"
        else -> "Something went wrong"
    }

    private fun toUtcIso(value: LocalDateTime): String =
        DateTimeFormatter.ISO_INSTANT.format(value.atZone(ZoneId.systemDefault()).toInstant())
}

/** Pure form validation for the create-challenge dialog (unit-tested). */
object ChallengeFormValidator {
    fun validate(title: String, start: LocalDateTime, end: LocalDateTime, maxParticipants: Int?): String? {
        if (title.isBlank()) return "Give the challenge a title"
        if (title.length > 120) return "Title is too long (max 120 chars)"
        if (end <= start) return "End must be after the start"
        if (maxParticipants != null && (maxParticipants < 2 || maxParticipants > 1000)) {
            return "Max participants must be between 2 and 1000"
        }
        return null
    }
}
