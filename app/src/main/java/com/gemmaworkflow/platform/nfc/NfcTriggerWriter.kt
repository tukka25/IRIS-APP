package com.gemmaworkflow.platform.nfc

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.data.repository.WorkflowRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Writes a workflow ID to an NFC tag via NDEF record.
 *
 * The tag stores a deep-link URI: gemmaworkflow://workflow/<workflowId>
 * On scan, [NfcTriggerHandler] reads this URI and routes to the workflow detail
 * screen for confirmation and execution.
 *
 * Usage:
 *   val writer = NfcTriggerWriter(context, workflowRepo)
 *   writer.writeTag(tag, workflowId)  // called from activity's onNewIntent
 *
 * NDEF format:
 *   RTD_URI record containing "gemmaworkflow://workflow/<workflowId>"
 *   OR RTD_TEXT record with the workflow ID as plain text fallback
 */
class NfcTriggerWriter(
    private val context: android.content.Context,
    private val workflowRepo: WorkflowRepository
) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "NfcTriggerWriter"
        const val SCHEME = "gemmaworkflow"
        const val AUTHORITY = "workflow"
        const val ACTION_NFC_SCANNED = "com.gemmaworkflow.platform.nfc.ACTION_NFC_SCANNED"
        const val EXTRA_WORKFLOW_ID = "workflow_id"

        // MIME type for gemmaworkflow records
        const val MIME_TYPE = "application/com.gemmaworkflow.workflow"

        fun buildDeepLinkUri(workflowId: String): String =
            Uri.Builder()
                .scheme(SCHEME)
                .authority(AUTHORITY)
                .appendPath(workflowId)
                .build()
                .toString()

        fun parseWorkflowIdFromUri(uri: String): String? {
            return try {
                val parsed = uri.toUri()
                if (parsed.scheme == SCHEME && parsed.host == AUTHORITY) {
                    parsed.pathSegments.firstOrNull()
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Returns true when NFC is available and enabled on this device.
     */
    fun isNfcAvailable(): Boolean = nfcAdapter != null

    /**
     * Returns true when NFC is enabled (can be turned off by user in settings).
     */
    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    /**
     * Returns a guidance message when NFC is not available or disabled.
     */
    fun nfcStatusMessage(): String = when {
        nfcAdapter == null -> "This device does not have NFC hardware."
        !nfcAdapter.isEnabled -> "NFC is turned off. Enable it in Settings to use tag triggers."
        else -> "NFC is ready."
    }

    /**
     * Write a workflow ID to an NFC tag.
     *
     * Creates two NDEF records for maximum compatibility:
     * 1. RTD_URI record with gemmaworkflow://workflow/<workflowId>
     * 2. RTD_TEXT record as fallback for readers that don't understand the scheme
     *
     * Returns ExecutionResult describing success or failure.
     */
    fun writeTag(tag: Tag, workflowId: String): ExecutionResult {
        if (nfcAdapter == null) {
            return ExecutionResult(
                stepId = "nfc.write_tag",
                success = false,
                message = "NFC not available on this device."
            )
        }

        if (!workflowRepo.exists(workflowId)) {
            return ExecutionResult(
                stepId = "nfc.write_tag",
                success = false,
                message = "Workflow '$workflowId' not found. Save it first before writing to a tag."
            )
        }

        val deepLinkUri = buildDeepLinkUri(workflowId)

        return try {
            val ndef = Ndef.get(tag)
            if (ndef == null) {
                return ExecutionResult(
                    stepId = "nfc.write_tag",
                    success = false,
                    message = "This tag is not NDEF-formatted. Use an NFC Forum Type 2 or Type 4 tag."
                )
            }

            if (!ndef.isWritable) {
                return ExecutionResult(
                    stepId = "nfc.write_tag",
                    success = false,
                    message = "This tag is read-only and cannot be written."
                )
            }

            val uriRecord = createUriRecord(deepLinkUri)
            val textRecord = createTextRecord(workflowId)
            val message = NdefMessage(arrayOf(uriRecord, textRecord))

            ndef.connect()
            try {
                if (ndef.maxSize < message.byteArrayLength) {
                    return ExecutionResult(
                        stepId = "nfc.write_tag",
                        success = false,
                        message = "Tag capacity too small (${ndef.maxSize} bytes, need ${message.byteArrayLength} bytes)."
                    )
                }
                ndef.writeNdefMessage(message)
                Log.i(TAG, "Wrote workflow '$workflowId' to NFC tag as $deepLinkUri")
                ExecutionResult(
                    stepId = "nfc.write_tag",
                    success = true,
                    message = "Tag written successfully. Scan to run workflow '$workflowId'."
                )
            } finally {
                ndef.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write NFC tag", e)
            ExecutionResult(
                stepId = "nfc.write_tag",
                success = false,
                message = "Write failed: ${e.message}"
            )
        }
    }

    /**
     * Create an RTD_URI NdefRecord.
     * The prefix byte encodes the URI scheme — we use 0x00 (no prefix) since
     * gemmaworkflow:// is not a registered scheme with a prefix shortcut.
     */
    private fun createUriRecord(uri: String): NdefRecord {
        return NdefRecord.createUri(uri)
    }

    /**
     * Create an RTD_TEXT NdefRecord as a fallback for readers that don't
     * understand the gemmaworkflow:// scheme.
     */
    private fun createTextRecord(text: String): NdefRecord {
        return NdefRecord.createTextRecord("en", text)
    }
}
