package com.gemmaworkflow.ui.home

import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.platform.inference.InferenceState

data class WorkflowGenerationUiState(
    val prompt: String = "send message to +971556778872 saying hi, and invite him to meeting on 6 oclock on next friday and then add it to my calender.",
    val inferenceState: InferenceState = InferenceState.Idle,
    val isModelReady: Boolean = false,
    val isBusy: Boolean = false,
    val stage: String = "",
    val stageTimeline: List<StageProgress> = emptyList(),
    val stageTokenUsage: List<StageTokenUsage> = emptyList(),
    val elapsedSeconds: Long = 0,
    val error: String? = null,
    val workflowPreview: PlannedWorkflow? = null,
    val rawJson: String? = null,
    val validationErrors: List<String> = emptyList(),
    val saved: Boolean = false,
    val runResults: List<ExecutionResult> = emptyList(),
    val debugMessages: List<DebugMessage> = emptyList()
) {
    val canGenerate: Boolean get() = isModelReady && !isBusy && prompt.isNotBlank()
    val hasWorkflow: Boolean get() = workflowPreview != null
    val isValid: Boolean get() = hasWorkflow && validationErrors.isEmpty()
}

enum class StageStatus { Pending, Running, Done }

data class StageProgress(
    val label: String,
    val status: StageStatus = StageStatus.Pending
)

/** Token usage for one agent stage. */
data class StageTokenUsage(
    val stageLabel: String,
    val inputChars: Int = 0,
    val estimatedTokens: Int = 0,
    val contextWindow: Int = 8192  // Gemma 4 E2B IT default
)

data class DebugMessage(
    val label: String,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
