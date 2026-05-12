package com.gemmaworkflow.platform.sms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.trigger.TriggerRegistry

/**
 * Unified notification listener for Android 14+.
 *
 * Handles all notification-based triggers:
 * - SMS Received (Android 14+ restriction — SMS_RECEIVED_ACTION no longer works)
 * - Messaging app notifications (WhatsApp, Telegram, Signal)
 * - Email received notifications (Gmail, Outlook, Samsung Email)
 *
 * The user must enable notification access in:
 *   Settings → Apps → GemmaWorkflow → Notification access → Allow
 */
class SmsNotificationListener : NotificationListenerService() {

    private val TAG = "SmsNotificationListener"
    private val CACHE_EXPIRY_MS = 30_000L

    /** Known package names per category. */
    private val SMS_PACKAGES = setOf(
        "com.google.android.apps.messages",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.oneplus.messaging",
        "com.miui.mms",
    )

    private val MESSAGING_PACKAGES = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.signal.android",
        "com.discord",
        "com.slack",
        "com.zulip.inzulip",
    )

    private val EMAIL_PACKAGES = setOf(
        "com.google.android.gm",           // Gmail
        "com.microsoft.office.outlook",     // Outlook
        "com.samsung.android.email",        // Samsung Email
        "com.sony.smartexplorer",            // Sony email
    )

    @Volatile
    private var cachedTriggerWorkflows: List<com.gemmaworkflow.domain.model.PlannedWorkflow> = emptyList()
    @Volatile
    private var lastCacheLoadMs: Long = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val body = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        if (body.isBlank() && title.isBlank()) return

        Log.d(TAG, "Notification from $packageName: title='$title' body='${body.take(60)}'")

        val allWorkflows = getTriggerWorkflows()

        // ── SMS workflows ────────────────────────────────────────────────
        val smsWorkflows = allWorkflows.filter { it.trigger is TriggerConfig.SmsReceived }
        for (workflow in smsWorkflows) {
            val trigger = workflow.trigger as TriggerConfig.SmsReceived
            if (SMS_PACKAGES.contains(packageName) && matchesSms(trigger, title, body)) {
                Log.i(TAG, "SMS notification matched workflow: ${workflow.name}")
                TriggerRegistry.fire(applicationContext, workflow)
            }
        }

        // ── Messaging (NotificationListenerConfig) workflows ──────────────
        val messagingWorkflows = allWorkflows.filter { it.trigger is TriggerConfig.NotificationListenerConfig }
        for (workflow in messagingWorkflows) {
            val trigger = workflow.trigger as TriggerConfig.NotificationListenerConfig
            if (matchesMessaging(trigger, packageName, title, body)) {
                Log.i(TAG, "Messaging notification matched workflow: ${workflow.name}")
                TriggerRegistry.fire(applicationContext, workflow)
            }
        }

        // ── Email workflows ───────────────────────────────────────────────
        val emailWorkflows = allWorkflows.filter { it.trigger is TriggerConfig.EmailReceived }
        for (workflow in emailWorkflows) {
            val trigger = workflow.trigger as TriggerConfig.EmailReceived
            if (EMAIL_PACKAGES.contains(packageName) && matchesEmail(trigger, title, body, subText, packageName)) {
                Log.i(TAG, "Email notification matched workflow: ${workflow.name}")
                TriggerRegistry.fire(applicationContext, workflow)
            }
        }
    }

    private fun matchesSms(trigger: TriggerConfig.SmsReceived, sender: String, body: String): Boolean {
        if (trigger.senderPattern != null && !Regex(trigger.senderPattern, RegexOption.IGNORE_CASE).containsMatchIn(sender)) {
            return false
        }
        if (trigger.bodyPattern != null && !Regex(trigger.bodyPattern, RegexOption.IGNORE_CASE).containsMatchIn(body)) {
            return false
        }
        return true
    }

    private fun matchesMessaging(trigger: TriggerConfig.NotificationListenerConfig, packageName: String, sender: String, body: String): Boolean {
        // Filter by specific app package patterns if set
        if (trigger.appPackagePatterns.isNotEmpty() && !trigger.appPackagePatterns.any { packageName.contains(it, ignoreCase = true) }) {
            return false
        }
        // If no package patterns set, check against known messaging packages
        if (trigger.appPackagePatterns.isEmpty() && !MESSAGING_PACKAGES.contains(packageName)) {
            return false
        }
        if (trigger.senderPattern != null && !Regex(trigger.senderPattern, RegexOption.IGNORE_CASE).containsMatchIn(sender)) {
            return false
        }
        if (trigger.bodyPattern != null && !Regex(trigger.bodyPattern, RegexOption.IGNORE_CASE).containsMatchIn(body)) {
            return false
        }
        return true
    }

    private fun matchesEmail(trigger: TriggerConfig.EmailReceived, title: String, body: String, subText: String, packageName: String): Boolean {
        // Filter by app package if set, otherwise check known email packages
        if (trigger.appPackage.isNotBlank() && packageName != trigger.appPackage) {
            return false
        }
        if (trigger.appPackage.isBlank() && !EMAIL_PACKAGES.contains(packageName)) {
            return false
        }
        if (trigger.senderPattern != null && !Regex(trigger.senderPattern, RegexOption.IGNORE_CASE).containsMatchIn(title)) {
            return false
        }
        if (trigger.subjectPattern != null && !Regex(trigger.subjectPattern, RegexOption.IGNORE_CASE).containsMatchIn(subText)) {
            return false
        }
        return true
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Nothing to do — we only act on incoming notifications
    }

    private fun getTriggerWorkflows(): List<com.gemmaworkflow.domain.model.PlannedWorkflow> {
        val now = System.currentTimeMillis()
        if (now - lastCacheLoadMs < CACHE_EXPIRY_MS && cachedTriggerWorkflows.isNotEmpty()) {
            return cachedTriggerWorkflows
        }
        val repository = WorkflowRepository(applicationContext)
        val refreshed = repository.loadAll().filter {
            it.trigger is TriggerConfig.SmsReceived ||
                it.trigger is TriggerConfig.NotificationListenerConfig ||
                it.trigger is TriggerConfig.EmailReceived
        }
        cachedTriggerWorkflows = refreshed
        lastCacheLoadMs = now
        return refreshed
    }
}
