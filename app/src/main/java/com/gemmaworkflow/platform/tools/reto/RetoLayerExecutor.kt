package com.gemmaworkflow.platform.tools.reto

import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.gemmaworkflow.platform.tools.ToolAwareGenerator
import com.gemmaworkflow.platform.tools.ToolCallParser
import com.gemmaworkflow.platform.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes RETO layers one at a time with constrained tool sets.
 *
 * v2: Minimum-call enforcement.
 * Tracks which tools have been called. If the model signals completion
 * before all tools in the layer have been tried, injects a nudge prompt
 * listing the uncalled tools. Requires 2 consecutive turns with no new
 * tool calls before accepting completion (prevents premature stop).
 */
class RetoLayerExecutor(
    private val engine: Engine,
    private val userRequest: String,
    private val installedApps: String = "",
    private val temperature: Float = 0.4f,
    private val maxCallsPerLayer: Int = 5,
    private val maxRepairsPerTool: Int = 2
) {
    private val observationStore = ObservationStore()
    private val repairs = mutableListOf<RetoRepairRecord>()
    private val repairAgent = RetoRepairAgent(engine)

    private companion object {
        const val TAG = "RetoLayerExecutor"
    }

    /**
     * Execute all layers in the sketch and return compact observations.
     */
    suspend fun executeLayers(sketch: RetoLayerSketch): ExecuteResult = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting layer execution: ${sketch.layers.size} layers")

        for (layer in sketch.layers) {
            Log.i(TAG, "--- Layer ${layer.index}: ${layer.objective} ---")
            Log.d(TAG, "  Allowed tools: ${layer.allowedTools.joinToString()}")

            val entities = sketch.detectedEntities[layer.index] ?: emptyList()
            if (entities.isNotEmpty()) {
                Log.d(TAG, "  Detected entities: ${entities.joinToString { "[${it.category}] ${it.text}" }}")
            }

            val success = executeLayer(layer, entities)
            if (!success) {
                Log.w(TAG, "Layer ${layer.index} did not complete — continuing with partial observations")
            }
        }

        Log.i(TAG, "Layer execution complete: ${observationStore.all().size} observations, ${repairs.size} repairs")

        ExecuteResult(
            observations = observationStore.all(),
            repairs = repairs,
            compactSummary = observationStore.compactSummary(sketch.layers.size),
            debugTrace = observationStore.debugTrace()
        )
    }

    /**
     * Execute a single layer with minimum-call enforcement.
     *
     * Tracks which distinct tools were called. If the model signals
     * LAYER_DONE without calling all tools, injects a nudge listing
     * uncalled tools. Requires 2 idle turns before accepting completion.
     */
    private suspend fun executeLayer(layer: RetoLayer, detectedEntities: List<DetectedEntity> = emptyList()): Boolean {
        val obsSummary = observationStore.compactSummary(layer.index)
        val distinctToolsCalled = mutableSetOf<String>()
        var idleTurns = 0  // consecutive turns with no new tool call

        var prompt = RetoPromptBuilder.buildLayerExecutionPrompt(
            layer = layer,
            observationSummary = obsSummary,
            userRequest = userRequest,
            installedApps = installedApps,
            detectedEntities = detectedEntities
        )

        var callsInLayer = 0

        while (callsInLayer < maxCallsPerLayer) {
            val generator = ToolAwareGenerator(
                engine = engine,
                allowedTools = layer.allowedTools,
                maxToolCalls = 1,  // one call at a time so we can inspect
                temperature = temperature,
                onToolCall = { event ->
                    Log.d(TAG, "Layer ${layer.index} tool event: ${event.type} ${event.toolName}")
                    if (event.type == ToolAwareGenerator.ToolCallEventType.CALL ||
                        event.type == ToolAwareGenerator.ToolCallEventType.SUCCESS) {
                        distinctToolsCalled.add(event.toolName)
                        idleTurns = 0  // reset idle counter
                    }
                }
            )

            val output = generator.generate(prompt)
            val toolCall = ToolCallParser.findToolCall(output)

            callsInLayer++

            if (toolCall == null) {
                // No tool call — model might be done or might be signaling completion
                idleTurns++

                if (output.contains("LAYER_DONE")) {
                    // Model signaled completion — check if all tools were tried
                    val uncalled = layer.allowedTools - distinctToolsCalled
                    if (uncalled.isNotEmpty() && idleTurns < 2) {
                        // Model stopped early — inject a nudge
                        val nudge = buildString {
                            appendLine()
                            appendLine("You signaled LAYER_DONE, but these tools have NOT been called yet:")
                            uncalled.forEach { tool ->
                                val t = ToolRegistry.get(tool)
                                appendLine("- $tool: ${t?.description ?: "no description"}")
                            }
                        appendLine()
                        appendLine("Read each tool's description above. Does it match anything in the user request?")
                        appendLine("If yes → call it. If no → explicitly say which tool you're skipping and why.")
                        appendLine("Then output LAYER_DONE again.")
                        }
                        prompt = buildString {
                            appendLine(output)
                            append(nudge)
                        }
                        Log.d(TAG, "Layer ${layer.index}: injected nudge for uncalled tools: ${uncalled.joinToString()}")
                        continue
                    }

                    // All tools called (or idle for 2 turns with no matches)
                    Log.d(TAG, "Layer ${layer.index} signaled LAYER_DONE — ${distinctToolsCalled.size} tools called: ${distinctToolsCalled.joinToString()}")
                    return true
                }

                // No tool call AND no LAYER_DONE — model produced text without signaling
                if (idleTurns >= 2) {
                    Log.d(TAG, "Layer ${layer.index}: 2 idle turns — accepting completion")
                    return true
                }

                // Feed output back as prompt for context
                prompt = output
                continue
            }

            // Tool call found — reset idle counter and record
            idleTurns = 0
            distinctToolsCalled.add(toolCall.name)

            // Execute the tool
            val result = ToolRegistry.execute(toolCall.name, toolCall.params)
            val resultBlock = ToolCallParser.formatResult(toolCall.name, result)

            // Build next prompt: previous output + tool result
            prompt = buildString {
                appendLine(output)
                appendLine()
                appendLine("MODEL_TOOL_CALL:")
                appendLine(toolCall.rawMatch)
                appendLine()
                append(resultBlock)
                appendLine()
                appendLine("TOOL_RESULT received. Continue with remaining tools if any descriptions still match.")
            }
        }

        Log.w(TAG, "Layer ${layer.index}: max calls ($maxCallsPerLayer) reached")
        return distinctToolsCalled.isNotEmpty()  // partial success
    }

    data class ExecuteResult(
        val observations: List<ToolObservation>,
        val repairs: List<RetoRepairRecord>,
        val compactSummary: String,
        val debugTrace: String
    )
}

