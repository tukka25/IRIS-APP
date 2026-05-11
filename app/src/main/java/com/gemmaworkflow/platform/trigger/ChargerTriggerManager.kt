package com.gemmaworkflow.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.ChargerType
import com.gemmaworkflow.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages [ChargerTriggerReceiver] registration for all charger-triggered workflows.
 *
 * Unlike battery triggers, charger events (ACTION_POWER_CONNECTED /
 * ACTION_POWER_DISCONNECTED) ARE manifest-declarable. However, routing the event
 * to the correct workflow requires the workflow name to be embedded in the broadcast
 * intent — which requires a dynamic registration layer to construct per-workflow intents.
 *
 * This manager registers a single receiver on app start that dispatches to all
 * active charger workflows based on their trigger configuration.
 */
object ChargerTriggerManager {

    private const val TAG = "ChargerTriggerManager"

    private val activeWorkflows = mutableMapOf<String, TriggerConfig.Charger>()
    private var isRegistered = false

    /**
     * Load all saved charger-triggered workflows and register the charger receiver.
     * Called from [GemmaWorkflowApp.onCreate].
     */
    fun registerAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val chargerWorkflows = repo.loadAll().mapNotNull { workflow ->
                (workflow.trigger as? TriggerConfig.Charger)?.let { workflow.name to it }
            }

            synchronized(activeWorkflows) {
                activeWorkflows.clear()
                activeWorkflows.putAll(chargerWorkflows)
            }

            if (activeWorkflows.isNotEmpty()) {
                registerReceiver(context)
                Log.i(TAG, "Registered for ${activeWorkflows.size} charger workflow(s)")
                // Fire immediately if currently charging (catch state on startup).
                checkAndFireIfCharging(context)
            } else {
                Log.d(TAG, "No charger workflows — skipping registration")
            }
        }
    }

    private fun registerReceiver(context: Context) {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(chargerReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(chargerReceiver, filter)
        }
        isRegistered = true
        Log.d(TAG, "Charger receiver registered")
    }

    private fun unregisterReceiver(context: Context) {
        if (!isRegistered) return
        runCatching {
            context.unregisterReceiver(chargerReceiver)
        }
        isRegistered = false
        Log.d(TAG, "Charger receiver unregistered")
    }

private fun fireWorkflow(context: Context, workflowName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val workflow = repo.get(workflowName) ?: return@launch
            TriggerRegistry.fire(context, workflow)
        }
    }

    private val chargerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val connected = intent.action == Intent.ACTION_POWER_CONNECTED
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val workflowMap: Map<String, TriggerConfig.Charger>

            synchronized(activeWorkflows) {
                workflowMap = activeWorkflows.toMap()
            }

            for ((workflowName, trigger) in workflowMap) {
                val validType = when (trigger.connectionType) {
                    ChargerType.ANY -> true
                    ChargerType.USB -> plugged == BatteryManager.BATTERY_PLUGGED_USB
                    ChargerType.AC -> plugged == BatteryManager.BATTERY_PLUGGED_AC
                    ChargerType.WIRELESS -> plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
                }

                if (!validType) continue

                val shouldFire = when (trigger.connectionType) {
                    ChargerType.ANY -> connected
                    else -> connected
                }

                if (shouldFire) {
                    Log.i(TAG, "Charger trigger fired: $workflowName (${if (connected) "connected" else "disconnected"}, type=$plugged)")
                    fireWorkflow(ctx, workflowName)
                }
            }
        }
    }

    private fun checkAndFireIfCharging(context: Context) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        if (plugged <= 0) return

        val workflowMap: Map<String, TriggerConfig.Charger>
        synchronized(activeWorkflows) {
            workflowMap = activeWorkflows.toMap()
        }

        for ((workflowName, trigger) in workflowMap) {
            val validType = when (trigger.connectionType) {
                ChargerType.ANY -> true
                ChargerType.USB -> plugged == BatteryManager.BATTERY_PLUGGED_USB
                ChargerType.AC -> plugged == BatteryManager.BATTERY_PLUGGED_AC
                ChargerType.WIRELESS -> plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
            }
            if (validType) {
                Log.i(TAG, "Charger trigger fired (startup catch): $workflowName (already charging, type=$plugged)")
                fireWorkflow(context, workflowName)
            }
        }
    }

    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.Charger) {
        synchronized(activeWorkflows) {
            val wasEmpty = activeWorkflows.isEmpty()
            activeWorkflows[workflowName] = trigger
            if (wasEmpty) registerReceiver(context)
        }
    }

    fun unregisterWorkflow(context: Context, workflowName: String) {
        synchronized(activeWorkflows) {
            activeWorkflows.remove(workflowName)
            if (activeWorkflows.isEmpty()) unregisterReceiver(context)
        }
    }
}