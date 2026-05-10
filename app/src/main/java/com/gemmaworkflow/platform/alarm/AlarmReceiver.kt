package com.gemmaworkflow.platform.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver that fires when a silent alarm (scheduled via
 * [AlarmApiExecutor]/AlarmManager) reaches its trigger time.
 *
 * Posts a high-priority notification with the alarm label rather than
 * launching the Clock app.
 *
 * Registered in AndroidManifest.xml for [AlarmApiExecutor.ACTION_ALARM_FIRE].
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmApiExecutor.ACTION_ALARM_FIRE) {
            Log.w(TAG, "Received unknown action: ${intent.action}")
            return
        }

        val message = intent.getStringExtra(AlarmApiExecutor.EXTRA_ALARM_MESSAGE) ?: "Alarm"
        val requestCode = intent.getIntExtra(AlarmApiExecutor.EXTRA_REQUEST_CODE, 0)

        Log.i(TAG, "Alarm fired: message='$message', requestCode=$requestCode")

        postNotification(context, message)
    }

    private fun postNotification(context: Context, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel on Android 8.0+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AlarmApiExecutor.NOTIFICATION_CHANNEL_ID,
                /* name = */ "Alarms",
                /* importance = */ NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Silent alarm notifications"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification using the system-approved AlarmClock notification style
        // so it appears alongside native clock-app alarms in the notification shade.
        val notification = android.app.Notification.Builder(context, AlarmApiExecutor.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(message)
            .setContentText("GemmaWorkflow alarm")
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Cannot post alarm notification: POST_NOTIFICATIONS permission not granted")
            return
        }

        try {
            notificationManager.notify(AlarmApiExecutor.NOTIFICATION_ID, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot post alarm notification due to missing permission", se)
        }
    }
}
