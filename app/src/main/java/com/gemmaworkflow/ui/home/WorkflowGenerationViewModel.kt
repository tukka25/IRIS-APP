package com.gemmaworkflow.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.parser.WorkflowJsonParser
import com.gemmaworkflow.domain.planner.PlannerAgents
import com.gemmaworkflow.domain.planner.PromptBuilder
import com.gemmaworkflow.domain.planner.RequestAnalysisParser
import com.gemmaworkflow.domain.runner.WorkflowRunner
import com.gemmaworkflow.domain.safety.WorkflowValidator
import com.gemmaworkflow.platform.capability.IntentDiscoveryEngine
import com.gemmaworkflow.platform.capability.PackageCapabilityScanner
import com.gemmaworkflow.platform.inference.InferenceManager
import com.gemmaworkflow.platform.inference.InferenceState
import com.gemmaworkflow.platform.tools.AgentToolAssignments
import com.gemmaworkflow.platform.tools.FindSkill
import com.gemmaworkflow.platform.tools.ToolAwareGenerator
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

            val agents = PlannerAgents(engine)
            val installedAppsSummary = capabilityScanner.installedAppsPromptSummary()
            val resolvableIds = capabilityScanner.resolvableActions(ActionSpecRegistry.allIds)
            val availableActions = ActionSpecRegistry.all.filter { it.id in resolvableIds }
            val capabilitySummary = ActionSpecRegistry.toPromptSummary(availableActions)

            // Load curated intent catalog + runtime discovery
            val intentCatalog = IntentDiscoveryEngine.loadCatalog(getApplication())
            val intentSummary = IntentDiscoveryEngine.buildSlmPromptSummary(getApplication())
            val combinedCapabilities = buildString {
                appendLine(intentSummary)
                appendLine()
                appendLine("=== High-level Actions (ONLY pick from these IDs) ===")
                append(capabilitySummary)
            }

            appendDebug("Available tools", availableActions.joinToString { it.id })
            appendDebug("Installed app list sent to AI", installedAppsSummary)
            appendDebug("Intent catalog", "${intentCatalog.apps.size} apps, ${intentCatalog.standardIntents.size} standard intents loaded")

            try {
                // Stage 1 — RequestAnalysisAgent (temporal + device tools)
                markStage(0, StageStatus.Running)
                _uiState.update { it.copy(stage = "Analysing request...") }
                val analysisTools = AgentToolAssignments.forAgent(AgentToolAssignments.PlannerAgent.RequestAnalysis)
                val analysisPrompt = PromptBuilder.buildRequestAnalysisPrompt(
                    userRequest = prompt,
                    installedApps = installedAppsSummary
                ) + "\n\n" + FindSkill.schemaFor(analysisTools) + "\n\nYou may call tools using: TOOL: name {params}"
                val analysisRaw = withContext(Dispatchers.Default) {
                    agents.requestAnalysis(
                        prompt = analysisPrompt,
                        allowedTools = analysisTools,
                        onToolEvent = { event -> appendToolDebug(event) }
                    )
                }
                // Yield to main thread so Compose can render
                delay(16)
                recordTokenUsage("Request Analysis", analysisPrompt.length)
                appendDebug("AI output: request analysis", analysisRaw)
                val analysis = RequestAnalysisParser.parse(analysisRaw)
                val triggerHint = analysis.normalizedTriggerHint
                appendDebug("Parsed analysis goal", analysis.goal)
                appendDebug("Trigger hint", triggerHint)
                appendDebug("Applications from request", analysis.applications.joinToString {
                    "${it.requestedName} -> ${it.selectedAppLabel} (${it.packageName}, ${it.confidence})"
                }.ifBlank { "none" })
                appendDebug("Candidate categories", analysis.candidateAppCategories.joinToString().ifBlank { "none" })
                appendDebug("Missing info", analysis.missingInfo.joinToString().ifBlank { "none" })
                markStage(0, StageStatus.Done)
                delay(16)

                // Stage 2 (deterministic, no model call)
                markStage(1, StageStatus.Running)
                _uiState.update { it.copy(stage = "Grounding capabilities...") }
                val nativeDiscovery = capabilityScanner.nativeDiscoverySummary(
                    requestedApplications = analysis.applicationSearchTerms,
                    availableActionIds = resolvableIds
                )
                appendDebug("Native discovery", nativeDiscovery)
                appendDebug("Full capabilities sent to AI", combinedCapabilities.take(200) + "...")
                markStage(1, StageStatus.Done)

                // Stage 3 — ActionPlanAgent (temporal + search + execution + reasoning)
                markStage(2, StageStatus.Running)
                _uiState.update { it.copy(stage = "Planning actions...") }
                val actionTools = AgentToolAssignments.forAgent(AgentToolAssignments.PlannerAgent.ActionPlan)
                val actionPlanPrompt = PromptBuilder.buildActionPlanPrompt(
                    goal = prompt,
                    triggerHint = triggerHint,
                    availableActions = combinedCapabilities,
                    nativeDiscovery = nativeDiscovery
                ) + "\n\n" + FindSkill.schemaFor(actionTools) + "\n\nYou may call tools using: TOOL: name {params}"
                val actionPlanRaw = withContext(Dispatchers.Default) {
                    agents.actionPlan(
                        prompt = actionPlanPrompt,
                        allowedTools = actionTools,
                        onToolEvent = { event -> appendToolDebug(event) }
                    )
                }
                delay(16)
                recordTokenUsage("Action Plan", actionPlanPrompt.length)
                appendDebug("AI output: action plan", actionPlanRaw)
                markStage(2, StageStatus.Done)
                delay(16)

                // Stage 4 — WorkflowJsonAgent (validation only)
                markStage(3, StageStatus.Running)
                _uiState.update { it.copy(stage = "Generating JSON...") }
                val jsonTools = AgentToolAssignments.forAgent(AgentToolAssignments.PlannerAgent.WorkflowJson)
                val jsonPrompt = PromptBuilder.buildWorkflowJsonPrompt(prompt, actionPlanRaw, capabilitySummary) +
                    "\n\n" + FindSkill.schemaFor(jsonTools) + "\n\nYou may call tools using: TOOL: name {params}"
                val jsonRaw = withContext(Dispatchers.Default) {
                    agents.workflowJson(
                        prompt = jsonPrompt,
                        allowedTools = jsonTools,
                        onToolEvent = { event -> appendToolDebug(event) }
                    )
                }
                delay(16)
                recordTokenUsage("JSON Generation", jsonPrompt.length)
                appendDebug("AI output: final workflow JSON", jsonRaw)
                markStage(3, StageStatus.Done)
                delay(16)

                // Parse + validate
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

    /** Estimate tokens from char count. ~4 chars per token for English text. */
    private fun estimateTokens(chars: Int): Int = (chars / 4).coerceAtLeast(1)

    private fun recordTokenUsage(stageLabel: String, promptChars: Int) {
        _uiState.update { state ->
            state.copy(
                stageTokenUsage = state.stageTokenUsage + StageTokenUsage(
                    stageLabel = stageLabel,
                    inputChars = promptChars,
                    estimatedTokens = estimateTokens(promptChars)
                )
            )
        }
    }

    /**
     * Colored tool call debugging.
     * Uses emoji prefixes for visual distinction in the debug panel:
     *   🔧 CALL    — tool is about to be invoked
     *   ✅ RESULT  — tool succeeded
     *   ❌ FAILED  — tool returned an error
     *   🚫 DENIED  — tool not in agent's allowed set
     */
    private fun appendToolDebug(event: ToolAwareGenerator.ToolCallEvent) {
        val (emoji, label, detail) = when (event.type) {
            ToolAwareGenerator.ToolCallEventType.CALL -> Triple(
                "\uD83D\uDD27", "CALL", "${event.toolName} ${event.input}"
            )
            ToolAwareGenerator.ToolCallEventType.SUCCESS -> Triple(
                "\u2705", "RESULT", "${event.toolName}\n${event.output.take(200)}"
            )
            ToolAwareGenerator.ToolCallEventType.FAILURE -> Triple(
                "\u274C", "FAILED", "${event.toolName}: ${event.output.take(200)}"
            )
            ToolAwareGenerator.ToolCallEventType.DENIED -> Triple(
                "\uD83D\uDEAB", "DENIED", event.output
            )
        }
        appendDebug(emoji, "$label $detail")
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
