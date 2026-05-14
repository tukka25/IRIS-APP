package com.irisapp.platform.trigger

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.platform.trigger.TriggerRegistry
import com.irisapp.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives Bluetooth connection state changes via ACTION_ACL_CONNECTED and
 * ACTION_ACL_DISCONNECTED. Also handles ACTION_BOND_STATE_CHANGED for
 * paired device connect/disconnect detection.
 * Registered in AndroidManifest.xml.
 */
class BluetoothTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return

        val workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME) ?: return

        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val deviceName = device?.name ?: "Unknown"
        val deviceAddress = device?.address ?: return

        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }

        Log.d(TAG, "Bluetooth event: ${if (connected) "connected" else "disconnected"} device=$deviceName ($deviceAddress) for $workflowName")

        CoroutineScope(Dispatchers.IO).launch {
            val repo = WorkflowRepository(context)
            val workflow = repo.get(workflowName) ?: return@launch

            val trigger = workflow.trigger as? TriggerConfig.Bluetooth ?: return@launch

            // If device address filter is set, validate it
            if (!trigger.deviceAddress.isNullOrBlank()) {
                if (trigger.deviceAddress != deviceAddress) {
                    Log.d(TAG, "Device address mismatch: expected '${trigger.deviceAddress}', got '$deviceAddress'")
                    return@launch
                }
            }

            TriggerRegistry.fire(context, workflow)
        }
    }

    companion object {
        private const val TAG = "BluetoothTriggerReceiver"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
    }
}