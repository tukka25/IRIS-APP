package com.gemmaworkflow.platform.tools

import com.gemmaworkflow.domain.planner.PlannerAgents

/**
 * Maps specific tools to specific planner agents.
 *
 * Context budget principle: each agent only sees tools it NEEDS.
 * This keeps prompts short and prevents the SLM from hallucinating
 * tool calls it shouldn't make.
 */
object AgentToolAssignments {

    /**
     * Stage 1 — RequestAnalysisAgent
     * Needs: temporal context + device awareness
     * Why: To understand "next Friday", "when I'm at the gym",
     *       and what apps are available.
     */
    val requestAnalysisTools = setOf(
        "get_current_time",      // When is "now"?
        "resolve_datetime",      // "next Friday at 6pm" → timestamp
        "compute_duration",      // "in 2 hours"
        "get_day_of_week",       // Quick day lookup
        "list_installed_apps",   // What can we automate?
        "get_device_location"    // Where is the user?
    )

    /**
     * Stage 3 — ActionPlanAgent
     * Needs: execution tools + search + intent resolution
     * Why: To select concrete actions with correct params,
     *       verify intents are resolvable, and search for
     *       places/contacts when the user is vague.
     */
    val actionPlanTools = setOf(
        "get_current_time",
        "resolve_datetime",
        "compute_duration",
        "resolve_intent",
        "web_search",
        "search_places",
        "lookup_contact",
        "send_intent",
        "open_uri",
        "share_text",
        "set_alarm",
        "create_calendar_event",
        "calculator"
    )

    /**
     * Stage 4 — WorkflowJsonAgent
     * Needs: validation only
     * Why: It just formats the final JSON. It should NOT
     *       call execution tools. It validates before output.
     */
    val workflowJsonTools = setOf(
        "validate_json",         // Check output before returning
        "calculator"             // Simple math if needed
    )

    /**
     * Capability grounding (Stage 2, deterministic Kotlin)
     * Uses ALL device tools but no LLM calls.
     * Not exposed to any agent — used by PlannerService directly.
     */
    val groundingTools = setOf(
        "list_installed_apps",
        "resolve_intent",
        "get_device_location",
        "get_current_time"
    )

    /**
     * Returns the prompt-safe tool listing for a specific agent role.
     */
    fun forAgent(agent: PlannerAgent): Set<String> = when (agent) {
        PlannerAgent.RequestAnalysis -> requestAnalysisTools
        PlannerAgent.ActionPlan -> actionPlanTools
        PlannerAgent.WorkflowJson -> workflowJsonTools
        PlannerAgent.Grounding -> groundingTools
    }

    enum class PlannerAgent {
        RequestAnalysis, ActionPlan, WorkflowJson, Grounding
    }
}
