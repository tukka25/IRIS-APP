package com.gemmaworkflow.ui.home

import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.platform.inference.InferenceState

data class WorkflowGenerationUiState(
    val prompt: String = "When I arrive at the gym, play my focus playlist on Spotify and save a note with today's workout plan.",
    val inferenceState: InferenceState = InferenceState.Idle,
    val isModelReady: Boolean = false,
    val isBusy: Boolean = false,
    val stage: String = "",
    val stageTimeline: List<StageProgress> = emptyList(),
    val elapsedSeconds: Long = 0,
    val error: String? = null,
    val workflowPreview: PlannedWorkflow? = null,
    val rawJson: String? = null,
    val validationErrors: List<String> = emptyList(),
    val saved: Boolean = false,
    val runResults: List<ExecutionResult> = emptyList()
) {
    val canGenerate: Boolean
        get() = isModelReady && !isBusy && prompt.isNotBlank()
    val hasWorkflow: Boolean
        get() = workflowPreview != null
    val isValid: Boolean
        get() = hasWorkflow && validationErrors.isEmpty()
}

enum class StageStatus { Pending, Running, Done }

data class StageProgress(
    val label: String,
    val status: StageStatus = StageStatus.Pending
)
