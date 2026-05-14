package com.gemmaworkflow.platform.trigger.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract
import java.util.Locale

/**
 * [ActivityResultContract] for launching Android speech recognition.
 *
 * Usage:
 * ```kotlin
 * val launcher = rememberLauncherForActivityResult(VoiceRecognitionContract()) { result ->
 *     val (resultCode, data) = result
 *     val transcript = voiceTrigger.parseResult(resultCode, data).getOrNull()
 * }
 * launcher.launch(Unit)
 * ```
 *
 * Output type is `Pair<Int, Intent?>` matching [Activity.onActivityResult].
 */
class VoiceRecognitionContract : ActivityResultContract<Unit, Pair<Int, Intent?>>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<Int, Intent?> {
        return resultCode to intent
    }
}