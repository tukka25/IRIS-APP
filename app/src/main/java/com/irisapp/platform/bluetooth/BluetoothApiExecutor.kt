package com.irisapp.platform.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Toggles Bluetooth on or off via BluetoothAdapter.
 *
 * - Android 12+ (API 31+): enable()/disable() require BLUETOOTH_CONNECT permission
 *   (runtime-granted). Without it, falls back to opening Bluetooth Settings.
 * - Android 10-11 (API 29-30): enable()/disable() work silently for third-party apps.
 * - Android 9 and below: no special restrictions.
 *
 * Params: state (String: "on" | "off" | "toggle")
 *
 * Required permission: android.permission.BLUETOOTH_CONNECT (API 31+)
 * Already declared in AndroidManifest.xml.
 */
class BluetoothApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothApiExecutor"
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        @SuppressLint("HardwareIds")
        BluetoothAdapter.getDefaultAdapter()
    }

    @SuppressLint("MissingPermission")
    fun execute(params: JsonObject): ExecutionResult {
        val state = params["state"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()

        if (bluetoothAdapter == null) {
            val msg = "Bluetooth is not available on this device"
            Log.e(TAG, msg)
            return ExecutionResult(stepId = "bluetooth.toggle", success = false, message = msg)
        }

        val targetState = when (state) {
            "on" -> true
            "off" -> false
            "toggle" -> !(bluetoothAdapter?.isEnabled ?: false)
            else -> return ExecutionResult(
                stepId = "bluetooth.toggle",
                success = false,
                message = "Invalid state '$state'. Use: on, off, toggle"
            )
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: must check BLUETOOTH_CONNECT runtime permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                // Redirect to Bluetooth Settings — user must grant the permission there
                openBluetoothSettings()
                return ExecutionResult(
                    stepId = "bluetooth.toggle",
                    success = false,
                    message = "BLUETOOTH_CONNECT permission required — opened Bluetooth Settings"
                )
            }
            setBluetooth(targetState)
        } else {
            // Android < 12: no runtime permission needed for enable/disable
            setBluetooth(targetState)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setBluetooth(enable: Boolean): ExecutionResult {
        return try {
            val adapter = bluetoothAdapter ?: return ExecutionResult(
                stepId = "bluetooth.toggle",
                success = false,
                message = "Bluetooth adapter unavailable"
            )

            val op = if (enable) "Enabling" else "Disabling"
            Log.i(TAG, "$op Bluetooth")

            val success = if (enable) adapter.enable() else adapter.disable()
            val stateLabel = if (enable) "on" else "off"

            if (success) {
                val msg = "Bluetooth set to $stateLabel"
                Log.i(TAG, msg)
                ExecutionResult(stepId = "bluetooth.toggle", success = true, message = msg)
            } else {
                // Returns false when already in the requested state or radio is busy
                val currentState = if (adapter.isEnabled) "on" else "off"
                val msg = "Bluetooth is already $currentState"
                Log.w(TAG, msg)
                ExecutionResult(stepId = "bluetooth.toggle", success = true, message = msg)
            }
        } catch (e: Exception) {
            val msg = "Failed to toggle Bluetooth: ${e.message}"
            Log.e(TAG, msg, e)
            openBluetoothSettings()
            ExecutionResult(
                stepId = "bluetooth.toggle",
                success = false,
                message = "$msg — opened Bluetooth Settings"
            )
        }
    }

    private fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Bluetooth Settings", e)
        }
    }
}