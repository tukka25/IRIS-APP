package com.irisapp.ui.nfc

import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.platform.nfc.DeepLink
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.SurfaceDark
import com.irisapp.ui.theme.GlassSurface
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.platform.nfc.DeepLinkRouter
import com.irisapp.platform.nfc.NfcTriggerWriter
import kotlinx.coroutines.flow.collectLatest

/**
 * Setup screen for NFC tag triggers.
 *
 * Two modes:
 * 1. **Write mode** — user selects a workflow and taps an NFC tag to write the workflow ID.
 * 2. **Observe mode** — passively listens for tag scans and shows the last scanned workflow.
 *
 * This screen is shown when the user selects "NFC tag" as the trigger type from the
 * trigger setup UI (integrated into the workflow detail screen).
 *
 * @param workflowIdToWrite Optional workflow ID to pre-populate when entering write mode.
 * @param onWorkflowSelected Called when the user picks a saved workflow to write.
 * @param onWriteSuccess Called when a tag is successfully written.
 * @param onWriteError Called on write failure with an error message.
 * @param onTagScanned Called when a tag is scanned (in observe/verification mode).
 */
@Composable
fun NfcSetupScreen(
    workflowIdToWrite: String? = null,
    onWorkflowSelected: (String) -> Unit = {},
    onWriteSuccess: (String) -> Unit = {},
    onWriteError: (String) -> Unit = {},
    onTagScanned: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val writer = remember { NfcTriggerWriter(context, WorkflowRepository(context)) }

    var isWriteMode by remember { mutableStateOf(workflowIdToWrite != null) }
    var selectedWorkflowId by remember { mutableStateOf(workflowIdToWrite ?: "") }
    var nfcStatus by remember { mutableStateOf(writer.nfcStatusMessage()) }
    var isNfcEnabled by remember { mutableStateOf(writer.isNfcEnabled()) }
    var writeState by remember { mutableStateOf<WriteState>(WriteState.Idle) }
    var savedWorkflows by remember { mutableStateOf<List<String>>(emptyList()) }

    // Load saved workflow names
    LaunchedEffect(Unit) {
        savedWorkflows = WorkflowRepository(context).listNames()
        isNfcEnabled = writer.isNfcEnabled()
        nfcStatus = writer.nfcStatusMessage()
    }

    // Listen for foreground NFC intents via DeepLinkRouter
    LaunchedEffect(Unit) {
        DeepLinkRouter.deepLinkEvents.collectLatest { deepLink ->
            when (deepLink) {
                is DeepLink.NfcScan -> {
                    onTagScanned(deepLink.workflowId)
                    if (deepLink.workflowId == selectedWorkflowId) {
                        writeState = WriteState.Success(deepLink.workflowId)
                    }
                }
                is DeepLink.RunWorkflow -> onTagScanned(deepLink.workflowId)
                is DeepLink.ShowDetail -> onTagScanned(deepLink.workflowId)
                is DeepLink.WriteComplete -> {
                    if (deepLink.workflowId == selectedWorkflowId) {
                        if (deepLink.success) {
                            writeState = WriteState.Success(deepLink.workflowId)
                            onWriteSuccess(deepLink.workflowId)
                        } else {
                            writeState = WriteState.Error(deepLink.message)
                            onWriteError(deepLink.message)
                        }
                        DeepLinkRouter.clearWriteMode()
                    }
                }
                else -> { /* unknown DeepLink variant — ignore */ }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("NFC Tag Setup", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

        // NFC availability card
        NfcStatusCard(
            nfcStatus = nfcStatus,
            isEnabled = isNfcEnabled
        )

        // Mode selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { isWriteMode = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Write Tag")
            }
            OutlinedButton(
                onClick = { isWriteMode = false },
                modifier = Modifier.weight(1f)
            ) {
                Text("Verify / Scan")
            }
        }

        HorizontalDivider()

        if (isWriteMode) {
            // ===== WRITE MODE =====
            Text("Write Workflow to Tag", style = MaterialTheme.typography.titleMedium)

            if (savedWorkflows.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "No saved workflows. Save a workflow first before writing to an NFC tag.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                // Workflow selector
                OutlinedTextField(
                    value = selectedWorkflowId,
                    onValueChange = {
                        selectedWorkflowId = it
                        onWorkflowSelected(it)
                    },
                    label = { Text("Workflow name") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Select or type a saved workflow name") },
                    singleLine = true
                )

                if (savedWorkflows.isNotEmpty()) {
                    Text(
                        "Saved workflows:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        savedWorkflows.take(3).forEach { name ->
                            OutlinedButton(
                                onClick = {
                                    selectedWorkflowId = name
                                    onWorkflowSelected(name)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 4.dp, vertical = 2.dp
                                )
                            ) {
                                Text(name, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Write instruction
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Ready to write",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (selectedWorkflowId.isNotBlank()) {
                                "Hold your phone near the NFC tag to write \"$selectedWorkflowId\" to it."
                            } else {
                                "Select a workflow above, then hold your phone near an NFC tag."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Write state indicator
                when (val state = writeState) {
                    is WriteState.Idle -> {
                        if (selectedWorkflowId.isNotBlank()) {
                            Button(
                                onClick = {
                                    DeepLinkRouter.setWriteMode(selectedWorkflowId)
                                    writeState = WriteState.Waiting
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isNfcEnabled && selectedWorkflowId.isNotBlank()
                            ) {
                                Text("Tap \"Write\" then hold phone to tag")
                            }
                        }
                    }
                    is WriteState.Waiting -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Hold phone near NFC tag...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is WriteState.Success -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "\u2713 Tag written successfully",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Workflow \"${state.workflowId}\" is now on the tag.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                DeepLinkRouter.clearWriteMode()
                                writeState = WriteState.Idle
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Write Another")
                        }
                    }
                    is WriteState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "\u2717 Write failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                DeepLinkRouter.clearWriteMode()
                                writeState = WriteState.Idle
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        } else {
            // ===== VERIFY/SCAN MODE =====
            Text("Verify Tag", style = MaterialTheme.typography.titleMedium)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Hold your phone near an NFC tag to verify its contents.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "The tag's workflow ID will be displayed below.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Text(
                "NFC is ${if (isNfcEnabled) "enabled" else "disabled"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isNfcEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun NfcStatusCard(nfcStatus: String, isEnabled: Boolean) {
    val (color, bgColor) = if (isEnabled) {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    }

    Card(colors = CardDefaults.cardColors(containerColor = bgColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEnabled) "\uD83D\uDD17" else "\u26A0\uFE0F",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "NFC Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
                Text(
                    nfcStatus,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private sealed class WriteState {
    data object Idle : WriteState()
    data object Waiting : WriteState()
    data class Success(val workflowId: String) : WriteState()
    data class Error(val message: String) : WriteState()
}
