package com.gemmaworkflow.platform.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.GeofenceTransition
import com.gemmaworkflow.domain.model.TriggerConfig

/**
 * Manages geofence registration and lifecycle.
 *
 * Architecture:
 * - [registerAll] is called once from [com.gemmaworkflow.app.GemmaWorkflowApp.onCreate]
 *   to restore all geofence workflows from the repository.
 * - [registerWorkflow] is called when a workflow with a geofence trigger is saved.
 * - [unregisterWorkflow] is called when a geofence workflow is deleted.
 * - When a geofence transition fires, [GeofenceBroadcastReceiver] receives it and
 *   calls [TriggerRegistry.fire].
 */
object GeofenceManager {

    private const val TAG = "GeofenceManager"

    /** Workflow name → geofence config */
    private val activeGeofences = mutableMapOf<String, TriggerConfig.Geofence>()

    private lateinit var geofencingClient: GeofencingClient

    /** PendingIntent fired by the system on geofence transitions. */
    private lateinit var geofencePendingIntent: PendingIntent

    /**
     * Application context stored at init so [hasLocationPermissions] can use it
     * in methods that don't receive a [Context] parameter.
     */
    private lateinit var appContext: Context

    /**
     * Initialize the client and register the broadcast receiver.
     * Called once from [GemmaWorkflowApp.onCreate].
     */
    fun registerAll(context: Context) {
        appContext = context.applicationContext
        geofencingClient = LocationServices.getGeofencingClient(context)

        // Build the PendingIntent that the system fires on geofence transitions.
        // FLAG_UPDATE_CURRENT ensures intent extras (workflowName) are kept current.
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        geofencePendingIntent = PendingIntent.getBroadcast(
            context,
            0, // requestCode — shared; workflow name is in geofence requestId
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Restore all saved workflows with geofence triggers
        restoreSavedGeofences()
    }

    /**
     * Add a geofence for a single workflow.
     * Called from [WorkflowGenerationViewModel] when a geofence workflow is saved.
     */
    fun registerWorkflow(context: Context, workflowName: String, trigger: TriggerConfig.Geofence) {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Missing location permissions — cannot register geofence for '$workflowName'")
            return
        }

        activeGeofences[workflowName] = trigger
        addGeofence(workflowName, trigger)
    }

    /**
     * Remove the geofence for a deleted workflow.
     * Called from [WorkflowGenerationViewModel] when a geofence workflow is deleted.
     */
    fun unregisterWorkflow(workflowName: String) {
        val removed = activeGeofences.remove(workflowName)
        if (removed == null) {
            Log.w(TAG, "No active geofence found for workflow '$workflowName' — nothing to remove")
            return
        }

        geofencingClient.removeGeofences(listOf(workflowName))
            .addOnSuccessListener { Log.i(TAG, "Geofence removed for workflow: $workflowName") }
            .addOnFailureListener { e: Exception -> Log.e(TAG, "Failed to remove geofence for '$workflowName'", e) }
    }

    /**
     * Load all saved workflows from the repository and register their geofences.
     * Called from [registerAll] on app startup to restore geofence state.
     */
    private fun restoreSavedGeofences() {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "No location permissions on restore — will register on next permission grant")
            return
        }

        val repository = WorkflowRepository(appContext)
        val workflows = repository.loadAll()
        val geofenceWorkflows = workflows.filter { it.trigger is TriggerConfig.Geofence }

        for (workflow in geofenceWorkflows) {
            val trigger = workflow.trigger as TriggerConfig.Geofence
            activeGeofences[workflow.name] = trigger
        }

        if (activeGeofences.isNotEmpty()) {
            addAllGeofences()
        }

        Log.i(TAG, "Restored ${activeGeofences.size} geofence workflows")
    }

    /**
     * Add all active geofences to the [GeofencingClient] in a single call.
     */
    private fun addAllGeofences() {
        val geofenceList = activeGeofences.map { (name, trigger) -> buildGeofence(name, trigger) }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT
            )
            .addGeofences(geofenceList)
            .build()

        if (hasLocationPermissions()) {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener { Log.i(TAG, "All geofences registered: ${activeGeofences.size}") }
                .addOnFailureListener { e: Exception -> Log.e(TAG, "Failed to register geofences", e) }
        }
    }

    /**
     * Add or update a single geofence (called on workflow save).
     */
    private fun addGeofence(workflowName: String, trigger: TriggerConfig.Geofence) {
        val geofence = buildGeofence(workflowName, trigger)

        val initialTrigger = when (trigger.transitionType) {
            GeofenceTransition.ENTER,
            GeofenceTransition.ENTER_EXIT -> GeofencingRequest.INITIAL_TRIGGER_ENTER
            GeofenceTransition.EXIT -> GeofencingRequest.INITIAL_TRIGGER_EXIT
            GeofenceTransition.DWELL -> GeofencingRequest.INITIAL_TRIGGER_DWELL
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(initialTrigger)
            .addGeofence(geofence)
            .build()

        if (hasLocationPermissions()) {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener { Log.i(TAG, "Geofence registered for: $workflowName") }
                .addOnFailureListener { e: Exception -> Log.e(TAG, "Failed to register geofence for '$workflowName'", e) }
        }
    }

    private fun buildGeofence(workflowName: String, trigger: TriggerConfig.Geofence): Geofence {
        val transitionTypes = when (trigger.transitionType) {
            GeofenceTransition.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
            GeofenceTransition.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
            GeofenceTransition.DWELL -> Geofence.GEOFENCE_TRANSITION_DWELL
            GeofenceTransition.ENTER_EXIT -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        }

        return Geofence.Builder()
            .setRequestId(workflowName)  // requestId = workflowName; used in receiver to route
            .setCircularRegion(trigger.latitude, trigger.longitude, trigger.radiusMeters)
            .setTransitionTypes(transitionTypes)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setLoiteringDelay(trigger.dwellDelaySeconds * 1000)
            .build()
    }

    private fun hasLocationPermissions(): Boolean {
        val hasFineLocation = ActivityCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return hasFineLocation && hasBackgroundLocation
    }
}
