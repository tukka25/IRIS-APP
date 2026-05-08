package com.gemmaworkflow.platform.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently copies text to the system clipboard via ClipboardManager.setPrimaryClip(),
 * without launching any share chooser or activity.
 *
 * No permissions required for plain-text clipboard operations on Android 10+.
 */
class ClipboardApiExecutor(private val context: Context) {

    private val clipboardManager: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    companion object {
        private const val TAG = "ClipboardApiExecutor"
    }

    /**
     * Execute the clipboard.copy_text action with the given params.
     * Returns a success ExecutionResult if the clip was set, or a failure result if
     * the "text" param is missing.
     */
    fun execute(params: JsonObject): ExecutionResult {
        val text = params["text"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        return copyText(text, "clipboard.copy_text")
    }

    /**
     * Silently execute share.share_text by copying the text to the clipboard.
     */
    fun executeShareText(params: JsonObject): ExecutionResult {
        val text = params["text"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        return copyText(text, "share.share_text")
    }

    /**
     * Silently execute share.share_image by copying the image URI to the clipboard.
     */
    fun executeShareImage(params: JsonObject): ExecutionResult {
        val uri = params["uri"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        return copyUri(uri, "share.share_image")
    }

    private fun copyText(text: String?, stepId: String): ExecutionResult {
        if (text.isNullOrEmpty()) {
            return ExecutionResult(
                stepId = stepId,
                success = false,
                message = "Missing or empty 'text' or 'uri' param"
            )
        }

        return try {
            val clip = ClipData.newPlainText(/* label = */ null, text)
            clipboardManager.setPrimaryClip(clip)
            Log.i(TAG, "Text/URI copied to clipboard: length=${text.length}")
            ExecutionResult(
                stepId = stepId,
                success = true,
                message = "Copied ${text.length} characters to clipboard"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard", e)
            ExecutionResult(
                stepId = stepId,
                success = false,
                message = e.message ?: "Failed to copy to clipboard"
            )
        }
    }

    private fun copyUri(uriText: String?, stepId: String): ExecutionResult {
        if (uriText.isNullOrBlank()) {
            return ExecutionResult(
                stepId = stepId,
                success = false,
                message = "Missing or empty 'uri' param"
            )
        }

        return try {
            val uri = Uri.parse(uriText)
            val clip = ClipData.newUri(context.contentResolver, null, uri)
            clipboardManager.setPrimaryClip(clip)
            Log.i(TAG, "URI copied to clipboard: $uri")
            ExecutionResult(
                stepId = stepId,
                success = true,
                message = "Copied image URI to clipboard"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to clipboard", e)
            ExecutionResult(
                stepId = stepId,
                success = false,
                message = e.message ?: "Failed to copy URI to clipboard"
            )
        }
    }
}
