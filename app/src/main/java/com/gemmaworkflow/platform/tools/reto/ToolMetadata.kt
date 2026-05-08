package com.gemmaworkflow.platform.tools.reto

/**
 * Extended metadata for every tool registered in ToolRegistry.
 *
 * Used by RETO orchestrator to:
 * - Assign tools to execution layers
 * - Block effectful tools during generation
 * - Perform schema-gate validation
 * - Parse structured facts from raw tool outputs
 */
data class ToolMetadata(
    val name: String,

    /** Safety classification */
    val mode: ToolMode,

    /** Which layer(s) this tool can appear in */
    val layerHints: Set<ToolLayerHint>,

    /** Named facts this tool produces (used for observation memory) */
    val produces: Set<String> = emptySet(),

    /** Named prerequisites this tool depends on */
    val requires: Set<String> = emptySet(),

    /** Error substrings that signal non-repairable failure */
    val failureSignals: Set<String> = emptySet(),

    /** Whether the repair agent should attempt to fix bad params */
    val repairable: Boolean = true,

    /** Max local repair attempts before marking as failed */
    val maxRepairAttempts: Int = 2,

    /** Convenience: can this tool be used during workflow generation? */
    val generationAllowed: Boolean = mode != ToolMode.EFFECTFUL
)
