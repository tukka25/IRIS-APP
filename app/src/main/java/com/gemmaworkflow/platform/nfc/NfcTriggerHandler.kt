package com.gemmaworkflow.platform.nfc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.ui.MainActivity

/**
 * BroadcastReceiver that fires when the system detects an NFC tag scan.
 *
 * Triggered by [NfcTriggerWriter] — the tag contains a deep-link URI
 * (gemmaworkflow://workflow/<workflowId>). This receiver:
 *   1. Parses the workflow ID from the tag URI
 *   2. Verifies the workflow exists in the repository
 *   3. Posts a confirmation notification with actions to run or view the workflow
 *
 * If the app is in the foreground when the tag is scanned, [DeepLinkRouter]
 * handles the routing directly instead.
 *
 * Registered in AndroidManifest.xml for ACTION_NDEF_DISCOVERED and
 * ACTION_TECH_DISCOVERED on the gemmaworkflow:// scheme.
 */
class NfcTriggerHandler : BroadcastReceiver() {

    companion object {
        private const val TAG = "NfcTriggerHandler"

        const val ACTION_NFC_SCANNED = "com.gemmaworkflow.platform.nfc.ACTION_NFC_SCANNED"
        const val EXTRA_WORKFLOW_ID = "workflow_id"

        const val CHANNEL_ID = "nfc_trigger_channel"
        const val NOTIFICATION_ID = 20250201

        /**
         * Build a PendingIntent that will be triggered when the user taps
         * the NFC scan notification.
         */
        fun buildTapIntent(context: Context, workflowId: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NFC_SCANNED
                putExtra(EXTRA_WORKFLOW_ID, workflowId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                workflowId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Build a PendingIntent that launches directly into the workflow detail screen
         * (bypasses the NFC confirmation prompt).
         */
        fun buildRunIntent(context: Context, workflowId: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = DeepLinkRouter.ACTION_RUN_WORKFLOW
                putExtra(DeepLinkRouter.EXTRA_WORKFLOW_ID, workflowId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                workflowId.hashCode() + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NfcTriggerWriter.ACTION_NFC_SCANNED &&
            intent.action != Intent.ACTION_VIEW) {
            Log.w(TAG, "Received unknown action: ${intent.action}")
            return
        }

        val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
            ?: intent.data?.let { uri -> NfcTriggerWriter.parseWorkflowIdFromUri(uri.toString()) }
            ?: run {
                Log.w(TAG, "No workflow ID found in NFC intent")
                return
            }

        Log.i(TAG, "NFC tag scanned for workflow: $workflowId")

        // Load the workflow to verify it exists
        val repo = WorkflowRepository(context)
        val workflow = repo.get(workflowId)
        if (workflow == null) {
            Log.w(TAG, "Workflow '$workflowId' not found in repository")
            postErrorNotification(context, workflowId)
            return
        }

        // If app is in foreground, route via DeepLinkRouter instead of notification
        if (isAppInForeground(context)) {
            DeepLinkRouter.routeNfcScan(context, workflowId)
            return
        }

        postConfirmationNotification(context, workflowId, workflow.name)
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName
        return appProcesses.any { process ->
            process.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
            && process.processName == packageName
        }
    }

    private fun postConfirmationNotification(context: Context, workflowId: String, workflowName: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel(context, notificationManager)

        val tapIntent = buildTapIntent(context, workflowId)
        val runIntent = buildRunIntent(context, workflowId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("NFC Tag Scanned")
            .setContentText("Run \"$workflowName\"?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                "Run Now",
                runIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "View",
                tapIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Posted NFC confirmation notification for '$workflowName'")
    }

    private fun postErrorNotification(context: Context, workflowId: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel(context, notificationManager)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("NFC Tag Error")
            .setContentText("Workflow not found: $workflowId")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NFC Triggers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when an NFC tag is scanned to trigger a workflow"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
