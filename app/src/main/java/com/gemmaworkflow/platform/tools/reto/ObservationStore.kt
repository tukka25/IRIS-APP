package com.gemmaworkflow.platform.tools.reto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import com.gemmaworkflow.platform.tools.ToolResult

/**
 * Compact observation memory for RETO.
 *
 * Stores ToolObservation objects and renders them as compact
 * summaries for inclusion in layer prompts. This replaces the
 * current approach of appending full raw transcript on every turn.
 */
class ObservationStore {

    private val observations = mutableListOf<ToolObservation>()

    fun add(observation: ToolObservation) {
        observations.add(observation)
    }

    fun all(): List<ToolObservation> = observations.toList()

    fun fromLayer(layerIndex: Int): List<ToolObservation> =
        observations.filter { it.layerIndex == layerIndex }

    fun beforeLayer(layerIndex: Int): List<ToolObservation> =
        observations.filter { it.layerIndex < layerIndex }

    /**
     * Render compact summary for use in layer execution prompts.
     *
     * Only shows parsed facts, not raw tool output strings.
     * This keeps context small and focused.
     */
    fun compactSummary(forLayerIndex: Int): String {
        val previous = beforeLayer(forLayerIndex)
        if (previous.isEmpty()) return "none"

        return buildString {
            appendLine("Validated observations from earlier layers:")
            previous.forEachIndexed { i, obs ->
                appendLine()
                appendLine("Observation ${i + 1}:")
                appendLine("  tool: ${obs.toolName}")
                appendLine("  success: ${obs.success}")
                if (obs.parsedFacts.isNotEmpty()) {
                    appendLine("  facts:")
                    obs.parsedFacts.forEach { (key, value) ->
                        appendLine("    $key = $value")
                    }
                }
                if (obs.error != null) {
                    appendLine("  error: ${obs.error}")
                }
            }
        }
    }

    /**
     * Full trace for debug UI / Logcat.
     */
    fun debugTrace(): String = buildString {
        appendLine("=== RETO Observation Trace (${observations.size} calls) ===")
        observations.forEachIndexed { i, obs ->
            appendLine()
            appendLine("--- Call ${i + 1} (Layer ${obs.layerIndex}, Attempt ${obs.attempt}) ---")
            appendLine("  tool: ${obs.toolName}")
            appendLine("  success: ${obs.success}")
            if (obs.parsedFacts.isNotEmpty()) {
                appendLine("  facts: ${obs.parsedFacts}")
            }
            if (obs.error != null) appendLine("  error: ${obs.error}")
            appendLine("  output: ${obs.output.take(300)}")
        }
    }

    fun clear() {
        observations.clear()
    }

    companion object {
        /**
         * Create a ToolObservation from a tool execution result.
         */
        fun fromResult(
            toolName: String,
            params: Map<String, String>,
            result: ToolResult,
            layerIndex: Int,
            attempt: Int
        ): ToolObservation {
            val parsedFacts = ToolFactParserRegistry.parse(toolName, result)
            return ToolObservation(
                toolName = toolName,
                params = buildJsonObject {
                    params.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                },
                success = result.success,
                output = result.output,
                parsedFacts = parsedFacts,
                error = result.error,
                layerIndex = layerIndex,
                attempt = attempt
            )
        }
    }
}
