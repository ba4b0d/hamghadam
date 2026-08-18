package com.fitnessapp.android.ui.challenges

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.android.data.model.Challenge
import com.fitnessapp.android.data.model.ChallengeFormatters
import com.fitnessapp.android.data.model.InviteInfo
import com.fitnessapp.android.data.model.LeaderboardEntry
import com.fitnessapp.android.data.model.UserPublicProfile
import com.fitnessapp.android.ui.profile.PublicProfileDialog
import com.fitnessapp.android.ui.theme.AccentOrange
import com.fitnessapp.android.ui.theme.SoftBlue
import com.fitnessapp.android.ui.theme.SoftBlueLight
import com.fitnessapp.android.ui.theme.SuccessGreen

/** Challenge detail entry: wires the ViewModel to the stateless content. */
@Composable
fun ChallengeDetailScreen(
    challengeId: Long,
    autoJoinCode: String? = null,
    openLeaderboard: Boolean = false,
    viewModel: ChallengeDetailViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(challengeId) {
        viewModel.load(challengeId, autoJoinCode?.takeIf { it.isNotBlank() })
    }
    LaunchedEffect(openLeaderboard) {
        if (openLeaderboard) viewModel.refreshLeaderboard()
    }

    ChallengeDetailContent(
        state = state,
        challengeId = challengeId,
        onBack = onBack,
        onRetry = { viewModel.load(challengeId) },
        onJoin = { viewModel.join() },
        onOpenJoinDialog = { viewModel.openJoinDialog() },
        onJoinCodeChange = viewModel::onJoinCode,
        onSubmitJoinDialog = { viewModel.submitJoinDialog() },
        onDismissJoinDialog = { viewModel.closeJoinDialog() },
        onLeave = { viewModel.leave() },
        onInvite = { viewModel.createInvite() },
        onDismissInvite = { viewModel.dismissInvite() },
        onStartNow = { viewModel.setStatus("active") },
        onEndNow = { viewModel.setStatus("ended") },
        onRefreshLeaderboard = { viewModel.refreshAll() },
        onShareInvite = { invite -> shareInvite(context, invite) },
        onCopyInvite = { invite -> copyInvite(context, invite) },
        onClearNotice = { viewModel.clearNotice() },
    )
}

private fun shareInvite(context: Context, invite: InviteInfo) {
    val text = "Join my challenge! Invite code: ${invite.code}\n${invite.deepLink}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Invite friends"))
}

private fun copyInvite(context: Context, invite: InviteInfo) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("challenge_invite", invite.deepLink))
}

/**
 * Stateless challenge detail — testable in Compose UI tests without the
 * ViewModel/DI container.
 */
