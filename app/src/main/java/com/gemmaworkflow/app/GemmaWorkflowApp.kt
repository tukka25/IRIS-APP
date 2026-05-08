package com.gemmaworkflow.app

import android.app.Application
import android.util.Log
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.alarm.TimeTriggerScheduler
import com.gemmaworkflow.ui.trigger.TimeTriggerNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for GemmaWorkflow.
 *
 * On startup (including after install, force-stop, or reboot recovery) all saved
 * workflows with a [TriggerConfig.Time] trigger are re-scheduled with AlarmManager
 * so they continue to fire without requiring the user to re-configure them.
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
        rescheduleTimeTriggers()
    }

    /**
     * Re-schedule all saved workflows that carry a [TriggerConfig.Time] trigger.
     * This is called on every cold start so alarms survive force-stop or reboot.
     */
    private fun rescheduleTimeTriggers() {
        appScope.launch(Dispatchers.IO) {
            try {
                val repo = WorkflowRepository(this@GemmaWorkflowApp)
                val scheduler = TimeTriggerScheduler(this@GemmaWorkflowApp)
                val allWorkflows = repo.loadAll()

                val timeTriggers = allWorkflows.mapNotNull { workflow ->
                    val trigger = workflow.trigger as? TriggerConfig.Time ?: return@mapNotNull null
                    workflow.name to trigger
                }

                timeTriggers.forEach { (name, trigger) ->
                    try {
                        scheduler.schedule(name, trigger)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reschedule '$name'", e)
                    }
                }

                Log.i(TAG, "Rescheduled ${timeTriggers.size} time-triggered workflows on startup")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling time triggers", e)
            }
        }
    }
}
