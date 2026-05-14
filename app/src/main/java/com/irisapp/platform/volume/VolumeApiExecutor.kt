package com.irisapp.platform.volume

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently sets the volume level for a given audio stream (ring/media/alarm/notification).
 *
 * Requires: MODIFY_AUDIO_SETTINGS — granted automatically on all Android builds.
 * No user interaction needed.
 */
class VolumeApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "VolumeApiExecutor"
    }

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun execute(params: JsonObject): ExecutionResult {
        val streamStr = params["stream"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase() ?: "ring"
        val streamType = when (streamStr) {
            "ring" -> AudioManager.STREAM_RING
            "media" -> AudioManager.STREAM_MUSIC
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> {
                return ExecutionResult(
                    stepId = "volume.set",
                    success = false,
                    message = "Invalid stream '$streamStr'. Use: ring, media, alarm, notification"
                )
            }
        }

        val mute = params["mute"]?.let {
            (it as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        } ?: false

        return if (mute) {
            setMute(streamType)
        } else {
            val levelStr = params["level"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            val level = levelStr?.toIntOrNull()
            if (level == null) {
                ExecutionResult(stepId = "volume.set", success = false, message = "Missing required param: level (0–100)")
            } else {
                setVolume(streamType, level.coerceIn(0, 100))
            }
        }
    }

    private fun setVolume(streamType: Int, levelPercent: Int): ExecutionResult {
        return try {
            val max = audioManager.getStreamMaxVolume(streamType)
            val volumeIndex = (levelPercent * max / 100).coerceIn(0, max)
            audioManager.setStreamVolume(streamType, volumeIndex, 0)
            val msg = "Volume set to $levelPercent% (index $volumeIndex/$max) for stream $streamType"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "volume.set", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to set volume: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "volume.set", success = false, message = msg)
        }
    }

    private fun setMute(streamType: Int): ExecutionResult {
        return try {
            audioManager.setStreamVolume(streamType, 0, 0)
            val msg = "Stream $streamType muted"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "volume.set", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to mute stream: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "volume.set", success = false, message = msg)
        }
    }
}
