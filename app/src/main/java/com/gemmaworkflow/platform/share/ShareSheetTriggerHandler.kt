package com.gemmaworkflow.platform.share

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gemmaworkflow.R
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.SharedContent
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.ui.MainActivity

/**
 * Handles incoming share sheet triggers.
 *
 * On receiving shared content, loads all saved workflows whose trigger is
 * [TriggerConfig.ShareSheet] and presents them to the user (or runs directly if only one).
 * Then executes the selected workflow with the shared content injected as step params.
 *
 * The notification channel "share_channel" is created on first use.
 */
object ShareSheetTriggerHandler {

    private const val TAG = "ShareSheetTriggerHandler"
    private const val CHANNEL_ID = "share_channel"
    private const val NOTIFICATION_ID_BASE = 3000

    /**
     * Called when a share intent is received by [MainActivity] via the share sheet.
     * Presents the workflow selector or runs directly if only one match.
     */
    fun handleIncomingShare(context: Context, sharedContent: SharedContent) {
        Log.i(TAG, "Handling incoming share: ${sharedContent.displaySummary()}")

        val repo = WorkflowRepository(context)
        val shareSheetWorkflows = repo.loadAll().filter { workflow ->
            workflow.trigger is TriggerConfig.ShareSheet
        }

        Log.i(TAG, "Found ${shareSheetWorkflows.size} share_sheet workflows")

        when {
            shareSheetWorkflows.isEmpty() -> {
                showNoWorkflowsNotification(context, sharedContent)
            }
            shareSheetWorkflows.size == 1 -> {
                // Single match: run directly without prompting
                runWorkflowWithShare(context, shareSheetWorkflows.first(), sharedContent)
            }
            else -> {
                // Multiple matches: show selector notification
                showSelectorNotification(context, sharedContent, shareSheetWorkflows)
            }
        }
    }

    /**
     * Returns the list of workflows that are configured for the share sheet trigger.
     * Used by the selector Activity to populate its list.
     */
    fun getShareSheetWorkflows(context: Context): List<PlannedWorkflow> {
        return WorkflowRepository(context).loadAll().filter { it.trigger is TriggerConfig.ShareSheet }
    }

    /**
     * Inject shared content as workflow step params and execute the workflow.
     *
     * For text shares: adds `{{shared_text}}` to the first step param named "text" or "content".
     * For image shares: adds `{{shared_uri}}` to the first step param named "uri" or "image_uri".
     * The actual injection is performed by replacing placeholder params in each step.
     */
    fun runWorkflowWithShare(
        context: Context,
        workflow: PlannedWorkflow,
        sharedContent: SharedContent
    ) {
        Log.i(TAG, "Running workflow '${workflow.name}' with shared content")

        // Build an intent to MainActivity that includes the shared content and workflow name.
        // MainActivity will handle running the workflow through the normal flow.
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_RUN_SHARE_WORKFLOW
            putExtra(EXTRA_SHARED_TEXT, sharedContent.text)
            sharedContent.uri?.let { putExtra(EXTRA_SHARED_URI, it.toString()) }
            putExtra(EXTRA_WORKFLOW_NAME, workflow.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    // ---------------------------------------------------------------------------------------------
    // Notification helpers
    // ---------------------------------------------------------------------------------------------

    private fun showNoWorkflowsNotification(context: Context, sharedContent: SharedContent) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Share Triggers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications for incoming share triggers"
                }
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SHOW_SHARE_CONTENT
            putExtra(EXTRA_SHARED_TEXT, sharedContent.text)
            sharedContent.uri?.let { putExtra(EXTRA_SHARED_URI, it.toString()) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            /* requestCode = */ 0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = sharedContent.displaySummary()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("No Share Sheet workflows")
            .setContentText("Create a workflow with Share Sheet trigger to handle: $summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "No Share Sheet workflows configured.\n\n" +
                "Shared: $summary\n\n" +
                "Open GemmaWorkflow to create a Share Sheet workflow."
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (!canPostNotifications(context)) return
        try {
            notificationManager.notify(NOTIFICATION_ID_BASE, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot post 'no workflows' notification", se)
        }
    }

    private fun showSelectorNotification(
        context: Context,
        sharedContent: SharedContent,
        workflows: List<PlannedWorkflow>
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Share Triggers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications for incoming share triggers"
                }
            )
        }

        val summary = sharedContent.displaySummary()

        // Build a notification that shows all matching workflows as a list.
        // Tapping the notification opens the full selector in the app.
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Run shared content with...")
            .setSummaryText(summary)

        workflows.take(5).forEach { workflow ->
            inboxStyle.addLine("\u2022 ${workflow.name}")
        }

        // Also add an "Open to choose" action that opens the full selector
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SHOW_SHARE_SELECTOR
            putExtra(EXTRA_SHARED_TEXT, sharedContent.text)
            sharedContent.uri?.let { putExtra(EXTRA_SHARED_URI, it.toString()) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            /* requestCode = */ 100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("${workflows.size} workflows can handle this")
            .setContentText(summary)
            .setStyle(inboxStyle)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_manage, "Choose\u2026", openPendingIntent)
            .build()

        if (!canPostNotifications(context)) return
        try {
            notificationManager.notify(NOTIFICATION_ID_BASE, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot post selector notification", se)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Cannot post share notification: POST_NOTIFICATIONS permission not granted")
        }
        return granted
    }

    // ---------------------------------------------------------------------------------------------
    // Constants for Intent extras
    // ---------------------------------------------------------------------------------------------

    /** Action: run a named workflow with the current pending shared content. */
    const val ACTION_RUN_SHARE_WORKFLOW = "com.gemmaworkflow.ACTION_RUN_SHARE_WORKFLOW"

    /** Action: display the pending shared content in the main screen. */
    const val ACTION_SHOW_SHARE_CONTENT = "com.gemmaworkflow.ACTION_SHOW_SHARE_CONTENT"

    /** Action: open the share workflow selector (for when user taps from notification). */
    const val ACTION_SHOW_SHARE_SELECTOR = "com.gemmaworkflow.ACTION_SHOW_SHARE_SELECTOR"

    /** Extra: shared plain text. */
    const val EXTRA_SHARED_TEXT = "shared_text"

    /** Extra: shared content URI as string. */
    const val EXTRA_SHARED_URI = "shared_uri"

    /** Extra: name of the workflow to run. */
    const val EXTRA_WORKFLOW_NAME = "workflow_name"
}
