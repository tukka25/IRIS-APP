package com.gemmaworkflow.platform.trigger

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextCompat
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages WiFi connection/disconnection trigger workflows.
 *
 * Uses a single shared receiver covering all WiFi workflows.
 * Registered in [GemmaWorkflowApp.rescheduleTriggers] alongside other managers.
 */
object WiFiTriggerManager {

    private const val TAG = "WiFiTriggerManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active WiFi workflows: workflow name -> trigger config
    private val activeWorkflows = mutableMapOf<String, TriggerConfig.WiFi>()

    // Guard against duplicate registration
    private var isRegistered = false

    // ── registration ────────────────────────────────────────────────────────

    /**
     * Load all WiFi workflows and register the receiver.
     * Call once from [GemmaWorkflowApp.onCreate].
     */
    fun registerAll(context: Context) {
        scope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(context)
            val workflows: List<com.gemmaworkflow.domain.model.PlannedWorkflow> = repo.loadAll()
            for (workflow in workflows) {
                val trigger = workflow.trigger as? TriggerConfig.WiFi ?: continue
                activeWorkflows[workflow.name] = trigger
            }
        }

        registerReceiver(context)
    }

    /**
     * Register a single workflow. Called when saving a workflow with a WiFi trigger.
     */
    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.WiFi) {
        activeWorkflows[workflowName] = trigger
        registerReceiver(context)
    }

    /**
     * Unregister a workflow. Called when deleting a workflow.
     */
    fun unregisterWorkflow(workflowName: String) {
        activeWorkflows.remove(workflowName)
    }

    // ── receiver ────────────────────────────────────────────────────────────

    private fun registerReceiver(context: Context) {
        if (isRegistered) return
        try {
            val filter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(wifiReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(wifiReceiver, filter)
            }
            isRegistered = true
            Log.i(TAG, "WiFi receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register WiFi receiver", e)
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != "android.net.conn.CONNECTIVITY_CHANGE") return

            val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            var ssid = ""
            var bssid: String? = null
            var isConnected = false

            val hasLocationPermission = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasLocationPermission) {
                try {
                    val wifiInfo = wifiManager.connectionInfo
                    ssid = wifiInfo?.ssid?.replace("\"", "") ?: ""
                    bssid = wifiInfo?.bssid
                    isConnected = wifiInfo != null && wifiInfo.networkId != -1
                } catch (e: SecurityException) {
                    Log.w(TAG, "WiFi SSID/BSSID access denied: ${e.message}")
                }
            } else {
                Log.w(TAG, "WiFi SSID/BSSID requires ACCESS_FINE_LOCATION permission")
            }

            Log.d(TAG, "WiFi state: connected=$isConnected ssid='$ssid' bssid='$bssid'")

            val toFire = synchronized(activeWorkflows) {
                activeWorkflows.filter { (name, trigger) ->
                    val ssidMatch = trigger.ssid == null || trigger.ssid == ssid
                    val bssidMatch = trigger.bssid == null || trigger.bssid == bssid
                    val stateMatch = trigger.connectionState == null || trigger.connectionState == isConnected
                    ssidMatch && bssidMatch && stateMatch
                }.toList()
            }

            for ((workflowName, _) in toFire) {
                Log.i(TAG, "WiFi trigger matched for '$workflowName'")
                scope.launch {
                    val repo = WorkflowRepository(ctx)
                    val workflow = repo.get(workflowName) ?: return@launch
                    TriggerRegistry.fire(ctx, workflow)
                }
            }
        }
    }
}