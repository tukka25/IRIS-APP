package com.gemmaworkflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaworkflow.domain.catalog.ActionCatalog
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.parser.WorkflowJsonParser
import com.gemmaworkflow.domain.planner.PlannerAgents
import com.gemmaworkflow.domain.planner.PromptBuilder
import com.gemmaworkflow.domain.runner.IntentDispatcher
import com.gemmaworkflow.domain.runner.UrlDispatcher
import com.gemmaworkflow.domain.runner.WorkflowRunner
import com.gemmaworkflow.domain.safety.WorkflowValidator
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import com.gemmaworkflow.platform.inference.InferenceManager
import com.gemmaworkflow.platform.inference.InferenceState
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

    init {
        viewModelScope.launch {
            InferenceManager.inferenceState.collect { state ->
                _uiState.update { it.copy(inferenceState = state, isModelReady = state is InferenceState.Ready) }
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
                    elapsedSeconds = 0)
            }

            val agents = PlannerAgents(engine)
            val resolvableIds = capabilityScanner.resolvableActions(ActionCatalog.allIds)
            val availableActions = ActionCatalog.all.filter { it.id in resolvableIds }
            val capabilitySummary = buildString {
                appendLine("Available actions (ONLY pick from this list):")
                for (a in availableActions) {
                    appendLine("- ${a.id}: ${a.description}")
                }
            }

            try {
                // Stage 1
                markStage(0, StageStatus.Running)
                _uiState.update { it.copy(stage = "Analysing request...") }
                val analysisRaw = withContext(Dispatchers.Default) {
                    agents.requestAnalysis(PromptBuilder.buildRequestAnalysisPrompt(prompt))
                }
                val triggerHint = extractTriggerHint(analysisRaw)
                markStage(0, StageStatus.Done)

                // Stage 2 (deterministic, no model call)
                markStage(1, StageStatus.Running)
                _uiState.update { it.copy(stage = "Grounding capabilities...") }
                markStage(1, StageStatus.Done)

                // Stage 3
                markStage(2, StageStatus.Running)
                _uiState.update { it.copy(stage = "Planning actions...") }
                val actionPlanRaw = withContext(Dispatchers.Default) {
                    agents.actionPlan(
                        PromptBuilder.buildActionPlanPrompt(prompt, triggerHint, capabilitySummary))
                }
                markStage(2, StageStatus.Done)

                // Stage 4
                markStage(3, StageStatus.Running)
                _uiState.update { it.copy(stage = "Generating JSON...") }
                val jsonRaw = withContext(Dispatchers.Default) {
                    agents.workflowJson(
                        PromptBuilder.buildWorkflowJsonPrompt(prompt, actionPlanRaw, capabilitySummary))
                }
                markStage(3, StageStatus.Done)

                // Parse + validate
                _uiState.update { it.copy(stage = "Validating...") }
                val workflow = WorkflowJsonParser.parse(jsonRaw)
                val errors = WorkflowValidator.validate(workflow)

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

    private fun extractTriggerHint(json: String): String {
        return Regex("\"trigger_hint\"\\s*:\\s*\"(\\w+)\"").find(json)?.groupValues?.getOrNull(1) ?: "manual"
    }

    fun saveWorkflow() {
        val workflow = uiState.value.workflowPreview ?: return
        savedWorkflows[workflow.name] = workflow
        _uiState.update { it.copy(saved = true) }
    }

    fun runWorkflow() {
        viewModelScope.launch(Dispatchers.Default) {
            val workflow = uiState.value.workflowPreview ?: return@launch
            _uiState.update { it.copy(isBusy = true, runResults = emptyList()) }
            val runner = WorkflowRunner(
                context = getApplication(),
                intentDispatcher = IntentDispatcher(getApplication()),
                urlDispatcher = UrlDispatcher(getApplication())
            )
            val results = runner.run(workflow)
            _uiState.update {
                it.copy(isBusy = false, saved = true, runResults = results,
                    stage = if (results.all { r -> r.success }) "All steps completed" else "Some steps failed")
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
