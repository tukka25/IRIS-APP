package com.irisapp.platform.cellular

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently toggles mobile data on or off via Settings.Global.
 *
 * Requires system app or root. On failure due to permission, falls back to
 * opening the Mobile Data Settings screen.
 *
 * Requires: WRITE_SECURE_SETTINGS permission (granted only to system apps).
 */
class CellularApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "CellularApiExecutor"
        private const val MOBILE_DATA = "mobile_data"
    }

    fun execute(params: JsonObject): ExecutionResult {
        val stateStr = params["state"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()

        if (stateStr != "on" && stateStr != "off") {
            return ExecutionResult(
                stepId = "cellular.toggle",
                success = false,
                message = "Invalid state '$stateStr'. Use: on or off"
            )
        }

        val enable = stateStr == "on"
        val value = if (enable) 1 else 0

        return try {
            setMobileData(value)
            val msg = "Mobile data ${if (enable) "enabled" else "disabled"}"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "cellular.toggle", success = true, message = msg)
        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS permission denied, falling back to Settings", e)
            openMobileDataSettings()
        } catch (e: Exception) {
            val msg = "Cellular toggle failed: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "cellular.toggle", success = false, message = msg)
        }
    }

    @Suppress("DEPRECATION")
    private fun setMobileData(value: Int) {
        Settings.Global.putInt(context.contentResolver, MOBILE_DATA, value)
    }

    private fun openMobileDataSettings(): ExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(
                stepId = "cellular.toggle",
                success = false,
                message = "WRITE_SECURE_SETTINGS permission required — opened Mobile Data Settings"
            )
        } catch (e: Exception) {
            // Fall back to wireless settings if DATA_ROAMING isn't available
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(
                    stepId = "cellular.toggle",
                    success = false,
                    message = "WRITE_SECURE_SETTINGS permission required — opened Wireless Settings"
                )
            } catch (e2: Exception) {
                ExecutionResult(
                    stepId = "cellular.toggle",
                    success = false,
                    message = "Failed to open settings: ${e2.message}"
                )
            }
        }
    }
}