package com.gemmaworkflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.gemmaworkflow.platform.inference.litert.LitertLmEngine
import com.gemmaworkflow.platform.inference.litert.ModelFileLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the LiteRT-LM GPU smoke-test screen.
 *
 * Replaces the previous llama.cpp smoke test with LiteRT-LM's
 * Kotlin API using GPU acceleration (OpenCL/Vulkan).
 */
class LlamaSmokeViewModel(application: Application) : AndroidViewModel(application) {

    private val modelFileLocator = ModelFileLocator(application)
    private val engine = LitertLmEngine()

    private val _uiState = MutableStateFlow(
        LlamaSmokeUiState(modelPath = modelFileLocator.defaultModelPath)
    )
    val uiState: StateFlow<LlamaSmokeUiState> = _uiState.asStateFlow()

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun loadModel() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isBusy = true, error = null, loadStatus = "Loading\u2026") }
            runCatching {
                modelFileLocator.requireDefaultModel()
                engine.initialize(
                    modelPath = modelFileLocator.defaultModelPath,
                    cacheDir = getApplication<Application>().cacheDir.absolutePath,
                    backend = Backend.GPU()
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isLoaded = true,
                        loadStatus = "Loaded \u2014 GPU (LiteRT-LM)"
                    )
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
}
