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
     * [onProgress] is called with detailed download metrics.
     * Returns true if successful, false otherwise.
     */
    suspend fun download(
        url: String,
        targetFile: File,
        onProgress: (progress: Float, downloaded: Long, total: Long, speed: Long, eta: Long) -> Unit
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

            val startTime = System.currentTimeMillis()
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
                        if (now - lastUpdate > 400) {
                            val progress = if (fileLength > 0) total.toFloat() / fileLength else 0f
                            val elapsedMillis = now - startTime
                            val speed = if (elapsedMillis > 0) (total * 1000) / elapsedMillis else 0L
                            val eta = if (speed > 0 && fileLength > 0) (fileLength - total) / speed else 0L
                            
                            onProgress(progress, total, fileLength, speed, eta)
                            lastUpdate = now
                        }
                    }
                }
            }

            // Rename temp file to target file
            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Download complete: ${targetFile.absolutePath}")
                onProgress(1.0f, fileLength, fileLength, 0L, 0L)
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
