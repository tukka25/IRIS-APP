package com.gemmaworkflow.platform.wifi

import android.annotation.SuppressLint
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

/**
 * Toggles WiFi on or off via WifiManager.setWifiEnabled().
 *
 * - Android 10+ (API 29+): setWifiEnabled() opens a system Settings tile
 *   (user must toggle manually) due to restricted background settings changes.
 * - Android < 10: setWifiEnabled() works silently.
 *
 * Params: state (String: "on" | "off" | "toggle")
 *
 * Required permissions (already declared in AndroidManifest.xml):
 *   - android.permission.ACCESS_WIFI_STATE
 *   - android.permission.CHANGE_WIFI_STATE
 */
class WifiApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "WifiApiExecutor"
    }

    private val wifiManager: WifiManager? by lazy {
        @SuppressLint("WifiManagerLeak")
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    @SuppressLint("MissingPermission")
    fun execute(params: JsonObject): ExecutionResult {
        val state = params["state"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()

        if (wifiManager == null) {
            val msg = "WiFi is not available on this device"
            Log.e(TAG, msg)
            return ExecutionResult(stepId = "wifi.toggle", success = false, message = msg)
        }

        val targetState = when (state) {
            "on" -> true
            "off" -> false
            "toggle" -> !(wifiManager?.isWifiEnabled ?: false)
            else -> return ExecutionResult(
                stepId = "wifi.toggle",
                success = false,
                message = "Invalid state '$state'. Use: on, off, toggle"
            )
        }

        return setWifi(targetState)
    }

    @SuppressLint("MissingPermission")
    private fun setWifi(enable: Boolean): ExecutionResult {
        return try {
            val wm = wifiManager ?: return ExecutionResult(
                stepId = "wifi.toggle",
                success = false,
                message = "WifiManager unavailable"
            )

            val op = if (enable) "Enabling" else "Disabling"
            Log.i(TAG, "$op WiFi")

            @Suppress("DEPRECATION")
            val success = wm.setWifiEnabled(enable)

            if (success) {
                val stateLabel = if (enable) "on" else "off"
                val msg = "WiFi set to $stateLabel"
                Log.i(TAG, msg)
                ExecutionResult(stepId = "wifi.toggle", success = true, message = msg)
            } else {
                // On Android 10+ this returns false because the system requires user interaction
                // Open WiFi Settings as a fallback so the user can toggle manually
                openWifiSettings()
                val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "Android 10+ requires manual toggle in Settings — opened WiFi Settings"
                } else {
                    "WiFi toggle failed — opened WiFi Settings"
                }
                Log.w(TAG, msg)
                ExecutionResult(stepId = "wifi.toggle", success = false, message = msg)
            }
        } catch (e: Exception) {
            val msg = "Failed to toggle WiFi: ${e.message}"
            Log.e(TAG, msg, e)
            openWifiSettings()
            ExecutionResult(
                stepId = "wifi.toggle",
                success = false,
                message = "$msg — opened WiFi Settings"
            )
        }
    }

    private fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WiFi Settings", e)
        }
    }
}