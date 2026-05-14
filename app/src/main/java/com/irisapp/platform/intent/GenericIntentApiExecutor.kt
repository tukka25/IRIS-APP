package com.irisapp.platform.intent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.json.JSONObject

/**
 * Sends a raw Android Intent (broadcast, activity start, or service start).
 * SECURITY-SENSITIVE — requires user confirmation before execution.
 *
 * No permissions are declared here; the caller bears risk based on the intent
 * being sent. Most broadcast intents require no permission. Activity/service
 * starts may require permissions depending on the target.
 */
class GenericIntentApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "GenericIntentApiExecutor"
    }

    fun execute(params: JsonObject): ExecutionResult {
        val action = params["action"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (action.isNullOrBlank()) {
            return ExecutionResult(
                stepId = "intent.send",
                success = false,
                message = "Missing required parameter: action"
            )
        }

        val target = params["target"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase() ?: "broadcast"
        val data = params["data"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val type = params["type"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val extrasRaw = params["extras"]?.let { (it as? JsonPrimitive)?.contentOrNull }

        return try {
            val intent = Intent(action)

            // Set data URI if provided
            if (!data.isNullOrBlank()) {
                intent.data = Uri.parse(data)
            }

            // Set MIME type if provided
            if (!type.isNullOrBlank()) {
                intent.type = type
            }

            // Parse and add extras from JSON string
            if (!extrasRaw.isNullOrBlank()) {
                parseExtras(extrasRaw, intent)
            }

            // Set flags
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Send based on target
            when (target) {
                "broadcast" -> {
                    context.sendBroadcast(intent)
                    val msg = "Broadcast sent: $action"
                    Log.i(TAG, msg)
                    ExecutionResult(stepId = "intent.send", success = true, message = msg)
                }
                "activity" -> {
                    context.startActivity(intent)
                    val msg = "Activity started: $action"
                    Log.i(TAG, msg)
                    ExecutionResult(stepId = "intent.send", success = true, message = msg)
                }
                "service" -> {
                    context.startService(intent)
                    val msg = "Service started: $action"
                    Log.i(TAG, msg)
                    ExecutionResult(stepId = "intent.send", success = true, message = msg)
                }
                else -> {
                    ExecutionResult(
                        stepId = "intent.send",
                        success = false,
                        message = "Invalid target '$target'. Use: broadcast, activity, service"
                    )
                }
            }
        } catch (e: Exception) {
            val msg = "Failed to send intent: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "intent.send", success = false, message = msg)
        }
    }

    /**
     * Parse a JSON string of extras and add them to the intent.
     * Supports String, Int, Boolean, Long, Double, Float, and null values.
     */
    private fun parseExtras(extrasJson: String, intent: Intent) {
        try {
            val json = JSONObject(extrasJson)
            json.keys().forEach { key ->
                val value = json.get(key)
                when (value) {
                    is String -> intent.putExtra(key, value)
                    is Int -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                    is Long -> intent.putExtra(key, value)
                    is Double -> intent.putExtra(key, value)
                    is Float -> intent.putExtra(key, value)
                    JSONObject.NULL -> { /* skip null extras */ }
                    else -> intent.putExtra(key, value.toString())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse extras JSON: ${e.message}")
        }
    }
}