package com.gemmaworkflow.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.SharedContent
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.inference.InferenceState
import com.gemmaworkflow.platform.nfc.DeepLink
import com.gemmaworkflow.platform.nfc.DeepLinkRouter
import com.gemmaworkflow.platform.nfc.NfcTriggerWriter
import com.gemmaworkflow.platform.share.ShareSheetTriggerHandler
import com.gemmaworkflow.ui.home.ConfirmationRequest
import com.gemmaworkflow.ui.home.StageStatus
import com.gemmaworkflow.ui.home.TimeTriggerSetupScreen
import com.gemmaworkflow.ui.home.ShareSheetSetupScreen
import com.gemmaworkflow.ui.home.WorkflowGenerationViewModel
import com.gemmaworkflow.ui.nfc.NfcSetupScreen
import com.gemmaworkflow.ui.theme.GemmaWorkflowTheme
import com.gemmaworkflow.ui.trigger.formatTriggerSummary

class MainActivity : ComponentActivity() {
    private val viewModel: WorkflowGenerationViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications will work
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        observeDeepLinkEvents()
        checkNotificationPermission()
        setContent {
            GemmaWorkflowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkflowGenerationScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // Share sheet intent: extract content and show workflow selector
        if (intent.action == Intent.ACTION_SEND) {
            if (intent.type == null) return
            val sharedContent: SharedContent? = when {
                intent.type == "text/plain" || intent.type?.startsWith("text/") == true -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                        SharedContent.Text(text = text)
                    }
                }
                intent.type?.startsWith("image/") == true -> {
                    intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                        SharedContent.Image(uri = uri, type = intent.type ?: "image/*")
                    }
                }
                else -> null
            }
            if (sharedContent != null) {
                viewModel.setSharedContent(sharedContent)
            }
            return
        }

        // Action from ShareSheetTriggerHandler notification: run named workflow with pending share
        if (intent.action == ShareSheetTriggerHandler.ACTION_RUN_SHARE_WORKFLOW) {
            val workflowName = intent.getStringExtra(ShareSheetTriggerHandler.EXTRA_WORKFLOW_NAME)
            val sharedText = intent.getStringExtra(ShareSheetTriggerHandler.EXTRA_SHARED_TEXT)
            val sharedUri = intent.getStringExtra(ShareSheetTriggerHandler.EXTRA_SHARED_URI)

            if (workflowName != null) {
                val content: SharedContent? = when {
                    !sharedText.isNullOrBlank() -> SharedContent.Text(text = sharedText)
                    !sharedUri.isNullOrBlank() -> SharedContent.Image(uri = android.net.Uri.parse(sharedUri))
                    else -> null
                }
                if (content != null) {
                    val workflow = viewModel.uiState.value.savedWorkflows.find { it.name == workflowName }
                    if (workflow != null) {
                        viewModel.selectWorkflowFromShare(workflow, content)
                    }
                }
            }
            return
        }

        // Action from ShareSheetTriggerHandler notification: open share selector UI
        if (intent.action == ShareSheetTriggerHandler.ACTION_SHOW_SHARE_SELECTOR) {
            val sharedText = intent.getStringExtra(ShareSheetTriggerHandler.EXTRA_SHARED_TEXT)
            val sharedUri = intent.getStringExtra(ShareSheetTriggerHandler.EXTRA_SHARED_URI)
            val content: SharedContent? = when {
                !sharedText.isNullOrBlank() -> SharedContent.Text(text = sharedText)
                !sharedUri.isNullOrBlank() -> SharedContent.Image(uri = android.net.Uri.parse(sharedUri))
                else -> null
            }
            if (content != null) {
                viewModel.setSharedContent(content)
            }
            return
        }

        // Action from ShareSheetTriggerHandler notification: just show the shared content
        if (intent.action == ShareSheetTriggerHandler.ACTION_SHOW_SHARE_CONTENT) {
            val sharedText = intent.getStringExtra(ShareSheetTriggerHandler.EXTRA_SHARED_TEXT)
            val sharedUri = intent.getStringExtra(ShareSheetTriggerHandler.EXTRA_SHARED_URI)
            val content: SharedContent? = when {
                !sharedText.isNullOrBlank() -> SharedContent.Text(text = sharedText)
                !sharedUri.isNullOrBlank() -> SharedContent.Image(uri = android.net.Uri.parse(sharedUri))
                else -> null
            }
            if (content != null) {
                viewModel.setSharedContent(content)
            }
            return
        }

        // Handle ACTION_RUN_WORKFLOW from TimeTriggerConfirmationActivity "Run Now" shortcut.
        if (intent.action == DeepLinkRouter.ACTION_RUN_WORKFLOW) {
            val workflowName = intent.getStringExtra(DeepLinkRouter.EXTRA_WORKFLOW_ID)
            if (workflowName != null) {
                val workflow = viewModel.uiState.value.savedWorkflows.find { it.name == workflowName }
                if (workflow != null) {
                    viewModel.runWorkflow(workflow)
                }
            }
            return
        }
    }

    /**
     * Observe [DeepLinkRouter.deepLinkEvents] and handle deep-link navigation.
     * Used primarily for [DeepLink.ShowDetail] emitted by [TimeTriggerConfirmationActivity]
     * when the user taps "View" from the time-trigger confirmation screen.
     */
    private fun observeDeepLinkEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                DeepLinkRouter.deepLinkEvents.collect { deepLink ->
                    when (deepLink) {
                        is DeepLink.ShowDetail -> {
                            val workflow = viewModel.uiState.value.savedWorkflows
                                .find { it.name == deepLink.workflowId }
                            if (workflow != null) {
                                viewModel.loadWorkflowDetail(workflow.name)
                            }
                        }
                        is DeepLink.RunWorkflow -> {
                            val workflow = viewModel.uiState.value.savedWorkflows
                                .find { it.name == deepLink.workflowId }
                            if (workflow != null) {
                                viewModel.runWorkflow(workflow)
                            }
                        }
                        is DeepLink.NfcScan -> {
                            // Already handled via NFC trigger flow; no action needed here.
                        }
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun WorkflowGenerationScreen(viewModel: WorkflowGenerationViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Show a confirmation dialog whenever a step requires user consent before executing.
    // This is placed before the early return so it shows even on the detail screen.
    state.pendingConfirmation?.let { request ->
        ConfirmationDialog(
            request = request,
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending
        )
    }

    state.selectedWorkflowDetail?.let { detail ->
        WorkflowDetailScreen(
            workflow = detail,
            isBusy = state.isBusy,
            onBack = viewModel::clearWorkflowDetail,
            onRun = { viewModel.runWorkflow(detail) },
            onSetupTrigger = {
                when (detail.trigger) {
                    is TriggerConfig.Time -> viewModel.showTimeTriggerSetup(detail)
                    is TriggerConfig.Manual -> viewModel.showTimeTriggerSetup(detail)
                    is TriggerConfig.ShareSheet -> viewModel.showShareSheetSetup(detail)
                    else -> { /* other triggers (Nfc, TaskerRequired) not yet supported */ }
                }
            }
        )
        return
    }

    // Show time trigger setup screen
    state.timeTriggerSetupWorkflow?.let { workflow ->
        TimeTriggerSetupScreen(
            initialTrigger = workflow.trigger as? TriggerConfig.Time,
            onSave = { trigger -> viewModel.saveTimeTrigger(workflow.name, trigger) },
            onCancel = viewModel::cancelTimeTriggerSetup
        )
        return
    }

    // Show share sheet trigger setup screen
    state.shareSheetSetupWorkflow?.let { workflow ->
        ShareSheetSetupScreen(
            workflowName = workflow.name,
            onSave = { viewModel.saveShareSheetTrigger(workflow.name, TriggerConfig.ShareSheet(setupState = com.gemmaworkflow.domain.model.SetupState.Ready)) },
            onCancel = viewModel::cancelShareSheetSetup
        )
        return
    }

    // Show share sheet picker when app was launched via ACTION_SEND
    state.sharedContent?.let { sharedContent ->
        ShareSheetPicker(
            sharedContent = sharedContent,
            workflows = state.savedWorkflows.filter { it.trigger is TriggerConfig.ShareSheet },
            onSelectWorkflow = { workflow ->
                viewModel.selectWorkflowFromShare(workflow, sharedContent)
            },
            onDismiss = { viewModel.clearSharedContent() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("GemmaWorkflow", style = MaterialTheme.typography.headlineMedium)

        ModelStatusCard(state.inferenceState)

        if (state.savedWorkflows.isNotEmpty()) {
            Text("Saved", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.savedWorkflows.take(4).forEach { wf ->
                    OutlinedButton(
                        onClick = { viewModel.loadWorkflowDetail(wf.name) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(wf.name, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("What should GemmaWorkflow do?") },
            supportingText = { Text("e.g. \"When I tap this, open Maps to the nearest coffee shop and share my location\"") }
        )

        Button(
            enabled = state.canGenerate,
            onClick = viewModel::generate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isBusy) "Generating\u2026" else "Generate Workflow")
        }

        if (state.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(state.stage, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${state.elapsedSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.stageTimeline.forEach { stage ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val icon = when (stage.status) {
                            StageStatus.Done -> "\u2713"
                            StageStatus.Running -> "\u25B6"
                            StageStatus.Pending -> "\u25CB"
                        }
                        val color = when (stage.status) {
                            StageStatus.Done -> MaterialTheme.colorScheme.primary
                            StageStatus.Running -> MaterialTheme.colorScheme.tertiary
                            StageStatus.Pending -> MaterialTheme.colorScheme.outline
                        }
                        Text(icon, color = color, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stage.label, color = color, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (state.error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = state.error.orEmpty(),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        val workflow = state.workflowPreview
        if (workflow != null) {
            HorizontalDivider()

            Text("Generated Workflow", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                    if (workflow.summary.isNotBlank()) {
                        Text(workflow.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Trigger: ${triggerLabel(workflow)}", style = MaterialTheme.typography.bodySmall)
                    Text("Actions (${workflow.actions.size}):", style = MaterialTheme.typography.labelMedium)
                    workflow.actions.forEach { step ->
                        Text(
                            "  \u2022 ${step.id} ${step.params}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (workflow.missingSetup.isNotEmpty()) {
                        Text(
                            "Needs setup: ${workflow.missingSetup.joinToString()}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (state.validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Validation errors:", style = MaterialTheme.typography.labelMedium)
                        state.validationErrors.forEach { err ->
                            Text("  \u2022 $err", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::saveWorkflow,
                    enabled = !state.saved
                ) {
                    Text(if (state.saved) "Saved" else "Save")
                }
                Button(
                    onClick = viewModel::runWorkflow,
                    enabled = state.isValid && !state.isBusy
                ) {
                    Text("Run Now")
                }
            }

            if (state.rawJson != null) {
                Text("Raw Model Output", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            text = state.rawJson.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (state.runResults.isNotEmpty()) {
            HorizontalDivider()
            Text("Execution Results", style = MaterialTheme.typography.titleMedium)
            state.runResults.forEach { result ->
                RunResultRow(result)
            }
        }

        if (state.debugMessages.isNotEmpty()) {
            HorizontalDivider()
            Text("Debug Trace", style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.debugMessages.forEach { message ->
                            Column {
                                Text(
                                    message.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    message.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelStatusCard(state: InferenceState) {
    val (label, color) = when (state) {
        is InferenceState.Idle -> "Idle" to MaterialTheme.colorScheme.outline
        is InferenceState.Loading -> "Loading model\u2026" to MaterialTheme.colorScheme.primary
        is InferenceState.Ready -> "Ready \u2014 GPU (LiteRT-LM)" to MaterialTheme.colorScheme.primary
        is InferenceState.MissingModel -> "Model not found" to MaterialTheme.colorScheme.error
        is InferenceState.GpuUnavailable -> "GPU unavailable: ${state.reason}" to MaterialTheme.colorScheme.error
        is InferenceState.Error -> "Error: ${state.message}" to MaterialTheme.colorScheme.error
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Text(
            text = label,
            modifier = Modifier.padding(12.dp),
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RunResultRow(result: ExecutionResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (result.success) "\u2713" else "\u2717",
            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(result.stepId, style = MaterialTheme.typography.bodySmall)
            if (result.message.isNotBlank()) {
                Text(result.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * An AlertDialog that presents a pending [ConfirmationRequest] to the user.
 * Displays the step label and its parameters, then calls [onConfirm] or [onDismiss]
 * accordingly.
 */
@Composable
private fun ConfirmationDialog(
    request: ConfirmationRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val paramsText = if (request.params.isEmpty()) {
        "No parameters"
    } else {
        request.params.entries.joinToString("\n") { (key, value) -> "$key: $value" }
    }
    AlertDialog(
        onDismissRequest = { /* Force explicit action */ },
        title = { Text("User Consent Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Action: ${request.stepLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("This action requires your confirmation before it can be executed:")
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = paramsText,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirm & Run")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip Step")
            }
        }
    )
}

private fun triggerLabel(workflow: com.gemmaworkflow.domain.model.PlannedWorkflow): String {
    return when (val t = workflow.trigger) {
        is com.gemmaworkflow.domain.model.TriggerConfig.Manual -> "Manual"
        is com.gemmaworkflow.domain.model.TriggerConfig.Time -> formatTriggerSummary(t)
        is com.gemmaworkflow.domain.model.TriggerConfig.Nfc -> "NFC"
        is com.gemmaworkflow.domain.model.TriggerConfig.ShareSheet -> "Share Sheet (${t.setupState})"
        is com.gemmaworkflow.domain.model.TriggerConfig.TaskerRequired -> "Tasker (${t.setupState})"
    }
}

/** Returns a unicode icon for an action id, matching the existing text-emoji style used in the UI. */
private fun stepIcon(actionId: String): String = when {
    actionId.startsWith("browser.") -> "\uD83C\uDF10"   // globe
    actionId.startsWith("maps.") -> "\uD83D\uDDFD"       // map
    actionId.startsWith("share.") -> "\uD83D\uDCE4"       // outbox tray
    actionId.startsWith("sms.") -> "\uD83D\uDCF7"        // phone
    actionId.startsWith("alarm.") -> "\u23F0"            // alarm clock
    actionId.startsWith("calendar.") -> "\uD83D\uDCC5"  // calendar
    else -> "\u25B6"                                      // play arrow
}

/**
 * Detail screen composable shown when the user taps a saved workflow.
 * Displays the workflow title, summary, trigger, and a scrollable list of steps
 * with icons and labels looked up from ActionSpecRegistry.
 */
@Composable
private fun WorkflowDetailScreen(
    workflow: PlannedWorkflow,
    isBusy: Boolean,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onSetupTrigger: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Workflow Detail", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(workflow.name, style = MaterialTheme.typography.titleMedium)
                if (workflow.summary.isNotBlank()) {
                    Text(workflow.summary, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Trigger: ${triggerLabel(workflow)}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Steps", style = MaterialTheme.typography.titleSmall)
        workflow.actions.forEach { step ->
            val spec = ActionSpecRegistry.find(step.id)
            val icon = stepIcon(step.id)
            val label = spec?.label ?: step.id
            val params = step.params.entries.joinToString(", ") { "${it.key}=${it.value}" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (params.isNotBlank()) {
                        Text(
                            params,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (step.requiresConfirmation) {
                    Text(
                        "\u26A0\uFE0F",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Button(
            onClick = onRun,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBusy) "Running\u2026" else "Run")
        }

        // Show "Schedule" or "Set up trigger" button based on trigger type
        val triggerConfig = workflow.trigger
        val showScheduleButton = triggerConfig is TriggerConfig.Manual || triggerConfig is TriggerConfig.Time
        val showShareSheetSetupButton = triggerConfig is TriggerConfig.ShareSheet

        if (showScheduleButton) {
            val label = if (triggerConfig is TriggerConfig.Time) "\u23F0 Edit Schedule" else "\u23F0 Schedule"
            OutlinedButton(
                onClick = onSetupTrigger,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
            }
        }

        if (showShareSheetSetupButton) {
            OutlinedButton(
                onClick = onSetupTrigger,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("\uD83D\uDCE4 Set up Share Sheet")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Shown when the app is launched via share sheet.
 * Displays the shared content and a list of saved workflows the user can run it with.
 * Selecting a workflow pre-loads it and closes the picker; the main screen then shows
 * the detail view so the user can confirm before running.
 */
@Composable
private fun ShareSheetPicker(
    sharedContent: SharedContent,
    workflows: List<PlannedWorkflow>,
    onSelectWorkflow: (PlannedWorkflow) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Share to GemmaWorkflow", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }

        // Show what was shared
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Shared content:", style = MaterialTheme.typography.labelMedium)
                when (sharedContent) {
                    is SharedContent.Text -> {
                        Text(
                            sharedContent.text.take(500),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    is SharedContent.Image -> {
                        Text(
                            "Image: ${sharedContent.uri.lastPathSegment ?: sharedContent.uri}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Text("Run with a saved workflow:", style = MaterialTheme.typography.titleMedium)

        if (workflows.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "No Share Sheet workflows.\nOpen workflow detail \u2192 trigger \u2192 set to Share Sheet to enable.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            workflows.forEach { workflow ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = false,
                            onClick = { onSelectWorkflow(workflow) },
                            role = Role.Button
                        )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                        if (workflow.summary.isNotBlank()) {
                            Text(workflow.summary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${workflow.actions.size} step${if (workflow.actions.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
