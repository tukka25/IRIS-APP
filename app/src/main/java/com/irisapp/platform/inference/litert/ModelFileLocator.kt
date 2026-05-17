package com.irisapp.platform.inference.litert

import android.content.Context
import java.io.File

/**
 * Locates LiteRT-LM model files (.litertlm) on the device.
 *
 * Models are expected in the app's external files directory under a "models"
 * subfolder. Push a .litertlm model with:
 *
 *   adb push your-model.litertlm /sdcard/Android/data/com.irisapp/files/models/
 */
class ModelFileLocator(private val context: Context) {

    private val modelDir: File = requireNotNull(context.getExternalFilesDir("models")) {
        "External files directory is not available"
    }

    /**
     * Returns the absolute path to a model file by name.
     */
    fun getModelPath(modelName: String): String =
        File(modelDir, modelName).absolutePath

    /**
     * Returns the default model File, or throws if it doesn't exist.
     */
    fun requireDefaultModel(): File {
        val model = File(getModelPath(DEFAULT_MODEL_NAME))
        require(model.exists()) {
            "Model not found at ${model.absolutePath}. " +
                "Download it via the Model Manager in the app."
        }
        return model
    }

    /**
     * Returns true if the given model file exists on disk.
     */
    fun modelExists(modelName: String): Boolean =
        File(modelDir, modelName).exists()

    companion object {
        /** Default LiteRT-LM model name — Gemma 4 E2B IT. */
        const val DEFAULT_MODEL_NAME = "gemma-4-E2B-it.litertlm"

        val AVAILABLE_MODELS = listOf(
            ModelMetadata(
                id = "gemma-4-e2b",
                fileName = "gemma-4-E2B-it.litertlm",
                label = "Gemma 4 E2B IT",
                description = "Fastest. Optimized for speed and low RAM.",
                sizeLabel = "2.6 GB",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
            ),
            ModelMetadata(
                id = "gemma-4-e4b",
                fileName = "gemma-4-E4B-it.litertlm",
                label = "Gemma 4 E4B IT",
                description = "Smartest. Best reasoning and accuracy.",
                sizeLabel = "3.7 GB",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
            )
        )
    }
}

data class ModelMetadata(
    val id: String,
    val fileName: String,
    val label: String,
    val description: String,
    val sizeLabel: String,
    val downloadUrl: String
)
