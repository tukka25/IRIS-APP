package com.irisapp.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.ChargerType
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.platform.trigger.TriggerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives ACTION_POWER_CONNECTED and ACTION_POWER_DISCONNECTED to detect
 * charger connection changes.
 * Registered in AndroidManifest.xml.
 */
class ChargerTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: return

        val connected = intent.action == Intent.ACTION_POWER_CONNECTED
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        Log.d(TAG, "Charger event: ${if (connected) "connected" else "disconnected"} plugged=$plugged for $workflowName")

        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val workflow = repo.get(workflowName) ?: return@launch

            val trigger = workflow.trigger as? TriggerConfig.Charger ?: return@launch

            val validType = when (trigger.connectionType) {
                ChargerType.ANY -> true
                ChargerType.USB -> plugged == BatteryManager.BATTERY_PLUGGED_USB
                ChargerType.AC -> plugged == BatteryManager.BATTERY_PLUGGED_AC
                ChargerType.WIRELESS -> plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
            }

            if (!validType) {
                Log.d(TAG, "Charger type mismatch: expected ${trigger.connectionType}, got $plugged")
                return@launch
            }

            TriggerRegistry.fire(context, workflow)
        }
    }

    companion object {
        private const val TAG = "ChargerTriggerReceiver"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
    }
}