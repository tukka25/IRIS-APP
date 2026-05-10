package com.gemmaworkflow.platform.tools.reto

import kotlinx.serialization.json.JsonObject

/**
 * RETO layer sketch — the orchestration plan for one workflow generation run.
 */
data class RetoLayerSketch(
    val request: String,
    val layers: List<RetoLayer>,
    /** Fact requirements identified in Phase 0. Drives deterministic resolution. */
    val requirementLedger: RequirementLedger = RequirementLedger.EMPTY
)

data class RetoLayer(
    val index: Int,
    val objective: String,
    val allowedTools: Set<String>,
    val requiredObservations: Set<String> = emptySet(),
    val outputContract: String
)

/**
 * A single tool invocation result, stored in compact observation memory.
 */
data class ToolObservation(
    val toolName: String,
    val params: JsonObject,
    val success: Boolean,
    val output: String,
    val parsedFacts: Map<String, String> = emptyMap(),
    val error: String? = null,
    val layerIndex: Int,
    val attempt: Int
)

/**
 * Full trace of a RETO orchestration run — for debug UI and Logcat.
 */
data class RetoTrace(
    val request: String,
    val layerSketch: RetoLayerSketch,
    val observations: List<ToolObservation>,
    val repairs: List<RetoRepairRecord>,
    val finalWorkflowJson: String?
)

data class RetoRepairRecord(
    val toolName: String,
    val layerIndex: Int,
    val attempt: Int,
    val originalArgs: JsonObject,
    val errorMessage: String,
    val repairedArgs: JsonObject?,
    val success: Boolean
)
