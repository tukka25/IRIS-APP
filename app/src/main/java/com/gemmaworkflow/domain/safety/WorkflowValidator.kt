package com.gemmaworkflow.domain.safety

import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.catalog.ParamSpec
import com.gemmaworkflow.domain.catalog.ParamType
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.model.WorkflowStep
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Validates a parsed workflow against the action allowlist.
 * Rejects unknown actions, missing params, unsupported triggers, and unsafe URLs.
 */
object WorkflowValidator {

    private val allowedUrlSchemes = setOf("https", "http")
    private val allowedUriSchemes = setOf("content", "file", "geo", "smsto", "tel")

    /**
     * Validate a workflow. Returns a list of error messages.
     * An empty list means the workflow is valid and safe to run.
     */
    fun validate(
        workflow: PlannedWorkflow,
        availableActionIds: Set<String> = ActionSpecRegistry.allIds
    ): List<String> {
        val errors = mutableListOf<String>()
        val triggerType = workflow.trigger.typeId()

        if (workflow.name.isBlank()) {
            errors.add("Workflow name is empty")
        }

        // Validate each action
        for ((index, step) in workflow.actions.withIndex()) {
            val prefix = "Action $index (${step.id})"

            // Check action exists in catalog
            val actionSpec = ActionSpecRegistry.find(step.id)
            if (actionSpec == null) {
                errors.add("$prefix: unknown action id — not in allowlist")
                continue
            }

            if (step.id !in availableActionIds) {
                errors.add("$prefix: action is not available on this device")
            }

            if (triggerType !in actionSpec.triggerCompatible) {
                errors.add("$prefix: action is not compatible with trigger '$triggerType'")
            }

            // Check required params
            for (param in actionSpec.params) {
                val value = step.params[param.name]
                if (param.required && (value == null || value is JsonNull)) {
                    errors.add("$prefix: missing required param '${param.name}'")
                } else if (value != null && value !is JsonNull) {
                    validateParam(prefix, param, value)?.let(errors::add)
                }
            }

            // Check for params not in the catalog (model hallucination)
            for (paramName in step.params.keys) {
                if (actionSpec.params.none { it.name == paramName }) {
                    errors.add("$prefix: unknown param '$paramName' (not in catalog)")
                }
            }

            if (actionSpec.requiresConfirmation && !step.requiresConfirmation) {
                errors.add("$prefix: requires_confirmation must be true")
            }
        }

        return errors
    }

    /**
     * Returns a set of action IDs that need user confirmation before running.
     */
    fun confirmationActions(steps: List<WorkflowStep>): Set<String> {
        return steps.filter { step ->
            ActionSpecRegistry.find(step.id)?.requiresConfirmation == true
        }.map { it.id }.toSet()
    }

    private fun validateParam(prefix: String, param: ParamSpec, value: JsonElement): String? {
        val primitive = runCatching { value.jsonPrimitive }.getOrNull()
        return when (param.type) {
            ParamType.String -> if (primitive?.contentOrNull != null) null else "$prefix: param '${param.name}' must be a string"
            ParamType.Url -> validateStringScheme(prefix, param.name, primitive?.contentOrNull, allowedUrlSchemes)
            ParamType.Uri -> validateStringScheme(prefix, param.name, primitive?.contentOrNull, allowedUriSchemes)
            ParamType.Int -> if (primitive?.intOrNull != null) null else "$prefix: param '${param.name}' must be an int"
            ParamType.Long,
            ParamType.DateTimeMillis -> if (primitive?.longOrNull != null) null else "$prefix: param '${param.name}' must be a long"
            ParamType.Boolean -> if (primitive?.booleanOrNull != null) null else "$prefix: param '${param.name}' must be a boolean"
            ParamType.StringArray -> {
                val array = value as? JsonArray
                if (array != null && array.all { element ->
                        (element as? JsonPrimitive)?.isString == true
                    }) {
                    null
                } else {
                    "$prefix: param '${param.name}' must be an array of strings"
                }
            }
            ParamType.Enum -> {
                val content = primitive?.contentOrNull
                if (content != null && content in param.enumValues) null else {
                    "$prefix: param '${param.name}' must be one of ${param.enumValues.joinToString()}"
                }
            }
        }
    }

    private fun validateStringScheme(
        prefix: String,
        paramName: String,
        value: String?,
        allowedSchemes: Set<String>
    ): String? {
        if (value.isNullOrBlank()) return "$prefix: param '$paramName' must be a non-empty string"
        val scheme = value.substringBefore(":", missingDelimiterValue = "")
        return if (scheme in allowedSchemes) null else "$prefix: unsupported scheme '$scheme' for param '$paramName'"
    }

    private fun TriggerConfig.typeId(): String = when (this) {
        TriggerConfig.Manual -> "manual"
        is TriggerConfig.Time -> "time"
        is TriggerConfig.Nfc -> "nfc"
        is TriggerConfig.ShareSheet -> "share_sheet"
        is TriggerConfig.Battery -> "battery"
        is TriggerConfig.Charger -> "charger"
        is TriggerConfig.WiFi -> "wifi"
        is TriggerConfig.Bluetooth -> "bluetooth"
        is TriggerConfig.AirplaneMode -> "airplane_mode"
        is TriggerConfig.DoNotDisturb -> "do_not_disturb"
        is TriggerConfig.AlarmStopped -> "alarm_stopped"
        is TriggerConfig.AppOpened -> "app_opened"
        is TriggerConfig.AppClosed -> "app_closed"
        is TriggerConfig.SmsReceived -> "sms_received"
        is TriggerConfig.NotificationListenerConfig -> "messaging_notification"
        is TriggerConfig.EmailReceived -> "email_received"
        is TriggerConfig.SleepProxy -> "sleep_proxy"
        is TriggerConfig.Geofence -> "geofence"
    }
}
