package com.irisapp.platform.airplane

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently toggles Airplane Mode on or off via Settings.Global + broadcasts.
 *
 * Requires system app or root. On failure due to permission, falls back to
 * opening the Airplane Mode Settings screen.
 *
 * Requires: WRITE_SECURE_SETTINGS permission (granted only to system apps).
 */
class AirplaneModeApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "AirplaneModeApiExecutor"
        private const val AIRPLANE_MODE_ON = "airplane_mode_on"
        private const val ACTION_AIRPLANE_MODE_CHANGED = "android.intent.action.AIRPLANE_MODE"
    }

    fun execute(params: JsonObject): ExecutionResult {
        val stateStr = params["state"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()

        if (stateStr != "on" && stateStr != "off") {
            return ExecutionResult(
                stepId = "airplane_mode.toggle",
                success = false,
                message = "Invalid state '$stateStr'. Use: on or off"
            )
        }

        val enable = stateStr == "on"

        return try {
            setAirplaneMode(enable)
            val msg = "Airplane mode ${if (enable) "enabled" else "disabled"}"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "airplane_mode.toggle", success = true, message = msg)
        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS permission denied, falling back to Settings", e)
            openAirplaneModeSettings()
        } catch (e: Exception) {
            val msg = "Airplane mode toggle failed: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "airplane_mode.toggle", success = false, message = msg)
        }
    }

    @Suppress("DEPRECATION")
    private fun setAirplaneMode(enable: Boolean) {
        Settings.Global.putInt(context.contentResolver, AIRPLANE_MODE_ON, if (enable) 1 else 0)
        // Broadcast the change so the system picks it up
        val intent = Intent(ACTION_AIRPLANE_MODE_CHANGED).apply {
            putExtra("state", enable)
        }
        context.sendBroadcast(intent)
    }

    private fun openAirplaneModeSettings(): ExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(
                stepId = "airplane_mode.toggle",
                success = false,
                message = "WRITE_SECURE_SETTINGS permission required — opened Airplane Mode Settings"
            )
        } catch (e: Exception) {
            ExecutionResult(
                stepId = "airplane_mode.toggle",
                success = false,
                message = "Failed to open airplane mode settings: ${e.message}"
            )
        }
    }
}