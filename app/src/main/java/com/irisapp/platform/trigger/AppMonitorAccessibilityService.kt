package com.irisapp.platform.trigger

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent

import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AccessibilityService that monitors foreground app changes and fires
 * [TriggerConfig.AppOpened] and [TriggerConfig.AppClosed] workflows.
 *
 * Detects when the user switches to or away from a target app using
 * [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] (package name in
 * [AccessibilityEvent.getPackageName]).
 *
 * AppClosed fires when the previously tracked app moves to background
 * (another app becomes foreground). AppOpened fires when the tracked
 * app comes to foreground.
 *
 * Registration: enabled manually by the user via Settings → Accessibility.
 * The service is not auto-started — the user must grant the permission.
 *
 * @see AppOpenedTriggerManager
 * @see AppClosedTriggerManager
 */
class AppMonitorAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Package of the app currently tracked for AppClosed
    private var lastForegroundPackage: String? = null
    // Whether we have already fired AppOpened for the current foreground app
    private var hasFiredAppOpenedForCurrent = false

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            notificationTimeout = 200
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info

        Log.i(TAG, "AppMonitorAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Ignore system packages and our own app
        if (packageName == "com.android.systemui" ||
            packageName == "com.android.launcher" ||
            packageName == packageName.takeIf { it == "com.android.launcher3" } ||
            packageName == "com.google.android.apps.nexuslauncher" ||
            packageName == this.packageName
        ) {
            // If lastForeground was a tracked app and we see a launcher/sysui, fire AppClosed
            val last = lastForegroundPackage
            if (last != null && last != this.packageName) {
                fireAppClosedIfMatching(packageName, last)
            }
            // Don't update lastForegroundPackage for launcher/system transitions
            // so we can detect when the user truly exits a tracked app
            return
        }

        val context = this@AppMonitorAccessibilityService

        // AppOpened: fire when a tracked app comes to foreground
        fireAppOpenedIfMatching(context, packageName)

        // AppClosed: fire when the previously foreground app is replaced
        val previous = lastForegroundPackage
        if (previous != null && previous != packageName && previous != this.packageName) {
            fireAppClosedIfMatching(packageName, previous)
        }

        lastForegroundPackage = packageName
    }

    private fun fireAppOpenedIfMatching(context: android.content.Context, foregroundPackage: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val repo = WorkflowRepository(context)
                val all = repo.loadAll()

                // Match AppOpened triggers where targetPackage is null (any) or matches foreground
                val matched = all.filter { workflow ->
                    val trigger = workflow.trigger
                    trigger is TriggerConfig.AppOpened &&
                            (trigger.appPackagePatterns.isEmpty() || trigger.appPackagePatterns.any { foregroundPackage.contains(it, ignoreCase = true) })
                }

                for (workflow in matched) {
                    Log.i(TAG, "AppOpened trigger matched for '${workflow.name}' (package: $foregroundPackage)")
                    TriggerRegistry.fire(context, workflow)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error firing AppOpened workflows", e)
            }
        }
    }

    private fun fireAppClosedIfMatching(newForegroundPackage: String, closedPackage: String) {
        val context = this@AppMonitorAccessibilityService
        scope.launch(Dispatchers.IO) {
            try {
                val repo = WorkflowRepository(context)
                val all = repo.loadAll()

                // Match AppClosed triggers where targetPackage is null (any) or matches the app that went to background
                val matched = all.filter { workflow ->
                    val trigger = workflow.trigger
                    trigger is TriggerConfig.AppClosed &&
                            (trigger.appPackagePatterns.isEmpty() || trigger.appPackagePatterns.any { closedPackage.contains(it, ignoreCase = true) })
                }

                for (workflow in matched) {
                    Log.i(TAG, "AppClosed trigger matched for '${workflow.name}' (closed: $closedPackage)")
                    TriggerRegistry.fire(context, workflow)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error firing AppClosed workflows", e)
            }
        }
    }

    override fun onInterrupt() {
        // Required override — called when the service is disrupted
        Log.w(TAG, "AppMonitorAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AppMonitorAccessibilityService destroyed")
    }

    companion object {
        private const val TAG = "AppMonitorAccessibilityService"
    }
}