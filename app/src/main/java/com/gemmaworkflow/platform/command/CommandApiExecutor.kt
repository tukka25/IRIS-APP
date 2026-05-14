package com.gemmaworkflow.platform.command

import android.content.Context
import android.util.Log
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.TimeUnit

/**
 * Executes arbitrary shell commands in user space via [Runtime.exec].
 *
 * SECURITY-SENSITIVE — requires user confirmation every time.
 * Root-only commands fail silently (no output, exit code unavailable).
 *
 * @param context Android context (for logging only — no system APIs used).
 * @param defaultTimeoutMs Default timeout for command execution (default: 5000 ms).
 * @param maxTimeoutMs Maximum allowed timeout (default: 30000 ms).
 */
class CommandApiExecutor(
    private val context: Context,
    private val defaultTimeoutMs: Int = 5000,
    private val maxTimeoutMs: Int = 30000
) {

    companion object {
        private const val TAG = "CommandApiExecutor"
    }

    fun execute(params: JsonObject): ExecutionResult {
        val command = params["command"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (command.isNullOrBlank()) {
            return ExecutionResult(
                stepId = "command.exec",
                success = false,
                message = "Missing required parameter: command"
            )
        }

        val timeoutMs = params["timeout_ms"]?.let {
            (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        } ?: defaultTimeoutMs

        val clampedTimeout = timeoutMs.coerceIn(0, maxTimeoutMs)
        val timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(clampedTimeout.toLong()).toInt().coerceAtLeast(1)

        Log.i(TAG, "Executing command: $command (timeout=${clampedTimeout}ms)")
        return runCommand(command, timeoutSeconds)
    }

    private fun runCommand(command: String, timeoutSeconds: Int): ExecutionResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", command))

            val completed = process.waitFor(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return ExecutionResult(
                    stepId = "command.exec",
                    success = false,
                    message = "Command timed out after ${timeoutSeconds}s: $command"
                )
            }

            val exitCode = process.exitValue()
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()

            if (exitCode != 0) {
                val msg = if (stderr.isNotEmpty()) {
                    "Command failed (exit $exitCode): $stderr"
                } else {
                    "Command failed with exit code $exitCode"
                }
                Log.w(TAG, msg)
                return ExecutionResult(
                    stepId = "command.exec",
                    success = false,
                    message = msg
                )
            }

            val outputMsg = stdout.ifEmpty { "Command executed successfully (exit 0, no output)" }
            Log.i(TAG, "Command succeeded: $command -> $outputMsg")
            ExecutionResult(
                stepId = "command.exec",
                success = true,
                message = outputMsg,
                output = stdout
            )
        } catch (e: java.io.IOException) {
            val msg = "IOException executing command: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "command.exec", success = false, message = msg)
        } catch (e: InterruptedException) {
            val msg = "Command interrupted: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "command.exec", success = false, message = msg)
        }
    }
}