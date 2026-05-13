package com.gemmaworkflow.platform.hotspot

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.lang.reflect.Method

/**
 * Silently toggles the mobile hotspot (Wi-Fi AP) on or off via WifiManager.
 *
 * Requires system app or root. On failure due to permission, falls back to
 * opening the hotspot Settings screen (ACTION_WIFI_AP_SETTINGS).
 *
 * Requires: WRITE_SETTINGS permission (granted by user via Settings).
 */
class HotspotApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "HotspotApiExecutor"
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    /**
     * Attempt to toggle hotspot via WifiManager API.
     * Falls back to Settings on SecurityException or RuntimeException.
     */
    fun execute(params: JsonObject): ExecutionResult {
        val stateStr = params["state"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()

        if (stateStr != "on" && stateStr != "off") {
            return ExecutionResult(
                stepId = "hotspot.toggle",
                success = false,
                message = "Invalid state '$stateStr'. Use: on or off"
            )
        }

        val enable = stateStr == "on"

        return try {
            val result = setWifiApEnabled(enable)
            val msg = if (result) {
                "Hotspot ${if (enable) "enabled" else "disabled"}"
            } else {
                "Hotspot toggle returned false — check system permissions"
            }
            Log.i(TAG, msg)
            ExecutionResult(stepId = "hotspot.toggle", success = result, message = msg)
        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_SETTINGS permission denied, falling back to Settings", e)
            openHotspotSettings()
        } catch (e: Exception) {
            val msg = "Hotspot toggle failed: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "hotspot.toggle", success = false, message = msg)
        }
    }

    @Suppress("DEPRECATION")
    private fun setWifiApEnabled(enable: Boolean): Boolean {
        return try {
            // Attempt to get the method; on some devices it may not exist or requires system app
            val method: Method = wifiManager.javaClass.getMethod("setWifiApEnabled",
                java.lang.Boolean.TYPE)
            // Passing null config keeps the existing configuration
            method.invoke(wifiManager, null, enable) as Boolean
        } catch (e: Exception) {
            val cause = e.cause ?: e
            Log.e(TAG, "setWifiApEnabled reflection failed: $cause", cause)
            throw e
        }
    }

    private fun openHotspotSettings(): ExecutionResult {
        return try {
            val intent = Intent("android.settings.TETHER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(
                stepId = "hotspot.toggle",
                success = false,
                message = "WRITE_SETTINGS permission required — opened Hotspot Settings"
            )
        } catch (e: Exception) {
            ExecutionResult(
                stepId = "hotspot.toggle",
                success = false,
                message = "Failed to open hotspot settings: ${e.message}"
            )
        }
    }
}