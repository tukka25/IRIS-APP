package com.gemmaworkflow.ui.home

/**
 * UI state for the LiteRT-LM GPU smoke-test screen.
 *
 * Holds model path, prompt, generated response, and load/error state.
 */
data class LlamaSmokeUiState(
    val modelPath: String = "",
    val prompt: String = "Write one sentence about why local AI on Android is useful.",
    val response: String = "",
    val loadStatus: String = "Not loaded",
    val error: String? = null,
    val isBusy: Boolean = false,
    val isLoaded: Boolean = false
) {
    val canGenerate: Boolean
        get() = isLoaded && !isBusy && prompt.isNotBlank()
}
