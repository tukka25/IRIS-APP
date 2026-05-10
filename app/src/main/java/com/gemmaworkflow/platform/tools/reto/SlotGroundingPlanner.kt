package com.gemmaworkflow.platform.tools.reto

import android.util.Log
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.catalog.LogicalAction
import com.gemmaworkflow.domain.catalog.ParamSpec
import com.gemmaworkflow.platform.tools.FindSkill
import com.gemmaworkflow.platform.tools.ToolRegistry
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase 2 — constrained slot grounding.
 *
 * The model sees selected action schemas and a filtered resolver-tool list.
 * It decides, for each action param, whether the value is literal, requires a
 * tool, or is missing. Kotlin validates any tool choice against the param's
 * factType before creating executable requirements.
 */
object SlotGroundingPlanner {

    private const val TAG = "SlotGroundingPlanner"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class GroundingResult(
        val ledger: RequirementLedger,
        val slots: List<SlotGrounding>,
        val rawOutput: String,
        val fallbackUsed: Boolean = false
    )

    data class SlotGrounding(
        val taskId: String,
        val actionId: String,
        val param: String,
        val status: SlotStatus,
        val value: String?,
        val tool: String?,
        val toolArgs: Map<String, String>,
        val reason: String
    )

    enum class SlotStatus {
        LITERAL,
        NEEDS_TOOL,
        MISSING,
        UNUSED
    }

