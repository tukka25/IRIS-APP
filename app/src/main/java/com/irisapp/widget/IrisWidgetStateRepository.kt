package com.irisapp.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Singleton helper that pushes [IrisWidgetState] updates to every active
 * widget instance from anywhere in the app (ViewModel, Service, BroadcastReceiver).
 *
 * Pattern:
 *   IrisWidgetStateRepository.setSlmState(context, SlmProcessState.SUCCESS, "Morning Routine")
 *
 * Internally it:
 *   1. Asks [GlanceAppWidgetManager] for all current GlanceIds of [WorkflowWidgetGlance].
 *   2. Calls [updateAppWidgetState] on each — this writes to each widget's DataStore.
 *   3. Calls [WorkflowWidgetGlance.update] to trigger an immediate re-render.
 *
 * All functions are suspend — call them from a coroutine scope
 * (e.g. viewModelScope, lifecycleScope, or ServiceScope).
 */
object IrisWidgetStateRepository {

    /**
     * Apply a transformation [update] to the state of EVERY widget instance.
     * Use this for full state control; prefer the convenience helpers below for
     * common transitions.
     */
    suspend fun pushStateToAll(
        context: Context,
        update: (IrisWidgetState) -> IrisWidgetState
    ) {
        val manager = GlanceAppWidgetManager(context)
        val ids     = manager.getGlanceIds(WorkflowWidgetGlance::class.java)

        // Update each widget's DataStore independently (they are isolated per instance).
        ids.forEach { glanceId ->
            updateAppWidgetState(context, WidgetStateDefinition, glanceId) { update(it) }
        }

        // Trigger a batch UI refresh for all updated instances.
        WorkflowWidgetGlance.updateAll(context)
    }

    /**
     * Convenience: transition all widgets to a specific [SlmProcessState].
     *
     * @param slmState      New pipeline phase.
     * @param workflowName  If non-null, also updates [IrisWidgetState.activeWorkflowName].
     */
    suspend fun setSlmState(
        context:      Context,
        slmState:     SlmProcessState,
        workflowName: String? = null
    ) {
        pushStateToAll(context) { current ->
            current.copy(
                slmState           = slmState,
                activeWorkflowName = workflowName ?: current.activeWorkflowName
            )
        }
    }

    /**
     * Called after a successful workflow execution.
     * Appends [true] to the front of [IrisWidgetState.recentResults] (capped at 5)
     * and transitions to SUCCESS state.
     */
    suspend fun recordSuccess(context: Context, workflowName: String) {
        pushStateToAll(context) { current ->
            current.copy(
                slmState           = SlmProcessState.SUCCESS,
                activeWorkflowName = workflowName,
                recentResults      = (listOf(true) + current.recentResults).take(5),
                currentStep        = null
            )
        }
    }

    /**
     * Called after a failed workflow execution.
     * Appends [false] to [IrisWidgetState.recentResults] and transitions to ERROR state.
     */
    suspend fun recordFailure(context: Context, workflowName: String) {
        pushStateToAll(context) { current ->
            current.copy(
                slmState           = SlmProcessState.ERROR,
                activeWorkflowName = workflowName,
                recentResults      = (listOf(false) + current.recentResults).take(5),
                currentStep        = null
            )
        }
    }

    /**
     * Records the name of the step that is about to start executing.
     * Clears by passing null when between steps.
     */
    suspend fun setCurrentStep(context: Context, stepName: String?) {
        pushStateToAll(context) { current ->
            current.copy(currentStep = stepName)
        }
    }

    /**
     * Appends a completed step result to [IrisWidgetState.stepLogs] (capped at 8).
     * Call this immediately after each action finishes so the widget updates live.
     */
    suspend fun logStep(context: Context, log: StepLog) {
        pushStateToAll(context) { current ->
            current.copy(stepLogs = (current.stepLogs + log).takeLast(8))
        }
    }

    /**
     * Refresh suggestion chips on all widgets from the current list of saved workflows.
     * Call this whenever the user saves or deletes a workflow.
     */
    suspend fun updateSuggestions(context: Context, suggestionNames: List<String>) {
        pushStateToAll(context) { current ->
            current.copy(suggestions = suggestionNames.take(3))
        }
    }

    /**
     * Reset all widgets to IDLE state (e.g. after user dismisses a result).
     */
    suspend fun resetToIdle(context: Context) {
        pushStateToAll(context) { current ->
            current.copy(
                slmState           = SlmProcessState.IDLE,
                activeWorkflowName = null
            )
        }
    }
}
