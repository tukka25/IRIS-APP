package com.irisapp.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irisapp.data.repository.ExecutionHistoryRepository
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.data.seed.DemoWorkflowSeeder
import com.irisapp.domain.catalog.ActionSpecRegistry
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.SharedContent
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.domain.parser.WorkflowJsonParser
import com.irisapp.domain.planner.RequestAnalysisParser
import com.irisapp.domain.planner.RetoWorkflowPlanner
import com.irisapp.domain.runner.ConfirmationRequired
import com.irisapp.domain.runner.PermissionRequired
import com.irisapp.domain.runner.WorkflowRunner
import com.irisapp.domain.safety.WorkflowValidator
import com.irisapp.domain.triggers.TriggerRegistry
import com.irisapp.domain.triggers.TriggerRegistrationResult
import com.irisapp.platform.alarm.TimeTriggerScheduler
import com.irisapp.widget.WorkflowWidgetGlance
import com.irisapp.platform.capability.PackageCapabilityScanner
import com.irisapp.platform.trigger.BatteryTriggerManager
import com.irisapp.platform.trigger.BluetoothTriggerManager
import com.irisapp.platform.trigger.ChargerTriggerManager
import com.irisapp.platform.trigger.DndTriggerManager
import com.irisapp.platform.trigger.WiFiTriggerManager
import com.irisapp.platform.trigger.AirplaneModeTriggerManager
import com.irisapp.platform.trigger.sound.SoundEventTriggerRegistry
import com.irisapp.platform.location.GeofenceManager
import com.irisapp.platform.inference.InferenceManager
import com.irisapp.platform.inference.InferenceState
import com.irisapp.platform.tools.reto.RetoTrace
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
    private var generationJob: Job? = null
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
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
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
                            com.irisapp.platform.tools.reto.CapabilityBinder.BindingStatus.SUPPORTED -> "✅"
                            com.irisapp.platform.tools.reto.CapabilityBinder.BindingStatus.WORKAROUND -> "🔄"
                            com.irisapp.platform.tools.reto.CapabilityBinder.BindingStatus.UNSUPPORTED -> "❌"
                            com.irisapp.platform.tools.reto.CapabilityBinder.BindingStatus.NEEDS_CLARIFICATION -> "❓"
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
                        com.irisapp.platform.tools.reto.RequirementStatus.RESOLVED -> "✅"
                        com.irisapp.platform.tools.reto.RequirementStatus.FAILED -> "❌"
                        com.irisapp.platform.tools.reto.RequirementStatus.PENDING -> "⏳"
                        else -> "⚠️"
                    }
                    appendDebug("  $emoji ${req.id}", "${req.factType.label} from \"${req.mention}\" via ${req.resolverTool ?: req.factType.resolverTool} args=${req.toolArgs} → ${req.status} (${if (req.blocking) "blocking" else "optional"})")
                }

                appendDebug("══ PHASE 2 — Analysis ══", analysisRaw)
                appendDebug("══ PHASE 3 — Action Plan ══", actionPlanRaw)
                appendDebug("══ PHASE 4 — Final JSON ══", jsonRaw)

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
                val isSessionError = e.message?.contains("session", ignoreCase = true) == true ||
                    e.message?.contains("FAILED_PRECONDITION", ignoreCase = true) == true
                if (isSessionError) {
                    Log.w(TAG, "Engine session corrupted — will reinit on next generate()")
                    InferenceManager.close()
                }
                appendDebug("Generation error", e.stackTraceToString())
                _uiState.update { it.copy(isBusy = false, error = e.message, stage = "Failed") }
            } finally {
                generationJob = null
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
                is com.irisapp.domain.model.TriggerConfig.Time -> "time"
                is com.irisapp.domain.model.TriggerConfig.Nfc -> "nfc"
                is com.irisapp.domain.model.TriggerConfig.ShareSheet -> "share_sheet"
                is com.irisapp.domain.model.TriggerConfig.Manual -> "manual"
                is com.irisapp.domain.model.TriggerConfig.Battery -> {
                    BatteryTriggerManager.registerWorkflow(
                        getApplication(),
                        workflow.name,
                        workflow.trigger as com.irisapp.domain.model.TriggerConfig.Battery
                    )
                    null  // handled — no TriggerRegistry.register call
                }
                is com.irisapp.domain.model.TriggerConfig.Charger -> {
                    val chargerTrigger = workflow.trigger as com.irisapp.domain.model.TriggerConfig.Charger
                    ChargerTriggerManager.registerWorkflow(getApplication(), workflow.name, chargerTrigger)
                    null  // handled — no TriggerRegistry.register call
                }
                is com.irisapp.domain.model.TriggerConfig.WiFi -> {
                    val wifiTrigger = workflow.trigger as com.irisapp.domain.model.TriggerConfig.WiFi
                    WiFiTriggerManager.registerWorkflow(getApplication(), workflow.name, wifiTrigger)
                    null  // handled — no TriggerRegistry.register call
                }
                is com.irisapp.domain.model.TriggerConfig.Bluetooth -> {
                    val bluetoothTrigger = workflow.trigger as com.irisapp.domain.model.TriggerConfig.Bluetooth
                    BluetoothTriggerManager.registerWorkflow(getApplication(), workflow.name, bluetoothTrigger)
                    null  // handled — no TriggerRegistry.register call
                }
                is com.irisapp.domain.model.TriggerConfig.AirplaneMode -> {
                    val airplaneTrigger = workflow.trigger as com.irisapp.domain.model.TriggerConfig.AirplaneMode
                    AirplaneModeTriggerManager.registerWorkflow(getApplication(), workflow.name, airplaneTrigger)
                    null  // handled — no TriggerRegistry.register call
                }
                is com.irisapp.domain.model.TriggerConfig.DoNotDisturb -> {
                    val dndTrigger = workflow.trigger as com.irisapp.domain.model.TriggerConfig.DoNotDisturb
                    DndTriggerManager.registerWorkflow(getApplication(), workflow.name, dndTrigger)
                    null  // handled — no TriggerRegistry.register call
                }
                is com.irisapp.domain.model.TriggerConfig.Geofence -> {
                    val geofenceTrigger = workflow.trigger as com.irisapp.domain.model.TriggerConfig.Geofence
                    com.irisapp.platform.location.GeofenceManager.registerWorkflow(getApplication(), workflow.name, geofenceTrigger)
                    null  // handled — no TriggerRegistry.register call
                }
                is com.irisapp.domain.model.TriggerConfig.AlarmStopped -> null  // handled by AlarmTriggerManager
                is com.irisapp.domain.model.TriggerConfig.AppOpened,
                is com.irisapp.domain.model.TriggerConfig.AppClosed -> null  // handled by AppMonitorAccessibilityService
                is com.irisapp.domain.model.TriggerConfig.SmsReceived -> null  // handled by SmsTriggerManager
                is com.irisapp.domain.model.TriggerConfig.NotificationListenerConfig -> null  // handled by SmsNotificationListener
                is com.irisapp.domain.model.TriggerConfig.EmailReceived -> null  // handled by SmsNotificationListener
                is com.irisapp.domain.model.TriggerConfig.SleepProxy -> null  // handled by SleepTriggerManager
                is com.irisapp.domain.model.TriggerConfig.Voice -> null  // handled by VoiceTriggerHandler
                is com.irisapp.domain.model.TriggerConfig.SoundEvent -> null  // handled by SoundEventTriggerService
            }
            if (triggerType != null) {
                val result = TriggerRegistry.register(workflow.name, triggerType, workflow.trigger)
                if (result.success) {
                    appendDebug("TriggerRegistry", "Registered '$workflow.name' with trigger '$triggerType'")
                } else {
                    appendDebug("TriggerRegistry", "Could not register trigger '$triggerType': ${result.message}")
                }
            }

            val saved = workflowRepo.loadAll()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(saved = true, savedWorkflows = saved) }
            }
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
        initialResults: List<com.irisapp.domain.model.ExecutionResult> = emptyList()
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
        } catch (e: PermissionRequired) {
            val spec = ActionSpecRegistry.find(e.step.id)
            val stepLabel = spec?.label ?: e.step.id
            currentRunner = runner
            appendDebug("Runner", "Permission required for step ${e.stepIndex}: ${e.step.id}: ${e.permissions}")
            _uiState.update {
                it.copy(
                    isBusy = false,
                    pendingPermission = PermissionRequest(
                        stepId = e.step.id,
                        stepLabel = stepLabel,
                        permissions = e.permissions
                    ),
                    resumeStepIndex = e.stepIndex
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution failed", e)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    error = "Execution failed: ${e.message}",
                    pendingConfirmation = null,
                    runningWorkflow = null
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
        _uiState.update { it.copy(pendingConfirmation = null, pendingPermission = null, isBusy = true) }
        viewModelScope.launch(Dispatchers.Default) {
            // Record the skipped step and resume from the next index
            val skippedResults = listOf(
                com.irisapp.domain.model.ExecutionResult(
                    stepId = workflow.actions.getOrNull(resumeIndex)?.id ?: "",
                    success = false,
                    message = "Skipped by user"
                )
            )
            runWorkflowWithRunner(runner, workflow, startIndex = resumeIndex + 1, initialResults = skippedResults)
        }
    }

    /**
     * Called after the user has been prompted for and granted the required permissions.
     * Calls [WorkflowRunner.grantPermissionsAndResume] to verify all permissions are now
     * granted, then resumes execution. If any permission was denied, the step is skipped.
     */
    fun grantPendingPermissions() {
        val runner = currentRunner ?: return
        val workflow = uiState.value.runningWorkflow ?: return
        val resumeIndex = uiState.value.resumeStepIndex
        val context = getApplication<Application>()

        // Clear the permission request UI immediately so the dialog closes
        _uiState.update { it.copy(pendingPermission = null, isBusy = true) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                runner.grantPermissionsAndResume(context)
                // All permissions now granted — resume from the permission step
                runWorkflowWithRunner(runner, workflow, startIndex = resumeIndex)
            } catch (e: PermissionRequired) {
                // Some permissions were still denied — skip the step
                val skippedResults = listOf(
                    com.irisapp.domain.model.ExecutionResult(
                        stepId = workflow.actions.getOrNull(resumeIndex)?.id ?: "",
                        success = false,
                        message = "Permission denied by user"
                    )
                )
                runner.dismissPendingStep()
                runWorkflowWithRunner(runner, workflow, startIndex = resumeIndex + 1, initialResults = skippedResults)
            }
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

    /** Set the raw JSON for the currently selected workflow preview (used by voice trigger). */
    fun setRawJson(rawJson: String) {
        _uiState.update { it.copy(rawJson = rawJson) }
    }

    fun loadWorkflowDetail(workflowId: String) {
        val workflow = uiState.value.savedWorkflows.find { it.name == workflowId }
        _uiState.update { it.copy(selectedWorkflowDetail = workflow) }
    }

    fun clearWorkflowDetail() {
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = workflowRepo.loadAll()
            _uiState.update {
                it.copy(
                    selectedWorkflowDetail = null,
                    savedWorkflows = fresh
                )
            }
        }
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
    fun saveTimeTrigger(workflowName: String, trigger: com.irisapp.domain.model.TriggerConfig.Time) {
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
    fun saveShareSheetTrigger(workflowName: String, trigger: com.irisapp.domain.model.TriggerConfig.ShareSheet) {
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

    /**
     * Show the Sound Event trigger setup screen for a workflow.
     */
    fun showSoundEventTriggerSetup(workflow: PlannedWorkflow) {
        _uiState.update {
            it.copy(
                soundEventTriggerSetupWorkflow = workflow,
                selectedWorkflowDetail = null
            )
        }
    }

    /**
     * Save the Sound Event trigger with selected sound classes.
     */
    fun saveSoundEventTrigger(workflowName: String, soundClasses: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = WorkflowRepository(getApplication())
            val workflow = repo.get(workflowName) ?: return@launch

            // Register each sound class → workflow mapping in the registry
            val context = getApplication<Application>()
            soundClasses.forEach { soundClass ->
                SoundEventTriggerRegistry.register(context, soundClass, workflowName)
            }

            val updatedWorkflow = workflow.copy(
                trigger = com.irisapp.domain.model.TriggerConfig.SoundEvent(soundClasses)
            )
            repo.save(updatedWorkflow)

            val saved = repo.loadAll()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        savedWorkflows = saved,
                        soundEventTriggerSetupWorkflow = null,
                        selectedWorkflowDetail = updatedWorkflow
                    )
                }
            }
            appendDebug("SoundEvent trigger", "Saved ${soundClasses.size} sound classes for '$workflowName'")
        }
    }

    /**
     * Dismiss the Sound Event trigger setup screen.
     */
    fun cancelSoundEventTriggerSetup() {
        _uiState.update { it.copy(soundEventTriggerSetupWorkflow = null) }
    }

    /** Open the manual workflow editor for creating a new workflow. */
    fun openNewWorkflowEditor() {
        val placeholder = com.irisapp.domain.model.PlannedWorkflow(
            name = "",
            summary = "",
            trigger = com.irisapp.domain.model.TriggerConfig.Manual,
            actions = emptyList()
        )
        _uiState.update { it.copy(editingWorkflow = placeholder, isNewWorkflow = true) }
    }

    /** Open the manual workflow editor for editing an existing workflow. */
    fun openEditWorkflowEditor(workflow: PlannedWorkflow) {
        _uiState.update { it.copy(editingWorkflow = workflow, isNewWorkflow = false) }
    }

    /** Save a manually-built workflow (from the editor) and close the editor. */
    fun saveEditedWorkflow(workflow: PlannedWorkflow) {
        viewModelScope.launch(Dispatchers.IO) {
            // Preserve rawModelOutput from the original workflow if editing.
            val originalRawOutput = uiState.value.editingWorkflow?.rawModelOutput ?: ""
            val toSave = workflow.copy(rawModelOutput = originalRawOutput)
            workflowRepo.save(toSave)

            // Re-register all trigger types (mirrors saveWorkflow)
            val trigger = toSave.trigger
            when (trigger) {
                is TriggerConfig.Time -> {
                    val result = TriggerRegistry.register(toSave.name, "time", trigger)
                    if (result.success) appendDebug("TriggerRegistry", "Re-registered time trigger")
                }
                is TriggerConfig.Nfc -> {
                    val result = TriggerRegistry.register(toSave.name, "nfc", trigger)
                    if (result.success) appendDebug("TriggerRegistry", "Re-registered NFC trigger")
                }
                is TriggerConfig.ShareSheet -> {
                    val result = TriggerRegistry.register(toSave.name, "share_sheet", trigger)
                    if (result.success) appendDebug("TriggerRegistry", "Re-registered share sheet trigger")
                }
                is TriggerConfig.Manual -> { /* no registration needed */ }
                is TriggerConfig.Battery -> {
                    BatteryTriggerManager.registerWorkflow(getApplication(), toSave.name, trigger)
                    appendDebug("TriggerRegistry", "Re-registered battery trigger")
                }
                is TriggerConfig.Charger -> {
                    ChargerTriggerManager.registerWorkflow(getApplication(), toSave.name, trigger)
                    appendDebug("TriggerRegistry", "Re-registered charger trigger")
                }
                is TriggerConfig.WiFi -> {
                    WiFiTriggerManager.registerWorkflow(getApplication(), toSave.name, trigger)
                    appendDebug("TriggerRegistry", "Re-registered WiFi trigger")
                }
                is TriggerConfig.Bluetooth -> {
                    BluetoothTriggerManager.registerWorkflow(getApplication(), toSave.name, trigger)
                    appendDebug("TriggerRegistry", "Re-registered Bluetooth trigger")
                }
                is TriggerConfig.AirplaneMode -> {
                    AirplaneModeTriggerManager.registerWorkflow(getApplication(), toSave.name, trigger)
                    appendDebug("TriggerRegistry", "Re-registered airplane mode trigger")
                }
                is TriggerConfig.DoNotDisturb -> {
                    DndTriggerManager.registerWorkflow(getApplication(), toSave.name, trigger)
                    appendDebug("TriggerRegistry", "Re-registered DND trigger")
                }
                is TriggerConfig.Geofence -> {
                    GeofenceManager.registerWorkflow(getApplication(), toSave.name, trigger)
                    appendDebug("TriggerRegistry", "Re-registered geofence trigger")
                }
                is TriggerConfig.AlarmStopped -> { /* handled by AlarmTriggerManager */ }
                is TriggerConfig.AppOpened,
                is TriggerConfig.AppClosed -> { /* handled by AppMonitorAccessibilityService */ }
                is TriggerConfig.SmsReceived -> { /* handled by SmsTriggerManager */ }
                is TriggerConfig.NotificationListenerConfig -> { /* handled by SmsNotificationListener */ }
                is TriggerConfig.EmailReceived -> { /* handled by SmsNotificationListener */ }
                is TriggerConfig.SleepProxy -> { /* handled by SleepTriggerManager */ }
                is TriggerConfig.Voice -> { /* handled by VoiceTriggerHandler */ }
                is TriggerConfig.SoundEvent -> {
                    // Register sound class → workflow mappings
                    val appContext = getApplication<Application>()
                    trigger.soundClasses.forEach { soundClass ->
                        SoundEventTriggerRegistry.register(appContext, soundClass, toSave.name)
                    }
                    appendDebug("TriggerRegistry", "Re-registered sound event trigger: ${trigger.soundClasses.size} classes")
                }
            }

            appendDebug("WorkflowGenViewModel", "saveEditedWorkflow completed for '${toSave.name}'")
            val saved = workflowRepo.loadAll()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        savedWorkflows = saved,
                        editingWorkflow = null,
                        isNewWorkflow = false
                    )
                }
            }
        }
    }

    /** Close the manual editor without saving. */
    fun cancelEditWorkflow() {
        _uiState.update { it.copy(editingWorkflow = null, isNewWorkflow = false) }
    }

    /** Switch the bottom tab: 0=Generate, 1=Workflows, 2=Manual Editor */
    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
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
        timerJob?.cancel()
        timerJob = null
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(workflowPreview = null, rawJson = null, isBusy = false,
            stage = "", stageTimeline = emptyList(), error = null, runResults = emptyList(), saved = false) }
    }

    private fun buildHistoryState(
        workflows: List<PlannedWorkflow>
    ): Pair<Map<String, WorkflowRunSummary>, List<RecentRun>> {
        val workflowNames = workflows.map { it.name }.toSet()
        val historyByWorkflow = historyRepo.getAll()
            .groupBy { it.workflowName }

        val summaries = workflows.associate { wf ->
            val history = historyByWorkflow[wf.name].orEmpty()
            wf.name to WorkflowRunSummary(
                recentHistory = history.takeLast(6).map { it.allSuccess },
                totalRuns = history.size,
                lastRunMillis = history.lastOrNull()?.timestampMillis ?: 0L
            )
        }
        val activity = historyByWorkflow
            .filterKeys { it in workflowNames }
            .flatMap { (workflowName, history) ->
                history.map { entry ->
                    RecentRun(
                        workflowName = workflowName,
                        success = entry.allSuccess,
                        timestampMillis = entry.timestampMillis
                    )
                }
            }
            .sortedByDescending { it.timestampMillis }
            .take(8)
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
        generationJob?.cancel()
        super.onCleared()
    }
}
