package com.gemmaworkflow.ui.home

import android.nfc.NfcAdapter
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.platform.nfc.NfcTriggerWriter

/**
 * Screen for writing a workflow deep-link to an NFC tag.
 *
 * Flow:
 *  1. User picks a saved workflow from the list.
 *  2. User taps "Write to Tag" — the Activity enables NFC foreground dispatch.
 *  3. User taps the NFC tag against the phone.
 *  4. [onTagApproaching] is called with the tag — the parent writes the NDEF record.
 *  5. Screen shows success or error feedback.
 *
 * @param savedWorkflows   List of workflows available to write to a tag.
 * @param nfcStatus        One of: "unavailable", "disabled", or "ready".
 * @param writeState       Current state of the write operation.
 * @param onWorkflowSelected Called with the workflow name when the user picks one.
 * @param onWriteRequested  Called when the user taps "Write to Tag" — caller should
 *                          enable NFC foreground dispatch.
 * @param onTagWritten     Called when the tag was successfully written.
 * @param onTagWriteError  Called when tag writing failed with the error message.
 * @param onCancel         Cancel and return to the previous screen.
 */
@Composable
fun NfcTriggerSetupScreen(
    savedWorkflows: List<PlannedWorkflow>,
    nfcStatus: NfcStatus,
    writeState: NfcWriteState,
    onWorkflowSelected: (String) -> Unit,
    onWriteRequested: () -> Unit,
    onTagWritten: () -> Unit,
    onTagWriteError: (String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedWorkflow by mutableStateOf<String?>(null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("NFC Tag Setup", style = MaterialTheme.typography.headlineSmall)

        // NFC readiness card
        NfcStatusCard(status = nfcStatus)

        if (savedWorkflows.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "No saved workflows. Generate and save a workflow first.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
            return@Column
        }

        // Workflow picker
        Text("Select Workflow", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(savedWorkflows, key = { it.name }) { workflow ->
                WorkflowNfcRow(
                    workflow = workflow,
                    isSelected = selectedWorkflow == workflow.name,
                    onClick = {
                        selectedWorkflow = workflow.name
                        onWorkflowSelected(workflow.name)
                    }
                )
            }
        }

        // Write state feedback
        when (writeState) {
            is NfcWriteState.Idle -> {
                if (selectedWorkflow != null && nfcStatus == NfcStatus.Ready) {
                    Button(
                        onClick = onWriteRequested,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedWorkflow != null
                    ) {
                        Icon(
                            Icons.Filled.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("  Write to NFC Tag")
                    }
                }
            }
            is NfcWriteState.AwaitingTag -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Nfc,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                "Hold tag near phone",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Tap \"${writeState.workflowName}\" against the NFC reader",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            is NfcWriteState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("\u2713", style = MaterialTheme.typography.headlineMedium)
                        Column {
                            Text("Tag written!", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Tap it any time to run \"${writeState.workflowName}\"",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Button(
                    onClick = onTagWritten,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
            is NfcWriteState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("\u2717", style = MaterialTheme.typography.headlineMedium)
                        Column {
                            Text("Write failed", style = MaterialTheme.typography.titleSmall)
                            Text(
                                writeState.message,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Button(
                    onClick = onTagWritten,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try Again")
                }
            }
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun NfcStatusCard(status: NfcStatus) {
    val (icon, color, text) = when (status) {
        NfcStatus.Unavailable -> Triple(
            "\u26A0\uFE0F",
            MaterialTheme.colorScheme.error,
            "This device does not have NFC hardware."
        )
        NfcStatus.Disabled -> Triple(
            "\u26A0\uFE0F",
            MaterialTheme.colorScheme.error,
            "NFC is turned off. Enable it in Settings."
        )
        NfcStatus.Ready -> Triple(
            "\u2713",
            MaterialTheme.colorScheme.primary,
            "NFC is ready."
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

@Composable
private fun WorkflowNfcRow(
    workflow: PlannedWorkflow,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workflow.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (workflow.summary.isNotBlank()) {
                    Text(
                        workflow.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** NFC hardware/readiness status on this device. */
sealed class NfcStatus {
    data object Unavailable : NfcStatus()
    data object Disabled : NfcStatus()
    data object Ready : NfcStatus()
}

/** Current state of the NFC tag write operation. */
sealed class NfcWriteState {
    data object Idle : NfcWriteState()
    data class AwaitingTag(val workflowName: String) : NfcWriteState()
    data class Success(val workflowName: String) : NfcWriteState()
    data class Error(val workflowName: String, val message: String) : NfcWriteState()
}
