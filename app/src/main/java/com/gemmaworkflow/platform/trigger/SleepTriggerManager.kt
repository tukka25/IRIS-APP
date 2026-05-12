package com.gemmaworkflow.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Manages Sleep / Bedtime Proxy trigger workflows.
 *
 * Sleep proxy is NOT a separate Android service — it is a condition layer on top
 * of the existing DND broadcast. When [DndTriggerManager] fires, this manager
 * checks whether any active sleep workflows match the current time window and
 * (optionally) charger state.
 *
 * Registered in [GemmaWorkflowApp.rescheduleTriggers].
 */
object SleepTriggerManager {

    private const val TAG = "SleepTriggerManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active sleep workflows: workflow name -> trigger config
    private val activeSleepWorkflows = mutableMapOf<String, TriggerConfig.SleepProxy>()

    // Tracks last known charger connection state to detect "just disconnected" events
    private var lastChargerConnected: Boolean? = null

    // Guard against duplicate registration
    private var isRegistered = false

    // ── registration ────────────────────────────────────────────────────────

    /**
     * Load all SleepProxy workflows and register the charger receiver.
     * Call once from [GemmaWorkflowApp.onCreate].
     */
    fun registerAll(context: Context) {
        scope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(context)
            val workflows: List<PlannedWorkflow> = repo.loadAll()
            for (workflow in workflows) {
                val trigger = workflow.trigger as? TriggerConfig.SleepProxy ?: continue
                activeSleepWorkflows[workflow.name] = trigger
            }
        }
        registerReceiver(context)
        registerChargerReceiver(context)
    }

    /**
     * Register a single SleepProxy workflow. Called when saving a workflow.
     */
    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.SleepProxy) {
        activeSleepWorkflows[workflowName] = trigger
        registerReceiver(context)
        registerChargerReceiver(context)
    }

    /**
     * Unregister a sleep workflow. Called when deleting a workflow.
     */
    fun unregisterWorkflow(workflowName: String) {
        activeSleepWorkflows.remove(workflowName)
    }

    // ── DND event listener ─────────────────────────────────────────────────

    /**
     * Called by [DndTriggerManager] when a DND state change occurs and DND is now active.
     * Checks each SleepProxy workflow against the current time window and charger state.
     */
    fun onDndActivated(context: Context, dndInterruptionFilter: Int?) {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val toFire = synchronized(activeSleepWorkflows) {
            activeSleepWorkflows.filter { (_, trigger) ->
                matchesSleepConditions(trigger, currentMinutes, isCharging(context))
            }.toList()
        }

        for ((workflowName, _) in toFire) {
            Log.i(TAG, "SleepProxy matched for '$workflowName', firing workflow")
            scope.launch {
                val repo = WorkflowRepository(context)
                val workflow = repo.get(workflowName) ?: return@launch
                TriggerRegistry.fire(context, workflow)
            }
        }
    }

    // ── charger tracking ────────────────────────────────────────────────────

    private fun registerChargerReceiver(context: Context) {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(chargerReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(chargerReceiver, filter)
            }
            Log.i(TAG, "Charger receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register charger receiver", e)
        }
    }

    private val chargerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val wasDisconnected = lastChargerConnected == false
                    lastChargerConnected = true
                    if (wasDisconnected) {
                        Log.d(TAG, "Charger disconnected event detected (just unplugged)")
                        // Fire sleep workflows that requireChargerDisconnected=true
                        fireIfJustDisconnected(ctx)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    lastChargerConnected = false
                    Log.d(TAG, "Charger disconnected")
                }
            }
        }
    }

    private fun registerReceiver(context: Context) {
        if (isRegistered) return
        isRegistered = true
        Log.i(TAG, "SleepTriggerManager registered")
    }

    // ── matching logic ──────────────────────────────────────────────────────

    /**
     * Returns true if the current time is within the sleep window and
     * charger conditions are satisfied.
     */
    private fun matchesSleepConditions(
        trigger: TriggerConfig.SleepProxy,
        currentMinutes: Int,
        isCurrentlyCharging: Boolean
    ): Boolean {
        val startMinutes = trigger.startTimeHour * 60 + trigger.startTimeMinute
        val endMinutes = trigger.endTimeHour * 60 + trigger.endTimeMinute

        val inTimeWindow = if (startMinutes <= endMinutes) {
            // Normal overnight window e.g. 22:00 to 07:00
            currentMinutes in startMinutes..endMinutes
        } else {
            // Overnight window crossing midnight e.g. 22:00 to 07:00 is start>end
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }

        if (!inTimeWindow) {
            Log.d(TAG, "SleepProxy: outside time window ($currentMinutes not in $startMinutes-$endMinutes)")
            return false
        }

        if (trigger.requireChargerDisconnected && isCurrentlyCharging) {
            Log.d(TAG, "SleepProxy: charger required disconnected but currently charging")
            return false
        }

        return true
    }

    /**
     * Fire sleep workflows that require the charger to be disconnected,
     * called immediately after the disconnect event is detected.
     */
    private fun fireIfJustDisconnected(context: Context) {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val toFire = synchronized(activeSleepWorkflows) {
            activeSleepWorkflows.filter { (_, trigger) ->
                trigger.requireChargerDisconnected && matchesSleepConditions(trigger, currentMinutes, isCurrentlyCharging = false)
            }.toList()
        }

        for ((workflowName, _) in toFire) {
            Log.i(TAG, "SleepProxy (charger-disconnected) matched for '$workflowName', firing workflow")
            scope.launch {
                val repo = WorkflowRepository(context)
                val workflow = repo.get(workflowName) ?: return@launch
                TriggerRegistry.fire(context, workflow)
            }
        }
    }

    private fun isCharging(context: Context): Boolean {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }
}