package com.gemmaworkflow.platform.share

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gemmaworkflow.domain.model.SharedContent

/**
 * BroadcastReceiver that intercepts ACTION_SEND intents when the app is opened
 * via the Android share sheet.
 *
 * Extracts the shared content and delegates to [ShareSheetTriggerHandler] to
 * present the workflow selector and run the selected workflow.
 *
 * Registered in AndroidManifest.xml for:
 *   - android.intent.action.SEND  (text/plain, image/any)
 */
class ShareReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShareReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) {
            Log.w(TAG, "Received unexpected action: ${intent.action}")
            return
        }

        val sharedContent = extractSharedContent(intent)
        if (sharedContent == null) {
            Log.w(TAG, "Could not extract shared content from intent")
            return
        }

        Log.i(TAG, "Received share: type=${sharedContent.type}, isText=${sharedContent.isText}, isImage=${sharedContent.isImage}")

        ShareSheetTriggerHandler.handleIncomingShare(context, sharedContent)
    }

    private fun extractSharedContent(intent: Intent): SharedContent? {
        val type = intent.type ?: return null

        return when {
            type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text.isNullOrBlank()) null
                else {
                    val sourceLabel = resolveSenderLabel(intent)
                    SharedContent.Text(text = text, sourceLabel = sourceLabel, type = type)
                }
            }
            type.startsWith("image/") -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                if (uri == null) null
                else {
                    val sourceLabel = resolveSenderLabel(intent)
                    SharedContent.Image(uri = uri, sourceLabel = sourceLabel, type = type)
                }
            }
            else -> null
        }
    }

    private fun resolveSenderLabel(intent: Intent): String? {
        // Try to get the sender's package name from the chooser target
        @Suppress("DEPRECATION")
        val chooserTarget = intent.getParcelableExtra<android.content.ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
        if (chooserTarget != null) {
            return chooserTarget.packageName
        }
        return null
    }
}
