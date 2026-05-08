package com.gemmaworkflow.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gemmaworkflow.domain.model.TriggerConfig

/**
 * Schedules and cancels exact alarms for time-triggered workflows.
 *
 * Each workflow gets one PendingIntent keyed by its name hash code.
 * When the alarm fires, [TimeTriggerReceiver] posts a notification and requires
 * user confirmation before executing the workflow.
 *
 * Repeat days are stored as alarm extras; the receiver re-schedules the next
 * occurrence before dispatching so daily/weekly patterns fire reliably.
 */
class TimeTriggerScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "TimeTriggerScheduler"

        const val ACTION_TIME_TRIGGER =
            "com.gemmaworkflow.platform.alarm.ACTION_TIME_TRIGGER"

        const val EXTRA_WORKFLOW_NAME = "workflow_name"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_REPEAT_DAYS = "repeat_days"

        private const val REQUEST_CODE_BASE = 100_000
    }

    /**
     * Schedule (or update) an alarm for the given time trigger.
     * Cancels any existing alarm for the same workflow before scheduling the new one.
     */
    fun schedule(workflowName: String, trigger: TriggerConfig.Time) {
        if (!canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms — skipping")
            return
        }

        // Always cancel any pre-existing alarm for this workflow first.
        cancel(workflowName)

        val requestCode = workflowRequestCode(workflowName)
        val triggerTimeMillis = computeNextTriggerTimeMillis(trigger.hour, trigger.minute)

        val intent = Intent(context, TimeTriggerReceiver::class.java).apply {
            action = ACTION_TIME_TRIGGER
            putExtra(EXTRA_WORKFLOW_NAME, workflowName)
            putExtra(EXTRA_HOUR, trigger.hour)
            putExtra(EXTRA_MINUTE, trigger.minute)
            putExtra(EXTRA_REPEAT_DAYS, trigger.repeatDays.toIntArray())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )

        Log.i(TAG, "Scheduled '$workflowName' at ${formatTime(trigger.hour, trigger.minute)} " +
                "next=${java.util.Date(triggerTimeMillis)}, repeatDays=${trigger.repeatDays}")
    }

    /**
     * Cancel any pending alarm for the given workflow.
     */
    fun cancel(workflowName: String) {
        val requestCode = workflowRequestCode(workflowName)
        val intent = Intent(context, TimeTriggerReceiver::class.java).apply {
            action = ACTION_TIME_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Cancelled alarm for '$workflowName'")
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun workflowRequestCode(workflowName: String): Int {
        return REQUEST_CODE_BASE + workflowName.hashCode()
    }

    private fun computeNextTriggerTimeMillis(hour: Int, minute: Int): Long {
        val now = java.util.Calendar.getInstance()
        val alarm = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (alarm.timeInMillis <= now.timeInMillis) {
            alarm.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return alarm.timeInMillis
    }

    private fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)

    private fun List<Int>.toIntArray(): IntArray = IntArray(size) { get(it) }
}
