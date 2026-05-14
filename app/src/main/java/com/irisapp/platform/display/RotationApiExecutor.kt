package com.irisapp.platform.display

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently locks screen rotation to portrait, landscape, or restores auto-rotate.
 *
 * Requires: WRITE_SETTINGS — must be granted by user via Settings.
 * On first execution without WRITE_SETTINGS, returns failure with guidance to Settings.
 * Fallback: opens Display Settings.
 */
class RotationApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "RotationApiExecutor"
    }

    fun canWriteSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun execute(params: JsonObject): ExecutionResult {
        if (!canWriteSettings()) {
            return ExecutionResult(
                stepId = "rotation.lock",
                success = false,
                message = "WRITE_SETTINGS permission not granted. Open Settings → Display → Adjust the brightness slider to grant."
            )
        }

        val modeStr = params["mode"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()

        return when (modeStr) {
            "portrait" -> setRotation(1)
            "landscape" -> setRotation(0)
            "auto" -> setAutoRotation()
            else -> ExecutionResult(
                stepId = "rotation.lock",
                success = false,
                message = "Invalid mode '$modeStr'. Use: portrait, landscape, auto"
            )
        }
    }

    /**
     * Lock rotation to a fixed value.
     * 1 = portrait, 0 = landscape (on most devices).
     */
    private fun setRotation(value: Int): ExecutionResult {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.USER_ROTATION,
                value
            )
            // Ensure auto-rotation is OFF when locking to a fixed orientation
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            )
            val msg = "Rotation locked to ${if (value == 1) "portrait" else "landscape"}"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "rotation.lock", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to lock rotation: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "rotation.lock", success = false, message = msg)
        }
    }

    /**
     * Restore auto-rotation by enabling the accelerometer rotation sensor.
     */
    private fun setAutoRotation(): ExecutionResult {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                1
            )
            val msg = "Auto-rotation restored"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "rotation.lock", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to enable auto-rotation: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "rotation.lock", success = false, message = msg)
        }
    }

    /**
     * Open Display Settings as a fallback when WRITE_SETTINGS is not granted.
     */
    fun openDisplaySettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context is Activity) {
                context.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open display settings", e)
            false
        }
    }
}