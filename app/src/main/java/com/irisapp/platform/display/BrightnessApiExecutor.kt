package com.irisapp.platform.display

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently sets screen brightness level (0–100) or toggles auto-brightness.
 *
 * Requires: WRITE_SETTINGS — must be granted by user via Settings.
 * On first execution without WRITE_SETTINGS, returns failure with guidance to Settings.
 */
class BrightnessApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "BrightnessApiExecutor"
    }

    fun canWriteSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun execute(params: JsonObject): ExecutionResult {
        if (!canWriteSettings()) {
            return ExecutionResult(
                stepId = "brightness.set",
                success = false,
                message = "WRITE_SETTINGS permission not granted. Open Settings → Display → Adjust the brightness slider to grant."
            )
        }

        val autoMode = params["auto"]?.let {
            (it as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        } ?: false

        return if (autoMode) {
            setAutoBrightness()
        } else {
            val levelStr = params["level"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            val level = levelStr?.toIntOrNull()
            if (level == null) {
                ExecutionResult(stepId = "brightness.set", success = false, message = "Missing 'level' (0–100) or 'auto'=true")
            } else {
                setBrightness(level.coerceIn(0, 100))
            }
        }
    }

    private fun setBrightness(levelPercent: Int): ExecutionResult {
        return try {
            // 0–100 → 0–255
            val brightnessValue = (levelPercent * 255 / 100).coerceIn(0, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
            // Disable auto-brightness when manually setting
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val msg = "Brightness set to $levelPercent% ($brightnessValue/255)"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "brightness.set", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to set brightness: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "brightness.set", success = false, message = msg)
        }
    }

    private fun setAutoBrightness(): ExecutionResult {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            )
            val msg = "Auto-brightness enabled"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "brightness.set", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to enable auto-brightness: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "brightness.set", success = false, message = msg)
        }
    }
}
