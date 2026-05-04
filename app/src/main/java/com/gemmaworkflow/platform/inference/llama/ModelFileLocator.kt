package com.gemmaworkflow.platform.inference.llama

import android.content.Context
import java.io.File

class ModelFileLocator(context: Context) {
    private val modelDir: File = requireNotNull(context.getExternalFilesDir("models")) {
        "External files directory is not available"
    }

    val defaultModelPath: String =
        File(modelDir, DEFAULT_MODEL_NAME).absolutePath

    fun requireDefaultModel(): File {
        val model = File(defaultModelPath)
        require(model.exists()) {
            "Model not found at $defaultModelPath. Run scripts/push_model_to_emulator.sh first."
        }
        return model
    }

    companion object {
        const val DEFAULT_MODEL_NAME = "gemma-planner-dev.Q4_K_M.gguf"
    }
}

