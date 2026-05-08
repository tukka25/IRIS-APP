package com.gemmaworkflow.platform.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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

    /** Prompt-native format used by GemmaWorkflow: TOOL: name {"key":"value"} */
    private val toolCallRegex = Regex("""TOOL:\s*([A-Za-z0-9_.-]+)\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL)

    /** Gemma 4 Hugging Face chat-template format. */
    private val gemma4ToolCallRegex =
        Regex("""<\|tool_call>call:([A-Za-z0-9_.-]+)\{(.*?)\}<tool_call\|>""", RegexOption.DOT_MATCHES_ALL)

    /** FunctionGemma documented format. */
    private val functionGemmaToolCallRegex =
        Regex("""<start_function_call>call:([A-Za-z0-9_.-]+)\{(.*?)\}<end_function_call>""", RegexOption.DOT_MATCHES_ALL)

    private val functionArgRegex = Regex(
        """([A-Za-z0-9_]+)\s*:\s*(?:<\|"\|>(.*?)<\|"\|>|<escape>(.*?)<escape>|([^,}]+))""",
        RegexOption.DOT_MATCHES_ALL
    )

    /**
     * Find the first tool call in the given text.
     * Returns null if no tool call is found.
     */
    fun findToolCall(text: String): ToolCall? {
        return findToolPrefixCall(text)
            ?: findGemmaTokenCall(text)
            ?: findJsonFunctionCall(text)
    }

    private fun findToolPrefixCall(text: String): ToolCall? {
        val match = toolCallRegex.find(text) ?: return null
        val toolName = match.groupValues[1]

        return try {
            val obj = json.parseToJsonElement(match.groupValues[2]) as? JsonObject ?: return null
            val params = obj.toStringParams()
            ToolCall(name = toolName, params = params, rawMatch = match.value)
        } catch (e: Exception) {
            null // Invalid JSON — skip
        }
    }

    private fun findGemmaTokenCall(text: String): ToolCall? {
        val match = gemma4ToolCallRegex.find(text)
            ?: functionGemmaToolCallRegex.find(text)
            ?: return null
        val toolName = match.groupValues[1]
        val params = parseFunctionStyleArgs(match.groupValues[2])
        return ToolCall(name = toolName, params = params, rawMatch = match.value)
    }

    private fun findJsonFunctionCall(text: String): ToolCall? {
        val element = parseLikelyJsonObject(text) ?: return null
        val root = element as? JsonObject ?: return null

        val functionRoot = root["function"]?.asObjectOrNull()
            ?: root["toolSpec"]?.asObjectOrNull()
            ?: root

        val name = functionRoot.stringValue("tool")
            ?: functionRoot.stringValue("tool_name")
            ?: functionRoot.stringValue("name")
            ?: return null

        val argsElement = functionRoot["params"]
            ?: functionRoot["arguments"]
            ?: functionRoot["args"]
            ?: return null

        val args = when (argsElement) {
            is JsonObject -> argsElement.toStringParams()
            is JsonPrimitive -> parseJsonObjectString(argsElement.content)?.toStringParams()
            else -> null
        } ?: return null

        return ToolCall(name = name, params = args, rawMatch = text.trim())
    }

    private fun parseLikelyJsonObject(text: String): JsonElement? {
        val trimmed = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return parseJsonObjectString(trimmed)
            ?: run {
                val start = trimmed.indexOf('{')
                val end = trimmed.lastIndexOf('}')
                if (start >= 0 && end > start) parseJsonObjectString(trimmed.substring(start, end + 1)) else null
            }
    }

    private fun parseJsonObjectString(value: String): JsonObject? {
        return runCatching { json.parseToJsonElement(value) as? JsonObject }.getOrNull()
    }

    private fun parseFunctionStyleArgs(args: String): Map<String, String> {
        return functionArgRegex.findAll(args).associate { match ->
            val key = match.groupValues[1]
            val value = match.groupValues.drop(2).firstOrNull { it.isNotBlank() }.orEmpty().trim()
            key to value.trim('"', '\'')
        }
    }

    private fun JsonObject.toStringParams(): Map<String, String> {
        return entries.associate { (key, value) ->
            val stringValue = when (value) {
                is JsonPrimitive -> value.content
                else -> value.toString()
            }
            key to stringValue
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return runCatching { jsonObject }.getOrNull()
    }

    private fun JsonObject.stringValue(key: String): String? {
        return (this[key] as? JsonPrimitive)?.jsonPrimitive?.content
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
