package com.irisapp.platform.inference.litert

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM inference engine wrapping Google's on-device LLM framework.
 *
 * Supports GPU acceleration via OpenCL/Vulkan backends through LiteRT-LM's
 * first-class Kotlin API. No JNI bridge needed.
 */
class LitertLmEngine {

    private var engine: Engine? = null

    companion object {
        private const val TAG = "LitertLmEngine"
    }

    /**
     * Initialize the LiteRT-LM engine with GPU backend.
     *
     * Call on a background thread — initialization can take up to 10 seconds.
     */
    suspend fun initialize(
        modelPath: String,
        cacheDir: String? = null,
        backend: Backend = Backend.GPU()
    ) = withContext(Dispatchers.Default) {
        close()

        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir
        )

        Log.i(TAG, "Initializing LiteRT-LM engine: $modelPath, backend=$backend")

        engine = Engine(config).also { it.initialize() }

        Log.i(TAG, "LiteRT-LM engine initialized")
    }

    /**
     * Generate a full response for the given prompt synchronously.
     */
    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        val currentEngine = engine ?: throw IllegalStateException(
            "Engine not initialized. Call initialize() first."
        )

        Log.d(TAG, "Generating for prompt (${prompt.length} chars)")

        val conversation = currentEngine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = 0.7
                )
            )
        )

        conversation.use { conv ->
            val response = conv.sendMessage(prompt)
            val text = response.toString()
            Log.d(TAG, "Generated ${text.length} chars")
            text
        }
    }

    fun close() {
        val currentEngine = engine
        if (currentEngine != null) {
            Log.d(TAG, "Closing LiteRT-LM engine")
            currentEngine.close()
            engine = null
        }
    }
}
