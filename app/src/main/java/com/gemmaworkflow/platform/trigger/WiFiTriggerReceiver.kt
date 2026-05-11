package com.gemmaworkflow.platform.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.trigger.TriggerRegistry
import com.gemmaworkflow.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives ConnectivityManager.CONNECTIVITY_ACTION to detect WiFi
 * connection/disconnection events.
 * Registered in AndroidManifest.xml.
 */
class WiFiTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != android.net.ConnectivityManager.CONNECTIVITY_ACTION) return

        val workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: return

        val networkInfo = intent.getParcelableExtra<NetworkInfo>(android.net.ConnectivityManager.EXTRA_NETWORK_INFO)
        if (networkInfo == null || networkInfo.type != android.net.ConnectivityManager.TYPE_WIFI) return

        val connected = networkInfo.state == NetworkInfo.State.CONNECTED
        val ssid = extractSsid(context)

        Log.d(TAG, "WiFi event: ${if (connected) "connected" else "disconnected"} SSID=$ssid for $workflowName")

        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val workflow = repo.get(workflowName) ?: return@launch

            val trigger = workflow.trigger as? TriggerConfig.WiFi ?: return@launch

            // If SSID filter is set, validate it
            if (!trigger.ssid.isNullOrBlank()) {
                val currentSsid = ssid?.removeSurrounding("\"")
                if (currentSsid != trigger.ssid) {
                    Log.d(TAG, "SSID mismatch: expected '${trigger.ssid}', got '$currentSsid'")
                    return@launch
                }
            }

            TriggerRegistry.fire(context, workflow)
        }
    }

    private fun extractSsid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo?.ssid?.takeIf { it != "<unknown ssid>" }
        } catch (e: SecurityException) {
            // Location permission not granted — SSID unavailable
            Log.w(TAG, "Could not get SSID: location permission required on Android 10+")
            null
        }
    }

    companion object {
        private const val TAG = "WiFiTriggerReceiver"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
    }
}