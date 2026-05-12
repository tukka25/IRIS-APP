package com.gemmaworkflow.platform.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gemmaworkflow.R
import com.gemmaworkflow.ui.MainActivity

/**
 * Fires when a GemmaWorkflow alarm goes off.
 * Shows a notification with Snooze and Dismiss actions, marks the alarm as FIRED.
 */
class AlarmFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarm_id") ?: return
        val workflowName = intent.getStringExtra("workflow_name") ?: ""

        Log.i(TAG, "Alarm fired: $alarmId for $workflowName")
        AlarmTriggerManager.markFired(alarmId)

        showAlarmNotification(context, alarmId, workflowName)
    }

    private fun showAlarmNotification(context: Context, alarmId: String, workflowName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        val channel = NotificationChannel(
            CHANNEL_ID, "GemmaWorkflow Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "GemmaWorkflow scheduled alarms"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)

        // Main tap → open app
        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPending = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action → AlarmDismissReceiver
        val dismissIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("workflow_name", workflowName)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, alarmId.hashCode() + 1,
            dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action → AlarmSnoozeReceiver
        val snoozeIntent = Intent(context, AlarmSnoozeReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("workflow_name", workflowName)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, alarmId.hashCode() + 2,
            snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GemmaWorkflow Alarm")
            .setContentText(workflowName.ifBlank { "Scheduled alarm" })
            .setAutoCancel(true)
            .setContentIntent(mainPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze", snoozePending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        notificationManager.notify(alarmId.hashCode(), notification)
    }

    companion object {
        private const val TAG = "AlarmFireReceiver"
        private const val CHANNEL_ID = "gemma_alarm_channel"
    }
}