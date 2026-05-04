package com.gemmaworkflow.platform.inference.litert

import android.content.Context
import java.io.File

/**
 * Locates LiteRT-LM model files (.litertlm) on the device.
 *
 * Models are expected in the app's external files directory under a "models"
 * subfolder. Push a .litertlm model with:
 *
 *   adb push your-model.litertlm /sdcard/Android/data/com.gemmaworkflow/files/models/
 */
class ModelFileLocator(context: Context) {

    private val modelDir: File = requireNotNull(context.getExternalFilesDir("models")) {
        "External files directory is not available"
    }

    /** Absolute path to the default model file. */
    val defaultModelPath: String =
        File(modelDir, DEFAULT_MODEL_NAME).absolutePath

    /**
     * Returns the default model File, or throws if it doesn't exist.
     */
    fun requireDefaultModel(): File {
        val model = File(defaultModelPath)
        require(model.exists()) {
            "Model not found at $defaultModelPath. " +
                "Push a .litertlm model via: " +
                "adb push <model>.litertlm /sdcard/Android/data/com.gemmaworkflow/files/models/"
        }
        return model
    }

    companion object {
        /** Default LiteRT-LM model name — Gemma 4 E2B IT from HuggingFace litert-community. */
        const val DEFAULT_MODEL_NAME = "gemma-4-E2B-it.litertlm"
    }
}
