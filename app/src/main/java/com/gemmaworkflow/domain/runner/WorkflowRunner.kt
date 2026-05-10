package com.gemmaworkflow.domain.runner

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.gemmaworkflow.domain.catalog.ActionSpec
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.catalog.ExecutionSpec
import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.WorkflowStep
import com.gemmaworkflow.platform.tools.ToolRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Executes a validated PlannedWorkflow step by step.
 */
class WorkflowRunner(
    private val context: Context,
    private val intentFactory: IntentFactory = IntentFactory()
) {
    suspend fun run(
        workflow: PlannedWorkflow,
        onDebug: (label: String, message: String) -> Unit = { _, _ -> }
    ): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        for (step in workflow.actions) {
            val result = executeStep(step, onDebug)
            results.add(result)
            onDebug("Tool result", "${step.id}: success=${result.success}, message=${result.message}")
            if (!result.success) break
        }
        return results
    }

    private suspend fun executeStep(
        step: WorkflowStep,
        onDebug: (label: String, message: String) -> Unit
    ): ExecutionResult {
        val spec = ActionSpecRegistry.find(step.id)
            ?: return ExecutionResult(stepId = step.id, success = false, message = "Unknown action")

        when (val execution = spec.execution) {
            is ExecutionSpec.PackageLaunch -> {
                return executePackageLaunch(step, spec, execution, onDebug)
            }
            is ExecutionSpec.InternalTool -> {
                return executeInternalTool(step, spec, execution, onDebug)
            }
            is ExecutionSpec.AndroidIntent -> Unit
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
