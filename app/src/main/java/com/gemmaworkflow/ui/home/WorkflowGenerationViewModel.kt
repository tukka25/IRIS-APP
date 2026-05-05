package com.gemmaworkflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.WorkflowStatus
import com.gemmaworkflow.domain.planner.PlannerResult
import com.gemmaworkflow.domain.planner.PlannerService
import com.gemmaworkflow.domain.runner.IntentDispatcher
import com.gemmaworkflow.domain.runner.UrlDispatcher
import com.gemmaworkflow.domain.runner.WorkflowRunner
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import com.gemmaworkflow.platform.inference.InferenceManager
import com.gemmaworkflow.platform.inference.InferenceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkflowGenerationViewModel(application: Application) : AndroidViewModel(application) {

    private val capabilityScanner = PackageCapabilityScanner(application)
    private val json = Json { encodeDefaults = true; prettyPrint = true }

    // Saved workflows (in-memory for MVP)
    private val savedWorkflows = mutableMapOf<String, PlannedWorkflow>()

    private val _uiState = MutableStateFlow(WorkflowGenerationUiState())
    val uiState: StateFlow<WorkflowGenerationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            InferenceManager.inferenceState.collect { state ->
                _uiState.update {
                    it.copy(
                        inferenceState = state,
                        isModelReady = state is InferenceState.Ready
                    )
                }
            }
        }
        viewModelScope.launch {
            InferenceManager.initialize(application)
        }
    }

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun generate() {
        viewModelScope.launch(Dispatchers.Default) {
            val prompt = uiState.value.prompt
            val engine = InferenceManager.engine ?: run {
                _uiState.update { it.copy(error = "Model not loaded yet") }
                return@launch
            }

            _uiState.update {
                it.copy(isBusy = true, error = null, stage = "Analysing request\u2026",
                    workflowPreview = null, rawJson = null, validationErrors = emptyList())
            }

            val plannerService = PlannerService(engine, capabilityScanner)
            when (val result = plannerService.plan(prompt)) {
                is PlannerResult.Success -> _uiState.update {
                    it.copy(isBusy = false, stage = "Done", workflowPreview = result.workflow,
                        rawJson = result.workflow.rawModelOutput, validationErrors = emptyList())
                }
                is PlannerResult.Failure -> _uiState.update {
                    it.copy(isBusy = false, stage = "Validation failed",
                        workflowPreview = result.workflow, rawJson = result.workflow.rawModelOutput,
                        validationErrors = result.errors)
                }
            }
        }
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
                    stage = if (results.all { r -> r.success }) "All steps completed"
                            else "Some steps failed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
