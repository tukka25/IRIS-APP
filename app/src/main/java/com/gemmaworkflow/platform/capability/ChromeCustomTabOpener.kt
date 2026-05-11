package com.gemmaworkflow.platform.capability

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.gemmaworkflow.domain.model.ExecutionResult

/**
 * Opens URLs in a Chrome Custom Tab — stays inside the app, no app-switch.
 *
 * Uses androidx.browser:browser to launch a Chrome-styled tab that lives in
 * the calling app's task. Falls back to a regular ACTION_VIEW intent if Chrome
 * is not installed.
 *
 * Requires API 26+ (matches app minSdk). No permissions needed.
 */
class ChromeCustomTabOpener(private val context: Context) {

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null

    companion object {
        private const val TAG = "ChromeCustomTabOpener"
        private const val CHROME_PACKAGE = "com.android.chrome"

        /**
         * Returns true if Chrome is installed and able to handle Custom Tab intents.
         */
        fun isChromeAvailable(context: Context): Boolean {
            return try {
                context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://")).apply {
                        setPackage(CHROME_PACKAGE)
                    },
                    PackageManager.MATCH_DEFAULT_ONLY
                ) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Bind to the Chrome Custom Tabs service. Must be called before [openUrl].
     * Calls [onReady] when the session is established (or immediately if already bound).
     */
    fun bindService(onReady: () -> Unit) {
        CustomTabsClient.bindCustomTabsService(
            context,
            CHROME_PACKAGE,
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                    client.warmup(0L)
                    customTabsClient = client
                    customTabsSession = client.newSession(null)
                    Log.d(TAG, "Custom Tabs service connected")
                    onReady()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    customTabsClient = null
                    customTabsSession = null
                    Log.d(TAG, "Custom Tabs service disconnected")
                }
            }
        )
    }

    /**
     * Launch a URL in a Chrome Custom Tab.
     * If Chrome is not available, falls back to a regular browser intent.
     *
     * @param url The full https:// or http:// URL to open.
     * @param toolbarColor Optional toolbar color (0xRRGGBB). Pass null for default.
     */
    fun openUrl(url: String, toolbarColor: Int? = null): ExecutionResult {
        // Custom Tabs launched from a non-Activity context (e.g. a Service or
        // broadcast receiver) require FLAG_ACTIVITY_NEW_TASK or they crash.
        val launchFlags = Intent.FLAG_ACTIVITY_NEW_TASK

        // If the CustomTabsService hasn't connected yet (bindService was called
        // but the async callback hasn't fired), customTabsSession is null and
        // launchUrl will fail. Fall back to a direct browser intent immediately.
        if (customTabsSession == null) {
            Log.d(TAG, "CustomTabsSession not ready — falling back to direct browser intent")
            return fallbackToBrowser(url)
        }

        return try {
            val builder = CustomTabsIntent.Builder(customTabsSession)
            builder.setShowTitle(true)
            toolbarColor?.let { builder.setToolbarColor(it) }

            val customTabsIntent = builder.build()
            customTabsIntent.intent.flags = launchFlags
            customTabsIntent.launchUrl(context, Uri.parse(url))

            Log.d(TAG, "Custom Tab opened: $url")
            ExecutionResult(
                stepId = "browser.open_url",
                success = true,
                message = "Opened in Custom Tab: $url"
            )
        } catch (_: ActivityNotFoundException) {
            // Chrome not installed — fall back to regular browser intent
            fallbackToBrowser(url)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Custom Tab", e)
            // Try fallback even for unexpected errors
            try {
                fallbackToBrowser(url)
            } catch (_: Exception) {
                ExecutionResult(
                    stepId = "browser.open_url",
                    success = false,
                    message = e.message ?: "Failed to open URL"
                )
            }
        }
    }

    /**
     * Fall back to a standard Intent.ACTION_VIEW — shows the system browser chooser.
     */
    private fun fallbackToBrowser(url: String): ExecutionResult {
        return try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            Log.d(TAG, "Fallback to browser: $url")
            ExecutionResult(
                stepId = "browser.open_url",
                success = true,
                message = "Opened in browser (Chrome unavailable)"
            )
        } catch (_: ActivityNotFoundException) {
            Log.e(TAG, "No browser available")
            ExecutionResult(
                stepId = "browser.open_url",
                success = false,
                message = "No browser installed to handle URL"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fallback browser failed", e)
            ExecutionResult(
                stepId = "browser.open_url",
                success = false,
                message = e.message ?: "Failed to open URL in browser"
            )
        }
    }
}