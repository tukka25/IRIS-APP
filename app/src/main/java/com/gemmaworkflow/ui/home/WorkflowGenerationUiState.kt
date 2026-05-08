package com.gemmaworkflow.ui.home

import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.platform.inference.InferenceState

/**
 * Carries the information needed to render a confirmation dialog for a pending step.
 * [stepId] identifies the action; [stepLabel] is the human-readable name;
 * [params] are the key/value parameters that will be executed on confirm.
 */
data class ConfirmationRequest(
    val stepId: String,
    val stepLabel: String,
    val params: Map<String, String>
)

data class WorkflowGenerationUiState(
    val prompt: String = "send message to +971****8872 saying hi, and invite him to meeting on 6 oclock on next friday and then add it to my calender.",
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
    val runResults: List<ExecutionResult> = emptyList(),
    val debugMessages: List<DebugMessage> = emptyList(),
    val savedWorkflows: List<PlannedWorkflow> = emptyList(),
    val selectedWorkflowName: String? = null,
    /** Set when a step requires user confirmation; cleared after confirm or dismiss. */
    val pendingConfirmation: ConfirmationRequest? = null,
    /** Index of the next step to run when resuming after confirmation. */
    val resumeStepIndex: Int = 0,
    /** Non-null when the user has tapped a saved workflow to see its detail. */
    val selectedWorkflowDetail: PlannedWorkflow? = null,
    /** The workflow currently being executed (used to resume after confirmation on the detail screen). */
    val runningWorkflow: PlannedWorkflow? = null
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

data class DebugMessage(
    val label: String,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

/** Mirrors the fields needed to render the detail screen for a saved workflow. */
data class WorkflowDetailState(
    val name: String,
    val summary: String,
    val trigger: com.gemmaworkflow.domain.model.TriggerConfig,
    val steps: List<StepDetail>
)

data class StepDetail(
    val id: String,
    val label: String,
    val icon: String,
    val params: String,
    val requiresConfirmation: Boolean
)
