package com.gemmaworkflow.domain.runner

import android.content.Context
import android.content.Intent
import com.gemmaworkflow.domain.model.ExecutionResult

/**
 * Dispatches Android share intents. Handles text and image sharing.
 */
class IntentDispatcher(private val context: Context) {

    fun shareText(text: String): ExecutionResult {
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share via"))
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = "share.share_text",
                    success = true,
                    message = "Share sheet opened"
                )
            },
            onFailure = { e ->
                ExecutionResult(
                    stepId = "share.share_text",
                    success = false,
                    message = e.message ?: "Failed to share text"
                )
            }
        )
    }

    fun shareImage(uri: String): ExecutionResult {
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share image via"))
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = "share.share_image",
                    success = true,
                    message = "Share sheet opened"
                )
            },
            onFailure = { e ->
                ExecutionResult(
                    stepId = "share.share_image",
                    success = false,
                    message = e.message ?: "Failed to share image"
                )
            }
        )
    }
}
