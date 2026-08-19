package com.fitnessapp.android.ui.challenges

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

data class ProportionalPreset(
    val icon: String,
    val title: String,
    val metric: String,
    val goalText: String,
)

val PROPORTIONAL_PRESETS = listOf(
    ProportionalPreset("🏃", "5km Daily Runner", "distance_km", "5.0 km / day"),
    ProportionalPreset("👟", "10k Step Master", "steps", "10,000 steps / day"),
    ProportionalPreset("🔥", "600 kcal Burner", "calories_kcal", "600 kcal / day"),
    ProportionalPreset("🌅", "Morning 3k Walk", "steps", "3,000 steps"),
    ProportionalPreset("🏔️", "25km Ultra Week", "distance_km", "25.0 km total"),
    ProportionalPreset("⚡", "500 kcal Streak", "calories_kcal", "500 kcal streak"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChallengeScreen(
    state: ChallengesUiState,
    onTitleChange: (String) -> Unit,
    onMetricChange: (String) -> Unit,
    onStartChange: (LocalDateTime) -> Unit,
    onEndChange: (LocalDateTime) -> Unit,
    onInviteOnlyChange: (Boolean) -> Unit,
    onMaxParticipantsChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Challenge", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onSubmit,
                        enabled = state.createTitle.isNotBlank() && !state.createBusy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        if (state.createBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create Challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Section 1: Presets Grid (2 Columns, Proportional)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quick Preset Templates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                // 2-Column Proportional Grid (3 Rows)
                for (rowIdx in 0..2) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        for (colIdx in 0..1) {
                            val presetIdx = rowIdx * 2 + colIdx
                            if (presetIdx < PROPORTIONAL_PRESETS.size) {
                                val preset = PROPORTIONAL_PRESETS[presetIdx]
                                val isSelected = state.createTitle == preset.title && state.createMetric == preset.metric

                                Card(
                                    onClick = {
                                        onTitleChange(preset.title)
                                        onMetricChange(preset.metric)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(preset.icon, style = MaterialTheme.typography.titleSmall)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                preset.title,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                preset.goalText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Custom Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Challenge Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = state.createTitle,
                        onValueChange = onTitleChange,
                        label = { Text("Challenge Title") },
                        placeholder = { Text("e.g. 5km Daily Runner") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Goal Metric", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    ) {
                        listOf(
                            "steps" to "Steps 👟",
                            "distance_km" to "Distance 📍 (km)",
                            "calories_kcal" to "Calories 🔥 (kcal)",
                            "sleep_seconds" to "Sleep 😴",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = state.createMetric == key,
                                onClick = { onMetricChange(key) },
                                label = { Text(label, fontWeight = FontWeight.Medium) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }

                    // Proportional Date Pickers
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScreenDateTimeField(
                            label = "Starts",
                            value = state.createStart,
                            onChange = onStartChange,
                            modifier = Modifier.weight(1f),
                        )
                        ScreenDateTimeField(
                            label = "Ends",
                            value = state.createEnd,
                            onChange = onEndChange,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Invite Only Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Invite Only", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Requires join code to enter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.createInviteOnly,
                            onCheckedChange = onInviteOnlyChange,
                        )
                    }

                    OutlinedTextField(
                        value = state.createMaxParticipants,
                        onValueChange = onMaxParticipantsChange,
                        label = { Text("Max Participants (Optional)") },
                        placeholder = { Text("Unlimited if blank") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.createError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenDateTimeField(
    label: String,
    value: LocalDateTime?,
    onChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentVal = value ?: LocalDateTime.now()
    val context = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("MMM dd, HH:mm")

    Card(
        onClick = {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    TimePickerDialog(
                        context,
                        { _, h, min ->
                            onChange(LocalDateTime.of(y, m + 1, d, h, min))
                        },
                        currentVal.hour,
                        currentVal.minute,
                        true,
                    ).show()
                },
                currentVal.year,
                currentVal.monthValue - 1,
                currentVal.dayOfMonth,
            ).show()
        },
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(currentVal.format(fmt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
