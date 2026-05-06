package com.gemmaworkflow.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** The fully validated workflow produced by the planner pipeline. */
@Serializable
data class PlannedWorkflow(
    val name: String,
    val summary: String = "",
    val trigger: TriggerConfig = TriggerConfig.Manual,
    val actions: List<WorkflowStep> = emptyList(),
    val missingSetup: List<String> = emptyList(),
    val rawModelOutput: String = ""
)

/** A single runnable step within a workflow. */
@Serializable
data class WorkflowStep(
    val id: String,                     // e.g. "browser.open_url"
    val params: JsonObject = buildJsonObject { },
    val requiresConfirmation: Boolean = false
)

/** Trigger configuration — what activates this workflow. */
@Serializable
sealed class TriggerConfig {

    @Serializable
    data object Manual : TriggerConfig()

    @Serializable
    data class Time(
        val hour: Int,
        val minute: Int,
        val repeatDays: List<Int> = emptyList()
    ) : TriggerConfig()

    @Serializable
    data class Nfc(
        val tagId: String? = null
    ) : TriggerConfig()

    @Serializable
    data class ShareSheet(
        val setupState: SetupState = SetupState.NeedsSetup
    ) : TriggerConfig()

    @Serializable
    data class TaskerRequired(
        val setupState: SetupState = SetupState.NeedsSetup
    ) : TriggerConfig()
}

/** Whether a feature is ready, needs setup, or is unsupported. */
@Serializable
enum class SetupState {
    Ready, NeedsSetup, Unsupported
}

/** Runtime status of a saved workflow. */
enum class WorkflowStatus {
    Draft, ManualOnly, NeedsSetup, Active, Off, Failed
}

/** Result of executing a single workflow step. */
@Serializable
data class ExecutionResult(
    val stepId: String,
    val success: Boolean,
    val message: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)