@Composable
fun ChallengeDetailContent(
    state: ChallengeDetailUiState,
    challengeId: Long,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onJoin: () -> Unit,
    onOpenJoinDialog: () -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onSubmitJoinDialog: () -> Unit,
    onDismissJoinDialog: () -> Unit,
    onLeave: () -> Unit,
    onInvite: () -> Unit,
    onDismissInvite: () -> Unit,
    onStartNow: () -> Unit,
    onEndNow: () -> Unit,
    onRefreshLeaderboard: () -> Unit,
    onShareInvite: (InviteInfo) -> Unit,
    onCopyInvite: (InviteInfo) -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedUserProfile by remember { mutableStateOf<UserPublicProfile?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Challenge",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        state.notice?.let {
            NoticeBanner(it, onClearNotice)
        }

        when {
            state.loading -> CenteredLoading()
            state.error != null && state.challenge == null -> ErrorCard(state.error!!, onRetry, challengeId)
            state.challenge == null -> CenteredLoading()
            else -> {
                val challenge = state.challenge!!
                HeaderCard(challenge, state.isCreator)
                if (!state.isParticipant && !challenge.isEnded) {
                    ActionsCard(
                        state = state,
                        challenge = challenge,
                        onJoin = onJoin,
                        onOpenJoinDialog = onOpenJoinDialog,
                        onLeave = onLeave,
                        onInvite = onInvite,
                        onStartNow = onStartNow,
                        onEndNow = onEndNow,
                    )
                } else if (state.isParticipant) {
                    ActionsCard(
                        state = state,
                        challenge = challenge,
                        onJoin = onJoin,
                        onOpenJoinDialog = onOpenJoinDialog,
                        onLeave = onLeave,
                        onInvite = onInvite,
                        onStartNow = onStartNow,
                        onEndNow = onEndNow,
                    )
                }
                LeaderboardCard(
                    state = state,
                    challenge = challenge,
                    onRefresh = onRefreshLeaderboard,
                    onUserClick = { entry ->
                        selectedUserProfile = UserPublicProfile(
                            id = entry.userId,
                            displayName = entry.displayName,
                        )
                    },
                )
                ParticipantsCard(challenge)
            }
        }
    }

    // Public Profile Dialog for Leaderboard users
    selectedUserProfile?.let { user ->
        PublicProfileDialog(
            user = user,
            onDismiss = { selectedUserProfile = null },
        )
    }

    // Join-with-code dialog (invite-only challenges)
    if (state.joinDialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissJoinDialog,
            title = { Text("Enter invite code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This challenge is invite-only. Ask the creator for the 8-character code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.joinCode,
                        onValueChange = onJoinCodeChange,
                        label = { Text("Invite code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.joinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.joinBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Joining…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onSubmitJoinDialog, enabled = !state.joinBusy) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = onDismissJoinDialog, enabled = !state.joinBusy) { Text("Cancel") }
            },
        )
    }

    // Invite share sheet preview
    state.invite?.let { invite ->
        InviteDialog(
            invite = invite,
            onShare = { onShareInvite(invite) },
            onCopy = { onCopyInvite(invite) },
            onDismiss = onDismissInvite,
        )
    }
}

@Composable
private fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SoftBlueLight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun HeaderCard(challenge: Challenge, isCreator: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SoftBlueLight),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftBlue.copy(alpha = 0.14f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SoftBlue, modifier = Modifier.padding(11.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        challenge.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SoftBlue,
                    )
                    Text(
                        "${challenge.metricLabel} challenge" + if (challenge.inviteOnly) " · invite only" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChipPublic(challenge.status)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                ChallengeFormatters.formatWindow(challenge.startsAt, challenge.endsAt),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                ChallengeFormatters.statusDescription(challenge.status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Created by ${challenge.creator.displayName ?: "User ${challenge.creator.id}"}" +
                    if (isCreator) " (you)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChipPublic(status: String) {
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
private fun ActionsCard(
    state: ChallengeDetailUiState,
    challenge: Challenge,
    onJoin: () -> Unit,
    onOpenJoinDialog: () -> Unit,
    onLeave: () -> Unit,
    onInvite: () -> Unit,
    onStartNow: () -> Unit,
    onEndNow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                !state.isParticipant && !challenge.isEnded -> {
                    if (challenge.inviteOnly) {
                        OutlinedButton(onClick = onOpenJoinDialog, enabled = !state.joinBusy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Join with invite code")
                        }
                    } else {
                        Button(onClick = onJoin, enabled = !state.joinBusy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Join challenge")
                        }
                    }
                    state.joinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.isParticipant && !challenge.isEnded -> {
                    Button(onClick = onInvite, enabled = !state.inviteBusy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Invite friends")
                    }
                    if (state.isCreator) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (challenge.isDraft) {
                                Button(onClick = onStartNow, enabled = !state.statusBusy, modifier = Modifier.weight(1f)) {
                                    Text("Start now")
                                }
                            } else if (challenge.isActive) {
                                OutlinedButton(onClick = onEndNow, enabled = !state.statusBusy, modifier = Modifier.weight(1f)) {
                                    Text("End now")
                                }
                            }
                        }
                    } else {
                        OutlinedButton(onClick = onLeave, enabled = !state.joinBusy, modifier = Modifier.fillMaxWidth()) {
                            Text("Leave challenge")
                        }
                    }
                    state.joinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                challenge.isEnded -> {
                    Text(
                        "This challenge has ended.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(
    state: ChallengeDetailUiState,
    challenge: Challenge,
    onRefresh: () -> Unit,
    onUserClick: (LeaderboardEntry) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Leaderboard", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (state.leaderboardLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            }
            val board = state.leaderboard
            when {
                board == null -> Text(
                    "Leaderboard unavailable right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                board.entries.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(
                        "No scores yet.",
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        "As of ${board.asOf} — scores appear once participants sync steps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                else -> {
                    Text(
                        "As of ${board.asOf} · ${ChallengeFormatters.formatTotal(board.metric, board.entries.firstOrNull()?.total ?: 0.0)} to lead",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    board.entries.forEach { entry ->
                        LeaderboardRow(
                            entry = entry,
                            metric = challenge.metric,
                            onClick = { onUserClick(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardRow(
    entry: LeaderboardEntry,
    metric: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val medalColor = when (entry.rank) {
        1 -> AccentOrange
        2 -> Color(0xFF9AA0A6)
        3 -> Color(0xFFB07A3A)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 6.dp)
            .then(
                if (entry.isMe) Modifier
                    .background(SoftBlueLight, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                else Modifier.padding(horizontal = 8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = medalColor, modifier = Modifier.size(28.dp)) {
            Text(
                text = entry.rank.toString(),
                color = if (entry.rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = (entry.displayName ?: "User ${entry.userId}") + if (entry.isMe) " (you)" else "",
                fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (entry.daily.isNotEmpty()) {
                Text(
                    text = "Daily: ${entry.daily.joinToString(" · ") { ChallengeFormatters.formatSteps(it.value) }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = ChallengeFormatters.formatTotal(metric, entry.total),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = if (entry.rank == 1) AccentOrange else SoftBlue,
        )
    }
}

@Composable
private fun ParticipantsCard(challenge: Challenge) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Participants (${challenge.participants.size}" +
                    (challenge.maxParticipants?.let { " / $it" } ?: "") + ")",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            challenge.participants.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (p.displayName ?: "User ${p.userId}") + if (p.isCreator) " (creator)" else "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        ChallengeFormatters.formatTotal(challenge.metric, p.total),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteDialog(
    invite: InviteInfo,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite friends") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Share this code or link — anyone with it can join while it's valid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftBlueLight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            invite.code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SoftBlue,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ChallengeFormatters.formatExpiry(invite.expiresAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    invite.deepLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy link")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun CenteredLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, challengeId: Long) {
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
            Text("Couldn't load challenge #$challengeId", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}