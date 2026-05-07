package com.gemmaworkflow.platform.tools

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps an SLM generation call with a tool execution loop.
 *
 * Flow:
 *   1. Send prompt to SLM
 *   2. Check if output contains TOOL: name {params}
 *   3. If yes: execute tool → inject TOOL_RESULT → go to 1
 *   4. If no: return final output
 *
 * This lets the SLM call tools like resolve_datetime or search_places
 * mid-generation, and receive the results in context.
 */
class ToolAwareGenerator(
    private val engine: Engine,
    private val allowedTools: Set<String> = emptySet(),
    private val maxToolCalls: Int = 5,
    private val onToolCall: (ToolCallEvent) -> Unit = {}
) {

    private val sampler = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)

    /**
     * Generate text with tool support.
     * The SLM can emit TOOL: name {params} and get results injected back.
     */
    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        var currentPrompt = prompt
        var toolCallCount = 0
        val finalOutput = StringBuilder()

        while (toolCallCount < maxToolCalls) {
            // Send to SLM
            val rawOutput = engine.createConversation(
                ConversationConfig(samplerConfig = sampler)
            ).use { conv ->
                conv.sendMessage(currentPrompt).toString()
            }

            // Check for tool calls
            val toolCall = ToolCallParser.findToolCall(rawOutput)

            if (toolCall == null) {
                // No tool call — this is the final output
                finalOutput.append(rawOutput)
                break
            }

            // Check if tool is allowed
            if (toolCall.name !in allowedTools && allowedTools.isNotEmpty()) {
                onToolCall(ToolCallEvent(
                    type = ToolCallEventType.DENIED,
                    toolName = toolCall.name,
                    input = rawOutput.take(100),
                    output = "Tool '${toolCall.name}' not available to this agent"
                ))
                finalOutput.append(rawOutput)
                break
            }

            // Execute the tool
            onToolCall(ToolCallEvent(
                type = ToolCallEventType.CALL,
                toolName = toolCall.name,
                input = toolCall.rawMatch
            ))

            val result = ToolRegistry.execute(toolCall.name, toolCall.params)

            onToolCall(ToolCallEvent(
                type = if (result.success) ToolCallEventType.SUCCESS else ToolCallEventType.FAILURE,
                toolName = toolCall.name,
                input = toolCall.rawMatch,
                output = if (result.success) result.output else (result.error ?: "Tool failed"),
                durationMs = 0
            ))

            // Inject result back into the prompt
            val resultBlock = ToolCallParser.formatResult(toolCall.name, result)
            currentPrompt = "$prompt\n\nThe tool '$currentPrompt' returned:\n$resultBlock\n\nContinue where you left off."

            toolCallCount++
        }

        finalOutput.toString().ifBlank { "No output generated after $toolCallCount tool calls" }
    }

    data class ToolCallEvent(
        val type: ToolCallEventType,
        val toolName: String,
        val input: String = "",
        val output: String = "",
        val durationMs: Long = 0
    )

    enum class ToolCallEventType {
        CALL, SUCCESS, FAILURE, DENIED
    }
}
