package com.gemmaworkflow.platform.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently sends a custom notification with title, body, channel and priority.
 *
 * Requires: POST_NOTIFICATIONS (Android 13+) — runtime request on Android 13+.
 * For Android < 13: no runtime permission needed, notification is sent directly.
 */
class NotificationApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "NotificationApiExecutor"
        private const val DEFAULT_CHANNEL = "notification_default"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun execute(params: JsonObject): ExecutionResult {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                return ExecutionResult(
                    stepId = "notification.send",
                    success = false,
                    message = "POST_NOTIFICATIONS permission not granted. Please grant it in system settings."
                )
            }
        }

        val title = params["title"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val body = params["body"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (title.isNullOrBlank() && body.isNullOrBlank()) {
            return ExecutionResult(
                stepId = "notification.send",
                success = false,
                message = "Missing required params: 'title' and/or 'body'"
            )
        }

        val channelId = params["channel"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: DEFAULT_CHANNEL
        val priorityStr = params["priority"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "normal"

        // Create channel for API 26+
        createChannel(channelId)

        val priority = when (priorityStr.lowercase()) {
            "low" -> NotificationCompat.PRIORITY_LOW
            "high" -> NotificationCompat.PRIORITY_HIGH
            "max" -> NotificationCompat.PRIORITY_MAX
            "min" -> NotificationCompat.PRIORITY_MIN
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: "GemmaWorkflow")
            .setContentText(body ?: "")
            .setAutoCancel(true)
            .setPriority(priority)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        try {
            notificationManager.notify(notificationId, notification)
            val msg = "Notification sent (id=$notificationId, channel=$channelId)"
            Log.i(TAG, msg)
            return ExecutionResult(stepId = "notification.send", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to send notification: ${e.message}"
            Log.e(TAG, msg, e)
            return ExecutionResult(stepId = "notification.send", success = false, message = msg)
        }
    }

    private fun createChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelId.replace("_", " ").replaceFirstChar { it.uppercase() },
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}