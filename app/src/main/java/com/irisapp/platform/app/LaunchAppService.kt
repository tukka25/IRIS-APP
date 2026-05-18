package com.irisapp.platform.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.irisapp.ui.MainActivity

/**
 * A foreground-capable Service used to launch apps from workflow steps.
 *
 * On Android 12+, `Activity.startActivity()` from a non-Activity context
 * (BroadcastReceiver, background Service, etc.) creates an "orphaned" Activity
 * with no task/back-stack. The system immediately kills it.
 *
 * Using a Service with `startForeground` lets us launch the target app's
 * Activity via `PendingIntent.getActivity()` with `FLAG_ACTIVITY_NEW_TASK`,
 * which is correctly attributed to the calling app and produces a valid
 * back-stack entry.
 *
 * On older Android versions this is not strictly required, but is harmless.
 */
class LaunchAppService : Service() {

    companion object {
        private const val TAG = "LaunchAppService"
        const val CHANNEL_ID = "launch_app_channel"
        const val NOTIFICATION_ID = 20250301
        const val ACTION_LAUNCH = "com.irisapp.platform.app.ACTION_LAUNCH"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_CLASS_NAME = "extra_class_name"

        fun launch(context: Context, packageName: String, className: String? = null) {
            val intent = Intent(context, LaunchAppService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                className?.let { putExtra(EXTRA_CLASS_NAME, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_LAUNCH) {
            stopSelf()
            return START_NOT_STICKY
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val className = intent.getStringExtra(EXTRA_CLASS_NAME)

        if (packageName.isNullOrBlank()) {
            Log.w(TAG, "No package name provided")
            stopSelf()
            return START_NOT_STICKY
        }

        // Show a foreground notification so Android doesn't kill us before launch.
        // The notification is dismissed immediately after the launch completes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Launching $packageName..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Launching $packageName..."))
        }

        val result = doLaunch(packageName, className)

        if (!result) {
            // If launch failed, keep notification visible briefly so user sees the error.
            Log.w(TAG, "Launch failed for $packageName — not stopping immediately")
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun doLaunch(packageName: String, className: String?): Boolean {
        return try {
            val pm = packageManager
            val launchIntent = if (!className.isNullOrBlank()) {
                Intent().setClassName(packageName, className)
            } else {
                pm.getLaunchIntentForPackage(packageName)
            }

            if (launchIntent == null) {
                Log.w(TAG, "No launch intent for '$packageName'")
                postErrorNotification("App not found: $packageName")
                return false
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // Use PendingIntent so the target Activity has a valid task affinity.
            val pending = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pending.send()

            Log.i(TAG, "Launched $packageName successfully")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $packageName", e)
            postErrorNotification("Package not found: $packageName")
            false
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "PendingIntent cancelled", e)
            postErrorNotification("Launch cancelled: $packageName")
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception launching $packageName", e)
            postErrorNotification("Cannot launch $packageName: permission denied")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            postErrorNotification("Launch failed: ${e.message ?: e::class.java.simpleName}")
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Launch",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Temporary notification while launching apps" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentTitle("IrisApp")
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun postErrorNotification(message: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("App Launch Failed")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID + 1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post error notification", e)
        }
    }
}