package com.fitnessapp.android.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.android.R
import com.fitnessapp.android.data.HcStatus
import com.fitnessapp.android.data.HealthConnectRepository
import com.fitnessapp.android.data.model.DailySummary
import com.fitnessapp.android.data.model.DashboardFormatters
import com.fitnessapp.android.ui.theme.AccentOrange
import com.fitnessapp.android.ui.theme.SoftBlue
import com.fitnessapp.android.ui.theme.SoftBlueLight
import com.fitnessapp.android.ui.theme.SuccessGreen
import java.time.LocalDate

/**
 * Dashboard tab: HC status, permission state, connect-watch onboarding, today's
 * summary with source attribution, last-7-days steps chart, sync.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onOpenHrTest: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    DashboardContent(
        state = state,
        today = viewModel.today(),
        onGrant = { permissionLauncher.launch(state.missingPermissions) },
        onConnect = { viewModel.openMatchmaking(context) },
        onRefresh = { viewModel.refreshAll() },
        onSyncNow = { viewModel.syncNow() },
        onOpenHrTest = onOpenHrTest,
        syncLine = viewModel.formatSyncLine(),
    )
}

/**
 * Stateless dashboard content — testable in Compose UI tests without the
 * ViewModel/DI container. [syncLine] is pre-formatted by the caller.
 */
@Composable
fun DashboardContent(
    state: DashboardUiState,
    today: LocalDate,
    onGrant: () -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenHrTest: () -> Unit = {},
    syncLine: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        when (state.hcStatus) {
            HcStatus.AVAILABLE -> {
                if (state.missingPermissions.isNotEmpty()) {
                    PermissionCard(state, onGrant)
                }
                val hasAnyData = !state.summary?.sourceApps.isNullOrEmpty() &&
                    (state.summary?.steps ?: 0L) > 0
                if (hasAnyData) {
                    DailySummaryCard(state, today, onRefresh, onSyncNow, onOpenHrTest)
                    WeekStripCard(state.week, today, state.weekLoading)
                    ConnectWatchCard(
                        possible = state.matchmakingPossible,
                        hasSources = true,
                        onConnect = onConnect,
                    )
                } else {
                    EmptyStateCard(
                        possible = state.matchmakingPossible,
                        permissionsGranted = state.missingPermissions.isEmpty(),
                        onConnect = onConnect,
                        onGrant = onGrant,
                    )
                }
            }
            HcStatus.UNAVAILABLE -> UnavailableCard("Health Connect is not installed on this device.")
            HcStatus.PROVIDER_UPDATE_REQUIRED -> UnavailableCard("Health Connect needs an update. Open the Play Store to update it.")
            HcStatus.UNKNOWN -> UnavailableCard("Could not determine Health Connect availability.")
        }
    }
}

@Composable
private fun PermissionCard(
    state: DashboardUiState,
    onGrant: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Data access", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Steps — powers your step challenges" to "android.permission.health.READ_STEPS",
                "Sleep — powers sleep challenges" to "android.permission.health.READ_SLEEP",
                "Heart rate — powers HR test screen & challenges" to "android.permission.health.READ_HEART_RATE",
            ).forEach { (label, perm) ->
                PermissionRow(label, perm in state.grantedPermissions)
            }
            Spacer(Modifier.height(8.dp))
            if (state.missingPermissions.isNotEmpty()) {
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant access in Health Connect")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("All permissions granted", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.hasBackgroundPermission)
                    "Background sync: enabled"
                else
                    "Background sync: off — daily sync runs when the app is open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            label.startsWith("Steps") -> Icons.AutoMirrored.Filled.DirectionsRun
            label.startsWith("Sleep") -> Icons.Filled.Bedtime
            else -> Icons.Filled.Favorite
        }
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "Granted" else "Not granted",
            color = if (granted) SuccessGreen else AccentOrange,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Hero empty/offboarding state: nothing connected yet → Matchmaking CTA. */
@Composable
private fun EmptyStateCard(
    possible: Boolean,
    permissionsGranted: Boolean,
    onConnect: () -> Unit,
    onGrant: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SoftBlueLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_hamghadam_mark),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Connect your watch",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SoftBlue,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Link Samsung Health, Google Fit, a Wear OS watch or other apps so their steps flow " +
                    "into Health Connect. Your 7-day summary will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            when {
                possible -> {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SoftBlue),
                    ) {
                        Icon(Icons.Filled.Watch, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect now")
                    }
                }
                permissionsGranted -> {
                    Text(
                        "No data yet — grant access above, then refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        "Matchmaking is not available on this device — grant access below to start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!permissionsGranted) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant data access first")
                }
            }
        }
    }
}

@Composable
private fun ConnectWatchCard(possible: Boolean, hasSources: Boolean, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftBlueLight),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Watch, contentDescription = null, tint = SoftBlue)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Sources", fontWeight = FontWeight.SemiBold, color = SoftBlue)
                Text(
                    "Manage or add apps in Health Connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (possible) {
                OutlinedButton(onClick = onConnect) { Text("Manage") }
            }
        }
    }
}

@Composable
private fun DailySummaryCard(
    state: DashboardUiState,
    today: LocalDate,
    onRefresh: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenHrTest: () -> Unit = {},
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (state.reading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(12.dp))

            val summary = state.summary
            if (summary == null) {
                Text(
                    "No data yet. Grant access and refresh.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Steps hero (primary metric)
                Text(
                    text = DashboardFormatters.formatCount(summary.steps),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoftBlue,
                )
                Text(
                    text = DashboardFormatters.stepsHeroSub(summary.steps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricMiniCard(
                        label = "Sleep",
                        value = summary.sleepSeconds?.let { DashboardFormatters.formatDuration(it) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    MetricMiniCard(
                        label = "Avg HR (Test)",
                        value = summary.avgHr?.let { "%.0f bpm".format(it) } ?: "—",
                        onClick = onOpenHrTest,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Source attribution
                if (summary.sourceApps.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Watch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = DashboardFormatters.sourceAttribution(summary.sourceApps),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                    Button(onClick = onSyncNow, modifier = Modifier.weight(1f)) {
                        if (state.syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Sync now")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onClick != null) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Test HR",
                        tint = AccentOrange,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun WeekStripCard(
    week: List<DailySummary?>,
    today: LocalDate,
    loading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 7 days", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            val totals = DashboardFormatters.weekTotals(week)
            Spacer(Modifier.height(4.dp))
            if (totals.hasData) {
                Text(
                    "Total ${DashboardFormatters.formatCount(totals.totalSteps)} steps · best ${DashboardFormatters.formatCount(totals.bestDaySteps)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "No data for the last 7 days yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            WeekStepsChart(week, today)
            Spacer(Modifier.height(12.dp))
            WeekLegend()
        }
    }
}

@Composable
private fun SyncStatusLine(syncLine: String?) {
    if (syncLine != null) {
        val failed = syncLine.startsWith("Sync failed") ||
            syncLine.startsWith("Session expired") ||
            syncLine.startsWith("Sync error") ||
            syncLine.startsWith("Server rejected")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(
                Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (failed) MaterialTheme.colorScheme.error else SuccessGreen,
            )
            Spacer(Modifier.width(8.dp))
            Text(syncLine, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UnavailableCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(text)
        }
    }
}
