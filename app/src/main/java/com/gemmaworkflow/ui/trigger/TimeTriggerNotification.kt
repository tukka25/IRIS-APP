package com.gemmaworkflow.ui.trigger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.gemmaworkflow.R

/**
 * Posts a high-priority notification when a time trigger fires.
 *
 * Tapping the notification opens [TimeTriggerConfirmationActivity] which
 * loads the workflow and asks the user to confirm before running it.
 * This mirrors the pattern used by [com.gemmaworkflow.platform.nfc.NfcTriggerHandler].
 *
 * Two action buttons are provided:
 * - "Run Now" — launches [TimeTriggerConfirmationActivity] in direct-run mode
 * - "View"   — opens the workflow detail screen
 *
 * The notification is posted via [postTimeTriggerNotification].
 */
object TimeTriggerNotification {

    private const val TAG = "TimeTriggerNotification"

    const val CHANNEL_ID = "time_trigger_channel"
    const val NOTIFICATION_ID = 20250301

    const val ACTION_SHOW_CONFIRMATION = "com.gemmaworkflow.ui.trigger.ACTION_TIME_TRIGGER_CONFIRM"
    const val ACTION_RUN_DIRECT = "com.gemmaworkflow.ui.trigger.ACTION_TIME_TRIGGER_RUN"
    const val EXTRA_WORKFLOW_NAME = "workflow_name"

    /**
     * Post a notification for a fired time trigger.
     *
     * The notification carries the workflow name as an extra so the
     * Activity can load it directly without parsing a URI.
     */
    fun post(context: Context, workflowName: String) {
        // Check permission before posting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot post notification: POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists
        init(context)

        val confirmationIntent = buildConfirmationIntent(context, workflowName)
        val runDirectIntent = buildRunDirectIntent(context, workflowName)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scheduled Workflow")
            .setContentText("Run \"$workflowName\"?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(confirmationIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                "Run Now",
                runDirectIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "View",
                confirmationIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Posted time trigger notification for '$workflowName'")
    }

    /**
     * Build a PendingIntent that opens [TimeTriggerConfirmationActivity]
     * for the named workflow.
     */
    fun buildConfirmationIntent(context: Context, workflowName: String): PendingIntent {
        val intent = Intent(context, TimeTriggerConfirmationActivity::class.java).apply {
            action = ACTION_SHOW_CONFIRMATION
            putExtra(EXTRA_WORKFLOW_NAME, workflowName)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            workflowName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Build a PendingIntent that launches [TimeTriggerConfirmationActivity]
     * in direct-run mode for the named workflow.
     */
    fun buildRunDirectIntent(context: Context, workflowName: String): PendingIntent {
        val intent = Intent(context, TimeTriggerConfirmationActivity::class.java).apply {
            action = ACTION_RUN_DIRECT
            putExtra(EXTRA_WORKFLOW_NAME, workflowName)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            workflowName.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Initialize notification channels.
     */
    fun init(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scheduled Workflows",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when a scheduled workflow reaches its trigger time"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
