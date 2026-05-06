package com.gemmaworkflow.platform.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.gemmaworkflow.platform.inference.litert.ModelFileLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Singleton-style manager that loads the LiteRT-LM model once on app start
 * and keeps it alive for all planner stages.
 *
 * Exposes [inferenceState] so the UI can observe LoadInProgress, Ready,
 * MissingModel, GpuUnavailable, or Error states.
 */
object InferenceManager {

    private const val TAG = "InferenceManager"

    var engine: Engine? = null
        private set

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val inferenceState: StateFlow<InferenceState> = _state.asStateFlow()

    private var initialized = false

    /**
     * Load the default LiteRT-LM model with GPU backend.
     * Safe to call multiple times — subsequent calls are no-ops if already loaded.
     */
    suspend fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        _state.value = InferenceState.Loading

        runCatching {
            withContext(Dispatchers.Default) {
                val locator = ModelFileLocator(context)
                val modelFile = locator.requireDefaultModel()

                Log.i(TAG, "Loading model: ${modelFile.absolutePath}")

                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.absolutePath
                )

                engine = Engine(config).also { it.initialize() }

                Log.i(TAG, "Model loaded successfully on GPU")
                _state.value = InferenceState.Ready
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to load model", throwable)
            val message = throwable.message ?: "Unknown error"

            _state.value = when {
                message.contains("not found", ignoreCase = true) ||
                message.contains("No such file", ignoreCase = true) ->
                    InferenceState.MissingModel

                message.contains("GPU", ignoreCase = true) ||
                message.contains("OpenCL", ignoreCase = true) ||
                message.contains("Vulkan", ignoreCase = true) ->
                    InferenceState.GpuUnavailable(message)

                else -> InferenceState.Error(message)
            }
        }
    }

    fun close() {
        engine?.close()
        engine = null
        _state.value = InferenceState.Idle
        initialized = false
    }
}

/** Observable inference states for the UI. */
sealed class InferenceState {
    data object Idle : InferenceState()
    data object Loading : InferenceState()
    data object Ready : InferenceState()
    data object MissingModel : InferenceState()
    data class GpuUnavailable(val reason: String) : InferenceState()
    data class Error(val message: String) : InferenceState()

    val isReady: Boolean get() = this is Ready
    val isBusy: Boolean get() = this is Loading
}
