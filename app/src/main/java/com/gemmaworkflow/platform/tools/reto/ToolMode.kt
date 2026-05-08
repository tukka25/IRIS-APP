package com.gemmaworkflow.platform.tools.reto

/**
 * Safety mode for any tool.
 *
 * - READ_ONLY: reads device/local info; safe during generation.
 * - VALIDATION: checks schema or generated JSON; safe during generation.
 * - DRY_RUN: builds previews, checks resolvability, no side effects.
 * - EFFECTFUL: mutates device state or opens/sends; NOT allowed during generation.
 */
enum class ToolMode { READ_ONLY, VALIDATION, DRY_RUN, EFFECTFUL }

/**
 * Coarse layer hints for RETO layer planning.
 * A tool can belong to multiple layer hints (e.g., get_contact is
 * both FACT_GROUNDING and PARAMETER_RESOLUTION).
 */
enum class ToolLayerHint {
    FACT_GROUNDING,        // resolve names, dates, locations, apps
    CAPABILITY_CHECK,      // check intent resolvability, app availability
    PARAMETER_RESOLUTION,  // resolve vague params into concrete values
    ACTION_CONSTRUCTION,   // build action plan from facts
    FINAL_VALIDATION,      // validate JSON output
    EXECUTION              // run real side effects (only in WorkflowRunner)
}
