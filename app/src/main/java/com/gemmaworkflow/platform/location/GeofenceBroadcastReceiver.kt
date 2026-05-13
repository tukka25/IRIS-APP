package com.gemmaworkflow.platform.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofencingEvent
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.trigger.TriggerRegistry

/**
 * Receives geofence transition events from the system and fires matching workflows.
 *
 * Triggered when the device enters, exits, or dwells in a registered geofence.
 * The workflow name (requestId) is extracted from the intent extras.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val TAG = "GeofenceBroadcastReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = com.google.android.gms.location.GeofencingEvent.fromIntent(intent)
            ?: run {
                Log.e(TAG, "Cannot parse geofencing event from intent")
                return
            }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: run {
            Log.w(TAG, "No triggering geofences in event")
            return
        }

        for (geofence in triggeringGeofences) {
            val workflowName = geofence.requestId
            Log.i(TAG, "Geofence transition $transitionType for workflow: $workflowName")

            val repository = WorkflowRepository(context)
            val workflow = repository.get(workflowName)

            if (workflow != null && workflow.trigger is TriggerConfig.Geofence) {
                TriggerRegistry.fire(context, workflow)
            } else {
                Log.w(TAG, "Workflow not found or wrong trigger type: $workflowName")
            }
        }
    }
}
