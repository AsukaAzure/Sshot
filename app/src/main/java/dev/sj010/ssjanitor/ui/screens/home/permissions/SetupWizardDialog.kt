package dev.sj010.ssjanitor.ui.screens.home.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SetupStep(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onGrant: () -> Unit
)

/**
 * Step-by-step setup that walks the user through every permission Sshot needs,
 * deep-linking to the matching system settings page. The step list is rebuilt
 * from live permission state, so steps auto-advance when the user returns from
 * system settings with the permission granted.
 */
@Composable
fun SetupWizardDialog(
    steps: List<SetupStep>,
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grantedCount = steps.count { it.granted }
    val currentStep = steps.firstOrNull { !it.granted }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up Sshot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { if (steps.isEmpty()) 1f else grantedCount.toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentStep == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "All set! Sshot is ready.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "You can change the overlay side and presets in Settings anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Step ${grantedCount + 1} of ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentStep.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentStep.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (currentStep == null) {
                Button(onClick = onFinish) { Text("Done") }
            } else {
                Button(onClick = currentStep.onGrant) { Text("Grant") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (currentStep == null) onFinish() else onDismiss() }) {
                Text(if (currentStep == null) "Finish" else "Later")
            }
        },
        modifier = modifier
    )
}
