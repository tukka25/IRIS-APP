package com.gemmaworkflow.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.ui.trigger.TimeTriggerNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that fires when a scheduled time trigger alarm reaches its trigger time.
 *
 * On receiving [TimeTriggerScheduler.ACTION_TIME_TRIGGER]:
 * 1. Posts a high-priority notification via [TimeTriggerNotification] prompting the user
 *    to confirm before the workflow runs.
 * 2. Re-schedules the next occurrence when [TriggerConfig.Time.repeatDays] is non-empty.
 *
 * The actual workflow execution is deferred to [TimeTriggerConfirmationActivity] which
 * is launched when the user taps the notification — this satisfies the acceptance criteria
 * that the workflow fires at the scheduled time and executes *after* confirmation.
 *
 * Registered in AndroidManifest.xml for [TimeTriggerScheduler.ACTION_TIME_TRIGGER].
 */
class TimeTriggerReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "TimeTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimeTriggerScheduler.ACTION_TIME_TRIGGER) {
            Log.w(TAG, "Received unknown action: ${intent.action}")
            return
        }

        val workflowName = intent.getStringExtra(TimeTriggerScheduler.EXTRA_WORKFLOW_NAME) ?: run {
            Log.e(TAG, "Missing EXTRA_WORKFLOW_NAME")
            return
        }
        val hour = intent.getIntExtra(TimeTriggerScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(TimeTriggerScheduler.EXTRA_MINUTE, -1)
        val repeatDaysArray = intent.getIntArrayExtra(TimeTriggerScheduler.EXTRA_REPEAT_DAYS)
        val repeatDays = repeatDaysArray?.toList() ?: emptyList()

        if (hour < 0 || minute < 0) {
            Log.e(TAG, "Invalid time extras: hour=$hour, minute=$minute")
            return
        }

        Log.i(TAG, "Time trigger fired for '$workflowName' at ${formatTime(hour, minute)}, repeatDays=$repeatDays")

        // Post a confirmation notification instead of running directly.
        // The user confirms in TimeTriggerConfirmationActivity.
        TimeTriggerNotification.post(context, workflowName)

        // Re-schedule the next occurrence if repeat is configured.
        if (repeatDays.isNotEmpty()) {
            scope.launch(Dispatchers.Default) {
                reschedule(context, workflowName, hour, minute, repeatDays)
            }
        }
    }

    private fun reschedule(context: Context, workflowName: String, hour: Int, minute: Int, repeatDays: List<Int>) {
        try {
            val scheduler = TimeTriggerScheduler(context)
            val trigger = TriggerConfig.Time(hour = hour, minute = minute, repeatDays = repeatDays)
            scheduler.schedule(workflowName, trigger)
            Log.i(TAG, "Re-scheduled '$workflowName' for next occurrence")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule '$workflowName'", e)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)
}