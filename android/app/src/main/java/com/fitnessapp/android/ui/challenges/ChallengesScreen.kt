package com.fitnessapp.android.ui.challenges

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.android.data.model.Challenge
import com.fitnessapp.android.data.model.ChallengeFormatters
import com.fitnessapp.android.ui.theme.AccentOrange
import com.fitnessapp.android.ui.theme.SoftBlue
import com.fitnessapp.android.ui.theme.SoftBlueLight
import com.fitnessapp.android.ui.theme.SuccessGreen
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

/** Challenges tab entry: wires the ViewModel to the stateless content. */
@Composable
fun ChallengesScreen(
    viewModel: ChallengesViewModel = viewModel(),
    onOpenChallenge: (Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val navigate by viewModel.navigateToChallenge.collectAsState()
    val context = LocalContext.current

    // Android 13+: ask for notification permission once so challenge pushes
    // (FCM challenge_started/ended/beat_you) can show when the app is backgrounded.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result is non-blocking for in-app routing */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(navigate) {
        navigate?.let {
            onOpenChallenge(it)
            viewModel.consumeNavigation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChallengesContent(
            state = state,
            myUserId = state.myUserId,
            onRetry = { viewModel.refresh() },
            onCreate = { viewModel.openCreate() },
            onJoinLink = { viewModel.openJoin() },
            onOpenChallenge = onOpenChallenge,
            onSignInHint = {},
        )

        if (state.isSignedIn) {
            FloatingActionButton(
                onClick = { viewModel.openCreate() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = AccentOrange,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create challenge")
            }
        }
    }

    if (state.createOpen) {
        CreateChallengeDialog(
            state = state,
            onTitleChange = viewModel::onCreateTitle,
            onMetricChange = viewModel::onCreateMetric,
            onStartChange = viewModel::onCreateStart,
            onEndChange = viewModel::onCreateEnd,
            onInviteOnlyChange = viewModel::onCreateInviteOnly,
            onMaxParticipantsChange = viewModel::onCreateMaxParticipants,
            onSubmit = { viewModel.submitCreate() },
            onDismiss = { viewModel.closeCreate() },
        )
    }

    if (state.joinOpen) {
        JoinLinkDialog(
            input = state.joinInput,
            busy = state.joinBusy,
            error = state.joinError,
            onInputChange = viewModel::onJoinInput,
            onSubmit = { viewModel.submitJoin() },
            onDismiss = { viewModel.closeJoin() },
        )
    }
}

/**
 * Stateless challenges list — testable in Compose UI tests without the
 * ViewModel/DI container.
 */
@Composable
fun ChallengesContent(
    state: ChallengesUiState,
    myUserId: Int?,
    onRetry: () -> Unit,
    onCreate: () -> Unit,
    onJoinLink: () -> Unit,
    onOpenChallenge: (Long) -> Unit,
    onSignInHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Challenges",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onJoinLink) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Join")
            }
        }

        when {
            !state.isSignedIn -> SignInCard(onSignInHint)
            state.loading -> CenteredLoading()
            state.error != null -> ErrorCard(state.error!!, onRetry)
            !state.hasAny -> EmptyCard(onCreate)
            else -> {
                if (state.active.isNotEmpty()) ChallengeSection("Active", state.active, myUserId, onOpenChallenge)
                if (state.upcoming.isNotEmpty()) ChallengeSection("Upcoming", state.upcoming, myUserId, onOpenChallenge)
                if (state.ended.isNotEmpty()) ChallengeSection("Ended", state.ended, myUserId, onOpenChallenge)
            }
        }
    }
}

@Composable
private fun ChallengeSection(
    title: String,
    challenges: List<Challenge>,
    myUserId: Int?,
    onOpen: (Long) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    challenges.forEach { challenge ->
        ChallengeCard(challenge, myUserId, onOpen)
    }
}

