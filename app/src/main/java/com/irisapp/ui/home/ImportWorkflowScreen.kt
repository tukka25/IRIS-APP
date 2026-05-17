package com.irisapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.irisapp.data.repository.WorkflowShareRepository
import kotlinx.coroutines.launch

/**
 * Shown when the app is opened via an `iris://import/{shareId}` deep-link.
 * Fetches the workflow from Firebase RTDB and prompts the user to confirm the import
 * with a custom name.
 */
@Composable
fun ImportWorkflowScreen(
    shareId: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var workflowName by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var stepsCount by remember { mutableIntStateOf(0) }
    var triggerDesc by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmName by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(shareId) {
        scope.launch {
            val data = WorkflowShareRepository.fetch(shareId)
            if (data != null) {
                try {
                    val name = (data["name"] as? String) ?: "Imported Workflow"
                    workflowName = name
                    summary = (data["summary"] as? String) ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val actions = data["actions"] as? List<*> ?: emptyList<Any>()
                    stepsCount = actions.size
                    @Suppress("UNCHECKED_CAST")
                    val trigger = data["trigger"] as? Map<String, Any> ?: emptyMap()
                    val triggerType = (trigger["type"] as? String) ?: "manual"
                    triggerDesc = triggerType.replace("_", " ").replaceFirstChar { it.uppercase() }
                    confirmName = "$name (imported)"
                    isLoading = false
                } catch (e: Exception) {
                    error = "Failed to parse workflow: ${e.message}"
                    isLoading = false
                }
            } else {
                error = "This workflow is no longer available or could not be found."
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Import Workflow", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }

        when {
            isLoading -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Fetching workflow...",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            error != null -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onCancel) {
                            Text("Dismiss")
                        }
                    }
                }
            }
            else -> {
                // Workflow preview card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Workflow:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            workflowName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (summary.isNotBlank()) {
                            Text(summary, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "$stepsCount step${if (stepsCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Trigger: $triggerDesc",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Rename field
                OutlinedTextField(
                    value = confirmName,
                    onValueChange = { confirmName = it },
                    label = { Text("Save as") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = { onConfirm(confirmName) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = confirmName.isNotBlank()
                ) {
                    Text("Import")
                }
            }
        }
    }
}