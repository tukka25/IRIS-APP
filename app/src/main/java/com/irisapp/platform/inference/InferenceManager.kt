package com.irisapp.platform.inference

import android.content.Context
import android.util.Log
import com.irisapp.BuildConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.irisapp.platform.inference.litert.ModelFileLocator
import com.irisapp.platform.tools.ToolInitializer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** The name of the currently loaded model, or null if none is loaded. */
    val currentModelName: String? get() = _currentModelName

    private var initialized = false
    private var _currentModelName: String? = null

    /** Serializes initialize / close / downloadAndInit to prevent concurrent coroutine races. */
    private val mutex = Mutex()

    /**
     * Load the specified LiteRT-LM model, preferring GPU.
     * If [modelName] is null, uses the default model from ModelFileLocator.
     */
    suspend fun initialize(context: Context, modelName: String? = null) = mutex.withLock {
        val targetModel = modelName ?: ModelFileLocator.DEFAULT_MODEL_NAME
        if (initialized && _currentModelName == targetModel) return@withLock

        // If already initialized with a different model, close it first
        if (initialized) {
            close()
        }

        initialized = true
        _currentModelName = targetModel
        _state.value = InferenceState.Loading

        runCatching {
            withContext(Dispatchers.Default) {
                val locator = ModelFileLocator(context)
                val modelFile = File(locator.getModelPath(targetModel))

                if (!modelFile.exists()) {
                    throw IllegalStateException("Model not found at ${modelFile.absolutePath}")
                }

                Log.i(TAG, "Loading model: ${modelFile.absolutePath}")
                logMemory("Before load")

                // Try GPU first; fall back to CPU if unavailable.
                val gpuResult = runCatching {
                    val gpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.GPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                    Engine(gpuConfig).also { it.initialize() }
                }

                val loadedBackend: InferenceBackend

                engine = if (gpuResult.isSuccess) {
                    Log.i(TAG, "Model loaded successfully on GPU")
                    loadedBackend = InferenceBackend.GPU
                    gpuResult.getOrThrow().also { logMemory("After GPU load") }
                } else {
                    val gpuError = gpuResult.exceptionOrNull()
                    if (BuildConfig.FORCE_GPU_INFERENCE) {
                        throw IllegalStateException(
                            "GPU inference is required for this build, but LiteRT-LM GPU init failed: ${gpuError?.message}",
                            gpuError
                        )
                    }

                    Log.w(TAG, "GPU unavailable (${gpuError?.message}), falling back to CPU")
                    val cpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                    loadedBackend = InferenceBackend.CPU
                    Engine(cpuConfig).also { it.initialize() }.also {
                        Log.i(TAG, "Model loaded successfully on CPU")
                        logMemory("After CPU load")
                    }
                }

                _state.value = InferenceState.Ready(loadedBackend)
                ToolInitializer.initialize(context)
            }
        }.onFailure { throwable ->
            initialized = false
            _currentModelName = null
            Log.e(TAG, "Failed to load model", throwable)
            val message = throwable.message ?: "Unknown error"

            _state.value = when {
                message.contains("not found", ignoreCase = true) ||
                message.contains("No such file", ignoreCase = true) ->
                    InferenceState.MissingModel

                message.contains("GPU", ignoreCase = true) ||
                message.contains("OpenCL", ignoreCase = true) ||
                message.contains("Vulkan", ignoreCase = true) ||
                message.contains("WebGPU", ignoreCase = true) ||
                message.contains("No adapters found", ignoreCase = true) ->
                    InferenceState.GpuUnavailable(message)

                else -> InferenceState.Error(message)
            }
        }
    }

    /**
     * Downloads the specified model and initializes it once complete.
     */
    suspend fun downloadAndInit(context: Context, modelName: String, url: String) = mutex.withLock {
        val current = _state.value
        if (current is InferenceState.Downloading || current is InferenceState.Loading) {
            Log.w(TAG, "Model operation already in progress (${current::class.simpleName}). Ignoring request for $modelName")
            return@withLock
        }

        val locator = ModelFileLocator(context)
        val targetFile = File(locator.getModelPath(modelName))

        // Initial zero-progress state
        _state.value = InferenceState.Downloading(
            progress = 0f,
            downloadedBytes = 0,
            totalBytes = 0,
            speedBytesPerSecond = 0,
            etaSeconds = 0,
            modelId = modelName
        )

        val success = com.irisapp.platform.inference.litert.ModelDownloader.download(
            url = url,
            targetFile = targetFile,
            onProgress = { progress, downloaded, total, speed, eta ->
                _state.value = InferenceState.Downloading(
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speedBytesPerSecond = speed,
                    etaSeconds = eta,
                    modelId = modelName
                )
            }
        )

        if (success) {
            initialize(context, modelName)
        } else {
            _state.value = InferenceState.Error("Download failed for $modelName")
        }
    }

    suspend fun close() = mutex.withLock {
        engine?.close()
        engine = null
        _state.value = InferenceState.Idle
        initialized = false
        _currentModelName = null
    }

    /** Log current memory usage. Helps diagnose OOM kills. */
    private fun logMemory(label: String) {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val max = runtime.maxMemory() / (1024 * 1024)
        Log.i(TAG, "$label: ${used}MB used, ${max}MB max heap")
    }
}

enum class InferenceBackend {
    GPU,
    CPU
}

/** Observable inference states for the UI. */
sealed class InferenceState {
    data object Idle : InferenceState()
    data object Loading : InferenceState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long,
        val etaSeconds: Long,
        val modelId: String? = null
    ) : InferenceState()
    data class Ready(val backend: InferenceBackend) : InferenceState()
    data object MissingModel : InferenceState()
    data class GpuUnavailable(val reason: String) : InferenceState()
    data class Error(val message: String) : InferenceState()

    val isReady: Boolean get() = this is Ready
    val isBusy: Boolean get() = this is Loading || this is Downloading
}
