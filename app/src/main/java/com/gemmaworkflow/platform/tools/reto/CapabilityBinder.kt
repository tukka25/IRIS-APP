package com.gemmaworkflow.platform.tools.reto

import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phase 1 — Capability Binding.
 *
 * Takes logical tasks from Phase 0 and maps them to the app's
 * supported actions. Identifies what is supported, unsupported,
 * needs a workaround, or needs user clarification.
 */
object CapabilityBinder {

    private const val TAG = "CapabilityBinder"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class BoundAction(
        val taskId: String,          // references Phase 0 task ID
        val taskDescription: String, // original task description
        val actionId: String?,       // matched action ID, or null if unsupported
        val status: BindingStatus,   // supported, unsupported, workaround, needs_clarification
        val reason: String,          // why this binding was chosen
        val taskAction: String = "",
        val taskTarget: String = "",
        val appHint: String = "",
        val entityMentions: List<TaskDecomposer.EntityMention> = emptyList(),
        val timeMentions: List<String> = emptyList()
    )

    enum class BindingStatus {
        SUPPORTED, UNSUPPORTED, WORKAROUND, NEEDS_CLARIFICATION
    }

    data class BindingResult(
        val boundActions: List<BoundAction>,
        val rawOutput: String,
        val candidatesByTaskId: Map<String, List<String>> = emptyMap()
    ) {
        /** Only successfully bound actions (supported or workaround). */
        val actionableBindings: List<BoundAction>
            get() = boundActions.filter {
                it.actionId != null &&
                it.status in setOf(BindingStatus.SUPPORTED, BindingStatus.WORKAROUND)
            }

        val supportedActionIds: List<String>
            get() = actionableBindings.mapNotNull { it.actionId }
    }

    /**
     * Bind logical tasks to supported actions.
     */
    suspend fun bind(
        engine: Engine,
        tasks: List<TaskDecomposer.LogicalTask>,
        availableActionIds: Set<String> = ActionSpecRegistry.allIds
    ): BindingResult {
        if (tasks.isEmpty()) {
            return BindingResult(emptyList(), "")
        }

        val candidatesByTask = tasks.associate { task ->
            val candidates = ActionSpecRegistry.findByLogicalAction(
                action = task.action,
                availableActionIds = availableActionIds
            )
            task.id to candidates
        }

        val actionList = tasks.joinToString("\n\n") { task ->
            val candidates = candidatesByTask[task.id].orEmpty()
            buildString {
                appendLine("Task ${task.id} (${task.action}): ${task.description}")
                if (candidates.isEmpty()) {
                    appendLine("  - No available actions for this logical category on this device.")
                } else {
                    candidates.forEach { a ->
                        appendLine("  - ${a.id}: ${a.description} (params: ${a.params.joinToString { p -> "${p.name}(${if (p.required) "required" else "optional"})" }})")
                    }
                }
            }.trimEnd()
        }

        val prompt = buildString {
            appendLine("You are a capability binder for GemmaWorkflow.")
            appendLine()
            appendLine("Given logical tasks from the user, choose the best available action for each task.")
            appendLine("Each task has already been filtered by its logical action category and by device availability.")
            appendLine()
            appendLine("Available actions by task (ONLY choose from the candidates under the same task):")
            appendLine(actionList)
            appendLine()
            appendLine("Logical tasks to bind:")
            tasks.forEach { task ->
                appendLine("  ${task.id}: ${task.description} (action type: ${task.action}, target: \"${task.target}\")")
            }
            appendLine()
            appendLine("For each task, decide:")
            appendLine("- SUPPORTED: a direct action exists")
            appendLine("- WORKAROUND: can be achieved through a different action")
            appendLine("- UNSUPPORTED: no action exists and no workaround possible")
            appendLine("- NEEDS_CLARIFICATION: the user's intent is too vague")
            appendLine()
            appendLine("Output JSON:")
            appendLine("""{
  "bound_actions": [
    {
      "task_id": "t1",
      "action_id": "sms.compose",
      "status": "SUPPORTED",
      "reason": "sms.compose directly handles sending messages"
    },
    {
      "task_id": "t2",
      "action_id": null,
      "status": "UNSUPPORTED",
      "reason": "No action exists for this task type"
    }
  ]
}""")
            appendLine()
            appendLine("Rules:")
            appendLine("- action_id: must be from that task's listed candidate actions, or null if unsupported")
            appendLine("- Never choose an action from another task's candidate list")
            appendLine("- status: one of SUPPORTED, WORKAROUND, UNSUPPORTED, NEEDS_CLARIFICATION")
            appendLine("- If a task has no candidates, set action_id=null and status=UNSUPPORTED")
            appendLine("- If a task can use a workaround, set status=WORKAROUND and explain in reason")
            appendLine("- Output ONLY valid JSON, no markdown")
        }

        Log.d(TAG, "Phase 1 prompt: ${prompt.length} chars")

        val rawOutput = engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }

