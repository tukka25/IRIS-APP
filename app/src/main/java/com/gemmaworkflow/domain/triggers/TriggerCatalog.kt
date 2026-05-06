package com.gemmaworkflow.domain.triggers

import com.gemmaworkflow.domain.model.SetupState

/**
 * Catalog of supported trigger types.
 */
data class TriggerInfo(
    val type: String,
    val label: String,
    val description: String,
    val setupState: SetupState = SetupState.Ready,
    val requiresTasker: Boolean = false
)

object TriggerCatalog {

    val all: List<TriggerInfo> = listOf(
        TriggerInfo(
            type = "manual",
            label = "Manual run",
            description = "Run the workflow by tapping a button."
        ),
        TriggerInfo(
            type = "time",
            label = "Scheduled time",
            description = "Run at a specific time of day, optionally on repeat days.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "nfc",
            label = "NFC tag",
            description = "Run when an NFC tag is scanned.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "share_sheet",
            label = "Share sheet",
            description = "Run when content is shared to GemmaWorkflow.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "tasker_setup_required",
            label = "Tasker automation",
            description = "Requires Tasker app to create the automation profile.",
            setupState = SetupState.NeedsSetup,
            requiresTasker = true
        )
    )

    fun find(type: String): TriggerInfo? = all.find { it.type == type }

    val supportedTypes: Set<String> = all.map { it.type }.toSet()
}

/**
 * Registers and unregisters workflow triggers.
 * MVP: manual run is always supported. Other triggers show NeedsSetup.
 */
object TriggerRegistry {

    fun register(workflowId: String, triggerType: String): TriggerRegistrationResult {
        val trigger = TriggerCatalog.find(triggerType)
            ?: return TriggerRegistrationResult(false, "Unknown trigger type: $triggerType")

        return when (trigger.setupState) {
            SetupState.Ready -> TriggerRegistrationResult(true, "Trigger registered")
            SetupState.NeedsSetup -> TriggerRegistrationResult(
                false,
                "'${trigger.label}' requires setup before it can be activated."
            )
            SetupState.Unsupported -> TriggerRegistrationResult(
                false,
                "'${trigger.label}' is not supported yet."
            )
        }
    }

    fun unregister(workflowId: String) {
        // MVP: no persistent trigger state to clean up
    }
}

data class TriggerRegistrationResult(
    val success: Boolean,
    val message: String
)
