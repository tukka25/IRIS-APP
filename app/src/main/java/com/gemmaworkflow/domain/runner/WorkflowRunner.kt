package com.gemmaworkflow.domain.runner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.gemmaworkflow.domain.catalog.ActionSpec
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.catalog.ExecutionSpec
import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.WorkflowStep
import com.gemmaworkflow.platform.alarm.AlarmApiExecutor
import com.gemmaworkflow.platform.calendar.CalendarApiExecutor
import com.gemmaworkflow.platform.capability.ChromeCustomTabOpener
import com.gemmaworkflow.platform.clipboard.ClipboardApiExecutor
import com.gemmaworkflow.platform.tools.ToolRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thrown by [WorkflowRunner.executeStep] when a step has [WorkflowStep.requiresConfirmation] set.
 * The runner pauses at that step; the UI calls [WorkflowRunner.confirmPendingStep][confirmPendingStep]
 * or [WorkflowRunner.dismissPendingStep][dismissPendingStep] to proceed.
 */
class ConfirmationRequired(
    val step: WorkflowStep,
    val stepIndex: Int
) : Exception("Confirmation required for step: ${step.id}")

/**
 * Executes a validated PlannedWorkflow step by step.
 */
class WorkflowRunner(
    private val context: Context,
    private val intentFactory: IntentFactory = IntentFactory(),
    private val calendarApiExecutor: CalendarApiExecutor = CalendarApiExecutor(context),
    private val alarmApiExecutor: AlarmApiExecutor = AlarmApiExecutor(context),
    private val clipboardApiExecutor: ClipboardApiExecutor = ClipboardApiExecutor(context),
    private val chromeCustomTabOpener: ChromeCustomTabOpener = ChromeCustomTabOpener(context)
) {
    private var pendingStep: WorkflowStep? = null
    private var pendingStepIndex: Int = -1
    /** Step indices the user has already confirmed — skip re-confirmation on resume. */
    private val confirmedSteps = mutableSetOf<Int>()

    suspend fun run(
        workflow: PlannedWorkflow,
        startIndex: Int = 0,
        onDebug: (label: String, message: String) -> Unit = { _, _ -> }
    ): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        // If resuming past confirmed steps, mark them as already-executed successes
        for (i in 0 until startIndex) {
            results.add(ExecutionResult(stepId = workflow.actions[i].id, success = true, message = "Confirmed"))
        }
        for (i in startIndex until workflow.actions.size) {
            val step = workflow.actions[i]
            val result = executeStep(step, i, onDebug)
            results.add(result)
            onDebug("Tool result", "${step.id}: success=${result.success}, message=${result.message}")
            if (!result.success) break
        }
        return results
    }

    fun confirmPendingStep(): WorkflowStep? {
        pendingStepIndex.let { confirmedSteps.add(it) }
        val step = pendingStep
        pendingStep = null
        pendingStepIndex = -1
        return step
    }

    fun dismissPendingStep(): WorkflowStep? {
        pendingStepIndex.let { confirmedSteps.add(it) }
        val step = pendingStep
        pendingStep = null
        pendingStepIndex = -1
        return step
    }

    private suspend fun executeStep(
        step: WorkflowStep,
        stepIndex: Int,
        onDebug: (label: String, String) -> Unit
    ): ExecutionResult {
        val spec = ActionSpecRegistry.find(step.id)
            ?: return ExecutionResult(stepId = step.id, success = false, message = "Unknown action")

        // Pause and request user confirmation before executing sensitive steps
        if (spec.requiresConfirmation || step.requiresConfirmation) {
            if (stepIndex !in confirmedSteps) {
                pendingStep = step
                pendingStepIndex = stepIndex
                throw ConfirmationRequired(step, stepIndex)
            }
            // stepIndex is in confirmedSteps — user already confirmed; execute without pausing
        }

        // Silently execute calendar.create_event via ContentResolver instead of launching an intent
        if (step.id == "calendar.create_event") {
            onDebug("Tool call", "${step.id} params=${step.params}")
            Log.d(TAG, "Tool call ${step.id} params=${step.params}")
            val result = calendarApiExecutor.execute(step.params)
            onDebug("CalendarApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "CalendarApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently execute clipboard.copy_text via ClipboardManager instead of launching the share sheet.
        if (step.id == "clipboard.copy_text") {
            onDebug("Tool call", "${step.id} params=${step.params}")
            Log.d(TAG, "Tool call ${step.id} params=${step.params}")
            val result = clipboardApiExecutor.execute(step.params)
            onDebug("ClipboardApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ClipboardApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently execute alarm.set_alarm via AlarmManager instead of launching the Clock app.
        // On Android 12+ if SCHEDULE_EXACT_ALARM is not granted we redirect to Settings.
        if (step.id == "alarm.set_alarm") {
            onDebug("Tool call", "${step.id} params=${step.params}")
            Log.d(TAG, "Tool call ${step.id} params=${step.params}")

            if (alarmApiExecutor.shouldRequestExactAlarmPermission()) {
                // Android 12+: SCHEDULE_EXACT_ALARM gate is closed — redirect to Settings.
                val settingsIntent = alarmApiExecutor.buildExactAlarmSettingsIntent()
                if (settingsIntent != null && context is Activity) {
                    context.startActivity(settingsIntent)
                }
                return ExecutionResult(
                    stepId = step.id,
                    success = false,
                    message = "SCHEDULE_EXACT_ALARM permission required — redirected to Settings"
                )
            }

            val result = alarmApiExecutor.execute(step.params)
            onDebug("AlarmApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "AlarmApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently copy text to clipboard instead of launching the share sheet.
        if (step.id == "share.share_text") {
            onDebug("Tool call", "${step.id} params=${step.params}")
            Log.d(TAG, "Tool call ${step.id} params=${step.params}")
            val result = clipboardApiExecutor.executeShareText(step.params)
            onDebug("ClipboardApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ClipboardApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently copy image URI to clipboard instead of launching the share sheet.
        if (step.id == "share.share_image") {
            onDebug("Tool call", "${step.id} params=${step.params}")
            Log.d(TAG, "Tool call ${step.id} params=${step.params}")
            val result = clipboardApiExecutor.executeShareImage(step.params)
            onDebug("ClipboardApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ClipboardApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Open URL in a Chrome Custom Tab — no app switch, falls back to regular browser.
        val customTabParams = intentFactory.buildCustomTabParams(spec, step.params)
        if (customTabParams != null) {
            onDebug("Tool call", "${step.id} params=${step.params}")
            Log.d(TAG, "Tool call ${step.id} params=${step.params}")
            val result = chromeCustomTabOpener.openUrl(customTabParams.url, customTabParams.toolbarColor)
            onDebug("ChromeCustomTabOpener", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ChromeCustomTabOpener result=${result.success} message=${result.message}")
            return result
        }

        when (val execution = spec.execution) {
            is ExecutionSpec.PackageLaunch -> {
                return executePackageLaunch(step, spec, execution, onDebug)
            }
            is ExecutionSpec.InternalTool -> {
                return executeInternalTool(step, spec, execution, onDebug)
            }
            is ExecutionSpec.AndroidIntent -> Unit
            is ExecutionSpec.CustomTab,
            ExecutionSpec.BuiltIn -> Unit
        }

        return runCatching {
            onDebug("Tool call", "${step.id} params=${step.params}")
            Log.d(TAG, "Tool call ${step.id} params=${step.params}")
            val intent = intentFactory.buildExecutableIntent(spec, step.params)
            onDebug("Intent built", intent.describe())
            Log.d(TAG, "Intent built action=${intent.action} data=${intent.data} type=${intent.type} package=${intent.`package`}")
            val resolved = resolveActivity(intent)
                ?: return tryFallback(step, spec, onDebug, "No native handler resolved for final intent")
            onDebug(
                "Native resolveActivity",
                "${resolved.activityInfo.loadLabel(context.packageManager)} (${resolved.activityInfo.packageName}/${resolved.activityInfo.name})"
            )
            context.startActivity(intent)
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = step.id,
                    success = true,
                    message = "Started ${spec.label}"
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Tool failed ${step.id}", error)
                if (error is ConfirmationRequired) throw error
                if (error is ActivityNotFoundException || error is IllegalArgumentException) {
                    tryFallback(step, spec, onDebug, error.message ?: "Failed to start ${spec.label}")
                } else {
                    ExecutionResult(
                        stepId = step.id,
                        success = false,
                        message = error.message ?: "Failed to start ${spec.label}"
                    )
                }
            }
        )
    }

    private fun executePackageLaunch(
        step: WorkflowStep,
        spec: ActionSpec,
        execution: ExecutionSpec.PackageLaunch,
        onDebug: (label: String, message: String) -> Unit
    ): ExecutionResult {
        val packageName = step.params[execution.packageParamName]?.asString()
            ?: return ExecutionResult(stepId = step.id, success = false, message = "Missing package name")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return tryFallback(step, spec, onDebug, "No launchable app for package $packageName")

        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            onDebug("Tool call", "${step.id} package=$packageName")
            onDebug("Native getLaunchIntentForPackage", packageName)
            context.startActivity(launchIntent)
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = step.id,
                    success = true,
                    message = "Started ${spec.label}"
                )
            },
            onFailure = {
                tryFallback(step, spec, onDebug, it.message ?: "Failed to launch $packageName")
            }
        )
    }

    private suspend fun executeInternalTool(
        step: WorkflowStep,
        spec: ActionSpec,
        execution: ExecutionSpec.InternalTool,
        onDebug: (label: String, message: String) -> Unit
    ): ExecutionResult {
        val input = step.params.mapValues { (_, value) -> value.asString().orEmpty() }
        onDebug("Tool call", "${execution.toolName} params=$input")
        val result = ToolRegistry.execute(execution.toolName, input)

        return if (result.success) {
            ExecutionResult(
                stepId = step.id,
                success = true,
                message = result.output.ifBlank { "Started ${spec.label}" }
            )
        } else {
            tryFallback(step, spec, onDebug, result.error ?: "Internal tool failed")
        }
    }

    private fun tryFallback(
        sourceStep: WorkflowStep,
        sourceSpec: ActionSpec,
        onDebug: (label: String, message: String) -> Unit,
        reason: String
    ): ExecutionResult {
        onDebug("Fallback lookup", "${sourceSpec.id}: $reason")

        for (fallbackId in sourceSpec.fallbackActionIds) {
            val fallbackSpec = ActionSpecRegistry.find(fallbackId) ?: continue
            val fallbackParams = FallbackParamMapper.mapParams(
                sourceSpec = sourceSpec,
                sourceStep = sourceStep,
                fallbackSpec = fallbackSpec
            )

            if (fallbackParams == null) {
                onDebug("Fallback skipped", "$fallbackId: could not map required params")
                continue
            }

            val fallbackIntentResult = runCatching {
                intentFactory.buildExecutableIntent(fallbackSpec, fallbackParams)
            }
            if (fallbackIntentResult.isFailure) {
                onDebug("Fallback skipped", "$fallbackId: ${fallbackIntentResult.exceptionOrNull()?.message}")
                continue
            }
            val fallbackIntent = fallbackIntentResult.getOrThrow()

            val resolved = resolveActivity(fallbackIntent)
            if (resolved == null) {
                onDebug("Fallback skipped", "$fallbackId: no native handler for ${fallbackIntent.describe()}")
                continue
            }

            return runCatching {
                onDebug("Fallback tool call", "$fallbackId params=$fallbackParams")
                onDebug("Fallback intent built", fallbackIntent.describe())
                onDebug(
                    "Fallback resolveActivity",
                    "${resolved.activityInfo.loadLabel(context.packageManager)} (${resolved.activityInfo.packageName}/${resolved.activityInfo.name})"
                )
                context.startActivity(fallbackIntent)
            }.fold(
                onSuccess = {
                    ExecutionResult(
                        stepId = sourceStep.id,
                        success = true,
                        message = "Started fallback ${fallbackSpec.label}"
                    )
                },
                onFailure = {
                    onDebug("Fallback failed", "$fallbackId: ${it.message}")
                    ExecutionResult(
                        stepId = sourceStep.id,
                        success = false,
                        message = it.message ?: "Fallback failed for ${fallbackSpec.label}"
                    )
                }
            )
        }

        return ExecutionResult(
            stepId = sourceStep.id,
            success = false,
            message = "No fallback available: $reason"
        )
    }

    private fun resolveActivity(intent: Intent): android.content.pm.ResolveInfo? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    private fun Intent.describe(): String {
        return "action=$action data=$data type=$type package=${`package`}"
    }

    private companion object {
        const val TAG = "WorkflowRunner"
    }
}

private fun JsonElement.asString(): String? {
    return runCatching { jsonPrimitive.contentOrNull }.getOrNull()
}
