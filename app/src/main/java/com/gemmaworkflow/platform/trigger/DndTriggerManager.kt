package com.gemmaworkflow.platform.trigger

import android.app.NotificationManager
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
 * Manages Do Not Disturb mode change trigger workflows.
 *
 * Uses a single shared receiver covering all DND workflows.
 * Detects interruption filter changes via [NotificationManager].
 *
 * Registered in [GemmaWorkflowApp.rescheduleTriggers] alongside other managers.
 * Requires [android.permission.ACCESS_NOTIFICATION_POLICY] on Android 7+.
 */
object DndTriggerManager {

    private const val TAG = "DndTriggerManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active DND workflows: workflow name -> trigger config
    private val activeWorkflows = mutableMapOf<String, TriggerConfig.DoNotDisturb>()

    // Guard against duplicate registration
    private var isRegistered = false

    // Android 7+ notification interruption filter broadcast
    private const val ACTION_INTERRUPTION_FILTER_CHANGED =
        "android.app.action.NOTIFICATION_INTERRUPTION_FILTER_CHANGED"

    // ── registration ────────────────────────────────────────────────────────

    /**
     * Load all DND workflows and register the receiver.
     * Call once from [GemmaWorkflowApp.onCreate].
     */
    fun registerAll(context: Context) {
        scope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(context)
            val workflows: List<com.gemmaworkflow.domain.model.PlannedWorkflow> = repo.loadAll()
            for (workflow in workflows) {
                val trigger = workflow.trigger as? TriggerConfig.DoNotDisturb ?: continue
                activeWorkflows[workflow.name] = trigger
            }
        }

        registerReceiver(context)
    }

    /**
     * Register a single workflow. Called when saving a workflow with a DND trigger.
     */
    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.DoNotDisturb) {
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
            val filter = IntentFilter(ACTION_INTERRUPTION_FILTER_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(dndReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(dndReceiver, filter)
            }
            isRegistered = true
            Log.i(TAG, "DND receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register DND receiver", e)
        }
    }

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_INTERRUPTION_FILTER_CHANGED) return

            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val currentFilter = notificationManager.currentInterruptionFilter
            val dndActive = currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL

            Log.d(TAG, "DND filter changed: $currentFilter")

            if (dndActive) {
                SleepTriggerManager.onDndActivated(ctx, currentFilter)
            }

            val toFire = synchronized(activeWorkflows) {
                activeWorkflows.filter { (_, trigger) ->
                    trigger.interruptionFilter == null || trigger.interruptionFilter == currentFilter
                }.toList()
            }

            for ((workflowName, _) in toFire) {
                Log.i(TAG, "DND trigger matched for '$workflowName'")
                scope.launch {
                    val repo = WorkflowRepository(ctx)
                    val workflow = repo.get(workflowName) ?: return@launch
                    TriggerRegistry.fire(ctx, workflow)
                }
            }
        }
    }
}
