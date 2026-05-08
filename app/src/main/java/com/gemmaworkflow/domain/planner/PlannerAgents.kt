package com.gemmaworkflow.domain.planner

import com.google.ai.edge.litertlm.Engine
import com.gemmaworkflow.platform.tools.ToolAwareGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Logical planner stages that reuse one loaded LiteRT-LM Engine.
 * Each "agent" is a call to the same model with a different system prompt.
 *
 * v3: Temperature is now configurable per stage.
 *     Stage 1 (tool selection): 0.4 — warmer, encourages tool use
 *     Stage 3 (action planning): 0.2 — conservative, deterministic
 *     Stage 4 (JSON output): 0.2 — strict, deterministic
 */
class PlannerAgents(private val engine: Engine) {

    /**
     * Stage 1: Analyze the user request.
     * Allowed: temporal tools only (2 tools).
     * Temperature: 0.4 (warmer — encourages tool calling per Gemma 4 best practices).
     */
    suspend fun requestAnalysis(
        prompt: String,
        allowedTools: Set<String> = emptySet(),
        temperature: Float = 0.4f,
        onToolEvent: (ToolAwareGenerator.ToolCallEvent) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        ToolAwareGenerator(
            engine = engine,
            allowedTools = allowedTools,
            maxToolCalls = 3,
            temperature = temperature,
            onToolCall = onToolEvent
        ).generate(prompt)
    }

    /**
     * Stage 3: Select concrete actions from available capabilities.
     * Allowed: temporal + search + execution + reasoning (13 tools).
     * Temperature: 0.2 (conservative).
     */
    suspend fun actionPlan(
        prompt: String,
        allowedTools: Set<String> = emptySet(),
        temperature: Float = 0.2f,
        onToolEvent: (ToolAwareGenerator.ToolCallEvent) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        ToolAwareGenerator(
            engine = engine,
            allowedTools = allowedTools,
            maxToolCalls = 5,
            temperature = temperature,
            onToolCall = onToolEvent
        ).generate(prompt)
    }

    /**
     * Stage 4: Output the final strict JSON workflow contract.
     * Allowed: validate_json + calculator (2 tools).
     * Temperature: 0.2 (strict, deterministic).
     */
    suspend fun workflowJson(
        prompt: String,
        allowedTools: Set<String> = emptySet(),
        temperature: Float = 0.2f,
        onToolEvent: (ToolAwareGenerator.ToolCallEvent) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        ToolAwareGenerator(
            engine = engine,
            allowedTools = allowedTools,
            maxToolCalls = 2,
            temperature = temperature,
            onToolCall = onToolEvent
        ).generate(prompt)
    }
}
