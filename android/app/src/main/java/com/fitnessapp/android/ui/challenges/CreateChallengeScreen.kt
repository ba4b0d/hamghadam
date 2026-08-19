package com.fitnessapp.android.ui.challenges

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
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
import androidx.compose.material3.OutlinedButton
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

data class RealisticPreset(
    val icon: String,
    val title: String,
    val metric: String,
    val description: String,
)

val REALISTIC_PRESETS = listOf(
    RealisticPreset("🏃", "5km Daily Runner", "distance_km", "Complete 5 kilometers daily"),
    RealisticPreset("👟", "10,000 Step Master", "steps", "Hit the gold standard 10k daily steps"),
    RealisticPreset("🔥", "600 kcal Fat Burner", "calories_kcal", "Burn 600 active calories"),
    RealisticPreset("🌅", "Early Bird Morning Walk", "steps", "Start your morning with 3,000 steps"),
    RealisticPreset("🏔️", "Weekly 25km Ultra", "distance_km", "Tackle 25km distance over 7 days"),
    RealisticPreset("⚡", "Daily 500 kcal Streak", "calories_kcal", "Keep a 500 kcal daily active burn"),
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
                title = { Text("New Challenge", fontWeight = FontWeight.Bold) },
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
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onSubmit,
                        enabled = state.createTitle.isNotBlank() && !state.createBusy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (state.createBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create Challenge 🚀", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Realistic Presets Section
            Text("Realistic Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                REALISTIC_PRESETS.forEach { preset ->
                    val isSelected = state.createTitle == preset.title && state.createMetric == preset.metric
                    Card(
                        onClick = {
                            onTitleChange(preset.title)
                            onMetricChange(preset.metric)
                        },
                        modifier = Modifier.width(200.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(preset.icon, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                preset.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                preset.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Challenge Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Challenge Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = state.createTitle,
                        onValueChange = onTitleChange,
                        label = { Text("Challenge Title") },
                        placeholder = { Text("e.g. 5km Daily Runner") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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
                                label = { Text(label) },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Invite Only", fontWeight = FontWeight.SemiBold)
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
                        shape = RoundedCornerShape(12.dp),
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
        shape = RoundedCornerShape(12.dp),
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
