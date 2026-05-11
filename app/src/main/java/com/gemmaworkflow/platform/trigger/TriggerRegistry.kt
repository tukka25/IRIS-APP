package com.gemmaworkflow.platform.trigger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.runner.ConfirmationRequired
import com.gemmaworkflow.domain.runner.WorkflowRunner
import com.gemmaworkflow.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for triggering workflows from system events (battery, charger, etc.).
 *
 * Background triggers can hit confirmation-gated steps. Rather than skipping, this
 * registry posts a notification prompting the user to confirm, then resumes the
 * runner when they do.
 */
object TriggerRegistry {

    private const val TAG = "TriggerRegistry"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initialize with application context. Call once from [GemmaWorkflowApp.onCreate]
     * before any trigger managers call [applicationContext].
     */
    @JvmStatic
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    private lateinit var applicationContext: Context
        private set

    private const val CHANNEL_ID = "workflow_confirm"
    private const val CHANNEL_NAME = "Workflow Confirmation"
    private const val NOTIFICATION_ID_BASE = 1000

    // Per-workflow pending execution state.
    private data class PendingExecution(
        val workflowName: String,
        val runner: WorkflowRunner,
        val workflow: PlannedWorkflow,
        val startIndex: Int
    )

    private val pendingExecutions = mutableMapOf<String, PendingExecution>()

    // ── public API ────────────────────────────────────────────────────────────

    fun fire(context: Context, workflowName: String) {
        val repo = WorkflowRepository(context)
        val workflow = repo.get(workflowName) ?: run {
            Log.w(TAG, "Workflow not found: $workflowName")
            return
        }
        fire(context, workflow)
    }

    fun fire(context: Context, workflow: PlannedWorkflow) {
        if (workflow.missingSetup.isNotEmpty()) {
            Log.w(TAG, "Workflow '${workflow.name}' has missing setup: ${workflow.missingSetup} — not firing")
            return
        }

        Log.i(TAG, "Firing workflow: ${workflow.name}")

        scope.launch(Dispatchers.Default) {
            val runner = WorkflowRunner(context = context)
            try {
                runner.run(workflow, startIndex = 0) { label, message ->
                    Log.d(TAG, "[$label] $message")
                }
            } catch (t: Throwable) {
                if (t is ConfirmationRequired) {
                    // Background trigger can't wait — store runner and notify user.
                    Log.w(TAG, "Workflow '${workflow.name}' needs confirmation, notifying user")
                    synchronized(pendingExecutions) {
                        pendingExecutions[workflow.name] = PendingExecution(
                            workflowName = workflow.name,
                            runner = runner,
                            workflow = workflow,
                            startIndex = t.stepIndex
                        )
                    }
                    showConfirmationNotification(context, workflow.name, t.step.id, t.stepIndex)
                } else {
                    Log.e(TAG, "Workflow '${workflow.name}' crashed", t)
                }
            }
        }
    }

    /**
     * Called when the user confirms the pending step for a workflow.
     * Resumes execution from the stored step index.
     */
    fun confirmAndResume(context: Context, workflowName: String) {
        val pending: PendingExecution?
        synchronized(pendingExecutions) {
            pending = pendingExecutions.remove(workflowName)
        }
        if (pending == null) {
            Log.w(TAG, "No pending execution for workflow: $workflowName")
            return
        }

        Log.i(TAG, "Resuming workflow after confirmation: $workflowName")

        scope.launch(Dispatchers.Default) {
            try {
                pending.runner.run(pending.workflow, startIndex = pending.startIndex) { label, message ->
                    Log.d(TAG, "[$label] $message")
                }
            } catch (t: Throwable) {
                if (t is ConfirmationRequired) {
                    // Another step needs confirmation — re-store and notify.
                    Log.w(TAG, "Workflow '${workflowName}' needs another confirmation, notifying user")
                    synchronized(pendingExecutions) {
                        pendingExecutions[workflowName] = PendingExecution(
                            workflowName = workflowName,
                            runner = pending.runner,
                            workflow = pending.workflow,
                            startIndex = t.stepIndex
                        )
                    }
                    showConfirmationNotification(context, workflowName, t.step.id, t.stepIndex)
                } else {
                    Log.e(TAG, "Workflow '$workflowName' crashed during resume", t)
                }
            }
        }
    }

    /**
     * Called when the user dismisses the confirmation for a workflow.
     */
    fun dismissConfirmation(context: Context, workflowName: String) {
        synchronized(pendingExecutions) {
            pendingExecutions.remove(workflowName)
        }
        Log.i(TAG, "Confirmation dismissed for workflow: $workflowName")
    }

    // ── notification ──────────────────────────────────────────────────────────

    private fun showConfirmationNotification(
        context: Context,
        workflowName: String,
        stepId: String,
        stepIndex: Int
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORKFLOW_NAME, workflowName)
            putExtra(EXTRA_STEP_INDEX, stepIndex)
            putExtra(EXTRA_ACTION, ACTION_CONFIRM)
        }
        val confirmPending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORKFLOW_NAME, workflowName)
            putExtra(EXTRA_ACTION, ACTION_DISMISS)
        }
        val dismissPending = PendingIntent.getActivity(
            context, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stepLabel = stepId.substringAfterLast(".")
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Confirm: $workflowName")
            .setContentText("Step ${stepIndex + 1}: $stepLabel")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(0, "Confirm", confirmPending)
            .addAction(0, "Dismiss", dismissPending)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_BASE + workflowName.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompts you to confirm workflow steps triggered in the background"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    const val EXTRA_WORKFLOW_NAME = "extra_workflow_name"
    const val EXTRA_STEP_INDEX = "extra_step_index"
    const val EXTRA_ACTION = "extra_action"
    const val ACTION_CONFIRM = "action_confirm"
    const val ACTION_DISMISS = "action_dismiss"
}