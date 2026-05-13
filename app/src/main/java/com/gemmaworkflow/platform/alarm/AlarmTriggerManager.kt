package com.gemmaworkflow.platform.alarm

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.trigger.TriggerRegistry

/**
 * Tracks GemmaWorkflow's own scheduled alarms and handles snooze/dismiss lifecycle.
 *
 * Alarm lifecycle: SCHEDULED → FIRED → SNOOZED/DISMISSED
 *
 * When an alarm fires, AlarmFireReceiver shows a notification. If user taps Snooze,
 * AlarmSnoozeReceiver calls snooze() which re-schedules and fires workflows.
 * If user taps Dismiss, AlarmDismissReceiver calls dismiss() which fires workflows.
 */
object AlarmTriggerManager {

    private const val TAG = "AlarmTriggerManager"
    private const val PREFS_NAME = "alarm_tracker_prefs"
    private const val KEY_PREFIX = "alarm_"

    // Maps alarmId → AlarmState
    private val activeAlarms = mutableMapOf<String, AlarmState>()

    enum class AlarmState { SCHEDULED, FIRED, SNOOZED, DISMISSED }

    data class AlarmRef(
        val alarmId: String,
        val workflowName: String,
        val state: AlarmState = AlarmState.SCHEDULED
    )

    fun hasActiveAlarms(): Boolean = activeAlarms.isNotEmpty()

    fun getAlarmCount(): Int = activeAlarms.size

    /**
     * Schedule a GemmaWorkflow alarm for a specific time.
     * Creates two pending intents:
     *   1. FIRE intent → AlarmFireReceiver → shows notification
     *   2. DISMISS intent → AlarmDismissReceiver → fires workflows on dismiss
     */
    fun scheduleAlarm(context: Context, alarmId: String, workflowName: String, triggerTimeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // FIRE intent - shows notification and marks alarm as FIRED
        val fireIntent = Intent(context, AlarmFireReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("workflow_name", workflowName)
        }
        val firePending = PendingIntent.getBroadcast(
            context, alarmId.hashCode(),
            fireIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // DISMISS intent - fires workflows when user dismisses
        val dismissIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("workflow_name", workflowName)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, alarmId.hashCode() + 1,
            dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // SNOOZE intent - re-schedules and fires workflows
        val snoozeIntent = Intent(context, AlarmSnoozeReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("workflow_name", workflowName)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, alarmId.hashCode() + 2,
            snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // showIntent - opens the alarm app UI when user taps the system alarm clock icon
        // Must be an Activity PendingIntent, not a BroadcastReceiver (which would fire workflows on tap)
        val showIntent = Intent(context, com.gemmaworkflow.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alarm_id", alarmId)
            putExtra("from_alarm", true)
        }
        val showPending = PendingIntent.getActivity(
            context, alarmId.hashCode() + 3,
            showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = android.app.AlarmManager.AlarmClockInfo(triggerTimeMillis, showPending)

        try {
            alarmManager.setAlarmClock(info, firePending)
            activeAlarms[alarmId] = AlarmState.SCHEDULED
            Log.i(TAG, "Scheduled alarm '$alarmId' for $workflowName at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(triggerTimeMillis))}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    fun cancelAlarm(context: Context, alarmId: String, workflowName: String? = null) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // offset 0: fire receiver
        Intent(context, AlarmFireReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            workflowName?.let { putExtra("workflow_name", it) }
        }.let { intent ->
            PendingIntent.getBroadcast(context, alarmId.hashCode() + 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
        }

        // offset 1: dismiss receiver
        Intent(context, AlarmDismissReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            workflowName?.let { putExtra("workflow_name", it) }
        }.let { intent ->
            PendingIntent.getBroadcast(context, alarmId.hashCode() + 1, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
        }

        // offset 2: snooze receiver
        Intent(context, AlarmSnoozeReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            workflowName?.let { putExtra("workflow_name", it) }
        }.let { intent ->
            PendingIntent.getBroadcast(context, alarmId.hashCode() + 2, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
        }

        activeAlarms.remove(alarmId)
        Log.i(TAG, "Cancelled alarm '$alarmId'")
    }

    /** Mark alarm as fired (called when notification is shown). */
    fun markFired(alarmId: String) {
        activeAlarms[alarmId] = AlarmState.FIRED
    }

    /** Mark alarm as snoozed and re-schedule for snoozeInterval minutes. */
    fun snooze(context: Context, alarmId: String, workflowName: String) {
        activeAlarms[alarmId] = AlarmState.SNOOZED
        val snoozeMinutes = getSnoozeDurationMinutes(context)
        val nextTrigger = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        scheduleAlarm(context, "${alarmId}_snoozed", workflowName, nextTrigger)
        Log.i(TAG, "Alarm '$alarmId' snoozed for $snoozeMinutes minutes")
    }

    /** Mark alarm as dismissed. */
    fun dismiss(alarmId: String) {
        activeAlarms[alarmId] = AlarmState.DISMISSED
        Log.i(TAG, "Alarm '$alarmId' dismissed")
    }

    /** Fire workflows matching this alarm's trigger. */
    fun fireAlarmWorkflows(context: Context, alarmId: String, workflowName: String?) {
        try {
            val repository = WorkflowRepository(context)
            val workflows = repository.loadAll()
            val alarmWorkflows = workflows.filter { it.trigger is TriggerConfig.AlarmStopped }

            val fired = alarmWorkflows.filter { wf ->
                // Match by workflowName if provided, otherwise match all AlarmStopped workflows
                // (alarmId is in the format: "{workflowName}_alarm" or "{workflowName}_snoozed")
                workflowName == null || wf.name == workflowName
            }

            if (fired.isEmpty()) {
                Log.i(TAG, "No alarm workflows to fire for alarmId=$alarmId")
                return
            }

            for (workflow in fired) {
                Log.i(TAG, "Firing alarm workflow: ${workflow.name}")
                TriggerRegistry.fire(context, workflow)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error firing alarm workflows", e)
        }
    }

    /** Load all GemmaWorkflow alarm workflows and register them. */
    fun registerAll(context: Context) {
        Log.d(TAG, "AlarmTriggerManager.registerAll() called — OWN alarm scheduling is managed via TimeTriggerScheduler")
    }

    /**
     * Register a workflow with an AlarmStopped trigger.
     * For OWN_ONLY alarms, scheduling is managed via TimeTriggerScheduler separately.
     */
    fun registerWorkflow(workflowName: String, trigger: TriggerConfig.AlarmStopped) {
        Log.d(TAG, "Alarm workflow registered: $workflowName (type=${trigger.alarmType})")
    }

    /**
     * Unregister a workflow's alarm trigger.
     */
    fun unregisterWorkflow(workflowName: String) {
        Log.d(TAG, "Alarm workflow unregistered: $workflowName")
    }

    private fun getSnoozeDurationMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("snooze_duration_minutes", 10)
    }
}