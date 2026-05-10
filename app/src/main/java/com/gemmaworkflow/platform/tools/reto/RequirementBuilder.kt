package com.gemmaworkflow.platform.tools.reto

import android.util.Log
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.catalog.LogicalAction
import com.gemmaworkflow.domain.catalog.ParamSpec

/**
 * Deterministic requirement builder.
 *
 * The SLM chooses an action_id in CapabilityBinder. Kotlin then inspects that
 * ActionSpec's params and creates only the grounding requirements needed for
 * those params. This keeps payload structure owned by the registry instead of
 * letting the model invent requirements.
 */
object RequirementBuilder {

    private const val TAG = "RequirementBuilder"

    fun build(
        userRequest: String,
        boundActions: List<CapabilityBinder.BoundAction>
    ): RequirementLedger {
        if (boundActions.isEmpty()) {
            Log.d(TAG, "No bound actions; skipping requirement building")
            return RequirementLedger.EMPTY
        }

        val requirements = mutableListOf<FactRequirement>()
        var index = 1

        for (binding in boundActions) {
            val actionId = binding.actionId ?: continue
            val spec = ActionSpecRegistry.find(actionId) ?: continue

            for (param in spec.params) {
                val factType = param.factType ?: continue
                if (!shouldCreateRequirement(param, binding)) continue

                val mention = chooseMention(
                    factType = factType,
                    binding = binding,
                    fallback = userRequest
                )

                requirements.add(
                    FactRequirement(
                        id = "r${index++}",
                        sourceAction = actionId,
                        slot = param.name,
                        mention = mention,
                        factType = factType,
                        blocking = isBlockingRequirement(param, binding)
                    )
                )
            }
        }

        Log.i(TAG, "Built ${requirements.size} deterministic requirements for ${boundActions.size} actions")
        return RequirementLedger(
            actionCandidates = boundActions.mapNotNull { it.actionId },
            requirements = requirements.distinctBy { "${it.sourceAction}:${it.slot}:${it.factType.label}:${it.mention}" }
        )
    }

    private fun isBlockingRequirement(
        param: ParamSpec,
        binding: CapabilityBinder.BoundAction
    ): Boolean {
        if (param.required) return true

        val logicalAction = LogicalAction.fromId(binding.taskAction)
        return when (param.factType) {
            FactType.CONTACT_PHONE,
            FactType.CONTACT_EMAIL -> logicalAction in setOf(LogicalAction.SendMessage, LogicalAction.MakeCall) &&
                hasMentionOfKind(binding, "contact")

            else -> false
        }
    }

    private fun shouldCreateRequirement(
        param: ParamSpec,
        binding: CapabilityBinder.BoundAction
    ): Boolean {
        if (param.required) return true

        val logicalAction = LogicalAction.fromId(binding.taskAction)
        return when (param.factType) {
            FactType.CONTACT_PHONE,
            FactType.CONTACT_EMAIL -> logicalAction in setOf(LogicalAction.SendMessage, LogicalAction.MakeCall) &&
                hasMentionOfKind(binding, "contact")

            FactType.DATETIME_ISO,
            FactType.DATETIME_UNIX_MS -> binding.timeMentions.isNotEmpty()

            FactType.FILE_URI -> hasMentionOfKind(binding, "file")
            FactType.MEDIA_URI -> hasMentionOfKind(binding, "media")
            FactType.NOTE_ID -> hasMentionOfKind(binding, "note")
            FactType.PLACE_ADDRESS,
            FactType.PLACE_COORDINATES -> hasMentionOfKind(binding, "place")
            FactType.INSTALLED_APP -> binding.appHint.isNotBlank() || hasMentionOfKind(binding, "app")
            else -> false
        }
    }

    private fun chooseMention(
        factType: FactType,
        binding: CapabilityBinder.BoundAction,
        fallback: String
    ): String {
        val kind = when (factType) {
            FactType.CONTACT_PHONE,
            FactType.CONTACT_EMAIL -> "contact"
            FactType.DATETIME_ISO,
            FactType.DATETIME_UNIX_MS,
            FactType.CURRENT_TIME -> "time"
            FactType.FILE_URI -> "file"
            FactType.MEDIA_URI -> "media"
            FactType.NOTE_ID -> "note"
            FactType.PLACE_ADDRESS,
            FactType.PLACE_COORDINATES,
            FactType.DEVICE_LOCATION -> "place"
            FactType.INSTALLED_APP -> "app"
            else -> ""
        }

        if (kind == "time") {
            return binding.timeMentions.firstOrNull()
                ?: binding.taskTarget.takeIf { it.isNotBlank() }
                ?: fallback
        }

        if (kind == "app") {
            return binding.appHint.takeIf { it.isNotBlank() }
                ?: binding.entityMentions.firstOrNull { it.kind.equals("app", ignoreCase = true) }?.text
                ?: binding.taskTarget.takeIf { it.isNotBlank() }
                ?: fallback
        }

        if (kind.isNotBlank()) {
            return binding.entityMentions.firstOrNull { it.kind.equals(kind, ignoreCase = true) }?.text
                ?: binding.taskTarget.takeIf { it.isNotBlank() }
                ?: fallback
        }

        return binding.taskTarget.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun hasMentionOfKind(
        binding: CapabilityBinder.BoundAction,
        kind: String
    ): Boolean {
        return binding.entityMentions.any { it.kind.equals(kind, ignoreCase = true) } ||
            binding.taskTarget.isNotBlank()
    }
}
