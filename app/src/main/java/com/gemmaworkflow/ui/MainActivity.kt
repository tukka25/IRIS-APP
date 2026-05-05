package com.gemmaworkflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.SetupState
import com.gemmaworkflow.platform.inference.InferenceState
import com.gemmaworkflow.ui.home.WorkflowGenerationViewModel
import com.gemmaworkflow.ui.home.StageStatus
import com.gemmaworkflow.ui.theme.GemmaWorkflowTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WorkflowGenerationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GemmaWorkflowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkflowGenerationScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun WorkflowGenerationScreen(viewModel: WorkflowGenerationViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("GemmaWorkflow", style = MaterialTheme.typography.headlineMedium)

        // --- Model Status ---
        ModelStatusCard(state.inferenceState)

        // --- Prompt ---
        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("What should GemmaWorkflow do?") },
            supportingText = { Text("e.g. \"When I tap this, open Maps to the nearest coffee shop and share my location\"") }
        )

        // --- Generate ---
        Button(
            enabled = state.canGenerate,
            onClick = viewModel::generate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isBusy) "Generating\u2026" else "Generate Workflow")
        }

        if (state.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // Timer + stage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(state.stage, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${state.elapsedSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Stage timeline
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.stageTimeline.forEach { stage ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val icon = when (stage.status) {
                            StageStatus.Done -> "\u2713"
                            StageStatus.Running -> "\u25B6"
                            StageStatus.Pending -> "\u25CB"
                        }
                        val color = when (stage.status) {
                            StageStatus.Done -> MaterialTheme.colorScheme.primary
                            StageStatus.Running -> MaterialTheme.colorScheme.tertiary
                            StageStatus.Pending -> MaterialTheme.colorScheme.outline
                        }
                        Text(icon, color = color, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stage.label,
                            color = color,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // --- Error ---
        if (state.error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = state.error.orEmpty(),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // --- Workflow Preview ---
        val workflow = state.workflowPreview
        if (workflow != null) {
            HorizontalDivider()

            Text("Generated Workflow", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                    if (workflow.summary.isNotBlank()) {
                        Text(workflow.summary, style = MaterialTheme.typography.bodyMedium)
                    }

                    // Trigger
                    Text("Trigger: ${triggerLabel(workflow)}", style = MaterialTheme.typography.bodySmall)

                    // Actions
                    Text("Actions (${workflow.actions.size}):", style = MaterialTheme.typography.labelMedium)
                    workflow.actions.forEach { step ->
                        Text(
                            "  \u2022 ${step.id} ${step.params}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (workflow.missingSetup.isNotEmpty()) {
                        Text(
                            "Needs setup: ${workflow.missingSetup.joinToString()}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // --- Validation Errors ---
            if (state.validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Validation errors:", style = MaterialTheme.typography.labelMedium)
                        state.validationErrors.forEach { err ->
                            Text("  \u2022 $err", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // --- Action Buttons ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::saveWorkflow,
                    enabled = !state.saved
                ) {
                    Text(if (state.saved) "Saved" else "Save")
                }

                Button(
                    onClick = viewModel::runWorkflow,
                    enabled = state.isValid && !state.isBusy
                ) {
                    Text("Run Now")
                }
            }

            // --- Raw JSON ---
            if (state.rawJson != null) {
                Text("Raw Model Output", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = state.rawJson.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // --- Run Results ---
        if (state.runResults.isNotEmpty()) {
            HorizontalDivider()
            Text("Execution Results", style = MaterialTheme.typography.titleMedium)
            state.runResults.forEach { result ->
                RunResultRow(result)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelStatusCard(state: InferenceState) {
    val (label, color) = when (state) {
        is InferenceState.Idle -> "Idle" to MaterialTheme.colorScheme.outline
        is InferenceState.Loading -> "Loading model\u2026" to MaterialTheme.colorScheme.primary
        is InferenceState.Ready -> "Ready \u2014 GPU (LiteRT-LM)" to MaterialTheme.colorScheme.primary
        is InferenceState.MissingModel -> "Model not found" to MaterialTheme.colorScheme.error
        is InferenceState.GpuUnavailable -> "GPU unavailable: ${state.reason}" to MaterialTheme.colorScheme.error
        is InferenceState.Error -> "Error: ${state.message}" to MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(12.dp),
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RunResultRow(result: ExecutionResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (result.success) "\u2713" else "\u2717",
            color = if (result.success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(result.stepId, style = MaterialTheme.typography.bodySmall)
            if (result.message.isNotBlank()) {
                Text(result.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun triggerLabel(workflow: com.gemmaworkflow.domain.model.PlannedWorkflow): String {
    return when (val t = workflow.trigger) {
        is com.gemmaworkflow.domain.model.TriggerConfig.Manual -> "Manual"
        is com.gemmaworkflow.domain.model.TriggerConfig.Time -> "Time (${t.hour}:${t.minute})"
        is com.gemmaworkflow.domain.model.TriggerConfig.Nfc -> "NFC"
        is com.gemmaworkflow.domain.model.TriggerConfig.ShareSheet ->
            "Share Sheet (${t.setupState})"
        is com.gemmaworkflow.domain.model.TriggerConfig.TaskerRequired ->
            "Tasker (${t.setupState})"
    }
}
