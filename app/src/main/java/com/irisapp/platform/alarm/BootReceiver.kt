package com.irisapp.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that fires after the device boots.
 * Re-schedules all saved workflows that have a [TriggerConfig.Time] trigger
 * so their alarms are not lost after a reboot.
 *
 * Registered in AndroidManifest.xml for ACTION_BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Device boot completed — rescheduling time-triggered workflows")

        scope.launch {
            rescheduleAllTimeTriggers(context)
        }
    }

    private suspend fun rescheduleAllTimeTriggers(context: Context) {
        val repo = WorkflowRepository(context)
        val scheduler = TimeTriggerScheduler(context)
        val allWorkflows = repo.loadAll()

        val timeTriggers = allWorkflows.filter { it.trigger is TriggerConfig.Time }
            .mapNotNull { workflow ->
                val trigger = workflow.trigger as? TriggerConfig.Time ?: return@mapNotNull null
                workflow.name to trigger
            }

        if (timeTriggers.isEmpty()) {
            Log.i(TAG, "No time-triggered workflows to reschedule")
            return
        }

        timeTriggers.forEach { (name, trigger) ->
            try {
                scheduler.schedule(name, trigger)
                Log.i(TAG, "Rescheduled '$name'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule '$name'", e)
            }
        }

        Log.i(TAG, "Rescheduled ${timeTriggers.size} time-triggered workflows")
    }
}
