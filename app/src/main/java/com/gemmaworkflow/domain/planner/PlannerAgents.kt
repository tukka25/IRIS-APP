package com.gemmaworkflow.domain.planner

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Logical planner stages that reuse one loaded LiteRT-LM Engine.
 * Each "agent" is a call to the same model with a different system prompt.
 *
 * This avoids loading multiple model instances and keeps phone memory under control.
 */
class PlannerAgents(private val engine: Engine) {

    private val sampler = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)

    /**
     * Stage 1: Analyze the user request.
     */
    suspend fun requestAnalysis(prompt: String): String = withContext(Dispatchers.Default) {
        engine.createConversation(
            ConversationConfig(samplerConfig = sampler)
        ).use { conv ->
            conv.sendMessage(prompt).toString()
        }
    }

    /**
     * Stage 3: Select concrete actions from available capabilities.
     */
    suspend fun actionPlan(prompt: String): String = withContext(Dispatchers.Default) {
        engine.createConversation(
            ConversationConfig(samplerConfig = sampler)
        ).use { conv ->
            conv.sendMessage(prompt).toString()
        }
    }

    /**
     * Stage 4: Output the final strict JSON workflow contract.
     */
    suspend fun workflowJson(prompt: String): String = withContext(Dispatchers.Default) {
        engine.createConversation(
            ConversationConfig(samplerConfig = sampler)
        ).use { conv ->
            conv.sendMessage(prompt).toString()
        }
    }
}
