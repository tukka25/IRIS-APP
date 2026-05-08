package com.gemmaworkflow.ui.home

import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.SharedContent
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

/**
 * Carries the information needed to render a confirmation dialog when an NFC tag
 * is scanned and the user needs to confirm before running the workflow.
 */
data class NfcScanConfirmation(
    val workflowId: String,
    val workflowName: String
)

/**
 * Represents content received via the Android share sheet that is pending
 * workflow selection by the user.
 */
data class PendingShare(
    val text: String?,
    val uri: String?,
    val sourceLabel: String?
) {
    val displaySummary: String
        get() = when {
            !text.isNullOrBlank() -> text.take(80).let { if (text.length > 80) "$it…" else it }
            !uri.isNullOrBlank() -> "Shared file: ${uri.substringAfterLast('/')}"
            else -> "Shared content"
        }
}

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
    val runningWorkflow: PlannedWorkflow? = null,
    /** Non-null when the user is setting up a time trigger for a saved workflow. */
    val timeTriggerSetupWorkflow: PlannedWorkflow? = null,
    /** Shared content from an incoming share intent. */
    val sharedContent: SharedContent? = null,

    // ── Share sheet state ──────────────────────────────────────────────────
    /**
     * Non-null when the user is setting up a share sheet trigger for a saved workflow.
     */
    val shareSheetSetupWorkflow: PlannedWorkflow? = null,

    /**
     * Non-null when content was shared to the app and the user needs to pick a workflow.
     */
    val pendingShare: PendingShare? = null,
    /**
     * List of saved workflows that support the share sheet trigger.
     */
    val shareSheetWorkflows: List<PlannedWorkflow> = emptyList(),

    // ── NFC trigger state ────────────────────────────────────────────────
    /** True when the NFC tag setup screen should be shown. */
    val showNfcSetup: Boolean = false,
    /** Current write-to-tag state. */
    val nfcWriteState: NfcWriteState = NfcWriteState.Idle,
    /** The name of the workflow selected for NFC writing. */
    val nfcWriteWorkflowId: String? = null,
    /** Pending NFC scan awaiting user confirmation to run. */
    val nfcScanConfirmation: NfcScanConfirmation? = null
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
