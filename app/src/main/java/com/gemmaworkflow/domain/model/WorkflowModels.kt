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

    /** Run when battery level crosses a threshold. */
    @Serializable
    data class Battery(
        val levelThreshold: Int,
        val condition: BatteryCondition = BatteryCondition.BELOW
    ) : TriggerConfig()

    /** Run when charger is connected or disconnected. */
    @Serializable
    data class Charger(
        val connectionType: ChargerType = ChargerType.ANY
    ) : TriggerConfig()

    /** Run when WiFi connects or disconnects. */
    @Serializable
    data class WiFi(
        val ssid: String? = null,
        val bssid: String? = null,
        val connectionState: Boolean? = null  // true=connect, false=disconnect, null=any
    ) : TriggerConfig()

    /** Run when Bluetooth device connects or disconnects. */
    @Serializable
    data class Bluetooth(
        val deviceAddress: String? = null,
        val connectionState: Boolean? = null  // true=connect, false=disconnect, null=any
    ) : TriggerConfig()

    /** Run when airplane mode is toggled. */
    @Serializable
    data class AirplaneMode(
        val enabled: Boolean = true
    ) : TriggerConfig()

    /** Run when Do Not Disturb mode changes. */
    @Serializable
    data class DoNotDisturb(
        val interruptionFilter: Int? = null
    ) : TriggerConfig()

    /** Run when a GemmaWorkflow-scheduled alarm is cancelled/stopped by the user. */
    @Serializable
    data class AlarmStopped(
        val alarmType: String = "default"
    ) : TriggerConfig()

    /** Run when an app opens or closes (via AccessibilityService). */
    @Serializable
    data class AppOpened(
        val appPackagePatterns: List<String> = emptyList(),
        val triggerOnOpen: Boolean = true,
        val triggerOnClose: Boolean = false
    ) : TriggerConfig()

    /** Alias for AppOpened — kept for UI compatibility. */
    @Serializable
    data class AppClosed(
        val appPackagePatterns: List<String> = emptyList(),
        val triggerOnOpen: Boolean = false,
        val triggerOnClose: Boolean = true
    ) : TriggerConfig()

    /** Run when an SMS is received. */
    @Serializable
    data class SmsReceived(
        val senderPattern: String? = null,
        val bodyPattern: String? = null
    ) : TriggerConfig()

    /** Run when a notification arrives from a messaging app (WhatsApp, Telegram, etc.). */
    @Serializable
    data class NotificationListenerConfig(
        val appPackagePatterns: List<String> = emptyList(),
        val senderPattern: String? = null,
        val bodyPattern: String? = null,
        val triggerOnDismiss: Boolean = false
    ) : TriggerConfig()

    /** Run when an email notification arrives. */
    @Serializable
    data class EmailReceived(
        val senderPattern: String? = null,
        val subjectPattern: String? = null,
        val appPackage: String = "com.google.android.gm"
    ) : TriggerConfig()

    /** Run when Do Not Disturb activates within a bedtime window. */
    @Serializable
    data class SleepProxy(
        val startTimeHour: Int = 22,
        val startTimeMinute: Int = 0,
        val endTimeHour: Int = 7,
        val endTimeMinute: Int = 0,
        val requireChargerDisconnected: Boolean = true,
        val requireDndActive: Boolean = true
    ) : TriggerConfig()

    /** Run when device enters/exits/dwells in a geographic zone. */
    @Serializable
    data class Geofence(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float = 100f,
        val transitionType: GeofenceTransition = GeofenceTransition.ENTER_EXIT,
        val dwellDelaySeconds: Int = 0,
        val name: String? = null
    ) : TriggerConfig()
}

@Serializable
enum class GeofenceTransition {
    ENTER,
    EXIT,
    DWELL,
    ENTER_EXIT
}

@Serializable
enum class BatteryCondition {
    BELOW, ABOVE
}

@Serializable
enum class ChargerType {
    ANY, USB, AC, WIRELESS
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
    /** Raw output from the step — use this for chaining into later step params via $step[N].output */
    val output: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

/** An entry in the execution history. */
@Serializable
data class ExecutionLogEntry(
    val workflowName: String,
    val timestampMillis: Long,
    val results: List<ExecutionResult>,
    val allSuccess: Boolean
)
