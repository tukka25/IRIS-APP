package com.irisapp.platform.media

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import android.view.KeyEvent
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject

/**
 * Silently controls media playback — play/pause, next track, previous track.
 *
 * API: MediaSessionManager.getActiveSessions() → MediaController.dispatchMediaButtonEvent()
 * Fallback: sendOrderedBroadcast(Intent.ACTION_MEDIA_BUTTON) works without any permission.
 * No permissions required.
 */
class MediaControlApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "MediaControlApiExecutor"
    }

    fun executePlayPause(params: JsonObject): ExecutionResult {
        return dispatchMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun executeNext(params: JsonObject): ExecutionResult {
        return dispatchMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun executePrevious(params: JsonObject): ExecutionResult {
        return dispatchMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun dispatchMediaButton(keyCode: Int): ExecutionResult {
        // Try MediaSessionManager first (API 21+)
        val mediaController = getActiveMediaController()
        if (mediaController != null) {
            return try {
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                mediaController.dispatchMediaButtonEvent(downEvent)
                mediaController.dispatchMediaButtonEvent(upEvent)
                val action = keyCodeToName(keyCode)
                Log.i(TAG, "$action dispatched via MediaController")
                ExecutionResult(stepId = keyCodeToActionId(keyCode), success = true, message = "$action sent")
            } catch (e: Exception) {
                val msg = "MediaController dispatch failed: ${e.message}"
                Log.e(TAG, msg, e)
                // Fall through to broadcast fallback
                fallbackBroadcast(keyCode)
            }
        } else {
            // No active session — use broadcast fallback
            return fallbackBroadcast(keyCode)
        }
        return ExecutionResult(stepId = keyCodeToActionId(keyCode), success = false, message = "No active media session found")
    }

    private fun getActiveMediaController(): MediaController? {
        return try {
            val listener = android.content.ComponentName(context, android.media.session.MediaSessionManager::class.java)
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            mediaSessionManager?.getActiveSessions(listener)?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "getActiveMediaController failed: ${e.message}")
            null
        }
    }

    private fun fallbackBroadcast(keyCode: Int): ExecutionResult {
        return try {
            val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            context.sendOrderedBroadcast(down, null)
            context.sendOrderedBroadcast(up, null)
            val action = keyCodeToName(keyCode)
            Log.i(TAG, "$action sent via broadcast fallback")
            ExecutionResult(stepId = keyCodeToActionId(keyCode), success = true, message = "$action sent (broadcast)")
        } catch (e: Exception) {
            val msg = "Broadcast fallback failed: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = keyCodeToActionId(keyCode), success = false, message = msg)
        }
    }

    private fun keyCodeToActionId(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "media.play_pause"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "media.next_track"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "media.previous_track"
        else -> "media.unknown"
    }

    private fun keyCodeToName(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play/Pause"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "Next track"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Previous track"
        else -> "Media button"
    }
}