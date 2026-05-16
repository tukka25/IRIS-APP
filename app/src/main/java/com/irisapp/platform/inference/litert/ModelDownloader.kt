package com.irisapp.platform.inference.litert

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles downloading large model files from a URL to a local file.
 * Reports progress via a callback.
 */
object ModelDownloader {
    private const val TAG = "ModelDownloader"
    private const val BUFFER_SIZE = 8 * 1024 // 8KB

    /**
     * Downloads a file from [url] to [targetFile].
     * [onProgress] is called with values from 0.0 to 1.0.
     * Returns true if successful, false otherwise.
     */
    suspend fun download(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            // Ensure parent directory exists
            targetFile.parentFile?.mkdirs()

            // Temporary file to avoid partial downloads being detected as complete
            val tempFile = File(targetFile.absolutePath + ".tmp")
            if (tempFile.exists()) tempFile.delete()

            Log.i(TAG, "Starting download from $url to ${targetFile.absolutePath}")
            val downloadUrl = URL(url)
            connection = downloadUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}: ${connection.responseMessage}")
                return@withContext false
            }

            val fileLength = connection.contentLengthLong
            Log.i(TAG, "File length: $fileLength bytes")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val data = ByteArray(BUFFER_SIZE)
                    var total: Long = 0
                    var count: Int
                    var lastUpdate = 0L

                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)

                        // Update progress at most every 200ms to avoid flooding UI
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 200) {
                            val progress = if (fileLength > 0) total.toFloat() / fileLength else 0f
                            onProgress(progress)
                            lastUpdate = now
                        }
                    }
                }
            }

            // Rename temp file to target file
            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Download complete: ${targetFile.absolutePath}")
                onProgress(1.0f)
                true
            } else {
                Log.e(TAG, "Failed to rename temp file to target file")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            if (targetFile.exists()) targetFile.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }
}
