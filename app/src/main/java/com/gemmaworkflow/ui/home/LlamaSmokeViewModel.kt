package com.gemmaworkflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaworkflow.platform.inference.llama.LlamaCppEngine
import com.gemmaworkflow.platform.inference.llama.ModelFileLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LlamaSmokeViewModel(application: Application) : AndroidViewModel(application) {
    private val modelFileLocator = ModelFileLocator(application)
    private val engine = LlamaCppEngine()

    private val _uiState = MutableStateFlow(
        LlamaSmokeUiState(modelPath = modelFileLocator.defaultModelPath)
    )
    val uiState: StateFlow<LlamaSmokeUiState> = _uiState.asStateFlow()

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun loadModel() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isBusy = true, error = null, loadStatus = "Loading") }
            runCatching {
                modelFileLocator.requireDefaultModel()
                engine.load(
                    modelPath = modelFileLocator.defaultModelPath,
                    contextSize = 512,
                    maxTokens = 16,
                    gpuLayers = GPU_LAYERS
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(isBusy = false, isLoaded = true, loadStatus = "Loaded, CPU emulator mode")
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isLoaded = false,
                        loadStatus = "Not loaded",
                        error = throwable.message ?: "Failed to load model"
                    )
                }
            }
        }
    }

    fun generate() {
        viewModelScope.launch(Dispatchers.Default) {
            val prompt = uiState.value.prompt
            _uiState.update { it.copy(isBusy = true, error = null, response = "") }
            runCatching {
                engine.generate(prompt)
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(isBusy = false, response = response.trim())
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        error = throwable.message ?: "Generation failed"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }

    private companion object {
        const val GPU_LAYERS = 0
    }
}
