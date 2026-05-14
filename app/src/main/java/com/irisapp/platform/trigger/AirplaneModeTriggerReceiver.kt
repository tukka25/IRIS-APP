package com.irisapp.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.platform.trigger.TriggerRegistry
import com.irisapp.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives ACTION_AIRPLANE_MODE_CHANGED to detect when airplane mode
 * is toggled on or off.
 * Registered in AndroidManifest.xml.
 */
class AirplaneModeTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_AIRPLANE_MODE_CHANGED) return

        val workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: return

        val enabled = intent.getBooleanExtra("state", false)

        Log.d(TAG, "Airplane mode: ${if (enabled) "ON" else "OFF"} for $workflowName")

        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val workflow = repo.get(workflowName) ?: return@launch

            val trigger = workflow.trigger as? TriggerConfig.AirplaneMode ?: return@launch

            // Validate condition if set
            if (trigger.enabled != enabled) {
                Log.d(TAG, "Airplane mode condition mismatch: expected ${if (trigger.enabled) "ON" else "OFF"}, got ${if (enabled) "ON" else "OFF"}")
                return@launch
            }

            TriggerRegistry.fire(context, workflow)
        }
    }

    companion object {
        private const val TAG = "AirplaneModeTriggerReceiver"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
    }
}