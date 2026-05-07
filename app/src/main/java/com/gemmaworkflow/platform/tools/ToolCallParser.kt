package com.gemmaworkflow.platform.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses tool calls from SLM-generated text.
 *
 * Format the SLM uses to call a tool:
 *   TOOL: tool_name {"key": "value", "key2": "value2"}
 *
 * The tool result is injected back as:
 *   TOOL_RESULT: tool_name
 *   <output text>
 */
object ToolCallParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Regex to find TOOL: name {params} in text. */
    private val toolCallRegex = Regex("""TOOL:\s*(\S+)\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Find the first tool call in the given text.
     * Returns null if no tool call is found.
     */
    fun findToolCall(text: String): ToolCall? {
        val match = toolCallRegex.find(text) ?: return null
        val toolName = match.groupValues[1]
        val paramsJson = match.groupValues[2]

        return try {
            // Regex captures inner content without braces — add them back for JSON parsing
            val jsonString = "{${match.groupValues[2]}}"
            val element = json.parseToJsonElement(jsonString)
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
            val params = obj.mapValues { it.value.jsonPrimitive.content }
            ToolCall(name = toolName, params = params, rawMatch = match.value)
        } catch (e: Exception) {
            null // Invalid JSON — skip
        }
    }

    /**
     * Format a tool result to inject back into the SLM context.
     */
    fun formatResult(name: String, result: ToolResult): String = buildString {
        appendLine("TOOL_RESULT: $name")
        if (result.success) {
            appendLine(result.output)
        } else {
            appendLine("ERROR: ${result.error ?: "Tool failed"}")
        }
    }

    data class ToolCall(
        val name: String,
        val params: Map<String, String>,
        val rawMatch: String
    )
}
