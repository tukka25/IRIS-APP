package com.irisapp.app

import android.app.Application
import android.util.Log
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.platform.alarm.TimeTriggerScheduler
import com.irisapp.platform.alarm.AlarmTriggerManager
import com.irisapp.platform.trigger.BatteryTriggerManager
import com.irisapp.platform.trigger.BluetoothTriggerManager
import com.irisapp.platform.trigger.ChargerTriggerManager
import com.irisapp.platform.trigger.DndTriggerManager
import com.irisapp.platform.trigger.SleepTriggerManager
import com.irisapp.platform.trigger.TriggerRegistry
import com.irisapp.platform.trigger.WiFiTriggerManager
import com.irisapp.platform.trigger.AirplaneModeTriggerManager
import com.irisapp.platform.location.GeofenceManager
import com.irisapp.platform.sms.SmsTriggerManager
import com.irisapp.ui.trigger.TimeTriggerNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for IrisApp.
 *
 * On startup (including after install, force-stop, or reboot recovery) all saved
 * workflows with [TriggerConfig.Time] or [TriggerConfig.Battery] triggers are
 * re-activated so they continue to fire without requiring the user to re-configure.
 */
class IrisApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "IrisApp"

        /** Singleton instance — set in onCreate(), available for TriggerRegistry etc. */
        lateinit var instance: IrisApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
                val repo = WorkflowRepository(this@IrisApp)
                val scheduler = TimeTriggerScheduler(this@IrisApp)
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
                TriggerRegistry.init(this@IrisApp)

                // ── Battery triggers: sticky broadcast receiver ───────────────
                // BatteryTriggerManager loads battery workflows internally.
                BatteryTriggerManager.registerAll(this@IrisApp)

                // ── Charger triggers: broadcast receiver ─────────────────────────
                // ChargerTriggerManager loads charger workflows internally.
                ChargerTriggerManager.registerAll(this@IrisApp)

                // ── WiFi triggers: network state receiver ─────────────────────────
                WiFiTriggerManager.registerAll(this@IrisApp)

                // ── Bluetooth triggers: ACL state receiver ─────────────────────
                BluetoothTriggerManager.registerAll(this@IrisApp)

                // ── Airplane mode triggers: broadcast receiver ────────────────
                AirplaneModeTriggerManager.registerAll(this@IrisApp)

                // ── DND triggers: interruption filter receiver ────────────────
                DndTriggerManager.registerAll(this@IrisApp)

                // ── Sleep proxy triggers: DND + charger state ─────────────────
                SleepTriggerManager.registerAll(this@IrisApp)

                // ── Geofence triggers: location manager ─────────────────────────
                GeofenceManager.registerAll(this@IrisApp)

                // ── SMS trigger index (receiver/listener use this cache) ───────
                SmsTriggerManager.registerAll(this@IrisApp)

                // ── Alarm stopped triggers ──────────────────────────────────────
                AlarmTriggerManager.registerAll(this@IrisApp)

            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling triggers", e)
            }
        }
    }
}
