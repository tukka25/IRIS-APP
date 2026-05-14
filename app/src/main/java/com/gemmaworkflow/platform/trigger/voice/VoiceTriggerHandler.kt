package com.gemmaworkflow.platform.trigger.voice

import android.content.Context
import android.util.Log
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.parser.WorkflowJsonParser
import com.gemmaworkflow.domain.planner.RetoWorkflowPlanner
import com.gemmaworkflow.domain.safety.WorkflowValidator
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import com.gemmaworkflow.platform.inference.InferenceManager
import com.gemmaworkflow.platform.tools.reto.RetoTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridges a voice transcript into the LLM workflow generation pipeline.
 *
 * Takes a raw transcript string, feeds it to [RetoWorkflowPlanner] (the same
 * planner used by [com.gemmaworkflow.ui.home.WorkflowGenerationViewModel]),
 * and returns a parsed + validated [PlannedWorkflow].
 *
 * Designed for use in [VoiceTriggerFab]: after the user reviews the generated
 * workflow, they can save/run it through the normal workflow pipeline.
 *
 * @param context  Application context (for InferenceManager / scanner init).
 */
class VoiceTriggerHandler(private val context: Context) {

    private val tag = "VoiceTriggerHandler"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── public callback interface ─────────────────────────────────────────────

    /**
     * Called as stages progress during generation.
     * UI can display "Listening…", "Generating…", "Done" etc.
     */
    interface GenerationCallback {
        fun onStage(stage: VoiceStage)
        fun onDebug(tag: String, message: String)
        fun onError(message: String)
        fun onWorkflowReady(workflow: PlannedWorkflow, rawJson: String, validationErrors: List<String>)
    }

    enum class VoiceStage {
        STARTING,
        TRANSCRIPT_RECEIVED,
        LOADING_MODEL,
        GENERATING,
        VALIDATING,
        DONE,
        ERROR
    }

    // ── generation ─────────────────────────────────────────────────────────────

    /**
     * Generate a workflow from a voice transcript.
     *
     * Uses the same [RetoWorkflowPlanner] + [InferenceManager] pipeline as the
     * text-based UI in [WorkflowGenerationViewModel.generate].
     *
     * The callback is invoked synchronously within the coroutine.
     */
    fun generateFromTranscript(
        transcript: String,
        callback: GenerationCallback
    ) {
        scope.launch {
            try {
                callback.onStage(VoiceStage.TRANSCRIPT_RECEIVED)
                callback.onDebug(tag, "Transcript: \"$transcript\"")

                val engine = InferenceManager.engine
                if (engine == null) {
                    callback.onStage(VoiceStage.LOADING_MODEL)
                    callback.onError("AI model not loaded yet. Please wait a moment and try again.")
                    callback.onStage(VoiceStage.ERROR)
                    return@launch
                }

                callback.onStage(VoiceStage.GENERATING)
                val capabilityScanner = PackageCapabilityScanner(context)

                val retoPlanner = RetoWorkflowPlanner(
                    engine = engine,
                    capabilityScanner = capabilityScanner
                )

                val result = withContext(Dispatchers.Default) {
                    retoPlanner.generateWorkflow(
                        userRequest = transcript,
                        onTrace = { trace: RetoTrace ->
                            callback.onDebug("RETO", trace.toString())
                        },
                        onPhase = { label: String, detail: String ->
                            callback.onDebug(label, detail)
                        }
                    )
                }

                val jsonRaw = result.workflowJsonRaw
                val analysis = com.gemmaworkflow.domain.planner.RequestAnalysisParser.parse(result.analysisRaw)

                callback.onStage(VoiceStage.VALIDATING)

                val workflow = WorkflowJsonParser.parse(jsonRaw)
                val resolvableIds = capabilityScanner.resolvableActions(
                    com.gemmaworkflow.domain.catalog.ActionSpecRegistry.allIds
                )
                val errors = WorkflowValidator.validate(workflow, resolvableIds)

                callback.onDebug(tag, "Parsed: ${workflow.name} with ${workflow.actions.size} actions")
                if (errors.isNotEmpty()) {
                    errors.forEach { callback.onDebug(tag, "Validation error: $it") }
                }

                callback.onStage(VoiceStage.DONE)
                callback.onWorkflowReady(workflow, jsonRaw, errors)

            } catch (e: Exception) {
                Log.e(tag, "Voice workflow generation failed", e)
                callback.onError(e.message ?: "Generation failed")
                callback.onStage(VoiceStage.ERROR)
            }
        }
    }
}
