package com.gemmaworkflow.domain.planner

import android.util.Log
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.SamplerConfig
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.parser.WorkflowJsonParser
import com.gemmaworkflow.domain.safety.WorkflowValidator
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import com.gemmaworkflow.platform.tools.reto.CapabilityBinder
import com.gemmaworkflow.platform.tools.reto.CoverageValidator
import com.gemmaworkflow.platform.tools.reto.RequirementStatus
import com.gemmaworkflow.platform.tools.reto.RetoOrchestrator
import com.gemmaworkflow.platform.tools.reto.RetoTrace
import com.gemmaworkflow.platform.tools.reto.TaskDecomposer

/**
 * RETO-backed workflow planner — v4: capability-first requirement-led architecture.
 *
 * Flow:
 *   Phase 0 — SLM decomposes logical tasks
 *   Phase 1 — SLM chooses action IDs from Kotlin-filtered capability candidates
 *   Phase 2 — SLM maps selected ActionSpec params to literals/tools
 *   Phase 3 — Kotlin resolves planned requirements through scoped tools
 *   Phase 4 — SLM formats final workflow JSON from grounded params
 */
class RetoWorkflowPlanner(
    private val engine: Engine,
    private val capabilityScanner: PackageCapabilityScanner
) {
    /** Exposed for debug UI — last Phase 0 result. */
    var lastDecomposition: TaskDecomposer.DecompositionResult? = null
        private set

    /** Exposed for debug UI — last Phase 1 result. */
    var lastBinding: CapabilityBinder.BindingResult? = null
        private set

    suspend fun generateWorkflow(
        userRequest: String,
        onTrace: (RetoTrace) -> Unit = {},
        onPhase: (String, String) -> Unit = { _, _ -> }
    ): RetoWorkflowResult {
        Log.i(TAG, "Generating workflow via RETO (requirement-led) for: ${userRequest.take(80)}")

        val installedAppsSummary = capabilityScanner.installedAppsPromptSummary()
        val resolvableIds = capabilityScanner.resolvableActions(ActionSpecRegistry.allIds)
        val availableActions = ActionSpecRegistry.all.filter { it.id in resolvableIds }
        val capabilitySummary = ActionSpecRegistry.toPromptSummary(availableActions)

        // Phase 0-2: RETO orchestration (decompose, bind, ground, resolve facts)
        val orchestrator = RetoOrchestrator(engine)
        val orchestrateResult = orchestrator.orchestrate(
            userRequest = userRequest,
            availableActionIds = resolvableIds,
            onPhase = onPhase
        )

        onTrace(orchestrateResult.trace)
        lastDecomposition = orchestrateResult.decomposition
        lastBinding = orchestrateResult.binding
        val coverage = CoverageValidator.validate(orchestrateResult.ledger)
        Log.d(TAG, "Ledger: ${orchestrateResult.ledger.requirements.size} requirements, ${orchestrateResult.ledger.blockingRequirements.size} blocking")
        Log.d(TAG, "Coverage: ${if (coverage.isComplete) "COMPLETE" else "INCOMPLETE (${coverage.missingBlocking.size} missing)"}")

        onPhase("Phase 0 — Requirements", buildString {
            appendLine("Actions: ${orchestrateResult.ledger.actionCandidates.joinToString()}")
            appendLine()
            orchestrateResult.ledger.requirements.forEach { req ->
                val status = when (req.status) {
                    RequirementStatus.RESOLVED -> "RESOLVED"
                    RequirementStatus.FAILED -> "FAILED"
                    RequirementStatus.PENDING -> "PENDING"
                    else -> req.status.name
                }
                appendLine("${req.id}: ${req.factType.label} from \"${req.mention}\" → $status (${if (req.blocking) "blocking" else "optional"})")
            }
        })
        onPhase("Phase 0 — Coverage", coverage.summary())
        onPhase("Phase 0 — Raw Debug", orchestrateResult.debugTrace)

        // Phase 2: Build request analysis from resolved facts
        onPhase("Phase 2 — Analysis Prompt", "Building request analysis from ${orchestrateResult.ledger.resolvedRequirements.size} resolved facts")
        val analysisRaw = buildRequestAnalysisFromFacts(
            userRequest = userRequest,
            installedApps = installedAppsSummary,
            compactFacts = orchestrateResult.compactSummary
        )
        onPhase("Phase 2 — Raw Output", analysisRaw)

        // Phase 3: Build action plan from resolved facts
        onPhase("Phase 3 — Action Plan", "Building action plan from resolved facts")
        val actionPlanRaw = buildActionPlanFromFacts(
            goal = userRequest,
            compactFacts = orchestrateResult.compactSummary,
            availableActions = capabilitySummary
        )
        onPhase("Phase 3 — Raw Output", actionPlanRaw)

        // Phase 4: Final workflow JSON
        onPhase("Phase 4 — JSON Generation", "Formatting final workflow JSON")
        val jsonRaw = buildWorkflowJson(
            goal = userRequest,
            actionPlanRaw = actionPlanRaw,
            capabilitySummary = capabilitySummary,
            compactFacts = orchestrateResult.compactSummary
        )
        onPhase("Phase 4 — Raw Output", jsonRaw)
        val verifiedJsonRaw = verifyAndRepairWorkflowJson(
            initialJson = jsonRaw,
            userRequest = userRequest,
            capabilitySummary = capabilitySummary,
            compactFacts = orchestrateResult.compactSummary,
            availableActionIds = resolvableIds,
            onPhase = onPhase
        )
        onPhase("Phase 4 — Verified JSON", verifiedJsonRaw)

        return RetoWorkflowResult(
            analysisRaw = analysisRaw,
            actionPlanRaw = actionPlanRaw,
            workflowJsonRaw = verifiedJsonRaw,
            trace = orchestrateResult.trace,
            ledger = orchestrateResult.ledger,
            debugTrace = orchestrateResult.debugTrace
        )
    }

    private suspend fun buildRequestAnalysisFromFacts(
        userRequest: String,
        installedApps: String,
        compactFacts: String
    ): String {
        val prompt = buildString {
            appendLine("You are a request analyzer for GemmaWorkflow.")
            appendLine()
            appendLine("User request: \"$userRequest\"")
            appendLine()
            appendLine(installedApps)
            appendLine()
            appendLine("Resolved facts from device:")
            appendLine(compactFacts)
            appendLine()
            appendLine("Analyze the request. You do NOT need to call any tools — all facts are already resolved above.")
            appendLine("Return JSON only:")
            appendLine("""{
  "goal": "concise goal statement",
  "trigger_hint": "manual" | "time" | "nfc" | "share_sheet" | "tasker_setup_required",
  "schedule_hints": { "hour": 9, "minute": 0, "repeat_days": [] } or null,
  "applications": [{ "requested_name": "...", "selected_app_label": "...", "package_name": "...", "confidence": "high" }],
  "candidate_app_categories": [...],
  "missing_info": [...]
}""")
        }

        return engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 40, topP = 0.95, temperature = 0.4
                )
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }
    }

    private suspend fun buildActionPlanFromFacts(
        goal: String,
        compactFacts: String,
        availableActions: String
    ): String {
        val prompt = buildString {
            appendLine("You are an action planner for GemmaWorkflow.")
            appendLine("Goal: $goal")
            appendLine()
            appendLine("Gathered facts:")
            appendLine(compactFacts)
            appendLine()
            appendLine(availableActions)
            appendLine()
            appendLine("Select concrete actions using the gathered facts. Return JSON only.")
            appendLine("Use values from 'Grounded action params' as final action params when they match the selected action schema.")
            appendLine("Do not list missing setup for facts that were already resolved.")
        }

        return engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 40, topP = 0.95, temperature = 0.2
                )
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }
    }

    private suspend fun buildWorkflowJson(
        goal: String,
        actionPlanRaw: String,
        capabilitySummary: String,
        compactFacts: String
    ): String {
        val prompt = PromptBuilder.buildWorkflowJsonPrompt(
            goal = goal,
            actionPlanJson = actionPlanRaw,
            catalogSummary = capabilitySummary,
            groundedFacts = compactFacts
        )

        return engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 40, topP = 0.95, temperature = 0.2
                )
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }
    }

    private suspend fun verifyAndRepairWorkflowJson(
        initialJson: String,
        userRequest: String,
        capabilitySummary: String,
        compactFacts: String,
        availableActionIds: Set<String>,
        onPhase: (String, String) -> Unit
    ): String {
        var candidate = initialJson
        repeat(JSON_REPAIR_ATTEMPTS + 1) { attempt ->
            val errors = verifyWorkflowJson(candidate, availableActionIds)
            if (errors.isEmpty()) {
                val label = if (attempt == 0) "initial output" else "repair attempt $attempt"
                onPhase("Phase 4 — JSON Verification", "Passed on $label.")
                return candidate
            }

            onPhase(
                "Phase 4 — JSON Verification",
                buildString {
                    appendLine("Attempt ${attempt + 1} failed:")
                    errors.forEach { appendLine("- $it") }
                }.trim()
            )

            if (attempt == JSON_REPAIR_ATTEMPTS) {
                return candidate
            }

            candidate = repairWorkflowJsonWithModel(
                userRequest = userRequest,
                capabilitySummary = capabilitySummary,
                compactFacts = compactFacts,
                previousJson = candidate,
                errors = errors
            )
            onPhase("Phase 4 — JSON Repair Raw Output", candidate)
        }
        return candidate
    }

    private fun verifyWorkflowJson(rawJson: String, availableActionIds: Set<String>): List<String> =
        runCatching {
            val workflow = WorkflowJsonParser.parse(rawJson)
            WorkflowValidator.validate(workflow, availableActionIds)
        }.getOrElse { error ->
            listOf("Parse error: ${error.message ?: error::class.java.simpleName}")
        }

    private suspend fun repairWorkflowJsonWithModel(
        userRequest: String,
        capabilitySummary: String,
        compactFacts: String,
        previousJson: String,
        errors: List<String>
    ): String {
        val prompt = buildString {
            appendLine("You are a strict JSON repair agent for GemmaWorkflow.")
            appendLine("The previous final workflow JSON failed parsing or validation.")
            appendLine("Return corrected JSON only. No markdown. No explanation. No trailing commas.")
            appendLine()
            appendLine("User request:")
            appendLine(userRequest)
            appendLine()
            appendLine("Available action schemas. Use ONLY these action IDs and param keys:")
            appendLine(capabilitySummary)
            appendLine()
            appendLine("Grounded facts and action params already resolved by Kotlin:")
            appendLine(compactFacts.ifBlank { "No grounded facts were provided." })
            appendLine()
            appendLine("Verifier errors:")
            errors.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Previous JSON:")
            appendLine(previousJson)
            appendLine()
            appendLine("Required root schema:")
            appendLine("""{
  "name": "short workflow name",
  "summary": "one sentence",
  "trigger": {
    "type": "manual" | "time" | "nfc" | "share_sheet" | "tasker_setup_required",
    "setup_state": "ready" | "needs_setup",
    "schedule": { "hour": 9, "minute": 0, "repeat_days": [] } or null
  },
  "actions": [
    {
      "id": "catalog.action.id",
      "params": {},
      "requires_confirmation": true or false
    }
  ],
  "missing_setup": []
}""")
            appendLine()
            appendLine("Repair rules:")
            appendLine("- Preserve the user's intent and the previously grounded facts.")
            appendLine("- Remove unknown action IDs and unknown params.")
            appendLine("- Fill required params when the value is present in grounded facts.")
            appendLine("- Keep numeric milliseconds as JSON numbers, not strings.")
            appendLine("- Never output arithmetic expressions such as 1777810000000 + 3600000. Compute the final value and output a single JSON number.")
            appendLine("- If calendar.create_event has begin_time_millis and no end_time_millis, compute end_time_millis as exactly one hour after begin_time_millis unless the user specified a duration.")
            appendLine("- Do not list missing_setup for values already present in params or grounded facts.")
        }

        return engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 20, topP = 0.9, temperature = 0.1
                )
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }
    }

    data class RetoWorkflowResult(
        val analysisRaw: String,
        val actionPlanRaw: String,
        val workflowJsonRaw: String,
        val trace: RetoTrace,
        val ledger: com.gemmaworkflow.platform.tools.reto.RequirementLedger,
        val debugTrace: String
    )

    private companion object {
        const val TAG = "RetoWorkflowPlanner"
        const val JSON_REPAIR_ATTEMPTS = 1
    }
}
