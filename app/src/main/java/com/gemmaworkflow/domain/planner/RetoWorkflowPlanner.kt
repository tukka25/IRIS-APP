package com.gemmaworkflow.domain.planner

import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
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
    private companion object {
        const val TAG = "RetoWorkflowPlanner"
    }

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
        onPhase("Phase 2 — Raw Output", analysisRaw.take(500))

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

        return RetoWorkflowResult(
            analysisRaw = analysisRaw,
            actionPlanRaw = actionPlanRaw,
            workflowJsonRaw = jsonRaw,
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
            com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
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
            com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
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
            com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                    topK = 40, topP = 0.95, temperature = 0.2
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
}
