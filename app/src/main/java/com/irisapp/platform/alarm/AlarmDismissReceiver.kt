package com.irisapp.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires when user taps "Dismiss" on a IrisApp alarm notification.
 * Marks the alarm as DISMISSED and fires matching workflows.
 */
class AlarmDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarm_id") ?: return
        val workflowName = intent.getStringExtra("workflow_name")

        Log.i(TAG, "Alarm dismissed: $alarmId")
        AlarmTriggerManager.dismiss(alarmId)
        AlarmTriggerManager.fireAlarmWorkflows(context, alarmId, workflowName)
    }

    companion object {
        private const val TAG = "AlarmDismissReceiver"
    }
}