/**
 * Lightweight repair agent that fixes bad tool call params.
 */
class RetoRepairAgent(private val engine: Engine) {

    suspend fun repair(
        toolName: String,
        originalArgs: Map<String, String>,
        errorMessage: String,
        attemptNumber: Int,
        metadata: ToolMetadata?
    ): Map<String, String>? {
        if (metadata != null && !metadata.repairable) return null
        if (attemptNumber > metadata?.maxRepairAttempts ?: 2) return null

        val tool = ToolRegistry.get(toolName) ?: return null

        val paramList = tool.parameters.joinToString("\n") {
            "  ${it.name} (${it.type}, ${if (it.required) "required" else "optional"}): ${it.description}"
        }

        val repairPrompt = RetoPromptBuilder.buildRepairPrompt(
            toolName = toolName,
            toolSchema = "Tool: $toolName\nParameters:\n$paramList",
            originalArgs = originalArgs.toString(),
            errorMessage = errorMessage,
            attemptNumber = attemptNumber
        )

        val result = engine.createConversation(
            com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                    topK = 40, topP = 0.95, temperature = 0.2
                )
            )
        ).use { conv -> conv.sendMessage(repairPrompt).toString() }

        val repaired = ToolCallParser.findToolCall(result)
        return repaired?.params
    }
}
