package com.irisapp.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Glance [ActionCallback] invoked when the user taps a suggestion pill in the widget.
 *
 * Glance resolves this class by name at tap time (similar to how BroadcastReceiver
 * works), so it must be a concrete, public, top-level class with a no-arg constructor.
 *
 * Execution sequence:
 *   1. Extract the workflow name from [ActionParameters].
 *   2. Immediately update the widget DataStore state → ANALYZING (instant feedback).
 *   3. Re-render the widget so the SLM Monitor Bar reflects the new state.
 *   4. Start [SlmExecutionService] as a foreground service to run the SLM pipeline.
 *      The service is responsible for updating the state back to SUCCESS / ERROR
 *      when the pipeline finishes, via [IrisWidgetStateRepository].
 */
class TriggerWorkflowAction : ActionCallback {

    companion object {
        /**
         * Typed key for the workflow name passed through [ActionParameters].
         * Using a typed Key<String> avoids string-cast crashes at runtime.
         * Declare this in the companion so all callsites share the same key object.
         */
        val KEY_WORKFLOW_NAME = ActionParameters.Key<String>("workflow_name")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // ── Step 1: Extract the workflow name ────────────────────────────────
        val workflowName = parameters[KEY_WORKFLOW_NAME]
        if (workflowName.isNullOrBlank()) {
            // Defensive guard: nothing we can do without a name.
            return
        }

        // ── Step 2: Optimistic UI update → ANALYZING ──────────────────────────
        // This updates only the DataStore for the widget instance that was tapped
        // (identified by glanceId).  Other widget instances are unaffected unless
        // you call IrisWidgetStateRepository.pushStateToAll().
        updateAppWidgetState(context, WidgetStateDefinition, glanceId) { current ->
            current.copy(
                activeWorkflowName = workflowName,
                slmState           = SlmProcessState.ANALYZING,
                stepLogs           = emptyList(),
                currentStep        = null
            )
        }

        // ── Step 3: Force an immediate widget re-render ───────────────────────
        // Without this call the widget only refreshes on its next periodic update.
        WorkflowWidgetGlance().update(context, glanceId)

        // ── Step 4: Launch the foreground service ─────────────────────────────
        // SlmExecutionService handles:
        //   • Posting a mandatory foreground notification (required on API 26+)
        //   • Loading the workflow definition from WorkflowRepository
        //   • Running the LiteRT-LM inference pipeline
        //   • Calling IrisWidgetStateRepository.setSlmState(SUCCESS | ERROR) on completion
        //
        // AndroidManifest.xml requirements for SlmExecutionService:
        //   <service
        //       android:name=".widget.SlmExecutionService"
        //       android:foregroundServiceType="dataSync"
        //       android:exported="false" />
        //
        //   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
        //   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
        val serviceIntent = Intent(context, SlmExecutionService::class.java).apply {
            // Pass the workflow name so the service knows what to run.
            putExtra(SlmExecutionService.EXTRA_WORKFLOW_NAME, workflowName)
            // Pass the glance ID string so the service can update only this widget.
            putExtra(SlmExecutionService.EXTRA_GLANCE_ID, glanceId.toString())
        }
        // startForegroundService is mandatory for foreground services (API 26+ = our minSdk).
        context.startForegroundService(serviceIntent)
    }
}
