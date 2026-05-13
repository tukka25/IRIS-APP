package com.gemmaworkflow.platform.http

import android.util.Log
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Silently executes HTTP requests (GET/POST/PUT/DELETE/PATCH) using HttpURLConnection.
 * No third-party library required.
 *
 * Requires: INTERNET permission (already declared in manifest).
 * Requires confirmation: true (arbitrary network calls).
 */
class HttpRequestApiExecutor {

    companion object {
        private const val TAG = "HttpRequestApiExecutor"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_RESPONSE_PREVIEW = 500
    }

    fun execute(params: JsonObject): ExecutionResult {
        val url = params["url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (url.isNullOrBlank()) {
            return ExecutionResult(stepId = "http_request", success = false, message = "Missing required param: url")
        }

        val method = params["method"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.uppercase() ?: "GET"
        val validMethods = setOf("GET", "POST", "PUT", "DELETE", "PATCH")
        if (method !in validMethods) {
            return ExecutionResult(stepId = "http_request", success = false, message = "Invalid method '$method'. Use: ${validMethods.joinToString()}")
        }

        val body = params["body"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val contentType = params["content_type"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: if (!body.isNullOrBlank()) "application/x-www-form-urlencoded" else null

        // Parse optional headers
        val headers = (params["headers"] as? JsonObject)?.let { headersObj ->
            headersObj.keys.associateWith { key ->
                (headersObj[key] as? JsonPrimitive)?.contentOrNull ?: ""
            }
        } ?: emptyMap()

        return executeRequest(url, method, headers, body, contentType)
    }

    private fun executeRequest(
        urlString: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        contentType: String?
    ): ExecutionResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true

            // Set headers
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            if (contentType != null) {
                connection.setRequestProperty("Content-Type", contentType)
            }

            // Write body for methods that support it
            if (body != null && method in setOf("POST", "PUT", "PATCH")) {
                connection.doOutput = true
                connection.outputStream.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage

            // Read response body (limited preview)
            val responseBody = try {
                val stream = if (responseCode >= 400) connection.errorStream else connection.inputStream
                if (stream != null) {
                    stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                } else ""
            } catch (e: Exception) {
                "(no body)"
            }

            val preview = if (responseBody.length > MAX_RESPONSE_PREVIEW) {
                responseBody.take(MAX_RESPONSE_PREVIEW) + "... [${responseBody.length - MAX_RESPONSE_PREVIEW} more chars]"
            } else {
                responseBody
            }

            val msg = "$responseCode $responseMessage | $preview"
            Log.d(TAG, msg)

            ExecutionResult(
                stepId = "http_request",
                success = responseCode in 200..299,
                message = msg
            )
        } catch (e: Exception) {
            val msg = "Connection error: ${e.message ?: e::class.java.simpleName}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "http_request", success = false, message = msg)
        } finally {
            connection?.disconnect()
        }
    }
}