        Log.d(TAG, "Phase 1 raw: ${rawOutput.take(500)}")

        val bound = parseBinding(rawOutput, tasks, candidatesByTask.mapValues { (_, candidates) -> candidates.map { it.id }.toSet() })
        Log.i(TAG, "Bound ${bound.size} actions: ${bound.filter { it.actionId != null }.joinToString { "${it.taskId}=${it.actionId}" }}")

        return BindingResult(
            boundActions = bound,
            rawOutput = rawOutput,
            candidatesByTaskId = candidatesByTask.mapValues { (_, candidates) -> candidates.map { it.id } }
        )
    }

    private fun parseBinding(
        raw: String,
        tasks: List<TaskDecomposer.LogicalTask>,
        allowedByTask: Map<String, Set<String>>
    ): List<BoundAction> {
        return runCatching {
            val extracted = extractJsonBlock(raw)
            val root = json.parseToJsonElement(extracted).jsonObject
            root["bound_actions"]?.jsonArray?.mapNotNull { element ->
                val obj = element.jsonObject
                val taskId = obj["task_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val task = tasks.find { it.id == taskId }
                val rawActionId = obj["action_id"]?.jsonPrimitive?.content
                    ?.takeUnless { it.equals("null", ignoreCase = true) }
                val allowedIds = allowedByTask[taskId].orEmpty()
                val actionId = rawActionId?.takeIf { it in allowedIds }
                val status = parseStatus(obj["status"]?.jsonPrimitive?.content)
                val reason = obj["reason"]?.jsonPrimitive?.content ?: ""

                if (rawActionId != null && actionId == null) {
                    return@mapNotNull BoundAction(
                        taskId = taskId,
                        taskDescription = task?.description ?: "",
                        actionId = null,
                        status = BindingStatus.UNSUPPORTED,
                        reason = "Model selected '$rawActionId', but it was not in the filtered candidate list for task $taskId.",
                        taskAction = task?.action.orEmpty(),
                        taskTarget = task?.target.orEmpty(),
                        appHint = task?.appHint.orEmpty(),
                        entityMentions = task?.entityMentions.orEmpty(),
                        timeMentions = task?.timeMentions.orEmpty()
                    )
                }

                BoundAction(
                    taskId = taskId,
                    taskDescription = task?.description ?: "",
                    actionId = actionId,
                    status = status,
                    reason = reason,
                    taskAction = task?.action.orEmpty(),
                    taskTarget = task?.target.orEmpty(),
                    appHint = task?.appHint.orEmpty(),
                    entityMentions = task?.entityMentions.orEmpty(),
                    timeMentions = task?.timeMentions.orEmpty()
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun parseStatus(raw: String?): BindingStatus = when (raw?.uppercase()) {
        "SUPPORTED" -> BindingStatus.SUPPORTED
        "WORKAROUND" -> BindingStatus.WORKAROUND
        "NEEDS_CLARIFICATION" -> BindingStatus.NEEDS_CLARIFICATION
        else -> BindingStatus.UNSUPPORTED
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
