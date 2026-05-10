package com.gemmaworkflow.domain.triggers

import android.content.Context
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.model.SetupState
import com.gemmaworkflow.platform.alarm.TimeTriggerScheduler

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
 *
 * For [TriggerConfig.Time], registers the alarm with [TimeTriggerScheduler].
 * For other trigger types, stores the intent/Easer profile as appropriate.
 */
object TriggerRegistry {

    private var scheduler: TimeTriggerScheduler? = null

    fun initialize(context: Context) {
        if (scheduler == null) {
            scheduler = TimeTriggerScheduler(context)
        }
    }

    fun register(workflowId: String, triggerType: String, trigger: TriggerConfig?): TriggerRegistrationResult {
        val triggerInfo = TriggerCatalog.find(triggerType)
            ?: return TriggerRegistrationResult(false, "Unknown trigger type: $triggerType")

        // If we have a Time trigger config, we can register it now (schedules the alarm).
        if (trigger is TriggerConfig.Time) {
            scheduleTimeTrigger(workflowId, trigger)
            return TriggerRegistrationResult(true, "Trigger registered")
        }

        return when (triggerInfo.setupState) {
            SetupState.Ready -> {
                TriggerRegistrationResult(true, "Trigger registered")
            }
            SetupState.NeedsSetup -> TriggerRegistrationResult(
                false,
                "'${triggerInfo.label}' requires setup before it can be activated."
            )
            SetupState.Unsupported -> TriggerRegistrationResult(
                false,
                "'${triggerInfo.label}' is not supported yet."
            )
        }
    }

    fun unregister(workflowId: String) {
        scheduler?.cancel(workflowId)
    }

    private fun scheduleTimeTrigger(workflowId: String, trigger: TriggerConfig.Time) {
        scheduler?.schedule(workflowId, trigger)
            ?: android.util.Log.w("TriggerRegistry", "Scheduler not initialized — cannot schedule time trigger")
    }
}

data class TriggerRegistrationResult(
    val success: Boolean,
    val message: String
)
