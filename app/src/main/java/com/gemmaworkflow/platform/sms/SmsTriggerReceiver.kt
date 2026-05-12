package com.gemmaworkflow.platform.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.trigger.TriggerRegistry

/**
 * Primary SMS listener for Android < 14.
 *
 * Receives SMS_RECEIVED_ACTION broadcasts and matches incoming messages against
 * registered SMS workflows. Fires matching workflows via [TriggerRegistry.fire].
 *
 * For Android 14+, [SmsNotificationListener] is the primary fallback as
 * SMS_RECEIVED_ACTION is restricted.
 */
class SmsTriggerReceiver : BroadcastReceiver() {

    private val TAG = "SmsTriggerReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "No SMS messages in intent")
            return
        }

        // Extract sender and body from the first message
        val sender = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }

        Log.i(TAG, "SMS received from $sender: ${body.take(50)}")

        // Load and filter SMS workflows
        val repository = WorkflowRepository(context)
        val workflows = repository.loadAll()
        val smsWorkflows = workflows.filter { it.trigger is TriggerConfig.SmsReceived }

        for (workflow in smsWorkflows) {
            val trigger = workflow.trigger as TriggerConfig.SmsReceived

            if (matchesSms(trigger, sender, body)) {
                Log.i(TAG, "SMS matched workflow: ${workflow.name} (from $sender)")
                TriggerRegistry.fire(context, workflow)
            }
        }
    }

    /**
     * Returns true if the incoming SMS matches the trigger's patterns.
     */
    private fun matchesSms(trigger: TriggerConfig.SmsReceived, sender: String, body: String): Boolean {
        // Normalize sender: strip +, spaces, dashes
        val normalizedSender = normalizePhoneNumber(sender)

        // Sender pattern check
        if (trigger.senderPattern != null) {
            val senderRegex = Regex(trigger.senderPattern, RegexOption.IGNORE_CASE)
            val normalizedPattern = normalizePhoneNumber(trigger.senderPattern)
            // Match against both original and normalized sender
            val senderMatches = senderRegex.containsMatchIn(sender) ||
                (normalizedPattern.isNotBlank() && senderRegex.containsMatchIn(normalizedSender))
            if (!senderMatches) return false
        }

        // Body pattern check
        if (trigger.bodyPattern != null) {
            val bodyRegex = Regex(trigger.bodyPattern, RegexOption.IGNORE_CASE)
            if (!bodyRegex.containsMatchIn(body)) return false
        }

        return true
    }

    /**
     * Normalize a phone number by stripping +, spaces, dashes, and leading zeros.
     * Makes comparison more robust across formats.
     */
    private fun normalizePhoneNumber(number: String): String {
        return number
            .replace("+", "")
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .trimStart('0')
    }
}