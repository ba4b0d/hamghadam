package com.fitnessapp.android.ui.dashboard

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.HcStatus
import com.fitnessapp.android.data.model.DailySummary
import com.fitnessapp.android.data.model.DashboardFormatters
import com.fitnessapp.android.data.network.ApiResult
import com.fitnessapp.android.data.sync.SyncScheduler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val hcStatus: HcStatus = HcStatus.UNKNOWN,
    val grantedPermissions: Set<String> = emptySet(),
    val missingPermissions: Set<String> = emptySet(),
    val hasBackgroundPermission: Boolean = false,
    val matchmakingPossible: Boolean = false,
    val summary: DailySummary? = null,
    /** Last 7 local days, oldest first; null = no data for that day. */
    val week: List<DailySummary?> = List(7) { null },
    val reading: Boolean = false,
    val weekLoading: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val error: String? = null,
    val isSignedIn: Boolean = false,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val container get() = (getApplication<FitnessApp>()).container

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    /** Today, injectable-ish for tests via [withToday]. */
    private var todayOverride: LocalDate? = null

    init {
        refreshAll()
    }

    /** Tests can pin "today" to a fixed date without touching the clock. */
    internal fun pinToday(date: LocalDate) {
        todayOverride = date
        refreshAll()
    }

    internal fun today(): LocalDate = todayOverride ?: LocalDate.now()

    fun refreshAll() {
        viewModelScope.launch {
            val repo = container.healthRepository
            _state.update {
                it.copy(
                    hcStatus = repo.status(),
                    isSignedIn = container.authStore.jwt != null,
                    error = null,
                )
            }
            refreshPermissions()
            refreshSummary()
            refreshWeek()
            refreshMatchmaking()
        }
    }

    private suspend fun refreshPermissions() {
        val repo = container.healthRepository
        val granted = repo.grantedPermissions()
        _state.update {
            it.copy(
                grantedPermissions = granted,
                missingPermissions = repo.requiredReadPermissionStrings() - granted,
                hasBackgroundPermission = repo.hasBackgroundReadPermission(),
            )
        }
    }

    private suspend fun refreshSummary() {
        val repo = container.healthRepository
        _state.update { it.copy(reading = true) }
        val summary = repo.readDailySummary(today())
        _state.update { it.copy(summary = summary, reading = false) }
    }

    /**
     * Week strip data, oldest first. Order of preference per day:
     *   1. local cache (fast, offline)
     *   2. server GET /daily/range (when signed in) — fills gaps from the backend
     *   3. direct Health Connect read (permissions granted) — "real aggregate"
     *      even before the first sync; rows are cached for next time.
     */
    private suspend fun refreshWeek() {
        val today = today()
        val start = today.minusDays(6)
        val end = today
        _state.update { it.copy(weekLoading = true) }

        val rows = container.dailyCache.range(start, end).associateBy { it.date }.toMutableMap()
        var rowsNeeded = (0 until 7).map { start.plusDays(it.toLong()).toString() }
            .filter { it !in rows }

        // Server fill (one range call) when signed in.
        val token = container.authStore.jwt
        if (token != null && rowsNeeded.isNotEmpty()) {
            when (val result = container.apiClient.getDailyRange(token, start.toString(), end.toString())) {
                is ApiResult.Success -> {
                    result.value.forEach { row ->
                        if (row.date in rowsNeeded) {
                            rows[row.date] = row
                            container.dailyCache.save(row)
                        }
                    }
                    rowsNeeded = rowsNeeded.filter { it !in rows }
                }
                is ApiResult.Unauthorized -> {
                    container.authStore.clearSession()
                    _state.update { it.copy(isSignedIn = false) }
                }
                else -> Unit // offline / server error: fall back to HC read
            }
        }

        // Direct HC read for days still missing (parallel, best-effort).
        if (rowsNeeded.isNotEmpty() &&
            container.healthRepository.status() == HcStatus.AVAILABLE &&
            container.healthRepository.missingReadPermissions().isEmpty()
        ) {
            coroutineScope {
                rowsNeeded.map { date ->
                    async {
                        runCatching { container.healthRepository.readDailySummary(LocalDate.parse(date)) }
                            .getOrNull()
                    }
                }.awaitAll().forEach { row ->
                    row?.let {
                        rows[it.date] = it
                        container.dailyCache.save(it)
                    }
                }
            }
        }

        val week = (0 until 7).map { start.plusDays(it.toLong()).toString() }.map { rows[it] }
        _state.update { it.copy(week = week, weekLoading = false) }
    }

    private suspend fun refreshMatchmaking() {
        val possible = container.healthRepository.matchmakingPossible()
        _state.update { it.copy(matchmakingPossible = possible) }
    }

    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            refreshPermissions()
            refreshSummary()
            refreshWeek()
            refreshMatchmaking()
            // Re-evaluate; a fresh grant usually means the user wants data flowing.
            if (container.authStore.jwt != null) {
                SyncScheduler.syncNow(getApplication())
            }
        }
    }

    /** Open the Health Connect "connect your watch/apps" matchmaking screen. */
    fun openMatchmaking(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val intent = container.healthRepository.matchmakingIntent()
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
                    context.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    _state.update { it.copy(error = "Could not open Health Connect settings") }
                }
            }
        }
    }

    /** Foreground sync: read today + POST to BE-C1, with immediate UI feedback. */
    fun syncNow() {
        val token = container.authStore.jwt
        if (token == null) {
            _state.update { it.copy(syncMessage = "Sign in on the Account tab first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, syncMessage = null) }
            try {
                val summary = container.healthRepository.readDailySummary(today())
                if (summary == null) {
                    _state.update { it.copy(syncing = false, syncMessage = "Health Connect unavailable") }
                    return@launch
                }
                when (val result = container.apiClient.postDaily(token, summary)) {
                    is ApiResult.Success -> {
                        container.authStore.recordSyncSuccess(summary.date)
                        container.dailyCache.save(summary)
                        _state.update {
                            it.copy(
                                syncing = false,
                                summary = summary,
                                syncMessage = "Synced ${summary.date} — HTTP ${result.httpCode}",
                            )
                        }
                        refreshWeek()
                    }
                    is ApiResult.Unauthorized -> {
                        container.authStore.clearSession()
                        _state.update { it.copy(syncing = false, syncMessage = "Session expired — sign in again", isSignedIn = false) }
                    }
                    is ApiResult.Validation -> {
                        _state.update { it.copy(syncing = false, syncMessage = "Server rejected payload: ${result.detail}") }
                    }
                    is ApiResult.Forbidden -> {
                        _state.update { it.copy(syncing = false, syncMessage = "Forbidden: ${result.detail}") }
                    }
                    is ApiResult.Conflict -> {
                        _state.update { it.copy(syncing = false, syncMessage = "Server conflict") }
                    }
                    is ApiResult.Failure -> {
                        container.authStore.recordSyncError(result.detail)
                        _state.update { it.copy(syncing = false, syncMessage = "Sync failed: ${result.detail}") }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(syncing = false, syncMessage = "Sync error: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** "2h ago" style relative time of the last successful sync. */
    fun formatLastSync(nowMillis: Long = System.currentTimeMillis()): String? {
        val (date, at) = container.authStore.lastSync()
        if (date == null) return null
        val relative = DashboardFormatters.formatRelativeTime(nowMillis, at)
        return "$date ($relative)"
    }

    fun formatSyncLine(nowMillis: Long = System.currentTimeMillis()): String? =
        state.value.syncMessage ?: formatLastSync(nowMillis)?.let { "Last sync: $it" }
}
