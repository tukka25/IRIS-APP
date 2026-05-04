package com.gemmaworkflow.platform.inference.llama

class LlamaCppEngine : AutoCloseable {
    private var handle: Long = 0L

    fun load(
        modelPath: String,
        contextSize: Int,
        maxTokens: Int,
        gpuLayers: Int
    ) {
        close()
        handle = nativeInit(modelPath, contextSize, maxTokens, gpuLayers)
        check(handle != 0L) { "Native model initialization returned an empty handle" }
    }

    fun generate(prompt: String): String {
        check(handle != 0L) { "Load the model before generating" }
        return nativeGenerate(handle, prompt)
    }

    override fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    private external fun nativeInit(
        modelPath: String,
        contextSize: Int,
        maxTokens: Int,
        gpuLayers: Int
    ): Long

    private external fun nativeGenerate(handle: Long, prompt: String): String

    private external fun nativeFree(handle: Long)

    companion object {
        init {
            System.loadLibrary("gemma-llama")
        }
    }
}

