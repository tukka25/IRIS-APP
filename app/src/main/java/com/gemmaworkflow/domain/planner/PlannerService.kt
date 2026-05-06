package com.gemmaworkflow.domain.planner

import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.gemmaworkflow.domain.catalog.ActionSpec
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.parser.WorkflowJsonParser
import com.gemmaworkflow.domain.safety.WorkflowValidator
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the multi-stage planner pipeline over one LiteRT-LM engine.
 *
 * Pipeline:
 *   User request → Agent 1 (analysis) → Capability grounding →
 *   Agent 2 (action plan) → Agent 3 (JSON) → Parser → Validator → Preview
 */
class PlannerService(
    private val engine: Engine,
    private val capabilityScanner: PackageCapabilityScanner
) {
    companion object {
        private const val TAG = "PlannerService"
    }

    /**
     * Run the full planner pipeline and return a validated PlannedWorkflow.
     */
    suspend fun plan(userRequest: String): PlannerResult = withContext(Dispatchers.Default) {
        val agents = PlannerAgents(engine)

        // Stage 1: Request analysis
        Log.d(TAG, "Stage 1: Request analysis")
        val installedAppsSummary = capabilityScanner.installedAppsPromptSummary()
        val analysisPrompt = PromptBuilder.buildRequestAnalysisPrompt(
            userRequest = userRequest,
            installedApps = installedAppsSummary
        )
        val analysisRaw = agents.requestAnalysis(analysisPrompt)

        // Extract the trigger hint from analysis
        val analysis = RequestAnalysisParser.parse(analysisRaw)
        val triggerHint = analysis.normalizedTriggerHint

        // Stage 2: Capability grounding (deterministic Kotlin, no model call)
        Log.d(TAG, "Stage 2: Capability grounding")
        val resolvableIds = capabilityScanner.resolvableActions(ActionSpecRegistry.allIds)
        val availableActions = ActionSpecRegistry.all
            .filter { it.id in resolvableIds }
            .let { buildCapabilityPrompt(it) }
        val nativeDiscovery = capabilityScanner.nativeDiscoverySummary(
            requestedApplications = analysis.applicationSearchTerms,
            availableActionIds = resolvableIds
        )

        // Stage 3: Action plan
        Log.d(TAG, "Stage 3: Action plan")
        val actionPlanPrompt = PromptBuilder.buildActionPlanPrompt(
            goal = userRequest,
            triggerHint = triggerHint,
            availableActions = availableActions,
            nativeDiscovery = nativeDiscovery
        )
        val actionPlanRaw = agents.actionPlan(actionPlanPrompt)

        // Stage 4: Final JSON
        Log.d(TAG, "Stage 4: Final JSON")
        val jsonPrompt = PromptBuilder.buildWorkflowJsonPrompt(
            goal = userRequest,
            actionPlanJson = actionPlanRaw,
            catalogSummary = availableActions
        )
        val jsonRaw = agents.workflowJson(jsonPrompt)

        // Parse and validate
        Log.d(TAG, "Parsing and validating")
        val workflow = WorkflowJsonParser.parse(jsonRaw)
        val errors = WorkflowValidator.validate(workflow, resolvableIds)

        if (errors.isNotEmpty()) {
            Log.w(TAG, "Validation errors: $errors")
            PlannerResult.Failure(
                workflow = workflow,
                errors = errors
            )
        } else {
            Log.i(TAG, "Workflow generated successfully: ${workflow.name}")
            PlannerResult.Success(workflow)
        }
    }

    private fun buildCapabilityPrompt(actions: List<ActionSpec>): String {
        return ActionSpecRegistry.toPromptSummary(actions)
    }
}

/** Result of the planner pipeline. */
sealed class PlannerResult {
    data class Success(val workflow: PlannedWorkflow) : PlannerResult()
    data class Failure(
        val workflow: PlannedWorkflow,
        val errors: List<String>
    ) : PlannerResult()
}