@Composable
fun ChallengeCard(
    challenge: Challenge,
    myUserId: Int?,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(challenge.id.toLong()) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (challenge.isActive) AccentOrange.copy(alpha = 0.14f) else SoftBlueLight,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = if (challenge.isActive) AccentOrange else SoftBlue,
                    modifier = Modifier.padding(11.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (challenge.inviteOnly) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Lock, contentDescription = "Invite only", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${challenge.metricLabel} · ${ChallengeFormatters.formatWindowShort(challenge.startsAt, challenge.endsAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(challenge.status)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${challenge.participants.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val mine = myUserId?.let { uid -> challenge.participants.firstOrNull { it.userId == uid } }
                    if (mine != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = ChallengeFormatters.formatTotal(challenge.metric, mine.total),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, bg, fg) = when (status) {
        "active" -> Triple("Active", AccentOrange.copy(alpha = 0.15f), AccentOrange)
        "ended" -> Triple("Ended", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple("Upcoming", SoftBlueLight, SoftBlue)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun SignInCard(onSignInHint: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftBlueLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SoftBlue, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("Sign in to see your challenges", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Create fitness challenges, invite friends with a code, and climb the leaderboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyCard(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("No challenges yet", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Create your first step, sleep, or heart rate challenge, or join one with an invite link.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Create a challenge")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("Couldn't load challenges", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun CenteredLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .testTag("challenges_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------------
// Create challenge dialog
// ---------------------------------------------------------------------------

@Composable
fun CreateChallengeDialog(
    state: ChallengesUiState,
    onTitleChange: (String) -> Unit,
    onMetricChange: (String) -> Unit,
    onStartChange: (LocalDateTime) -> Unit,
    onEndChange: (LocalDateTime) -> Unit,
    onInviteOnlyChange: (Boolean) -> Unit,
    onMaxParticipantsChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New challenge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.createTitle,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    placeholder = { Text("e.g. 10k steps weekend") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Challenge Metric", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(
                        "steps" to "Steps",
                        "sleep_seconds" to "Sleep",
                        "avg_hr" to "Heart Rate",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = state.createMetric == key,
                            onClick = { onMetricChange(key) },
                            label = { Text(label) },
                        )
                    }
                }

                DateTimeField(
                    label = "Start",
                    value = state.createStart,
                    onChange = onStartChange,
                )
                DateTimeField(
                    label = "End",
                    value = state.createEnd,
                    onChange = onEndChange,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Invite only", fontWeight = FontWeight.Medium)
                        Text(
                            "Friends need a join code to enter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.createInviteOnly, onCheckedChange = onInviteOnlyChange)
                }
                OutlinedTextField(
                    value = state.createMaxParticipants,
                    onValueChange = onMaxParticipantsChange,
                    label = { Text("Max participants (optional)") },
                    placeholder = { Text("unlimited") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.createError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.createBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Creating…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !state.createBusy) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.createBusy) { Text("Cancel") }
        },
    )
}

/**
 * Read-only date+time field: tapping opens the platform DatePicker then
 * TimePicker. Pure UI helper (dates chosen in device-local time, converted
 * to UTC on submit).
 */
@Composable
fun DateTimeField(
    label: String,
    value: LocalDateTime?,
    onChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val display by remember(value) {
        mutableStateOf(
            value?.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")) ?: ""
        )
    }
    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Pick date & time") },
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val cal = Calendar.getInstance()
                value?.let {
                    cal.set(it.year, it.monthValue - 1, it.dayOfMonth, it.hour, it.minute)
                }
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        val base = value ?: LocalDateTime.now().withSecond(0).withNano(0)
                        val dateTime = LocalDateTime.of(y, m + 1, d, base.hour, base.minute)
                        TimePickerDialog(
                            context,
                            { _, h, min -> onChange(LocalDateTime.of(y, m + 1, d, h, min)) },
                            dateTime.hour,
                            dateTime.minute,
                            true,
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
    )
}

// ---------------------------------------------------------------------------
// Join-by-invite-link dialog
// ---------------------------------------------------------------------------

@Composable
fun JoinLinkDialog(
    input: String,
    busy: Boolean,
    error: String?,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a challenge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste the invite link you received. It looks like:\n" +
                        "fitnessapp://challenges/2/join?code=R7NRX322",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("Invite link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Joining…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !busy) { Text("Join") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
