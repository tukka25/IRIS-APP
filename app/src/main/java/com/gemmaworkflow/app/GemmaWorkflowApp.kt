package com.gemmaworkflow.app

import android.app.Application
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.alarm.TimeTriggerScheduler
import com.gemmaworkflow.platform.alarm.AlarmTriggerManager
import com.gemmaworkflow.platform.trigger.BatteryTriggerManager
import com.gemmaworkflow.platform.trigger.BluetoothTriggerManager
import com.gemmaworkflow.platform.trigger.ChargerTriggerManager
import com.gemmaworkflow.platform.trigger.DndTriggerManager
import com.gemmaworkflow.platform.trigger.SleepTriggerManager
import com.gemmaworkflow.platform.trigger.TriggerRegistry
import com.gemmaworkflow.platform.trigger.WiFiTriggerManager
import com.gemmaworkflow.platform.trigger.AirplaneModeTriggerManager
import com.gemmaworkflow.platform.location.GeofenceManager
import com.gemmaworkflow.platform.sms.SmsTriggerManager
import com.gemmaworkflow.ui.trigger.TimeTriggerNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for GemmaWorkflow.
 *
 * On startup (including after install, force-stop, or reboot recovery) all saved
 * workflows with [TriggerConfig.Time] or [TriggerConfig.Battery] triggers are
 * re-activated so they continue to fire without requiring the user to re-configure.
 */
class GemmaWorkflowApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "GemmaWorkflowApp"
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure notification channel is created on startup
        TimeTriggerNotification.init(this)
        rescheduleTriggers()
    }

    /**
     * Re-activate all system event triggers on every cold start:
     *  - [TriggerConfig.Time] → AlarmManager exact alarms
     *  - [TriggerConfig.Battery] → sticky broadcast receiver
     * This ensures alarms and battery watchers survive force-stop or reboot.
     */
    private fun rescheduleTriggers() {
        appScope.launch(Dispatchers.IO) {
            try {
                val repo = WorkflowRepository(this@GemmaWorkflowApp)
                val scheduler = TimeTriggerScheduler(this@GemmaWorkflowApp)
                val allWorkflows = repo.loadAll()

                // ── Time triggers: AlarmManager ───────────────────────────────
                val timeTriggers = allWorkflows.mapNotNull { workflow ->
                    val trigger = workflow.trigger as? TriggerConfig.Time ?: return@mapNotNull null
                    workflow.name to trigger
                }
                timeTriggers.forEach { (name, trigger) ->
                    try {
                        scheduler.schedule(name, trigger)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reschedule time trigger '$name'", e)
                    }
                }
                Log.i(TAG, "Rescheduled ${timeTriggers.size} time-triggered workflows on startup")

                // Give TriggerRegistry the app context so other managers can use it.
                TriggerRegistry.init(this@GemmaWorkflowApp)

                // ── Battery triggers: sticky broadcast receiver ───────────────
                // BatteryTriggerManager loads battery workflows internally.
                BatteryTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── Charger triggers: broadcast receiver ─────────────────────────
                // ChargerTriggerManager loads charger workflows internally.
                ChargerTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── WiFi triggers: network state receiver ─────────────────────────
                WiFiTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── Bluetooth triggers: ACL state receiver ─────────────────────
                BluetoothTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── Airplane mode triggers: broadcast receiver ────────────────
                AirplaneModeTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── DND triggers: interruption filter receiver ────────────────
                DndTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── Sleep proxy triggers: DND + charger state ─────────────────
                SleepTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── Geofence triggers: location manager ─────────────────────────
                GeofenceManager.registerAll(this@GemmaWorkflowApp)

                // ── SMS trigger index (receiver/listener use this cache) ───────
                SmsTriggerManager.registerAll(this@GemmaWorkflowApp)

                // ── Alarm stopped triggers ──────────────────────────────────────
                AlarmTriggerManager.registerAll(this@GemmaWorkflowApp)

            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling triggers", e)
            }
        }
    }
}
