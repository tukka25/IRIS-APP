package com.irisapp.widget

import kotlinx.serialization.Serializable

/**
 * One completed (or failed) step in the current workflow execution.
 * Written by [SlmExecutionService] as each action finishes; rendered live in the widget.
 */
@Serializable
data class StepLog(
    val stepName: String,
    val output: String,
    val success: Boolean,
    val durationMs: Long = 0L,
    val timestampMs: Long = 0L
)

/**
 * Single source of truth for the IRIS home-screen widget UI.
 *
 * State transitions (driven by the SLM pipeline):
 *   IDLE  →  ANALYZING  →  TOOL_CALL  →  SUCCESS | ERROR
 */
@Serializable
data class IrisWidgetState(
    val activeWorkflowName: String? = null,
    val slmState: SlmProcessState = SlmProcessState.IDLE,
    val recentResults: List<Boolean> = listOf(true, true, false, true, true),
    val suggestions: List<String> = listOf(
        "Coffee & Note",
        "Morning Routine",
        "Summarize Inbox"
    ),
    /** Live per-step execution log; cleared at the start of each new run. */
    val stepLogs: List<StepLog> = emptyList(),
    /** Name of the step currently executing, null when between steps or idle. */
    val currentStep: String? = null
)

/**
 * Discrete phases of the on-device SLM execution pipeline.
 *
 * @param displayString Human-readable label shown in the SLM Monitor Bar.
 * @param colorHex      ARGB hex (Long) used by the widget to color the indicator.
 *                      Note: kotlinx.serialization encodes enums by *name*, so
 *                      these constructor parameters are NOT stored in DataStore —
 *                      they are recomputed from the enum constant on read-back.
 */
@Serializable
enum class SlmProcessState(val displayString: String, val colorHex: Long) {
    IDLE      ("Ready",                  0xFF888888L),
    ANALYZING ("Interpreting intent...", 0xFF00F2FEL),
    TOOL_CALL ("Executing actions...",   0xFFB200FFL),
    SUCCESS   ("Completed",             0xFF7CF0A8L),   // GreenSuccess from app theme
    ERROR     ("Failed",                0xFFFF6B6BL)    // matches colorScheme.error
}
