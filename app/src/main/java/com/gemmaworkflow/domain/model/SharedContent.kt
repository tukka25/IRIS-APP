package com.gemmaworkflow.domain.model

import android.net.Uri

/**
 * Represents content received via Android's share sheet (ACTION_SEND intent).
 */
sealed class SharedContent {
    abstract val sourceLabel: String?
    abstract val type: String

    val isText: Boolean get() = this is Text
    val isImage: Boolean get() = this is Image

    abstract val text: String?
    abstract val uri: Uri?

    /** Plain text shared from any app. */
    data class Text(
        override val text: String,
        override val sourceLabel: String? = null,
        override val type: String = "text/plain"
    ) : SharedContent() {
        override val uri: Uri? = null
    }

    /** A shared image, identified by its content URI. */
    data class Image(
        override val uri: Uri,
        override val type: String = "image/*",
        override val sourceLabel: String? = null
    ) : SharedContent() {
        override val text: String? = null
    }

    fun displaySummary(): String {
        return when (this) {
            is Text -> text.take(80).let { if (text.length > 80) "$it…" else it }
            is Image -> "Shared image: ${uri.lastPathSegment ?: "image"}"
        }
    }
}
