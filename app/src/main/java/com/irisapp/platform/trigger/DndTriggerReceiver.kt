package com.irisapp.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.platform.trigger.TriggerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives ACTION_NOTIFICATION_INTERRUPTION_FILTER_CHANGED to detect when
 * Do Not Disturb mode changes.
 * Registered in AndroidManifest.xml.
 */
class DndTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DND_CHANGED) return

        val workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: return

        val filter = intent.getIntExtra(EXTRA_FILTER, -1)

        Log.d(TAG, "DND filter changed: $filter for $workflowName")

        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val workflow = repo.get(workflowName) ?: return@launch

            val trigger = workflow.trigger as? TriggerConfig.DoNotDisturb ?: return@launch

            if (trigger.interruptionFilter != null && trigger.interruptionFilter != filter) {
                Log.d(TAG, "DND filter mismatch: expected $trigger.interruptionFilter, got $filter")
                return@launch
            }

            TriggerRegistry.fire(context, workflow)
        }
    }

    companion object {
        private const val TAG = "DndTriggerReceiver"
        const val ACTION_DND_CHANGED = "android.app.action.NOTIFICATION_INTERRUPTION_FILTER_CHANGED"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
        const val EXTRA_FILTER = "android:notification_interruption_filter"
    }
}