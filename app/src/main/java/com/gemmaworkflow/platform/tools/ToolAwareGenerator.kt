package com.gemmaworkflow.platform.tools

import android.util.Log
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
    private val temperature: Float = 0.2f,
    private val onToolCall: (ToolCallEvent) -> Unit = {},
    private val schemaGate: Any? = null  // ToolSchemaGate type — avoids circular dependency
) {

    private val sampler = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature.toDouble())

    private companion object {
        const val TAG = "ToolAwareGenerator"
    }

    /**
     * Generate text with tool support.
     * The SLM can emit TOOL: name {params} and get results injected back.
     */
    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        var currentPrompt = prompt
        var toolCallCount = 0
        val transcript = StringBuilder(prompt)
        val finalOutput = StringBuilder()

        while (toolCallCount < maxToolCalls) {
            // Send to SLM
            val rawOutput = engine.createConversation(
                ConversationConfig(samplerConfig = sampler)
            ).use { conv ->
                conv.sendMessage(currentPrompt).toString()
            }
            Log.d(TAG, "SLM raw output (${rawOutput.length} chars): ${rawOutput.take(800)}")

            // Check for tool calls
            val toolCall = ToolCallParser.findToolCall(rawOutput)

            if (toolCall == null) {
                // No tool call — this is the final output
                Log.d(TAG, "No tool call parsed; treating output as final answer")
                finalOutput.append(rawOutput)
                break
            }
            Log.d(TAG, "Parsed tool call: ${toolCall.name} params=${toolCall.params}")

            // Check if tool is allowed
            if (toolCall.name !in allowedTools && allowedTools.isNotEmpty()) {
                Log.d(TAG, "Denied tool call: ${toolCall.name}; allowed=${allowedTools.joinToString()}")
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
            transcript.appendLine()
            transcript.appendLine("MODEL_TOOL_CALL:")
            transcript.appendLine(toolCall.rawMatch)
            transcript.appendLine()
            transcript.append(resultBlock)
            transcript.appendLine()
            transcript.appendLine("Use TOOL_RESULT above. If enough information is available, return the requested final JSON only. If another tool is still necessary, output exactly one more TOOL call and no other text.")
            currentPrompt = transcript.toString()

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