    suspend fun plan(
        engine: Engine,
        userRequest: String,
        boundActions: List<CapabilityBinder.BoundAction>
    ): GroundingResult {
        if (boundActions.isEmpty()) {
            return GroundingResult(RequirementLedger.EMPTY, emptyList(), "")
        }

        val prompt = buildPrompt(userRequest, boundActions)
        Log.d(TAG, "Phase 2 slot-grounding prompt: ${prompt.length} chars")

        return runCatching {
            val rawOutput = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)
                )
            ).use { conv -> conv.sendMessage(prompt).toString() }

            val slots = parseSlots(rawOutput)
            val ledger = buildLedger(userRequest, boundActions, slots)
            Log.i(TAG, "Slot grounding built ${ledger.requirements.size} tool requirements and ${ledger.literalSlots.size} literal slots")
            GroundingResult(ledger = ledger, slots = slots, rawOutput = rawOutput)
        }.getOrElse { error ->
            Log.w(TAG, "Slot grounding failed, falling back to deterministic builder: ${error.message}")
            GroundingResult(
                ledger = RequirementBuilder.build(userRequest, boundActions),
                slots = emptyList(),
                rawOutput = error.message.orEmpty(),
                fallbackUsed = true
            )
        }
    }

    fun buildLedger(
        userRequest: String,
        boundActions: List<CapabilityBinder.BoundAction>,
        slots: List<SlotGrounding>
    ): RequirementLedger {
        val requirements = mutableListOf<FactRequirement>()
        val literals = mutableListOf<GroundedSlotValue>()
        val handledSlotKeys = mutableSetOf<String>()
        var index = 1

        for (slot in slots) {
            val binding = boundActions.firstOrNull { it.taskId == slot.taskId && it.actionId == slot.actionId } ?: continue
            val spec = ActionSpecRegistry.find(slot.actionId) ?: continue
            val param = spec.params.firstOrNull { it.name == slot.param } ?: continue
            val slotKey = "${slot.actionId}:${slot.param}"

            when (slot.status) {
                SlotStatus.LITERAL -> {
                    val value = slot.value?.takeIf { it.isNotBlank() } ?: continue
                    handledSlotKeys.add(slotKey)
                    literals.add(
                        GroundedSlotValue(
                            sourceAction = slot.actionId,
                            slot = slot.param,
                            value = value,
                            reason = slot.reason
                        )
                    )
                }
                SlotStatus.NEEDS_TOOL -> {
                    val factType = param.factType ?: continue
                    val tool = slot.tool?.takeIf { it in resolverCandidatesForParam(spec, param) }
                        ?: continue
                    handledSlotKeys.add(slotKey)
                    requirements.add(
                        FactRequirement(
                            id = "r${index++}",
                            sourceAction = slot.actionId,
                            slot = slot.param,
                            mention = chooseMention(factType, binding, slot, userRequest),
                            factType = factType,
                            blocking = isBlockingSlot(param, binding),
                            resolverTool = tool,
                            toolArgs = sanitizeToolArgs(tool, slot.toolArgs)
                        )
                    )
                }
                SlotStatus.MISSING -> {
                    val factType = param.factType ?: continue
                    handledSlotKeys.add(slotKey)
                    requirements.add(
                        FactRequirement(
                            id = "r${index++}",
                            sourceAction = slot.actionId,
                            slot = slot.param,
                            mention = chooseMention(factType, binding, slot, userRequest),
                            factType = factType,
                            blocking = isBlockingSlot(param, binding),
                            status = RequirementStatus.FAILED,
                            failureReason = slot.reason.ifBlank { "Missing required information for ${slot.param}" }
                        )
                    )
                }
                SlotStatus.UNUSED -> handledSlotKeys.add(slotKey)
            }
        }

        val fallbackLedger = RequirementBuilder.build(userRequest, boundActions)
        fallbackLedger.requirements
            .filter { "${it.sourceAction}:${it.slot}" !in handledSlotKeys }
            .forEach { requirements.add(it.copy(id = "r${index++}")) }

        if (requirements.isEmpty() && literals.isEmpty()) {
            return fallbackLedger
        }

        return RequirementLedger(
            actionCandidates = boundActions.mapNotNull { it.actionId },
            requirements = requirements.distinctBy { "${it.sourceAction}:${it.slot}:${it.factType.label}:${it.mention}:${it.resolverTool}" },
            literalSlots = literals.distinctBy { "${it.sourceAction}:${it.slot}:${it.value}" }
        )
    }

    private fun buildPrompt(
        userRequest: String,
        boundActions: List<CapabilityBinder.BoundAction>
    ): String {
        val selectedActions = boundActions.mapNotNull { binding ->
            val actionId = binding.actionId ?: return@mapNotNull null
            val spec = ActionSpecRegistry.find(actionId) ?: return@mapNotNull null
            binding to spec
        }
        val allowedTools = selectedActions
            .flatMap { (_, spec) -> ActionSpecRegistry.toolNamesForAction(spec) }
            .toSet()
        val now = ZonedDateTime.now()
        val nowIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val timezone = now.zone.id

        return buildString {
            appendLine("You are the Slot Grounding Agent for GemmaWorkflow.")
            appendLine()
            appendLine("Your job is to decide how each selected action parameter should be filled.")
            appendLine("You do not execute tools directly. You output a JSON grounding plan that Kotlin validates and executes.")
            appendLine()
            appendLine("User request: \"$userRequest\"")
            appendLine("Current device time: $nowIso")
            appendLine("Current timezone: $timezone")
            appendLine()
            appendLine("Selected actions and parameter schemas:")
            selectedActions.forEach { (binding, spec) ->
                appendLine("- task_id=${binding.taskId}, action_id=${spec.id}")
                appendLine("  task: ${binding.taskDescription}")
                appendLine("  target: ${binding.taskTarget.ifBlank { "none" }}")
                appendLine("  app_hint: ${binding.appHint.ifBlank { "none" }}")
                if (binding.entityMentions.isNotEmpty()) {
                    appendLine("  entity_mentions: ${binding.entityMentions.joinToString { "${it.kind}:${it.text}" }}")
                }
                if (binding.timeMentions.isNotEmpty()) {
                    appendLine("  time_mentions: ${binding.timeMentions.joinToString()}")
                }
                val actionTools = ActionSpecRegistry.toolBindingsForAction(spec)
                if (actionTools.isNotEmpty()) {
                    appendLine("  action_tools:")
                    actionTools.forEach { tool ->
                        val scope = if (tool.paramNames.isEmpty()) {
                            "general"
                        } else {
                            "params=${tool.paramNames.joinToString()}"
                        }
                        appendLine("    - ${tool.toolName} ($scope): ${tool.purpose}")
                    }
                }
                appendLine("  params:")
                spec.params.forEach { param ->
                    appendLine("    - ${param.name}: type=${param.type.promptName}, required=${param.required}, fact_type=${param.factType?.label ?: "none"}")
                    val tools = resolverCandidatesForParam(spec, param)
                    if (tools.isNotEmpty()) {
                        appendLine("      resolver_candidates: ${tools.joinToString()}")
                    }
                    if (param.description.isNotBlank()) {
                        appendLine("      description: ${param.description}")
                    }
                }
            }
            appendLine()
            appendLine("Allowed resolver tool schemas:")
            appendLine(FindSkill.schemaFor(allowedTools))
            appendLine()
            appendLine("Return JSON only:")
            appendLine("""{
  "slots": [
    {
      "task_id": "t1",
      "action_id": "sms.compose",
      "param": "phone",
      "status": "needs_tool",
      "tool": "get_contact",
      "tool_args": { "name": "Maya" },
      "value": null,
      "reason": "Maya is a contact mention"
    },
    {
      "task_id": "t1",
      "action_id": "sms.compose",
      "param": "message",
      "status": "literal",
      "tool": null,
      "tool_args": {},
      "value": "hi",
      "reason": "The message body is explicitly stated"
    }
  ]
}""")
            appendLine()
            appendLine("Rules:")
            appendLine("- status must be one of: literal, needs_tool, missing, unused")
            appendLine("- Use literal when the value is directly in the user request.")
            appendLine("- Use needs_tool only when the param has resolver_candidates and the user gave a mention that must be grounded.")
            appendLine("- General action_tools are shared helpers for this action; only use them as needs_tool when they appear under that param's resolver_candidates.")
            appendLine("- tool must be exactly one of that param's resolver_candidates.")
            appendLine("- tool_args must match the tool schema exactly. Do not invent parameter names.")
            appendLine("- For datetime_millis params, prefer tool=resolve_datetime and use:")
            appendLine("  tool_args={\"expression\":\"normalized user phrase like next Friday at 6pm\",\"reference_time_iso\":\"$nowIso\",\"timezone\":\"$timezone\"}")
            appendLine("- If the user says '6 oclock' without AM/PM, preserve it as '6 o'clock' unless the request clearly says morning/evening/PM.")
            appendLine("- Use missing when a required param cannot be filled from the user request and has no usable mention.")
            appendLine("- Use unused for optional params that should not be filled.")
            appendLine("- Output one slot object for each required param, plus optional params only when useful.")
            appendLine("- Do not output Android intent actions, extras, package names, or implementation details.")
        }
    }

    private fun parseSlots(raw: String): List<SlotGrounding> {
        val extracted = extractJsonBlock(raw)
        val root = json.parseToJsonElement(extracted).jsonObject
        return root["slots"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            SlotGrounding(
                taskId = obj.string("task_id") ?: return@mapNotNull null,
                actionId = obj.string("action_id") ?: return@mapNotNull null,
                param = obj.string("param") ?: return@mapNotNull null,
                status = parseStatus(obj.string("status")),
                value = obj.string("value"),
                tool = obj.string("tool"),
                toolArgs = obj["tool_args"].toStringMap(),
                reason = obj.string("reason").orEmpty()
            )
        }.orEmpty()
    }

    private fun parseStatus(raw: String?): SlotStatus = when (raw?.lowercase()) {
        "literal" -> SlotStatus.LITERAL
        "needs_tool" -> SlotStatus.NEEDS_TOOL
        "missing" -> SlotStatus.MISSING
        else -> SlotStatus.UNUSED
    }

    private fun allowedResolverTools(factType: FactType): Set<String> {
        val matching = ToolMetadataRegistry.generationSafe()
            .filter { factType.label in it.produces && ToolLayerHint.PARAMETER_RESOLUTION in it.layerHints }
            .map { it.name }
        return (matching + factType.resolverTool)
            .filter { ToolRegistry.get(it) != null || ToolMetadataRegistry.get(it) != null }
            .toSet()
    }

    private fun resolverCandidatesForParam(
        spec: com.gemmaworkflow.domain.catalog.ActionSpec,
        param: ParamSpec
    ): Set<String> {
        val actionScopedTools = ActionSpecRegistry.toolNamesForAction(spec)
        val factTypeTools = param.factType?.let(::allowedResolverTools).orEmpty()
        val paramMappedTools = ActionSpecRegistry.resolverToolNamesForParam(spec, param)
        return (factTypeTools + paramMappedTools)
            .filter { it in actionScopedTools }
            .toSet()
    }

    private fun isBlockingSlot(
        param: ParamSpec,
        binding: CapabilityBinder.BoundAction
    ): Boolean {
        if (param.required) return true

        val logicalAction = LogicalAction.fromId(binding.taskAction)
        return when (param.factType) {
            FactType.CONTACT_PHONE,
            FactType.CONTACT_EMAIL -> logicalAction in setOf(LogicalAction.SendMessage, LogicalAction.MakeCall) &&
                hasEntityTarget(binding)

            else -> false
        }
    }

    private fun hasEntityTarget(binding: CapabilityBinder.BoundAction): Boolean {
        return binding.entityMentions.any { it.kind.equals("contact", ignoreCase = true) } ||
            binding.taskTarget.isNotBlank()
    }

    private fun sanitizeToolArgs(toolName: String, args: Map<String, String>): Map<String, String> {
        val tool = ToolRegistry.get(toolName) ?: return args
        val allowed = tool.parameters.map { it.name }.toSet()
        return args.filterKeys { it in allowed }
    }

    private fun chooseMention(
        factType: FactType,
        binding: CapabilityBinder.BoundAction,
        slot: SlotGrounding,
        fallback: String
    ): String {
        val firstArg = slot.toolArgs.values.firstOrNull { it.isNotBlank() }
        if (!firstArg.isNullOrBlank()) return firstArg

        return when (factType) {
            FactType.DATETIME_ISO,
            FactType.DATETIME_UNIX_MS,
            FactType.CURRENT_TIME -> binding.timeMentions.firstOrNull()
            FactType.INSTALLED_APP -> binding.appHint.takeIf { it.isNotBlank() }
            else -> binding.entityMentions.firstOrNull()?.text
        } ?: binding.taskTarget.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun JsonObject.string(name: String): String? {
        val element = this[name] ?: return null
        if (element.toString() == "null") return null
        return runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
    }

    private fun JsonElement?.toStringMap(): Map<String, String> {
        val obj = runCatching { this?.jsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapNotNull { (key, value) ->
            val content = runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
            if (content == null) null else key to content
        }.toMap()
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        if (start == -1) throw IllegalArgumentException("No JSON found")
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw IllegalArgumentException("Unclosed JSON")
    }
}
