package com.gemmaworkflow.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.parser.WorkflowJsonParser
import com.gemmaworkflow.domain.planner.RequestAnalysisParser
import com.gemmaworkflow.domain.planner.RetoWorkflowPlanner
import com.gemmaworkflow.domain.runner.WorkflowRunner
import com.gemmaworkflow.domain.safety.WorkflowValidator
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import com.gemmaworkflow.platform.inference.InferenceManager
import com.gemmaworkflow.platform.inference.InferenceState
import com.gemmaworkflow.platform.tools.reto.RetoTrace
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkflowGenerationViewModel(application: Application) : AndroidViewModel(application) {

    private val capabilityScanner = PackageCapabilityScanner(application)
    private val savedWorkflows = mutableMapOf<String, PlannedWorkflow>()
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(WorkflowGenerationUiState())
    val uiState: StateFlow<WorkflowGenerationUiState> = _uiState.asStateFlow()

    private val timelineStages = listOf(
        "Request analysis",
        "Capability grounding",
        "Action plan",
        "Final JSON"
    )

    private companion object {
        const val TAG = "WorkflowGeneration"
    }

    init {
        viewModelScope.launch {
            InferenceManager.inferenceState.collect { state ->
                _uiState.update { it.copy(inferenceState = state, isModelReady = state is InferenceState.Ready) }
                appendDebug("Model", state.toString())
            }
        }
        viewModelScope.launch { InferenceManager.initialize(application) }
    }

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    /**
     * Run the pipeline on the MAIN thread, switching to Default only for
     * inference calls. This prevents the ANR "not responding" dialog.
     */
    fun generate() {
        viewModelScope.launch {
            val prompt = uiState.value.prompt
            val engine = InferenceManager.engine ?: run {
                _uiState.update { it.copy(error = "Model not loaded yet") }
                return@launch
            }

            val startTime = System.currentTimeMillis()
            timerJob = startTimer(startTime)

            val timeline = timelineStages.map { StageProgress(label = it) }
            _uiState.update {
                it.copy(isBusy = true, error = null, stage = "", workflowPreview = null,
                    rawJson = null, validationErrors = emptyList(), stageTimeline = timeline,
                    elapsedSeconds = 0, debugMessages = emptyList())
            }
            appendDebug("User request", prompt)

            val installedAppsSummary = capabilityScanner.installedAppsPromptSummary()
            val resolvableIds = capabilityScanner.resolvableActions(ActionSpecRegistry.allIds)
            val availableActions = ActionSpecRegistry.all.filter { it.id in resolvableIds }

            appendDebug("Available tools", availableActions.joinToString { it.id })
            appendDebug("Installed app list sent to AI", installedAppsSummary)

            try {
                appendDebug("Pipeline", "RETO orchestration active (requirement-led)")

                val retoPlanner = RetoWorkflowPlanner(
                    engine = engine,
                    capabilityScanner = capabilityScanner
                )

                markStage(0, StageStatus.Running)
                markStage(1, StageStatus.Running)
                markStage(2, StageStatus.Running)
                markStage(3, StageStatus.Running)
                _uiState.update { it.copy(stage = "Phase 0: Extracting requirements...") }

                val retoResult = withContext(Dispatchers.Default) {
                    retoPlanner.generateWorkflow(
                        userRequest = prompt,
                        onTrace = { trace -> appendRetoDebug(trace) },
                        onPhase = { label, detail -> appendDebug(label, detail) }
                    )
                }

                (0..3).forEach { markStage(it, StageStatus.Done); delay(8) }

                val analysisRaw = retoResult.analysisRaw
                val actionPlanRaw = retoResult.actionPlanRaw
                val jsonRaw = retoResult.workflowJsonRaw

                appendDebug("══ PHASE 0 — DECOMPOSITION ══", retoResult.debugTrace)

                val decomposition = retoPlanner.lastDecomposition
                if (decomposition != null) {
                    appendDebug("══ TASK DECOMPOSITION ══", "${decomposition.tasks.size} logical tasks identified")
                    decomposition.tasks.forEach { task ->
                        appendDebug("  ${task.id}", "${task.action}: ${task.description} (target: \"${task.target}\")")
                    }
                }

                val binding = retoPlanner.lastBinding
                if (binding != null) {
                    appendDebug("══ CAPABILITY BINDING ══", "${binding.boundActions.size} tasks mapped to actions")
                    binding.boundActions.forEach { b ->
                        val emoji = when (b.status) {
                            com.gemmaworkflow.platform.tools.reto.CapabilityBinder.BindingStatus.SUPPORTED -> "✅"
                            com.gemmaworkflow.platform.tools.reto.CapabilityBinder.BindingStatus.WORKAROUND -> "🔄"
                            com.gemmaworkflow.platform.tools.reto.CapabilityBinder.BindingStatus.UNSUPPORTED -> "❌"
                            com.gemmaworkflow.platform.tools.reto.CapabilityBinder.BindingStatus.NEEDS_CLARIFICATION -> "❓"
                        }
                        appendDebug("  $emoji ${b.taskId}", "${b.taskDescription} → ${b.actionId ?: "unsupported"} (${b.reason.take(60)})")
                    }
                }

                val ledger = retoResult.ledger
                appendDebug("══ REQUIREMENT LEDGER ══", "${ledger.requirements.size} requirements, ${ledger.blockingRequirements.size} blocking, ${ledger.actionCandidates.size} actions")
                ledger.literalSlots.forEach { slot ->
                    appendDebug("  📝 literal ${slot.sourceAction}.${slot.slot}", "${slot.value} (${slot.reason.ifBlank { "from user request" }})")
                }
                ledger.requirements.forEach { req ->
                    val emoji = when (req.status) {
                        com.gemmaworkflow.platform.tools.reto.RequirementStatus.RESOLVED -> "✅"
                        com.gemmaworkflow.platform.tools.reto.RequirementStatus.FAILED -> "❌"
                        com.gemmaworkflow.platform.tools.reto.RequirementStatus.PENDING -> "⏳"
                        else -> "⚠️"
                    }
                    appendDebug("  $emoji ${req.id}", "${req.factType.label} from \"${req.mention}\" via ${req.resolverTool ?: req.factType.resolverTool} args=${req.toolArgs} → ${req.status} (${if (req.blocking) "blocking" else "optional"})")
                }

                appendDebug("══ PHASE 2 — Analysis ══", analysisRaw.take(600))
                appendDebug("══ PHASE 3 — Action Plan ══", actionPlanRaw.take(600))
                appendDebug("══ PHASE 4 — Final JSON ══", jsonRaw.take(600))

                val analysis = RequestAnalysisParser.parse(analysisRaw)
                appendDebug("Parsed analysis goal", analysis.goal)
                appendDebug("Trigger hint", analysis.normalizedTriggerHint)
                appendDebug("Missing info", analysis.missingInfo.joinToString().ifBlank { "none" })

                // Parse + validate (shared between both paths)
                _uiState.update { it.copy(stage = "Validating...") }
                val workflow = WorkflowJsonParser.parse(jsonRaw)
                val errors = WorkflowValidator.validate(workflow, resolvableIds)
                appendDebug("Parsed workflow", "${workflow.name} with ${workflow.actions.size} actions")
                if (errors.isEmpty()) {
                    appendDebug("Validation", "Valid workflow")
                } else {
                    appendDebug("Validation errors", errors.joinToString(separator = "\n"))
                }

                timerJob?.cancel()
                val elapsed = (System.currentTimeMillis() - startTime) / 1000

                _uiState.update {
                    if (errors.isEmpty()) {
                        it.copy(isBusy = false, stage = "Done", elapsedSeconds = elapsed,
                            workflowPreview = workflow, rawJson = jsonRaw, validationErrors = emptyList())
                    } else {
                        it.copy(isBusy = false, stage = "Validation failed", elapsedSeconds = elapsed,
                            workflowPreview = workflow, rawJson = jsonRaw, validationErrors = errors)
                    }
                }
            } catch (e: Exception) {
                timerJob?.cancel()
                Log.e(TAG, "Generation failed", e)
                appendDebug("Generation error", e.stackTraceToString())
                _uiState.update { it.copy(isBusy = false, error = e.message, stage = "Failed") }
            }
        }
    }

    private fun startTimer(startTime: Long): Job {
        return viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000) }
            }
        }
    }

    private fun markStage(index: Int, status: StageStatus) {
        _uiState.update { state ->
            val timeline = state.stageTimeline.toMutableList()
            if (index in timeline.indices) {
                timeline[index] = timeline[index].copy(status = status)
            }
            state.copy(stageTimeline = timeline)
        }
    }

    fun saveWorkflow() {
        val workflow = uiState.value.workflowPreview ?: return
        savedWorkflows[workflow.name] = workflow
        appendDebug("Save workflow", "Saved '${workflow.name}' in memory")
        _uiState.update { it.copy(saved = true) }
    }

    fun runWorkflow() {
        viewModelScope.launch(Dispatchers.Default) {
            val workflow = uiState.value.workflowPreview ?: return@launch
            _uiState.update { it.copy(isBusy = true, runResults = emptyList()) }
            val runner = WorkflowRunner(context = getApplication())
            appendDebug("Runner", "Running '${workflow.name}' with ${workflow.actions.size} actions")
            val results = runner.run(workflow) { label, message ->
                appendDebug(label, message)
            }
            _uiState.update {
                it.copy(isBusy = false, saved = true, runResults = results,
                    stage = if (results.all { r -> r.success }) "All steps completed" else "Some steps failed")
            }
        }
    }

    private fun appendDebug(label: String, message: String) {
        Log.d(TAG, "$label: $message")
        _uiState.update { state ->
            state.copy(
                debugMessages = (state.debugMessages + DebugMessage(label = label, message = message))
                    .takeLast(80)
            )
        }
    }

    /**
     * RETO orchestration trace logging for debug panel.
     * Shows layers, tool calls, observations, and repairs.
     */
    private fun appendRetoDebug(trace: RetoTrace) {
        appendDebug("\uD83D\uDCCB RETO", "${trace.layerSketch.layers.size} layers planned")
        trace.layerSketch.layers.forEach { layer ->
            appendDebug("  Layer ${layer.index}", "${layer.objective} [${layer.allowedTools.joinToString()}]")
        }
        trace.observations.forEachIndexed { i, obs ->
            val emoji = if (obs.success) "\u2705" else "\u274C"
            appendDebug("  $emoji Obs $i", "${obs.toolName} → ${obs.output.take(100)}")
        }
        trace.repairs.forEach { repair ->
            appendDebug("  \uD83D\uDD27 Repair", "${repair.toolName} L${repair.layerIndex} attempt ${repair.attempt}: ${if (repair.success) "fixed" else "failed"}")
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
