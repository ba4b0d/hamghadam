package com.fitnessapp.android.permissions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.fitnessapp.android.ui.theme.FitnessAppTheme

/**
 * Health Connect permissions rationale screen (ACTION_SHOW_PERMISSIONS_RATIONALE).
 *
 * The Health Connect controller refuses to show its consent UI unless the
 * requesting app declares and supports this intent: it launches this activity
 * first; when the user taps Continue we re-launch the permission request so the
 * controller's own consent screen appears.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessAppTheme {
                RationaleContent(
                    onContinue = {
                        permissionLauncher.launch(
                            setOf(
                                HealthPermission.getReadPermission(StepsRecord::class),
                                HealthPermission.getReadPermission(SleepSessionRecord::class),
                                HealthPermission.getReadPermission(HeartRateRecord::class),
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RationaleContent(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Why we need your health data",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Reads your daily step count, sleep duration, and heart rate data from Health Connect " +
                "so you can view them on your health dashboard and — with your consent — participate in " +
                "friendly step, sleep, and heart rate challenges with friends.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Requested read categories: Steps (READ_STEPS), Sleep (READ_SLEEP), and Heart Rate (READ_HEART_RATE). " +
                "Data is used solely to power your in-app health dashboard and challenge leaderboards.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "You control access at any time: open Health Connect → Permissions and " +
                "revoke any category. Data is used strictly for the fitness features you see in this app; " +
                "it is never sold or shared with advertisers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}
