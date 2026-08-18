package com.fitnessapp.android.ui.hr

import android.app.Application
import androidx.health.connect.client.records.HeartRateRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.HcStatus
import com.fitnessapp.android.data.HeartRateSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

data class HrTestUiState(
    val hcStatus: HcStatus = HcStatus.UNKNOWN,
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val summary: HeartRateSummary? = null,
    val records: List<HeartRateRecord> = emptyList(),
    val error: String? = null,
)

class HrTestViewModel(app: Application) : AndroidViewModel(app) {

    private val container get() = (getApplication<FitnessApp>()).container

    private val _state = MutableStateFlow(HrTestUiState())
    val state: StateFlow<HrTestUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val repo = container.healthRepository
            val status = repo.status()
            val missing = repo.missingReadPermissions()
            val hasPermission = "android.permission.health.READ_HEART_RATE" !in missing

            _state.update {
                it.copy(
                    hcStatus = status,
                    hasPermission = hasPermission,
                    loading = true,
                    error = null,
                )
            }

            if (status != HcStatus.AVAILABLE || !hasPermission) {
                _state.update { it.copy(loading = false) }
                return@launch
            }

            val end = Instant.now()
            val start = end.minus(24, ChronoUnit.HOURS)

            val summary = repo.readHeartRateSummary(start, end)
            val records = repo.readHeartRateRecords(start, end)

            _state.update {
                it.copy(
                    loading = false,
                    summary = summary,
                    records = records.sortedByDescending { r -> r.startTime },
                )
            }
        }
    }
}
