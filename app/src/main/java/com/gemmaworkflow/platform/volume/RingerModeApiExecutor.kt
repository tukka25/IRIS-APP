package com.gemmaworkflow.platform.volume

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently sets the global ringer mode (normal / silent / vibrate) or
 * Do Not Disturb mode (dnd_all / dnd_priority / dnd_none) via AudioManager
 * and NotificationManager (API 26+ for DND).
 *
 * Requires: MODIFY_AUDIO_SETTINGS for basic ringer modes (granted automatically).
 * Requires: ACCESS_NOTIFICATION_POLICY for DND modes (granted automatically on most builds).
 * Falls back to AudioManager.setRingerMode for basic modes when ACCESS_NOTIFICATION_POLICY is not held.
 */
class RingerModeApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "RingerModeApiExecutor"
    }

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun execute(params: JsonObject): ExecutionResult {
        val modeStr = params["mode"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()

        return when (modeStr) {
            "normal" -> setRingerMode(AudioManager.RINGER_MODE_NORMAL)
            "silent" -> setRingerMode(AudioManager.RINGER_MODE_SILENT)
            "vibrate" -> setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
            "dnd_all" -> setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            "dnd_priority" -> setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            "dnd_none" -> setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            else -> ExecutionResult(
                stepId = "ringer_mode.set",
                success = false,
                message = "Invalid mode '$modeStr'. Use: normal, silent, vibrate, dnd_all, dnd_priority, dnd_none"
            )
        }
    }

    /**
     * Set basic ringer mode via AudioManager.
     * Falls back gracefully when ACCESS_NOTIFICATION_POLICY is not held.
     */
    private fun setRingerMode(mode: Int): ExecutionResult {
        return try {
            audioManager.setRingerMode(mode)
            val modeName = when (mode) {
                AudioManager.RINGER_MODE_NORMAL -> "normal"
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "unknown"
            }
            val msg = "Ringer mode set to $modeName"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "ringer_mode.set", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to set ringer mode: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "ringer_mode.set", success = false, message = msg)
        }
    }

    /**
     * Set DND interruption filter via NotificationManager (API 26+).
     * Requires ACCESS_NOTIFICATION_POLICY permission.
     */
    private fun setInterruptionFilter(filter: Int): ExecutionResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationManager.setInterruptionFilter(filter)
            }
            val filterName = when (filter) {
                NotificationManager.INTERRUPTION_FILTER_NONE -> "dnd_all (none)"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "dnd_priority"
                NotificationManager.INTERRUPTION_FILTER_ALL -> "dnd_none (all)"
                else -> "filter=$filter"
            }
            val msg = "DND mode set to $filterName"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "ringer_mode.set", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to set DND mode: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "ringer_mode.set", success = false, message = msg)
        }
    }
}
