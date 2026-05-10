package com.gemmaworkflow.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaworkflow.data.repository.ExecutionHistoryRepository
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.data.seed.DemoWorkflowSeeder
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.SharedContent
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.parser.WorkflowJsonParser
import com.gemmaworkflow.domain.planner.PlannerAgents
import com.gemmaworkflow.domain.planner.PromptBuilder
import com.gemmaworkflow.domain.planner.RequestAnalysisParser
import com.gemmaworkflow.domain.runner.ConfirmationRequired
import com.gemmaworkflow.domain.runner.WorkflowRunner
import com.gemmaworkflow.domain.safety.WorkflowValidator
import com.gemmaworkflow.domain.triggers.TriggerRegistry
import com.gemmaworkflow.domain.triggers.TriggerRegistrationResult
import com.gemmaworkflow.platform.alarm.TimeTriggerScheduler
import com.gemmaworkflow.widget.WorkflowWidgetGlance
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
    private val workflowRepo = WorkflowRepository(application)
    private val historyRepo = ExecutionHistoryRepository(application)
    private var timerJob: Job? = null
    /** The active runner, retained across confirmation pauses. */
    private var currentRunner: WorkflowRunner? = null

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
        TriggerRegistry.initialize(application)
        viewModelScope.launch(Dispatchers.IO) {
            DemoWorkflowSeeder.seedIfNeeded(application, workflowRepo)
            val saved = workflowRepo.loadAll()
            val (summaries, activity) = buildHistoryState(saved)
            _uiState.update { it.copy(savedWorkflows = saved, workflowSummaries = summaries, recentActivity = activity) }
        }
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

            val combinedCapabilities = buildString {
                appendLine("=== Available Android Actions ===")
                appendLine("You may select these by their exact action ID. Parameters are exactly as listed.")
                appendLine()
                append(capabilitySummary)
            }

            appendDebug("Available tools", availableActions.joinToString { it.id })
            appendDebug("Installed app list sent to AI", installedAppsSummary)

            try {
                // Stage 1
                markStage(0, StageStatus.Running)
                _uiState.update { it.copy(stage = "Analysing request...") }
                val analysisRaw = withContext(Dispatchers.Default) {
                    agents.requestAnalysis(
                        PromptBuilder.buildRequestAnalysisPrompt(
                            userRequest = prompt,
                            installedApps = installedAppsSummary
                        )
                    )
                }
                delay(16)
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
                delay(16)

                // Stage 3
                markStage(2, StageStatus.Running)
                _uiState.update { it.copy(stage = "Planning actions...") }
                val actionPlanRaw = withContext(Dispatchers.Default) {
                    agents.actionPlan(
                        PromptBuilder.buildActionPlanPrompt(
                            goal = prompt,
                            triggerHint = triggerHint,
                            availableActions = combinedCapabilities,
                            nativeDiscovery = nativeDiscovery
                        )
                    )
                }
                delay(16)
                appendDebug("AI output: action plan", actionPlanRaw)
                markStage(2, StageStatus.Done)
                delay(16)

                // Stage 4
                markStage(3, StageStatus.Running)
                _uiState.update { it.copy(stage = "Generating JSON...") }
                val jsonRaw = withContext(Dispatchers.Default) {
                    agents.workflowJson(
                        PromptBuilder.buildWorkflowJsonPrompt(prompt, actionPlanRaw, capabilitySummary))
                }
                delay(16)
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
                val isSessionError = e.message?.contains("session", ignoreCase = true) == true ||
                    e.message?.contains("FAILED_PRECONDITION", ignoreCase = true) == true
                if (isSessionError) {
                    Log.w(TAG, "Engine session corrupted — will reinit on next generate()")
                    InferenceManager.close()
                }
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
        viewModelScope.launch(Dispatchers.IO) {
            workflowRepo.save(workflow)
            appendDebug("Save workflow", "Saved '${workflow.name}' to disk")

            // Register the trigger (schedules AlarmManager for time triggers).
            val triggerType = when (workflow.trigger) {
                is com.gemmaworkflow.domain.model.TriggerConfig.Time -> "time"
                is com.gemmaworkflow.domain.model.TriggerConfig.Nfc -> "nfc"
                is com.gemmaworkflow.domain.model.TriggerConfig.ShareSheet -> "share_sheet"
                is com.gemmaworkflow.domain.model.TriggerConfig.TaskerRequired -> "tasker_setup_required"
                is com.gemmaworkflow.domain.model.TriggerConfig.Manual -> "manual"
            }
            val result = TriggerRegistry.register(workflow.name, triggerType, workflow.trigger)
            if (result.success) {
                appendDebug("TriggerRegistry", "Registered '$workflow.name' with trigger '$triggerType'")
            } else {
                appendDebug("TriggerRegistry", "Could not register trigger '$triggerType': ${result.message}")
            }

            _uiState.update { it.copy(saved = true) }
        }
    }

    fun runWorkflow() {
        runWorkflow(uiState.value.workflowPreview ?: return)
    }

    fun runWorkflow(workflow: PlannedWorkflow) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isBusy = true, runResults = emptyList(), runningWorkflow = workflow) }
            val runner = WorkflowRunner(context = getApplication())
            currentRunner = runner
            appendDebug("Runner", "Running '${workflow.name}' with ${workflow.actions.size} actions")
            runWorkflowWithRunner(runner, workflow, startIndex = 0)
        }
    }

    private suspend fun runWorkflowWithRunner(
        runner: WorkflowRunner,
        workflow: PlannedWorkflow,
        startIndex: Int,
        initialResults: List<com.gemmaworkflow.domain.model.ExecutionResult> = emptyList()
    ) {
        try {
            val results = initialResults + runner.run(workflow, startIndex) { label, message ->
                appendDebug(label, message)
            }
            historyRepo.log(workflow.name, results)
            WorkflowWidgetGlance.updateAll(getApplication())
            currentRunner = null
            val saved = workflowRepo.loadAll()
            val (summaries, activity) = buildHistoryState(saved)
            _uiState.update {
                it.copy(isBusy = false, saved = true, runResults = results,
                    pendingConfirmation = null, runningWorkflow = null,
                    savedWorkflows = saved, workflowSummaries = summaries, recentActivity = activity,
                    stage = if (results.all { r -> r.success }) "All steps completed" else "Some steps failed")
            }
        } catch (e: ConfirmationRequired) {
            val spec = ActionSpecRegistry.find(e.step.id)
            val stepLabel = spec?.label ?: e.step.id
            val params: Map<String, String> = e.step.params.entries.associate { it.key to it.value.toString() }
            currentRunner = runner
            appendDebug("Runner", "Confirmation required for step ${e.stepIndex}: ${e.step.id}")
            _uiState.update {
                it.copy(
                    isBusy = false, // Stop progress bar while waiting for user
                    pendingConfirmation = ConfirmationRequest(
                        stepId = e.step.id,
                        stepLabel = stepLabel,
                        params = params
                    ),
                    resumeStepIndex = e.stepIndex
                )
            }
        }
    }

    /**
     * Called when the user confirms the pending step — clears the pending confirmation
     * and resumes execution from the saved [WorkflowGenerationUiState.resumeStepIndex].
     */
    fun confirmPending() {
        val runner = currentRunner ?: return
        val workflow = uiState.value.runningWorkflow ?: return
        val resumeIndex = uiState.value.resumeStepIndex
        // Mark the step as confirmed so resume doesn't re-throw ConfirmationRequired
        runner.confirmPendingStep()
        // Consume the confirmation immediately so the dialog closes right away
        _uiState.update { it.copy(pendingConfirmation = null, isBusy = true) }
        viewModelScope.launch(Dispatchers.Default) {
            runWorkflowWithRunner(runner, workflow, startIndex = resumeIndex)
        }
    }

    /**
     * Called when the user dismisses the pending step — skips that step and continues
     * with the next one.
     */
    fun dismissPending() {
        val runner = currentRunner ?: return
        val workflow = uiState.value.runningWorkflow ?: return
        val resumeIndex = uiState.value.resumeStepIndex
        // Mark as dismissed so resume skips this step without re-throwing
        runner.dismissPendingStep()
        _uiState.update { it.copy(pendingConfirmation = null, isBusy = true) }
        viewModelScope.launch(Dispatchers.Default) {
            // Record the skipped step and resume from the next index
            val skippedResults = listOf(
                com.gemmaworkflow.domain.model.ExecutionResult(
                    stepId = workflow.actions.getOrNull(resumeIndex)?.id ?: "",
                    success = false,
                    message = "Skipped by user"
                )
            )
            runWorkflowWithRunner(runner, workflow, startIndex = resumeIndex + 1, initialResults = skippedResults)
        }
    }

    fun selectWorkflow(workflow: PlannedWorkflow) {
        _uiState.update {
            it.copy(
                workflowPreview = workflow,
                rawJson = null,
                validationErrors = emptyList(),
                saved = true,
                runResults = emptyList(),
                error = null,
                selectedWorkflowName = workflow.name
            )
        }
    }

    fun loadWorkflowDetail(workflowId: String) {
        val workflow = uiState.value.savedWorkflows.find { it.name == workflowId }
        _uiState.update { it.copy(selectedWorkflowDetail = workflow) }
    }

    fun clearWorkflowDetail() {
        _uiState.update { it.copy(selectedWorkflowDetail = null) }
    }

    // ── NFC trigger setup ───────────────────────────────────────────────────

    fun openNfcSetup() {
        _uiState.update {
            it.copy(
                showNfcSetup = true,
                nfcWriteState = NfcWriteState.Idle,
                nfcWriteWorkflowId = null
            )
        }
    }

    fun closeNfcSetup() {
        _uiState.update {
            it.copy(
                showNfcSetup = false,
                nfcWriteState = NfcWriteState.Idle,
                nfcWriteWorkflowId = null
            )
        }
    }

    fun onNfcWorkflowSelected(workflowId: String) {
        _uiState.update { it.copy(nfcWriteWorkflowId = workflowId) }
    }

    fun onNfcWriteRequested() {
        val workflowId = uiState.value.nfcWriteWorkflowId ?: return
        _uiState.update { it.copy(nfcWriteState = NfcWriteState.AwaitingTag(workflowId)) }
    }

    fun onNfcTagWritten(workflowId: String) {
        _uiState.update { it.copy(nfcWriteState = NfcWriteState.Success(workflowId)) }
    }

    fun onNfcTagWriteError(workflowId: String, message: String) {
        _uiState.update { it.copy(nfcWriteState = NfcWriteState.Error(workflowId, message)) }
    }

    /**
     * Called by MainActivity when an NFC tag scan has been parsed and the
     * user needs to confirm before running the workflow.
     */
    fun onNfcTagScanned(workflowId: String) {
        val workflow = uiState.value.savedWorkflows.find { it.name == workflowId }
        if (workflow != null) {
            _uiState.update {
                it.copy(nfcScanConfirmation = NfcScanConfirmation(workflowId, workflow.name))
            }
        }
    }

    /**
     * User confirmed the NFC scan — run the workflow directly without going
     * through the detail screen.
     */
    fun confirmNfcScan() {
        val confirmation = uiState.value.nfcScanConfirmation ?: return
        val workflow = uiState.value.savedWorkflows.find { it.name == confirmation.workflowId }
            ?: return
        _uiState.update { it.copy(nfcScanConfirmation = null) }
        runWorkflow(workflow)
    }

    /**
     * User dismissed the NFC scan confirmation.
     */
    fun dismissNfcScan() {
        _uiState.update { it.copy(nfcScanConfirmation = null) }
    }

    /** Show the NFC tag setup screen for a given workflow. */
    fun showNfcSetup(workflowId: String? = null) {
        _uiState.update {
            it.copy(
                showNfcSetup = true,
                nfcWriteWorkflowId = workflowId,
                nfcWriteState = NfcWriteState.Idle
            )
        }
    }

    /** Hide the NFC setup screen and return to the main screen. */
    fun hideNfcSetup() {
        _uiState.update { it.copy(showNfcSetup = false) }
    }

    /** Called by MainActivity after a tag write attempt. */
    fun onNfcWriteResult(success: Boolean, message: String) {
        val workflowId = uiState.value.nfcWriteWorkflowId ?: return
        _uiState.update {
            it.copy(
                nfcWriteState = if (success) {
                    NfcWriteState.Success(workflowId)
                } else {
                    NfcWriteState.Error(workflowId, message)
                }
            )
        }
    }

    /**
     * Show the time trigger setup screen for a given workflow.
     */
    fun showTimeTriggerSetup(workflow: PlannedWorkflow) {
        _uiState.update {
            it.copy(
                timeTriggerSetupWorkflow = workflow,
                selectedWorkflowDetail = null // Hide detail screen to show setup
            )
        }
    }

    /**
     * Save a time trigger for the workflow and schedule it via AlarmManager.
     */
    fun saveTimeTrigger(workflowName: String, trigger: com.gemmaworkflow.domain.model.TriggerConfig.Time) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(getApplication())
            val scheduler = TimeTriggerScheduler(getApplication())
            val workflow = repo.get(workflowName) ?: return@launch

            // Update the workflow's trigger in-place.
            val updatedWorkflow = workflow.copy(trigger = trigger)
            repo.save(updatedWorkflow)

            // Schedule the alarm.
            scheduler.schedule(workflowName, trigger)

            // Refresh saved workflows and clear the setup screen.
            val saved = repo.loadAll()
            _uiState.update {
                it.copy(
                    savedWorkflows = saved,
                    selectedWorkflowDetail = if (it.selectedWorkflowDetail?.name == workflowName) {
                        updatedWorkflow
                    } else {
                        it.selectedWorkflowDetail
                    },
                    timeTriggerSetupWorkflow = null
                )
            }
            appendDebug("Time trigger", "Scheduled for '$workflowName' at %d:%02d".format(trigger.hour, trigger.minute))
        }
    }

    /**
     * Dismiss the time trigger setup screen without saving.
     */
    fun cancelTimeTriggerSetup() {
        _uiState.update { it.copy(timeTriggerSetupWorkflow = null) }
    }

    /**
     * Show the share sheet trigger setup screen for a given workflow.
     */
    fun showShareSheetSetup(workflow: PlannedWorkflow) {
        _uiState.update {
            it.copy(
                shareSheetSetupWorkflow = workflow,
                selectedWorkflowDetail = null // Hide detail screen to show setup
            )
        }
    }

    /**
     * Save a Share Sheet trigger for the workflow and reload the saved list.
     */
    fun saveShareSheetTrigger(workflowName: String, trigger: com.gemmaworkflow.domain.model.TriggerConfig.ShareSheet) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(getApplication())
            val workflow = repo.get(workflowName) ?: return@launch

            val updatedWorkflow = workflow.copy(trigger = trigger)
            repo.save(updatedWorkflow)

            val saved = repo.loadAll()
            _uiState.update {
                it.copy(
                    savedWorkflows = saved,
                    selectedWorkflowDetail = if (it.selectedWorkflowDetail?.name == workflowName) {
                        updatedWorkflow
                    } else {
                        it.selectedWorkflowDetail
                    },
                    shareSheetSetupWorkflow = null
                )
            }
            appendDebug("ShareSheet trigger", "Enabled for '$workflowName'")
        }
    }

    /**
     * Dismiss the share sheet trigger setup screen without saving.
     */
    fun cancelShareSheetSetup() {
        _uiState.update { it.copy(shareSheetSetupWorkflow = null) }
    }

    /** Called by MainActivity when the app receives an ACTION_SEND intent. */
    fun setSharedContent(content: SharedContent) {
        _uiState.update { it.copy(sharedContent = content) }
    }

    /** Clears the pending shared content without running anything. */
    fun clearSharedContent() {
        _uiState.update { it.copy(sharedContent = null) }
    }

    /**
     * Called when the user selects a workflow from the share sheet picker.
     * Pre-loads the workflow detail and clears the share sheet state so the
     * main/detail screen renders next.
     */
    fun selectWorkflowFromShare(workflow: PlannedWorkflow, sharedContent: SharedContent) {
        // Inject shared content as prompt context — the detail screen will
        // then show the workflow so the user can confirm before running.
        val promptHint = when (sharedContent) {
            is SharedContent.Text -> sharedContent.text.take(200)
            is SharedContent.Image -> "[Image: ${sharedContent.uri.lastPathSegment ?: sharedContent.uri}]"
        }
        _uiState.update {
            it.copy(
                sharedContent = null,
                selectedWorkflowDetail = workflow,
                prompt = promptHint
            )
        }
    }

    fun clearPreview() {
        _uiState.update { it.copy(workflowPreview = null, rawJson = null, isBusy = false,
            stage = "", stageTimeline = emptyList(), error = null, runResults = emptyList(), saved = false) }
    }

    private fun buildHistoryState(
        workflows: List<PlannedWorkflow>
    ): Pair<Map<String, WorkflowRunSummary>, List<RecentRun>> {
        val summaries = workflows.associate { wf ->
            val history = historyRepo.forWorkflow(wf.name)
            wf.name to WorkflowRunSummary(
                recentHistory = history.takeLast(6).map { it.allSuccess },
                totalRuns = history.size,
                lastRunMillis = history.lastOrNull()?.timestampMillis ?: 0L
            )
        }
        val activity = workflows.flatMap { wf ->
            historyRepo.forWorkflow(wf.name).map { entry ->
                RecentRun(
                    workflowName = wf.name,
                    success = entry.allSuccess,
                    timestampMillis = entry.timestampMillis
                )
            }
        }.sortedByDescending { it.timestampMillis }.take(8)
        return Pair(summaries, activity)
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

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
