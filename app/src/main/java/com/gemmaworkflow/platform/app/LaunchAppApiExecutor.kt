package com.gemmaworkflow.platform.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Silently launches an installed app by package name (and optional class name).
 * Uses getLaunchIntentForPackage() which respects the app's main launcher activity.
 *
 * Requires: QUERY_ALL_PACKAGES (Android 11+) — declared in manifest.
 */
class LaunchAppApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "LaunchAppApiExecutor"
    }

    fun execute(params: JsonObject): ExecutionResult {
        val packageName = params["package_name"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (packageName.isNullOrBlank()) {
            return ExecutionResult(stepId = "launch_app", success = false, message = "Missing required param: package_name")
        }

        return launchApp(packageName, params["class_name"]?.let { (it as? JsonPrimitive)?.contentOrNull })
    }

    private fun launchApp(packageName: String, className: String?): ExecutionResult {
        return try {
            val pm = context.packageManager

            val intent = if (!className.isNullOrBlank()) {
                // Launch a specific activity within the app
                Intent().setClassName(packageName, className)
            } else {
                // Launch the app's main launcher activity
                pm.getLaunchIntentForPackage(packageName)
            }

            if (intent == null) {
                val msg = "No launch intent found for '$packageName'. App may have no launcher activity."
                Log.w(TAG, msg)
                return ExecutionResult(stepId = "launch_app", success = false, message = msg)
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)

            val msg = if (className != null) "Launched $packageName/$className" else "Launched $packageName"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "launch_app", success = true, message = msg)
        } catch (e: PackageManager.NameNotFoundException) {
            val msg = "Package not found: '$packageName'"
            Log.w(TAG, msg)
            ExecutionResult(stepId = "launch_app", success = false, message = msg)
        } catch (e: SecurityException) {
            val msg = "Cannot launch '$packageName': ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "launch_app", success = false, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to launch '$packageName': ${e.message ?: e::class.java.simpleName}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "launch_app", success = false, message = msg)
        }
    }
}