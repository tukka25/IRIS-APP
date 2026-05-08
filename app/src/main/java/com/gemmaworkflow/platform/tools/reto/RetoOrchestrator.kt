package com.gemmaworkflow.platform.tools.reto

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.gemmaworkflow.platform.tools.ToolRegistry

/**
 * Top-level RETO orchestrator for GemmaWorkflow.
 *
 * Coordinates:
 * 1. Deterministic layer planning (RetoLayerPlanner)
 * 2. Layer-by-layer execution (RetoLayerExecutor)
 * 3. Observation collection and compact summarization
 * 4. Final trace generation for debug UI
 *
 * Usage:
 *   val orchestrator = RetoOrchestrator(engine, context)
 *   val result = orchestrator.orchestrate(userRequest, installedApps)
 */
class RetoOrchestrator(
    private val engine: Engine,
    private val context: Context
) {
    private companion object {
        const val TAG = "RetoOrchestrator"
    }

    /**
     * Run full RETO orchestration for a user request.
     *
     * Returns compact observations that can be fed into the
     * action planning and JSON generation stages.
     */
    suspend fun orchestrate(
        userRequest: String,
        installedAppsSummary: String = "",
        needsIntentCheck: Boolean = true
    ): OrchestrateResult {
        Log.i(TAG, "Starting orchestration for: ${userRequest.take(80)}")

        // 1. Get available tools
        val availableTools = ToolRegistry.all().map { it.name }.toSet()
        Log.d(TAG, "Available tools: ${availableTools.joinToString()}")

        // 2. Plan layers deterministically
        val sketch = RetoLayerPlanner.plan(
            request = userRequest,
            installedAppsSummary = installedAppsSummary,
            availableTools = availableTools,
            needsIntentCheck = needsIntentCheck
        )

        Log.i(TAG, "Layer sketch: ${sketch.layers.size} layers")
        sketch.layers.forEach { layer ->
            Log.d(TAG, "  Layer ${layer.index}: ${layer.objective} [${layer.allowedTools.joinToString()}]")
        }

        // 3. Execute layers
        val executor = RetoLayerExecutor(
            engine = engine,
            userRequest = userRequest,
            installedApps = installedAppsSummary,
            temperature = 0.4f
        )

        val execResult = executor.executeLayers(sketch)

        // 4. Build result trace
        val trace = RetoTrace(
            request = userRequest,
            layerSketch = sketch,
            observations = execResult.observations,
            repairs = execResult.repairs,
            finalWorkflowJson = null  // filled in later by WorkflowJsonAgent
        )

        Log.i(TAG, "Orchestration complete: ${execResult.observations.size} observations, ${execResult.repairs.size} repairs")

        return OrchestrateResult(
            sketch = sketch,
            observations = execResult.observations,
            repairs = execResult.repairs,
            compactSummary = execResult.compactSummary,
            trace = trace,
            debugTrace = execResult.debugTrace
        )
    }

    data class OrchestrateResult(
        val sketch: RetoLayerSketch,
        val observations: List<ToolObservation>,
        val repairs: List<RetoRepairRecord>,
        val compactSummary: String,
        val trace: RetoTrace,
        val debugTrace: String
    )
}
