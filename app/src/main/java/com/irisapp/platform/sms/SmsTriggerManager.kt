package com.irisapp.platform.sms

import android.content.Context
import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.TriggerConfig

/**
 * Manages active SMS trigger workflows.
 *
 * [registerAll] is called from [IrisApp.onCreate] to restore all saved SMS workflows.
 * [registerWorkflow] is called when a workflow with an SMS trigger is saved.
 * [unregisterWorkflow] is called when an SMS workflow is deleted.
 *
 * The actual SMS detection is handled by:
 * - [SmsTriggerReceiver] (primary, Android < 14) — listens for SMS_RECEIVED_ACTION broadcasts
 * - [SmsNotificationListener] (fallback, Android 14+) — NotificationListenerService for SMS app notifications
 */
object SmsTriggerManager {

    private const val TAG = "SmsTriggerManager"

    /** Workflow name → SMS trigger config for active workflows. */
    private val activeSmsWorkflows = mutableMapOf<String, TriggerConfig.SmsReceived>()

    /**
     * Restore all saved SMS workflows from the repository.
     * Called from [IrisApp.onCreate] on app startup.
     */
    fun registerAll(context: Context) {
        val repository = WorkflowRepository(context)
        val workflows = repository.loadAll()
        val smsWorkflows = workflows.filter { it.trigger is TriggerConfig.SmsReceived }

        activeSmsWorkflows.clear()
        for (workflow in smsWorkflows) {
            val trigger = workflow.trigger as TriggerConfig.SmsReceived
            activeSmsWorkflows[workflow.name] = trigger
        }

        Log.i(TAG, "Restored ${activeSmsWorkflows.size} SMS workflows")
    }

    /**
     * Register a workflow with an SMS trigger.
     * Called from [WorkflowGenerationViewModel] when an SMS workflow is saved.
     */
    fun registerWorkflow(workflowName: String, trigger: TriggerConfig.SmsReceived) {
        activeSmsWorkflows[workflowName] = trigger
        Log.i(TAG, "SMS workflow registered: $workflowName")
    }

    /**
     * Remove a workflow's SMS trigger registration.
     * Called from [WorkflowGenerationViewModel] when an SMS workflow is deleted.
     */
    fun unregisterWorkflow(workflowName: String) {
        val removed = activeSmsWorkflows.remove(workflowName)
        if (removed != null) {
            Log.i(TAG, "SMS workflow unregistered: $workflowName")
        }
    }

    /**
     * Returns all active SMS workflows for matching by receivers.
     */
    fun getActiveWorkflows(): Map<String, TriggerConfig.SmsReceived> = activeSmsWorkflows

    /**
     * Check if SMS permission is granted.
     */
    fun hasSmsPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if NotificationListenerService access is enabled.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(context.packageName) == true
    }
}