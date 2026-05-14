package com.irisapp.platform.trigger.voice

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irisapp.domain.model.PlannedWorkflow

/**
 * Voice trigger FAB — shown as a mic button on the home screen.
 *
 * States:
 * ```
 * IDLE → LISTENING → PROCESSING → PREVIEW → (running)
 *         ↘ ERROR (recognition unavailable / no speech)
 * ```
 *
 * In PREVIEW state a card is shown with the generated workflow summary.
 * User can tap "Run" to execute or "Dismiss" to cancel.
 */
@Composable
fun VoiceTriggerFab(
    onWorkflowGenerated: (PlannedWorkflow, rawJson: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val voiceTrigger = remember { VoiceIntentTrigger(context) }
    val voiceHandler = remember { VoiceTriggerHandler(context) }

    var fabState by remember { mutableStateOf(VoiceFabState.IDLE) }
    var transcript by remember { mutableStateOf<String?>(null) }
    var generatedWorkflow by remember { mutableStateOf<PlannedWorkflow?>(null) }
    var rawJson by remember { mutableStateOf<String?>(null) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var stageLabel by remember { mutableStateOf<String?>(null) }

    // Launcher for speech recognition activity
    val speechLauncher = rememberLauncherForActivityResult(
        contract = VoiceRecognitionContract()
    ) { result: Pair<Int, Intent?> ->
        val (resultCode, data) = result
        val parseResult = voiceTrigger.parseResult(resultCode, data)
        parseResult.fold(
            onSuccess = { text ->
                transcript = text
                fabState = VoiceFabState.PROCESSING
                stageLabel = "Generating workflow…"

                voiceHandler.generateFromTranscript(text, object : VoiceTriggerHandler.GenerationCallback {
                    override fun onStage(stage: VoiceTriggerHandler.VoiceStage) {
                        stageLabel = when (stage) {
                            VoiceTriggerHandler.VoiceStage.STARTING -> "Starting…"
                            VoiceTriggerHandler.VoiceStage.TRANSCRIPT_RECEIVED -> "Transcript received"
                            VoiceTriggerHandler.VoiceStage.LOADING_MODEL -> "Loading AI model…"
                            VoiceTriggerHandler.VoiceStage.GENERATING -> "Generating workflow…"
                            VoiceTriggerHandler.VoiceStage.VALIDATING -> "Validating…"
                            VoiceTriggerHandler.VoiceStage.DONE -> "Ready"
                            VoiceTriggerHandler.VoiceStage.ERROR -> "Error"
                        }
                        if (stage == VoiceTriggerHandler.VoiceStage.ERROR) {
                            fabState = VoiceFabState.ERROR
                        }
                    }

                    override fun onDebug(tag: String, message: String) {
                        // TODO: route to debug console if enabled
                    }

                    override fun onError(message: String) {
                        errorMessage = message
                        fabState = VoiceFabState.ERROR
                    }

                    override fun onWorkflowReady(
                        workflow: PlannedWorkflow,
                        json: String,
                        errors: List<String>
                    ) {
                        generatedWorkflow = workflow
                        rawJson = json
                        validationErrors = errors
                        fabState = VoiceFabState.PREVIEW
                    }
                })
            },
            onFailure = { e ->
                errorMessage = e.message ?: "Speech not recognized"
                fabState = VoiceFabState.ERROR
            }
        )
    }

    // Pulsing animation for LISTENING state
    val pulseScale by animateFloatAsState(
        targetValue = if (fabState == VoiceFabState.LISTENING) 1.15f else 1f,
        label = "pulse"
    )

    Box(modifier = modifier) {
        // Error/Preview card — anchored above the FAB
        AnimatedVisibility(
            visible = fabState == VoiceFabState.PREVIEW ||
                    fabState == VoiceFabState.ERROR ||
                    fabState == VoiceFabState.PROCESSING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            VoicePreviewCard(
                state = fabState,
                transcript = transcript,
                workflow = generatedWorkflow,
                errorMessage = errorMessage,
                stageLabel = stageLabel,
                validationErrors = validationErrors,
                onRun = { workflow, json ->
                    onWorkflowGenerated(workflow, json)
                    fabState = VoiceFabState.IDLE
                },
                onDismiss = {
                    fabState = VoiceFabState.IDLE
                    transcript = null
                    generatedWorkflow = null
                    errorMessage = null
                    stageLabel = null
                }
            )
        }

        // The FAB itself
        FloatingActionButton(
            onClick = {
                when (fabState) {
                    VoiceFabState.IDLE -> {
                        if (voiceTrigger.isAvailable) {
                            fabState = VoiceFabState.LISTENING
                            activity?.let {
                                speechLauncher.launch(Unit)
                            }
                        } else {
                            errorMessage = "Speech recognition not available on this device"
                            fabState = VoiceFabState.ERROR
                        }
                    }
                    VoiceFabState.LISTENING -> {
                        // Cancelled by user tapping again
                        fabState = VoiceFabState.IDLE
                    }
                    VoiceFabState.ERROR,
                    VoiceFabState.PREVIEW -> {
                        // Handled by card buttons
                    }
                    VoiceFabState.PROCESSING -> {
                        // Ignore taps while generating
                    }
                }
            },
            containerColor = when (fabState) {
                VoiceFabState.LISTENING -> MaterialTheme.colorScheme.error
                VoiceFabState.PROCESSING -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .scale(pulseScale)
        ) {
            Icon(
                imageVector = when (fabState) {
                    VoiceFabState.LISTENING -> Icons.Default.MicOff
                    VoiceFabState.PROCESSING -> Icons.Default.VolumeUp
                    VoiceFabState.ERROR -> Icons.Default.MicOff
                    else -> Icons.Default.Mic
                },
                contentDescription = "Voice trigger"
            )
        }
    }
}

@Composable
private fun VoicePreviewCard(
    state: VoiceFabState,
    transcript: String?,
    workflow: PlannedWorkflow?,
    errorMessage: String?,
    stageLabel: String?,
    validationErrors: List<String>,
    onRun: (PlannedWorkflow, String) -> Unit,
    onDismiss: () -> Unit
) {
    val workflowRawJson = remember(workflow) { workflow?.let { "{}" } ?: "" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (state) {
                        VoiceFabState.PROCESSING -> "Voice Workflow"
                        VoiceFabState.PREVIEW -> "Workflow Ready"
                        VoiceFabState.ERROR -> "Voice Error"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }

            // Transcript
            transcript?.let {
                Text(
                    text = "\"$it\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Processing indicator
            if (state == VoiceFabState.PROCESSING) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(stageLabel ?: "Processing…", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Error
            if (state == VoiceFabState.ERROR) {
                Text(
                    text = errorMessage ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Workflow preview
            if (state == VoiceFabState.PREVIEW && workflow != null) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = workflow.actions.joinToString(" → ") { it.id.substringAfterLast(".") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (validationErrors.isNotEmpty()) {
                    Text(
                        text = "${validationErrors.size} validation warning(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onRun(workflow, workflowRawJson) },
                        enabled = validationErrors.isEmpty()
                    ) {
                        Text("Run")
                    }
                }
            }
        }
    }
}

private enum class VoiceFabState {
    IDLE,
    LISTENING,
    PROCESSING,
    PREVIEW,
    ERROR
}