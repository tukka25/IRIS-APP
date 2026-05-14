package com.irisapp.platform.trigger.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Thin wrapper around Android's built-in speech recognition Intent.
 *
 * Uses [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] — no third-party deps,
 * works offline on most devices, and requires only `RECORD_AUDIO` permission.
 *
 * Usage:
 * ```
 * val trigger = VoiceIntentTrigger(context)
 * startActivityForResult(trigger.recognize(), REQUEST_CODE)
 * // In Activity.onActivityResult:
 * val transcript = trigger.parseResult(resultCode, data).getOrNull()
 * ```
 */
class VoiceIntentTrigger(private val context: Context) {

    private val tag = "VoiceIntentTrigger"

    /**
     * Returns `true` if speech recognition is available on this device.
     */
    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Build the speech recognition Intent.
     *
     * - `LANGUAGE_MODEL_FREE_FORM` — conversational natural language
     * - `EXTRA_MAX_RESULTS = 1` — we only need the best match
     * - `EXTRA_PARTIAL_RESULTS = false` — wait for final transcription
     */
    fun recognize(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        // Use device locale if known, otherwise default
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    }

    /**
     * Parse the result of a speech recognition activity.
     *
     * @param resultCode  From [Activity.onActivityResult]
     * @param data        From [Activity.onActivityResult]
     * @return `Result.success(transcript)` on success,
     *         `Result.failure` if cancelled, no match, or recognition unavailable.
     */
    fun parseResult(resultCode: Int, data: Intent?): Result<String> {
        if (resultCode != Activity.RESULT_OK) {
            Log.w(tag, "Speech recognition result not OK: code=$resultCode")
            return Result.failure(Exception("Speech recognition cancelled or failed (code=$resultCode)"))
        }

        if (data == null) {
            Log.w(tag, "Speech recognition returned null data")
            return Result.failure(Exception("No recognition data returned"))
        }

        val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?: run {
                Log.w(tag, "No recognition results in data")
                return Result.failure(Exception("No speech detected"))
            }

        val transcript = matches.firstOrNull()?.trim() ?: ""
        return if (transcript.isNotBlank()) {
            Log.i(tag, "Speech recognized: \"$transcript\"")
            Result.success(transcript)
        } else {
            Log.w(tag, "Empty transcript")
            Result.failure(Exception("No speech detected"))
        }
    }

    companion object {
        const val REQUEST_CODE = 10001
    }
}
