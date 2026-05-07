package com.gemmaworkflow.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Silently schedules alarms via AlarmManager.setExactAndAllowWhileIdle() instead of
 * launching the Clock app with Intent.ACTION_SET_ALARM.
 *
 * On Android 12+ (API 31+), SCHEDULE_EXACT_ALARM permission is restricted —
 * the system Settings page must be used to grant it.  Until it is granted the
 * app falls back to an in-app notification that explains why the alarm may not
 * fire at the exact requested time.
 *
 * When the alarm fires, [AlarmReceiver] posts a visible notification rather than
 * launching any app.
 */
class AlarmApiExecutor(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmApiExecutor"
        private const val PERMISSION_SCHEDULE_EXACT_ALARM = "android.permission.SCHEDULE_EXACT_ALARM"

        // Intent action matched by AlarmReceiver — must match the manifest registration.
        const val ACTION_ALARM_FIRE =
            "com.gemmaworkflow.platform.alarm.ACTION_ALARM_FIRE"

        // Extra key for the alarm message (label) passed through the pending intent.
        const val EXTRA_ALARM_MESSAGE = "alarm_message"

        // Extra key for the request code used to deduplicate PendingIntents.
        const val EXTRA_REQUEST_CODE = "request_code"

        // Channel ID for the notification posted by AlarmReceiver.
        const val NOTIFICATION_CHANNEL_ID = "alarm_channel"

        // Notification ID — fixed since we only ever show one alarm notification at a time.
        const val NOTIFICATION_ID = 20250101
    }

    /**
     * Returns true when the app can schedule exact alarms.
     * On Android 12+ this reflects the system-level gate in Settings.
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            // Before S the permission is granted at install; no gate in Settings.
            true
        }
    }

    /**
     * Returns true if the user should be directed to Settings to enable exact alarms.
     * On Android 12+ this is the case when canScheduleExactAlarms() returns false.
     */
    fun shouldRequestExactAlarmPermission(): Boolean {
        return !canScheduleExactAlarms()
    }

    /**
     * Build an Intent that opens the Android 12+ exact alarm Settings page.
     * Returns null on API < 31 where no such Settings page exists.
     */
    fun buildExactAlarmSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(AlarmClock.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }

    /**
     * Schedule an exact alarm for the given hour + minute.
     *
     * @param hour        24-hour format, 0–23.
     * @param minutes     0–59.
     * @param message     Optional label shown in the notification when the alarm fires.
     * @param requestCode Unique code used to create/deduplicate the PendingIntent so that
     *                    multiple alarms don't overwrite each other.
     *
     * Returns [ExecutionResult.Success] when the alarm was successfully scheduled,
     * or a failure result when the permission is not yet granted.
     */
    fun scheduleAlarm(hour: Int, minutes: Int, message: String?, requestCode: Int): ExecutionResult {
        if (!canScheduleExactAlarms()) {
            return ExecutionResult(
                stepId = "alarm.set_alarm",
                success = false,
                message = "SCHEDULE_EXACT_ALARM permission not granted; redirect to Settings"
            )
        }

        val triggerTimeMillis = computeTriggerTimeMillis(hour, minutes)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_ALARM_MESSAGE, message ?: "Alarm")
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            Log.i(TAG, "Alarm scheduled for ${formatTime(hour, minutes)}, requestCode=$requestCode")
            ExecutionResult(
                stepId = "alarm.set_alarm",
                success = true,
                message = "Alarm set for ${formatTime(hour, minutes)}"
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "setExactAndIdle failed", e)
            ExecutionResult(
                stepId = "alarm.set_alarm",
                success = false,
                message = "SCHEDULE_EXACT_ALARM permission denied: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
            ExecutionResult(
                stepId = "alarm.set_alarm",
                success = false,
                message = e.message ?: "Failed to set alarm"
            )
        }
    }

    /**
     * Execute the alarm.set_alarm action from workflow params.
     *
     * Expected params:
     *   - hour           (int, required)   — 24-hour time, 0–23
     *   - minutes        (int, required)   — 0–59
     *   - message        (string, optional)— alarm label
     *   - _requestCode   (int, optional)  — deduplication key (defaults to 0)
     */
    fun execute(params: JsonObject): ExecutionResult {
        val hour = params["hour"]?.let { (it as? JsonPrimitive)?.intOrNull }
            ?: return missingParam("hour")
        val minutes = params["minutes"]?.let { (it as? JsonPrimitive)?.intOrNull }
            ?: return missingParam("minutes")
        val message = params["message"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val requestCode = params["_requestCode"]?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0

        return scheduleAlarm(hour, minutes, message, requestCode)
    }

    private fun missingParam(name: String): ExecutionResult {
        return ExecutionResult(
            stepId = "alarm.set_alarm",
            success = false,
            message = "Missing required param: $name"
        )
    }

    /**
     * Compute the epoch milliseconds for the next occurrence of the given hour/minute.
     * If the time has already passed today, returns tomorrow's timestamp.
     */
    private fun computeTriggerTimeMillis(hour: Int, minutes: Int): Long {
        val now = java.util.Calendar.getInstance()
        val alarm = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minutes)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        // If the alarm time is already past today's time, advance to tomorrow.
        if (alarm.timeInMillis <= now.timeInMillis) {
            alarm.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return alarm.timeInMillis
    }

    private fun formatTime(hour: Int, minutes: Int): String {
        return "%02d:%02d".format(hour, minutes)
    }
}
