package com.gemmaworkflow.platform.sound

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wrapper around YAMNet TFLite model for sound classification.
 *
 * YAMNet is a pre-trained audio event classifier that outputs 521 scores —
 * one per AudioSet class — from a 0.96-second audio clip (16 kHz, 15600 samples).
 *
 * Model file: `yamnet.tflite` (place in `assets/yamnet.tflite`).
 * Download: https://tfhub.dev/google/yamnet/1
 */
class YamnetClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null

    /** Map from class index → human-readable class name (e.g. 3 → "Speech"). */
    private var classLabels: List<String> = emptyList()

    private val isLoaded: Boolean
        get() = interpreter != null

    /**
     * Load the YAMNet model from assets/yamnet.tflite.
     * Returns true on success.
     */
    fun load(): Boolean {
        if (interpreter != null) return true

        try {
            // Load labels from assets/yamnet_class_map.csv
            val labelsRaw = FileUtil.loadLabels(context, "yamnet_class_map.csv")
            classLabels = labelsRaw
                .map { it.substringAfter("\t").substringBefore(",").trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }

            val modelBuffer = FileUtil.loadMappedFile(context, "yamnet.tflite")
            interpreter = Interpreter(modelBuffer)

            Log.i(TAG, "YAMNet loaded: ${classLabels.size} classes")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YAMNet model", e)
            return false
        }
    }

    /**
     * Classify a raw PCM audio buffer (16-bit signed integers, mono 16 kHz).
     *
     * @param pcmSamples  Raw PCM samples as short array from AudioRecord.
     * @param numSamples Number of valid samples (≤ buffer size). Must be ≥ 15600.
     * @return List of ClassificationResult sorted by descending confidence, or
     *         empty list if model not loaded or samples too short.
     */
    fun classify(pcmSamples: ShortArray, numSamples: Int): List<ClassificationResult> {
        val interp = interpreter ?: return emptyList()

        if (numSamples < MIN_SAMPLES) {
            Log.w(TAG, "Buffer too short for YAMNet: $numSamples samples (need $MIN_SAMPLES)")
            return emptyList()
        }

        return try {
            // Build input buffer: 16-bit PCM → float32 normalisation (±1.0)
            val floatBuffer = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                floatBuffer[i] = pcmSamples[i] / 32768.0f
            }

            // Wrap float array as a direct ByteBuffer (float32, native byte order)
            val inputBuffer = ByteBuffer.allocateDirect(numSamples * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            inputBuffer.put(floatBuffer, 0, numSamples)
            inputBuffer.rewind()

            // YAMNet output: [1, 521]
            val outputBuffer = Array(1) { FloatArray(521) }

            interp.run(inputBuffer, outputBuffer)

            // Map scores to class labels and sort by confidence descending
            outputBuffer[0].mapIndexed { index, score ->
                ClassificationResult(
                    className = classLabels.getOrElse(index) { "unknown" },
                    confidence = score
                )
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            emptyList()
        }
    }

    /**
     * Returns the full list of YAMNet class labels (index → name).
     * Unavailable until [load] succeeds.
     */
    fun getClassLabels(): List<String> = classLabels

    fun close() {
        interpreter?.close()
        interpreter = null
        classLabels = emptyList()
    }

    companion object {
        private const val TAG = "YamnetClassifier"
        /**
         * YAMNet requires 0.96 s of audio at 16 kHz = 15 600 samples.
         * We pad/truncate to exactly this size per classification window.
         */
        const val SAMPLE_RATE = 16_000
        const val MIN_SAMPLES = 15_600
        const val CLIP_DURATION_SEC = 0.96
    }
}

/**
 * A single classification result from YAMNet.
 *
 * @property className  Human-readable AudioSet class name, e.g. "Speech", "Dog bark".
 * @property confidence Confidence score in [0, 1].
 */
data class ClassificationResult(
    val className: String,
    val confidence: Float
)