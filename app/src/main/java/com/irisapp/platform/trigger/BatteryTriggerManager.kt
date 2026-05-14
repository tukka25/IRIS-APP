package com.irisapp.platform.trigger

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.BatteryCondition
import com.irisapp.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages [BatteryTriggerReceiver] registration for all battery-triggered workflows.
 *
 * [ACTION_BATTERY_CHANGED] is a sticky broadcast — it cannot be declared in the manifest.
 * This manager registers a single receiver on app start for all battery workflows,
 * and unregisters it when no battery workflows remain.
 *
 * Inspired by Easer's [BatteryLevelTracker][ryey.easer.skills.usource.battery_level.BatteryLevelTracker].
 */
object BatteryTriggerManager {

    private const val TAG = "BatteryTriggerManager"
    private const val ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED"

    // Single shared receiver covering all battery workflows.
    // Keyed by workflow name so we can skip workflows that no longer have battery triggers.
    private val activeWorkflows = mutableMapOf<String, TriggerConfig.Battery>()
    // Tracks whether a workflow has already fired at current battery level.
    // Prevents re-firing on every percent change — only fires again after battery
    // crosses back above threshold and drops below again.
    private val firedFlags = mutableMapOf<String, Boolean>()

    private var isRegistered = false

    /**
     * Load all saved battery-triggered workflows and register the battery receiver.
     * Called from [IrisApp.onCreate].
     */
    fun registerAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val batteryWorkflows = repo.loadAll().mapNotNull { workflow ->
                (workflow.trigger as? TriggerConfig.Battery)?.let { workflow.name to it }
            }

            synchronized(activeWorkflows) {
                activeWorkflows.clear()
                activeWorkflows.putAll(batteryWorkflows)
            }

            if (activeWorkflows.isNotEmpty()) {
                registerReceiver(context)
                Log.i(TAG, "Registered for ${activeWorkflows.size} battery workflow(s)")
            } else {
                Log.d(TAG, "No battery workflows — skipping registration")
            }
        }
    }

    /**
     * Register the battery receiver if not already registered.
     * Safe to call multiple times.
     */
    private fun registerReceiver(context: Context) {
        if (isRegistered) return
        val filter = IntentFilter(ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(batteryReceiver, filter)
        }
        isRegistered = true
        Log.d(TAG, "Battery receiver registered")
    }

    /**
     * Unregister the battery receiver. Called when no battery workflows remain.
     */
    private fun unregisterReceiver(context: Context) {
        if (!isRegistered) return
        runCatching {
            context.unregisterReceiver(batteryReceiver)
        }
        isRegistered = false
        Log.d(TAG, "Battery receiver unregistered")
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_BATTERY_CHANGED) return

            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", 100)
            if (level < 0) return

            val batteryPct = (level * 100) / scale.coerceAtLeast(1)

            // Read a snapshot of active workflows under lock.
            val workflows: Map<String, TriggerConfig.Battery>
            synchronized(activeWorkflows) {
                workflows = activeWorkflows.toMap()
            }

            for ((workflowName, trigger) in workflows) {
                val triggered = when (trigger.condition) {
                    BatteryCondition.BELOW -> batteryPct < trigger.levelThreshold
                    BatteryCondition.ABOVE -> batteryPct > trigger.levelThreshold
                }

                val alreadyFired = firedFlags[workflowName] == true
                val shouldFire = triggered && !alreadyFired

                if (shouldFire) {
                    Log.i(TAG, "Battery trigger fired: ${workflowName} (${trigger.condition.name} ${trigger.levelThreshold}%, current=$batteryPct%)")
                    firedFlags[workflowName] = true
                    fireWorkflow(ctx, workflowName)
                } else if (!triggered) {
                    // Battery no longer in trigger range — reset so it can fire again
                    firedFlags[workflowName] = false
                }
            }
        }

        private fun fireWorkflow(context: Context, workflowName: String) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = WorkflowRepository(context)
                val workflow = repo.get(workflowName) ?: return@launch
                // Delegate to TriggerRegistry like other triggers do.
                TriggerRegistry.fire(context, workflow)
            }
        }
    }

    /**
     * Remove a workflow from the active set and unregister if empty.
     */
    fun unregisterWorkflow(context: Context, workflowName: String) {
        synchronized(activeWorkflows) {
            activeWorkflows.remove(workflowName)
            if (activeWorkflows.isEmpty()) {
                unregisterReceiver(context)
            }
        }
    }

    /**
     * Update or insert a workflow in the active set.
     */
    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.Battery) {
        synchronized(activeWorkflows) {
            val wasEmpty = activeWorkflows.isEmpty()
            activeWorkflows[workflowName] = trigger
            if (wasEmpty) {
                registerReceiver(context)
            }
        }
    }
}