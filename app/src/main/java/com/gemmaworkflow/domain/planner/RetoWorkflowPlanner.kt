package com.gemmaworkflow.domain.planner

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import com.gemmaworkflow.platform.tools.reto.RetoOrchestrator
import com.gemmaworkflow.platform.tools.reto.RetoPromptBuilder
import com.gemmaworkflow.platform.tools.reto.RetoTrace

/**
 * RETO-backed workflow planner that replaces the direct agent calls
 * in WorkflowGenerationViewModel with layer-constrained orchestration.
 *
 * Designed to produce the same output format (raw analysis string,
 * raw action plan string, raw workflow JSON string) so the existing
 * parsers and validators work unchanged.
 */
class RetoWorkflowPlanner(
    private val engine: Engine,
    private val context: Context,
    private val capabilityScanner: PackageCapabilityScanner
) {
    private companion object {
        const val TAG = "RetoWorkflowPlanner"
    }

    suspend fun generateWorkflow(
        userRequest: String,
        onTrace: (RetoTrace) -> Unit = {}
    ): RetoWorkflowResult {
        Log.i(TAG, "Generating workflow via RETO for: ${userRequest.take(80)}")

        val installedAppsSummary = capabilityScanner.installedAppsPromptSummary()
        val resolvableIds = capabilityScanner.resolvableActions(ActionSpecRegistry.allIds)
        val availableActions = ActionSpecRegistry.all.filter { it.id in resolvableIds }
        val capabilitySummary = ActionSpecRegistry.toPromptSummary(availableActions)

        // Phase 1: RETO orchestration (fact grounding + capability check)
        val orchestrator = RetoOrchestrator(engine, context)
        val orchestrateResult = orchestrator.orchestrate(
            userRequest = userRequest,
            installedAppsSummary = installedAppsSummary,
            needsIntentCheck = true
        )

        onTrace(orchestrateResult.trace)
        Log.d(TAG, "Orchestration observations:\n${orchestrateResult.debugTrace}")

        // Phase 2: Build request analysis from observations
        val analysisRaw = buildRequestAnalysisFromFacts(
            userRequest = userRequest,
            installedApps = installedAppsSummary,
            compactFacts = orchestrateResult.compactSummary
        )

        // Phase 3: Build action plan from observations
        val actionPlanRaw = buildActionPlanFromFacts(
            goal = userRequest,
            compactFacts = orchestrateResult.compactSummary,
            availableActions = capabilitySummary
        )

        // Phase 4: Final workflow JSON
        val jsonRaw = buildWorkflowJson(
            goal = userRequest,
            actionPlanRaw = actionPlanRaw,
            capabilitySummary = capabilitySummary
        )

        return RetoWorkflowResult(
            analysisRaw = analysisRaw,
            actionPlanRaw = actionPlanRaw,
            workflowJsonRaw = jsonRaw,
            observations = orchestrateResult.observations,
            trace = orchestrateResult.trace
        )
    }

    /**
     * Synthesize request analysis from RETO observations.
     *
     * Uses a lightweight SLM call with pre-resolved facts.
     */
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
            appendLine("Analyze the request. Return JSON only:")
            appendLine("""
            {
              "goal": "concise goal statement",
              "trigger_hint": "manual" | "time" | "nfc" | "share_sheet" | "tasker_setup_required",
              "schedule_hints": { "hour": 9, "minute": 0, "repeat_days": [] } or null,
              "applications": [{ "requested_name": "...", "selected_app_label": "...", "package_name": "...", "confidence": "high" }],
              "candidate_app_categories": [...],
              "missing_info": [...]
            }
            """.trimIndent())
        }

        return engine.createConversation(
            com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                    topK = 40, topP = 0.95, temperature = 0.4
                )
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }
    }

    /**
     * Build action plan from compact facts.
     */
    private suspend fun buildActionPlanFromFacts(
        goal: String,
        compactFacts: String,
        availableActions: String
    ): String {
        val prompt = RetoPromptBuilder.buildFinalActionPlanPrompt(
            goal = goal,
            observationSummary = compactFacts,
            availableActions = availableActions
        )

        return engine.createConversation(
            com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                    topK = 40, topP = 0.95, temperature = 0.2
                )
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }
    }

    /**
     * Build final workflow JSON.
     */
    private suspend fun buildWorkflowJson(
        goal: String,
        actionPlanRaw: String,
        capabilitySummary: String
    ): String {
        val prompt = PromptBuilder.buildWorkflowJsonPrompt(goal, actionPlanRaw, capabilitySummary)

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
        val observations: List<com.gemmaworkflow.platform.tools.reto.ToolObservation>,
        val trace: RetoTrace
    )
}
