package com.gemmaworkflow.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages airplane mode toggle trigger workflows.
 *
 * Uses a single shared receiver covering all airplane mode workflows.
 * Registered in [GemmaWorkflowApp.rescheduleTriggers] alongside other managers.
 */
object AirplaneModeTriggerManager {

    private const val TAG = "AirplaneModeTriggerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active airplane mode workflows: workflow name -> trigger config
    private val activeWorkflows = mutableMapOf<String, TriggerConfig.AirplaneMode>()

    // Guard against duplicate registration
    private var isRegistered = false

    // ── registration ────────────────────────────────────────────────────────

    /**
     * Load all airplane mode workflows and register the receiver.
     * Call once from [GemmaWorkflowApp.onCreate].
     */
    fun registerAll(context: Context) {
        scope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(context)
            val workflows: List<com.gemmaworkflow.domain.model.PlannedWorkflow> = repo.loadAll()
            for (workflow in workflows) {
                val trigger = workflow.trigger as? TriggerConfig.AirplaneMode ?: continue
                activeWorkflows[workflow.name] = trigger
            }
        }

        registerReceiver(context)
    }

    /**
     * Register a single workflow. Called when saving a workflow with an airplane mode trigger.
     */
    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.AirplaneMode) {
        activeWorkflows[workflowName] = trigger
        registerReceiver(context)
    }

    /**
     * Unregister a workflow. Called when deleting a workflow.
     */
    fun unregisterWorkflow(workflowName: String) {
        activeWorkflows.remove(workflowName)
    }

    // ── receiver ────────────────────────────────────────────────────────────

    private fun registerReceiver(context: Context) {
        if (isRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(airplaneReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(airplaneReceiver, filter)
            }
            isRegistered = true
            Log.i(TAG, "Airplane mode receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register airplane mode receiver", e)
        }
    }

    private val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_AIRPLANE_MODE_CHANGED) return

            val enabled = intent.getBooleanExtra("state", false)
            Log.d(TAG, "Airplane mode: ${if (enabled) "ON" else "OFF"}")

            val toFire = synchronized(activeWorkflows) {
                activeWorkflows.filter { (_, trigger) ->
                    // TriggerConfig.AirplaneMode.enabled is non-nullable; exact match is required.
                    trigger.enabled == enabled
            }.toList() }

            for ((workflowName, _) in toFire) {
                Log.i(TAG, "Airplane mode trigger matched for '$workflowName'")
                scope.launch {
                    val repo = WorkflowRepository(ctx)
                    val workflow = repo.get(workflowName) ?: return@launch
                    TriggerRegistry.fire(ctx, workflow)
                }
            }
        }
    }
}
