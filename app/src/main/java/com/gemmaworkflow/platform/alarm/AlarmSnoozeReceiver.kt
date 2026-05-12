package com.gemmaworkflow.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires when user taps "Snooze" on a GemmaWorkflow alarm notification.
 * Re-schedules the alarm for the configured snooze interval and fires matching workflows.
 */
class AlarmSnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarm_id") ?: return
        val workflowName = intent.getStringExtra("workflow_name") ?: ""

        Log.i(TAG, "Alarm snoozed: $alarmId")
        AlarmTriggerManager.snooze(context, alarmId, workflowName)
        AlarmTriggerManager.fireAlarmWorkflows(context, alarmId, workflowName)
    }

    companion object {
        private const val TAG = "AlarmSnoozeReceiver"
    }
}