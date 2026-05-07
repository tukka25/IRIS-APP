package com.gemmaworkflow.domain.planner

import com.google.ai.edge.litertlm.Engine
import com.gemmaworkflow.platform.tools.ToolAwareGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Logical planner stages that reuse one loaded LiteRT-LM Engine.
 * Each "agent" is a call to the same model with a different system prompt.
 *
 * v2: Agents can now call tools (resolve_datetime, search_places, etc.)
 *     via ToolAwareGenerator. Each agent stage receives only its allowed
 *     tool set to keep context minimal.
 */
class PlannerAgents(private val engine: Engine) {

    /**
     * Stage 1: Analyze the user request.
     * Allowed: temporal + device tools (6 tools)
     */
    suspend fun requestAnalysis(
        prompt: String,
        allowedTools: Set<String> = emptySet(),
        onToolEvent: (ToolAwareGenerator.ToolCallEvent) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        ToolAwareGenerator(
            engine = engine,
            allowedTools = allowedTools,
            maxToolCalls = 3,
            onToolCall = onToolEvent
        ).generate(prompt)
    }

    /**
     * Stage 3: Select concrete actions from available capabilities.
     * Allowed: temporal + search + execution + reasoning (13 tools)
     */
    suspend fun actionPlan(
        prompt: String,
        allowedTools: Set<String> = emptySet(),
        onToolEvent: (ToolAwareGenerator.ToolCallEvent) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        ToolAwareGenerator(
            engine = engine,
            allowedTools = allowedTools,
            maxToolCalls = 5,
            onToolCall = onToolEvent
        ).generate(prompt)
    }

    /**
     * Stage 4: Output the final strict JSON workflow contract.
     * Allowed: validate_json + calculator (2 tools)
     */
    suspend fun workflowJson(
        prompt: String,
        allowedTools: Set<String> = emptySet(),
        onToolEvent: (ToolAwareGenerator.ToolCallEvent) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        ToolAwareGenerator(
            engine = engine,
            allowedTools = allowedTools,
            maxToolCalls = 2,
            onToolCall = onToolEvent
        ).generate(prompt)
    }
}
