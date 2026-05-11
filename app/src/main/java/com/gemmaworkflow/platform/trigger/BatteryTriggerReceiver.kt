package com.gemmaworkflow.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gemmaworkflow.domain.model.BatteryCondition
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.trigger.TriggerRegistry
import com.gemmaworkflow.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives ACTION_BATTERY_CHANGED to detect when battery level crosses
 * the user-configured threshold for a workflow.
 *
 * Note: ACTION_BATTERY_CHANGED is a sticky broadcast — it cannot be
 * registered in the manifest. This receiver is registered programmatically
 * via BatteryTriggerManager when a battery trigger is saved.
 */
class BatteryTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", 100)
        if (level < 0) return

        val batteryPct = (level * 100) / scale.coerceAtLeast(1)
        val workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: return

        Log.d(TAG, "Battery level: $batteryPct% for workflow: $workflowName")

        val threshold = intent.getIntExtra(EXTRA_THRESHOLD, -1)
        val conditionCode = intent.getIntExtra(EXTRA_CONDITION, 0)
        val condition = if (conditionCode == 0) BatteryCondition.BELOW else BatteryCondition.ABOVE

        val triggered = when (condition) {
            BatteryCondition.BELOW -> batteryPct < threshold
            BatteryCondition.ABOVE -> batteryPct > threshold
            else -> false
        }

        if (triggered) {
            Log.i(TAG, "Battery trigger fired: ${condition.name} $threshold% (current: $batteryPct%)")
            fireWorkflow(context, workflowName)
        }
    }

    private fun fireWorkflow(context: Context, workflowName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val workflow = repo.get(workflowName) ?: return@launch
            TriggerRegistry.fire(context, workflow)
        }
    }

    companion object {
        private const val TAG = "BatteryTriggerReceiver"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
        const val EXTRA_THRESHOLD = "threshold"
        const val EXTRA_CONDITION = "condition"
    }
}