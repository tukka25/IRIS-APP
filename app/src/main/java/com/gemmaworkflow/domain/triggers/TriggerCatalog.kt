package com.gemmaworkflow.domain.triggers

import android.content.Context
import android.util.Log
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.model.SetupState
import com.gemmaworkflow.platform.alarm.TimeTriggerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Catalog of supported trigger types.
 */
data class TriggerInfo(
    val type: String,
    val label: String,
    val description: String,
    val setupState: SetupState = SetupState.Ready
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
            type = "battery",
            label = "Battery level",
            description = "Run when battery level goes above or below a threshold.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "charger",
            label = "Charger connected",
            description = "Run when the device is plugged in or unplugged.",
            setupState = SetupState.Ready
        ),
        TriggerInfo(
            type = "wifi",
            label = "WiFi connected",
            description = "Run when WiFi connects or disconnects.",
            setupState = SetupState.Ready
        ),
        TriggerInfo(
            type = "bluetooth",
            label = "Bluetooth device",
            description = "Run when a Bluetooth device connects or disconnects.",
            setupState = SetupState.Ready
        ),
        TriggerInfo(
            type = "airplane_mode",
            label = "Airplane mode",
            description = "Run when airplane mode is toggled on or off.",
            setupState = SetupState.Ready
        ),
        TriggerInfo(
            type = "dnd",
            label = "Do Not Disturb",
            description = "Run when Do Not Disturb mode changes.",
            setupState = SetupState.Ready
        ),
        TriggerInfo(
            type = "geofence",
            label = "Arrive / Leave",
            description = "Run when the device enters, exits, or dwells at a location.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "alarm_stopped",
            label = "Alarm stopped",
            description = "Run when a GemmaWorkflow alarm is dismissed or stopped.",
            setupState = SetupState.Ready
        ),
        TriggerInfo(
            type = "app_opened",
            label = "App opened",
            description = "Run when a specific app is opened.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "app_closed",
            label = "App closed",
            description = "Run when a specific app is closed.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "sms_received",
            label = "SMS received",
            description = "Run when an SMS matching criteria is received.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "messaging_notification",
            label = "Messaging notification",
            description = "Run when a messaging app notification arrives.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "email_received",
            label = "Email received",
            description = "Run when a new email arrives.",
            setupState = SetupState.NeedsSetup
        ),
        TriggerInfo(
            type = "sleep_proxy",
            label = "Sleep schedule",
            description = "Run on a sleep/wind-down schedule.",
            setupState = SetupState.NeedsSetup
        )
    )

    fun find(type: String): TriggerInfo? = all.find { it.type == type }

    val supportedTypes: Set<String> = all.map { it.type }.toSet()

    /**
     * Compact, token-efficient trigger list for SLM prompts.
     * Format: "type: description"
     * The SLM picks from these when producing trigger_hint.
     */
    fun toCompactPrompt(): String = buildString {
        appendLine("Available trigger types (pick one for trigger_hint):")
        all.forEach { trigger ->
            val setup = when (trigger.setupState) {
                SetupState.Ready -> ""
                SetupState.NeedsSetup -> " [needs setup]"
                SetupState.Unsupported -> " [unsupported]"
            }
            appendLine("  ${trigger.type}: ${trigger.description}$setup")
        }
    }
}

/**
 * Registers and unregisters workflow triggers.
 *
 * For [TriggerConfig.Time], registers the alarm with [TimeTriggerScheduler].
 * For other trigger types, stores the intent/Easer profile as appropriate.
 */
object TriggerRegistry {

    private var scheduler: TimeTriggerScheduler? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    /**
     * Fires a workflow — delegates to [com.gemmaworkflow.platform.trigger.TriggerRegistry].
     * Kept here for backward compatibility with any code that references
     * domain.triggers.TriggerRegistry.fireWorkflow.
     */
    @Deprecated("Use platform.trigger.TriggerRegistry.fire() directly", ReplaceWith("com.gemmaworkflow.platform.trigger.TriggerRegistry.fire(context, workflow)"))
    fun fireWorkflow(context: Context, workflow: PlannedWorkflow) {
        com.gemmaworkflow.platform.trigger.TriggerRegistry.fire(context, workflow)
    }
}

data class TriggerRegistrationResult(
    val success: Boolean,
    val message: String
)
