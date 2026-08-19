package com.fitnessapp.android.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fitnessapp.android.R
import com.fitnessapp.android.data.model.UserProfile
import com.fitnessapp.android.data.model.UserStats
import com.fitnessapp.android.ui.theme.AccentOrange
import com.fitnessapp.android.ui.theme.FitnessAppTheme
import com.fitnessapp.android.ui.theme.SoftBlue
import com.fitnessapp.android.ui.theme.SoftBlueLight
import com.fitnessapp.android.ui.theme.SuccessGreen
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    onSignOut: () -> Unit = {},
    onSyncNow: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showBioDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    viewModel.uploadAvatar(bytes, mimeType = mimeType)
                }
            } catch (_: Exception) {
                // handle error
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "My Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar with Picker Button
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(SoftBlueLight)
                            .border(3.dp, SoftBlue, CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!state.user?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(state.user?.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            val initial = state.user?.displayName?.firstOrNull()?.uppercaseChar()?.toString()
                                ?: state.user?.email?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = SoftBlue,
                            )
                        }

                        if (state.isUploadingAvatar) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    }

                    // Camera Badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(32.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Change Avatar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Display Name + Edit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.user?.displayName ?: "User",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = { showNameDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit name",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = state.user?.email.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Auth Provider Chip
                val provider = state.user?.authProvider ?: "email"
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (provider == "google") SoftBlueLight else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (provider == "google") {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Google Account",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = SoftBlue,
                            )
                        } else {
                            Text(
                                "Email Account",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // Bio Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "About Me",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { showBioDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.user?.bio.isNullOrBlank()) "Add Bio" else "Edit")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (!state.user?.bio.isNullOrBlank()) {
                    Text(
                        text = state.user?.bio.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = "No bio yet. Tell friends about your fitness goals and daily routines!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Stats Row
        Text(
            text = "Activity Stats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileStatCard(
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                iconColor = SoftBlue,
                label = "Total Steps",
                value = formatStepsNumber(state.stats.totalSteps),
                modifier = Modifier.weight(1f),
            )
            ProfileStatCard(
                icon = Icons.Filled.EmojiEvents,
                iconColor = AccentOrange,
                label = "Won",
                value = state.stats.challengesWon.toString(),
                modifier = Modifier.weight(1f),
            )
            ProfileStatCard(
                icon = Icons.Filled.Flag,
                iconColor = SuccessGreen,
                label = "Challenges",
                value = state.stats.totalChallenges.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        // Account Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Account Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onSyncNow,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync data")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Sign out")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                var showDeleteConfirm by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Account & Data")
                }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete Account?") },
                        text = { Text("Permanently delete your account, step history, challenge records, and friends list? This action is irreversible.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirm = false
                                    viewModel.deleteAccount(onDeleted = onSignOut)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Delete Permanently")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Keep Account")
                            }
                        }
                    )
                }
            }
        }

        // Banner messages
        state.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = SuccessGreen,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        state.error?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    // Edit Bio Dialog
    if (showBioDialog) {
        var bioText by remember { mutableStateOf(state.user?.bio.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text("Edit Bio") },
            text = {
                Column {
                    OutlinedTextField(
                        value = bioText,
                        onValueChange = { if (it.length <= 255) bioText = it },
                        label = { Text("Bio (max 255 chars)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        supportingText = {
                            Text("${bioText.length}/255", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBio(bioText)
                        showBioDialog = false
                    },
                    enabled = !state.isUpdatingBio,
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBioDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Name Dialog
    if (showNameDialog) {
        var nameText by remember { mutableStateOf(state.user?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Edit Display Name") },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { if (it.length <= 100) nameText = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameText.isNotBlank()) {
                            viewModel.updateDisplayName(nameText)
                        }
                        showNameDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileStatCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatStepsNumber(steps: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(steps)
}

@Preview(showBackground = true)
@Composable
fun ProfileStatCardPreview() {
    FitnessAppTheme {
        ProfileStatCard(
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            iconColor = SoftBlue,
            label = "Total Steps",
            value = "125,430",
        )
    }
}
