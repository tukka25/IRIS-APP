package com.irisapp.platform.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Displays a transient on-screen toast message.
 *
 * No permissions required. Lightweight feedback-only action.
 */
class ToastApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ToastApiExecutor"
    }

    fun execute(params: JsonObject): ExecutionResult {
        val message = params["message"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (message.isNullOrBlank()) {
            return ExecutionResult(stepId = "toast.show", success = false, message = "Missing required param: message")
        }

        val durationStr = params["duration"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()
        val duration = when (durationStr) {
            "long" -> Toast.LENGTH_LONG
            else -> Toast.LENGTH_SHORT
        }

        return try {
            // Toast requires the current thread to have a Looper (i.e. be the main thread).
            // If we're on a background thread (e.g. WorkflowRunner on Dispatchers.Default),
            // post the show call to the main thread to avoid a "Can't toast on a thread
            // that has not called Looper.prepare()" crash.
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(context, message, duration).show()
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, duration).show()
                }
            }
            val msg = "Toast shown: $message"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "toast.show", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to show toast: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "toast.show", success = false, message = msg)
        }
    }
}