package com.gemmaworkflow.platform.tools.impl

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolParam
import com.gemmaworkflow.platform.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Reminder and timer tools.
 * Uses AlarmClock intents for standard alarms/timers.
 * App-owned reminders use WorkManager/AlarmManager.
 */
class SetCountdownTimerTool(private val context: Context) : Tool {
    override val name = "set_countdown_timer"
    override val description = "Opens the clock app to set a countdown timer. Use for 'set a timer for 5 minutes'."
    override val parameters = listOf(
        ToolParam("minutes", "int", description = "Timer duration in minutes"),
        ToolParam("message", "string", required = false, description = "Optional timer label")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val minutes = input["minutes"]?.toIntOrNull() ?: return ToolResult(false, "", "Missing 'minutes' param")
        val message = input["message"] ?: "GemmaWorkflow timer"

        return withContext(Dispatchers.IO) {
            runCatching {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult(true, "Timer set: $minutes minutes ($message)")
            }.getOrElse { e ->
                ToolResult(false, "", "Timer failed: ${e.message}")
            }
        }
    }
}

class CreateLocalReminderTool(private val context: Context) : Tool {
    override val name = "create_local_reminder"
    override val description = "Schedules a local reminder notification at a specific time. Use for 'remind me to call Mom at 3pm'."
    override val parameters = listOf(
        ToolParam("title", "string", description = "Reminder title"),
        ToolParam("time_millis", "int", description = "Target time in epoch milliseconds"),
        ToolParam("message", "string", required = false, description = "Optional reminder body")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val title = input["title"] ?: return ToolResult(false, "", "Missing 'title'")
        val timeMs = input["time_millis"]?.toLongOrNull() ?: return ToolResult(false, "", "Missing 'time_millis'")
        val message = input["message"] ?: ""

        return withContext(Dispatchers.IO) {
            runCatching {
                val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("title", title)
                    putExtra("message", message)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, title.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= 31) {
                    if (alarmMgr.canScheduleExactAlarms()) {
                        alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
                    } else {
                        alarmMgr.set(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
                }

                ToolResult(true, "Reminder set: \"$title\" at ${formatTime(timeMs)}")
            }.getOrElse { e ->
                ToolResult(false, "", "Reminder failed: ${e.message}")
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", cal.get(Calendar.MINUTE))}"
    }
}

/**
 * Minimal BroadcastReceiver stub for reminder notifications.
 * Registered in the manifest — actual notification display handled here.
 */
class ReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        val message = intent.getStringExtra("message") ?: ""

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return // Permission not granted — silently skip
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "gemmaworkflow_reminders"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = android.app.NotificationChannel(
                channelId, "Reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }.apply {
            setSmallIcon(android.R.drawable.ic_dialog_info)
            setContentTitle(title)
            setContentText(message)
            setAutoCancel(true)
        }.build()

        notificationManager.notify(title.hashCode(), notification)
    }
}
