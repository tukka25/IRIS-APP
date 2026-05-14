package com.gemmaworkflow.platform.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Routes NFC tag scans and deep-link URIs to the appropriate destination in the app.
 *
 * Two routing paths:
 * 1. **Foreground routing** — when the app is in the foreground, [NfcAdapter] delivers
 *    scans directly to the Activity via onNewIntent(). The Activity calls [routeFromActivity]
 *    which emits a [DeepLink] event on [deepLinkEvents] for observers to handle.
 * 2. **Background routing** — when the app is backgrounded, [NfcTriggerHandler] posts a
 *    notification. Tapping it launches [MainActivity] with the workflow ID in the intent,
 *    which calls [routeFromActivity] the same way.
 *
 * [routeNfcScan] is called directly from [NfcTriggerHandler] when it detects the app
 * is in the foreground.
 *
 * The [DeepLink] sealed class encodes the destination and any payload so callers
 * (ViewModel, Activity) can navigate without duplicating the routing logic.
 */
class DeepLinkRouter {

    companion object {
        private const val TAG = "DeepLinkRouter"

        const val ACTION_RUN_WORKFLOW = "com.gemmaworkflow.platform.nfc.ACTION_RUN_WORKFLOW"
        const val ACTION_SHOW_WORKFLOW_DETAIL = "com.gemmaworkflow.platform.nfc.ACTION_SHOW_WORKFLOW_DETAIL"
        const val EXTRA_WORKFLOW_ID = "workflow_id"

        private val _deepLinkEvents = MutableSharedFlow<DeepLink>(extraBufferCapacity = 1)
        val deepLinkEvents: SharedFlow<DeepLink> = _deepLinkEvents.asSharedFlow()

        /**
         * Emit a [DeepLink] event. Callers that are not inside the DeepLinkRouter
         * companion object (e.g. [TimeTriggerConfirmationActivity]) use this instead
         * of trying to emit directly on [deepLinkEvents].
         */
        fun emitDeepLink(deepLink: DeepLink) {
            _deepLinkEvents.tryEmit(deepLink)
        }

        /**
         * Parse the workflow ID from a gemmaworkflow:// URI.
         * URI format: gemmaworkflow://workflow/<workflowId>
         */
        fun parseWorkflowId(uri: String): String? {
            return NfcTriggerWriter.parseWorkflowIdFromUri(uri)
        }

        /**
         * Called by NfcTriggerHandler when the app is in the foreground —
         * routes directly via the SharedFlow instead of posting a notification.
         */
        fun routeNfcScan(context: android.content.Context, workflowId: String) {
            Log.i(TAG, "Routing NFC scan for workflow: $workflowId")
            _deepLinkEvents.tryEmit(DeepLink.NfcScan(workflowId))
        }

        /**
         * Called from [MainActivity.onNewIntent] when an NFC intent arrives
         * while the activity is already running.
         */
        fun routeFromActivity(intent: Intent) {
            when (intent.action) {
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED,
                Intent.ACTION_VIEW -> {
                    handleNfcOrViewIntent(intent)
                }
                ACTION_RUN_WORKFLOW -> {
                    val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
                    if (workflowId != null) {
                        Log.i(TAG, "Direct run intent for workflow: $workflowId")
                        _deepLinkEvents.tryEmit(DeepLink.RunWorkflow(workflowId))
                    }
                }
                ACTION_SHOW_WORKFLOW_DETAIL -> {
                    val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
                    if (workflowId != null) {
                        Log.i(TAG, "Show detail intent for workflow: $workflowId")
                        _deepLinkEvents.tryEmit(DeepLink.ShowDetail(workflowId))
                    }
                }
                NfcTriggerHandler.ACTION_NFC_SCANNED -> {
                    val workflowId = intent.getStringExtra(NfcTriggerHandler.EXTRA_WORKFLOW_ID)
                    if (workflowId != null) {
                        Log.i(TAG, "NFC scanned intent for workflow: $workflowId")
                        _deepLinkEvents.tryEmit(DeepLink.NfcScan(workflowId))
                    }
                }
            }
        }

        private fun handleNfcOrViewIntent(intent: Intent) {
            // The URI is in intent.data (used for both NFC NDEF URI records and ACTION_VIEW)
            val uri = intent.data?.toString() ?: return

            val workflowId = parseWorkflowId(uri)

            if (workflowId != null) {
                Log.i(TAG, "Parsed NFC/view URI: $uri -> workflowId: $workflowId")
                _deepLinkEvents.tryEmit(DeepLink.NfcScan(workflowId))
            } else {
                Log.w(TAG, "Unknown NFC/view URI format: $uri")
            }
        }

        /**
         * Returns true if the given intent should be routed by this router.
         */
        fun canHandle(intent: Intent?): Boolean = when (intent?.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            Intent.ACTION_VIEW,
            NfcTriggerHandler.ACTION_NFC_SCANNED,
            ACTION_RUN_WORKFLOW,
            ACTION_SHOW_WORKFLOW_DETAIL -> true
            else -> false
        }
    }
}

/**
 * Sealed class representing deep-link destinations within the app.
 * Emitted on [DeepLinkRouter.deepLinkEvents] for observers to handle.
 */
sealed class DeepLink {
    abstract val workflowId: String

    /** NFC tag was scanned — show confirmation then run */
    data class NfcScan(override val workflowId: String) : DeepLink()

    /** User confirmed — run directly without further prompting */
    data class RunWorkflow(override val workflowId: String) : DeepLink()

    /** Show workflow detail screen */
    data class ShowDetail(override val workflowId: String) : DeepLink()
}
