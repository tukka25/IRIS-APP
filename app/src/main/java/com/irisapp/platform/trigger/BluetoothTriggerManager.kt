package com.irisapp.platform.trigger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.TriggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages Bluetooth connection/disconnection trigger workflows.
 *
 * Uses a single shared receiver covering all Bluetooth workflows.
 * Detects ACL connect/disconnect events and optionally filters by device address.
 *
 * Registered in [IrisApp.rescheduleTriggers] alongside other managers.
 */
object BluetoothTriggerManager {

    private const val TAG = "BluetoothTriggerManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active Bluetooth workflows: workflow name -> trigger config
    private val activeWorkflows = mutableMapOf<String, TriggerConfig.Bluetooth>()

    // Guard against duplicate registration
    private var isRegistered = false

    // ── registration ────────────────────────────────────────────────────────

    /**
     * Load all Bluetooth workflows and register the receiver.
     * Call once from [IrisApp.onCreate].
     */
    fun registerAll(context: Context) {
        scope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(context)
            val workflows: List<com.irisapp.domain.model.PlannedWorkflow> = repo.loadAll()
            for (workflow in workflows) {
                val trigger = workflow.trigger as? TriggerConfig.Bluetooth ?: continue
                activeWorkflows[workflow.name] = trigger
            }
        }

        registerReceiver(context)
    }

    /**
     * Register a single workflow. Called when saving a workflow with a Bluetooth trigger.
     */
    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.Bluetooth) {
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
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(bluetoothReceiver, filter)
            }
            isRegistered = true
            Log.i(TAG, "Bluetooth receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bluetooth receiver", e)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == null) return

            val device: BluetoothDevice? = try {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } catch (e: Exception) {
                null
            }

            val deviceAddress: String? = try {
                device?.address
            } catch (e: SecurityException) {
                Log.w(TAG, "Bluetooth device address denied: ${e.message}")
                null
            }
            val deviceName: String = try {
                device?.name ?: "Unknown"
            } catch (e: SecurityException) {
                Log.w(TAG, "Bluetooth device name denied: ${e.message}")
                "Unknown"
            }

            val connected = when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> true
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> false
                else -> return
            }

            Log.d(TAG, "Bluetooth: ${if (connected) "CONNECTED" else "DISCONNECTED"} $deviceName ($deviceAddress)")

            fireWorkflows(ctx, connected, deviceAddress, deviceName)
        }
    }

    private fun fireWorkflows(context: Context, connected: Boolean, deviceAddress: String?, deviceName: String) {
        val toFire = synchronized(activeWorkflows) {
            activeWorkflows.filter { (workflowName, trigger) ->
                val stateMatch = trigger.connectionState == null || trigger.connectionState == connected
                val addrMatch = trigger.deviceAddress.isNullOrBlank() || deviceAddress == trigger.deviceAddress
                stateMatch && addrMatch
            }.toList()
        }

        for ((workflowName, _) in toFire) {
            Log.i(TAG, "Bluetooth trigger matched for '$workflowName' (device=$deviceName)")
            scope.launch {
                val repo = WorkflowRepository(context)
                val workflow = repo.get(workflowName) ?: return@launch
                TriggerRegistry.fire(context, workflow)
            }
        }
    }
}