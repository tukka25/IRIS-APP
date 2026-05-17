package com.irisapp.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.irisapp.R
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.catalog.ActionSpecRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that drives the on-device SLM execution pipeline when a
 * widget suggestion pill is tapped.
 *
 * Why a foreground service?
 *   Widget callbacks ([TriggerWorkflowAction]) execute in a short-lived process.
 *   The SLM inference (LiteRT-LM GPU) can take 3–15 seconds — far beyond the
 *   10-second window allowed for BroadcastReceivers or Glance ActionCallbacks.
 *   A foreground service gives us an unconstrained coroutine scope and keeps
 *   the process alive for the duration of inference.
 *
 * AndroidManifest.xml — add inside <application>:
 *   <service
 *       android:name=".widget.SlmExecutionService"
 *       android:foregroundServiceType="dataSync"
 *       android:exported="false" />
 *
 * And at the top level (outside <application>):
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
 */
class SlmExecutionService : Service() {

    companion object {
        /** Intent extra: name of the workflow to execute. */
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
        /** Intent extra: string form of the originating widget's GlanceId. */
        const val EXTRA_GLANCE_ID     = "glance_id"

        private const val NOTIFICATION_ID = 7001
        private const val CHANNEL_ID      = "iris_slm_execution"
    }

    // Coroutine scope tied to this service's lifecycle.
    // SupervisorJob ensures one failed workflow doesn't cancel other concurrent runs.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val workflowName = intent?.getStringExtra(EXTRA_WORKFLOW_NAME)
        if (workflowName.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Post the mandatory foreground notification immediately.
        // Must be called within 5 seconds of onStartCommand on API 26+.
        startForeground(NOTIFICATION_ID, buildNotification(workflowName))

        serviceScope.launch {
            runWorkflow(workflowName, startId)
        }

        // START_NOT_STICKY: if the process is killed, don't auto-restart without an Intent.
        return START_NOT_STICKY
    }

    private suspend fun runWorkflow(workflowName: String, startId: Int) {
        try {
            // Phase 1: ANALYZING was set optimistically by TriggerWorkflowAction.
            // Small pause so the widget renders the ANALYZING state before we proceed.
            delay(400)

            // Phase 2: Load workflow definition.
            val workflow = WorkflowRepository(applicationContext).get(workflowName)
                ?: throw IllegalArgumentException("Workflow '$workflowName' not found")

            // Phase 3: Begin action execution.
            IrisWidgetStateRepository.setSlmState(
                context      = applicationContext,
                slmState     = SlmProcessState.TOOL_CALL,
                workflowName = workflowName
            )

            val actions = workflow.actions
            var allSuccess = true

            if (actions.isEmpty()) {
                // No actions defined — log a single placeholder step.
                executeStep(workflowName = workflowName, stepName = "Run Workflow", simulatedMs = 600)
            } else {
                for (step in actions) {
                    val stepName = ActionSpecRegistry.find(step.id)?.label ?: step.id
                    val success = executeStep(
                        workflowName  = workflowName,
                        stepName      = stepName,
                        simulatedMs   = 500 + (600 * Math.random()).toLong()
                        // ── TODO: replace body of executeStep with real runner:
                        // val result = ActionExecutor.run(step, applicationContext)
                        // return result.success
                    )
                    if (!success) { allSuccess = false; break }
                }
            }

            // Phase 4: Record final result and push to widget.
            if (allSuccess) {
                IrisWidgetStateRepository.recordSuccess(applicationContext, workflowName)
            } else {
                IrisWidgetStateRepository.recordFailure(applicationContext, workflowName)
            }

        } catch (e: Exception) {
            IrisWidgetStateRepository.setCurrentStep(applicationContext, null)
            IrisWidgetStateRepository.recordFailure(applicationContext, workflowName)
        } finally {
            stopSelf(startId)
        }
    }

    /**
     * Simulates executing one action step:
     *   1. Announces the step name live (widget shows "⋯ stepName").
     *   2. Waits [simulatedMs] to mimic real work.
     *   3. Logs the result with measured wall-clock duration.
     *
     * Replace the [delay] call with a real ActionExecutor call when the runner is ready.
     */
    private suspend fun executeStep(
        workflowName: String,
        stepName: String,
        simulatedMs: Long
    ): Boolean {
        IrisWidgetStateRepository.setCurrentStep(applicationContext, stepName)

        val t0 = System.currentTimeMillis()
        delay(simulatedMs)  // ← replace with: val result = ActionExecutor.run(step, ctx)
        val durationMs = System.currentTimeMillis() - t0

        val success = true  // ← replace with: result.success
        val output  = if (success) stepOutputHint(stepName) else "Error — see logs"

        IrisWidgetStateRepository.logStep(
            applicationContext,
            StepLog(
                stepName    = stepName,
                output      = output,
                success     = success,
                durationMs  = durationMs,
                timestampMs = System.currentTimeMillis()
            )
        )
        IrisWidgetStateRepository.setCurrentStep(applicationContext, null)
        return success
    }

    /** Returns a plausible one-line output hint based on the step label. */
    private fun stepOutputHint(label: String): String = when {
        label.contains("email",    ignoreCase = true) -> "Message queued"
        label.contains("calendar", ignoreCase = true) -> "Event created"
        label.contains("sms",      ignoreCase = true) ||
        label.contains("whatsapp", ignoreCase = true) -> "Sent to recipient"
        label.contains("search",   ignoreCase = true) -> "Results fetched"
        label.contains("copy",     ignoreCase = true) -> "Copied to clipboard"
        label.contains("open",     ignoreCase = true) ||
        label.contains("browser",  ignoreCase = true) -> "Launched"
        label.contains("navigate", ignoreCase = true) -> "Route ready"
        label.contains("alarm",    ignoreCase = true) -> "Alarm set"
        label.contains("timer",    ignoreCase = true) -> "Timer started"
        label.contains("note",     ignoreCase = true) -> "Saved"
        label.contains("contact",  ignoreCase = true) -> "Contact resolved"
        label.contains("dial",     ignoreCase = true) -> "Call initiated"
        else -> "Done"
    }

    private fun buildNotification(workflowName: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IRIS — Running Workflow")
            .setContentText(workflowName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            .also { ensureNotificationChannel() }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "IRIS Workflow Execution",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while IRIS is running an on-device workflow"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
