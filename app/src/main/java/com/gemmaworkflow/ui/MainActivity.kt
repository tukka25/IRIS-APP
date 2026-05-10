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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.gemmaworkflow.ui.home.RecentRun
import com.gemmaworkflow.ui.home.WorkflowGenerationUiState
import com.gemmaworkflow.ui.home.WorkflowRunSummary
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon

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

        lifecycleScope.launch {
            // Wait for savedWorkflows to be loaded (it might be empty on cold start)
            withTimeoutOrNull(2000) {
                viewModel.uiState.first { it.savedWorkflows.isNotEmpty() }
            }

            // Share sheet intent: extract content and show workflow selector
            if (intent.action == Intent.ACTION_SEND) {
                if (intent.type == null) return@launch
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
                return@launch
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
                return@launch
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
                return@launch
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
                return@launch
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
                return@launch
            }
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

    var selectedTab by remember { mutableIntStateOf(0) }
    var showGenerate by remember { mutableStateOf(false) }

    if (showGenerate || state.isBusy || state.workflowPreview != null) {
        GenerateScreen(
            state = state,
            viewModel = viewModel,
            onBack = { viewModel.clearPreview(); showGenerate = false }
        )
        return
    }

    Scaffold(
        containerColor = Color(0xFF0D0D0F),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1C1C1E), tonalElevation = 0.dp) {
                val navItems = listOf(
                    Triple(Icons.Filled.Bolt, "Workflows", 0),
                    Triple(Icons.Filled.Bookmarks, "Library", 1),
                    Triple(Icons.Filled.History, "History", 2),
                    Triple(Icons.Filled.Settings, "Settings", 3)
                )
                navItems.forEach { (icon, label, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF007AFF),
                            selectedTextColor = Color(0xFF007AFF),
                            indicatorColor = Color(0xFF1C1C1E),
                            unselectedIconColor = Color(0xFF636366),
                            unselectedTextColor = Color(0xFF636366)
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> WorkflowsHomeTab(state, viewModel, padding, onNewWorkflow = { showGenerate = true })
            1 -> LibraryTab(padding)
            2 -> HistoryTab(state, padding)
            else -> SettingsTab(padding)
        }
    }
}

// \u2500\u2500 Dark home tab \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

private val HomeBackground = Color(0xFF0D0D0F)
private val HomeSectionLabel = Color(0xFF636366)
private val HomeCardBg = Color(0xFF1C1C1E)

@Composable
private fun WorkflowsHomeTab(
    state: WorkflowGenerationUiState,
    viewModel: WorkflowGenerationViewModel,
    padding: PaddingValues,
    onNewWorkflow: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .padding(padding),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Workflows", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModelStatusPill(state.inferenceState)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF007AFF), CircleShape)
                            .clickable(onClick = onNewWorkflow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New workflow", tint = Color.White,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (state.savedWorkflows.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SAVED", style = MaterialTheme.typography.labelMedium, color = HomeSectionLabel)
                    Text("${state.savedWorkflows.size}", style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF007AFF))
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            items(state.savedWorkflows) { wf ->
                WorkflowCard(
                    workflow = wf,
                    summary = state.workflowSummaries[wf.name],
                    onClick = { viewModel.loadWorkflowDetail(wf.name) }
                )
            }
        }

        if (state.recentActivity.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.size(16.dp))
                Text("RECENT ACTIVITY", style = MaterialTheme.typography.labelMedium,
                    color = HomeSectionLabel,
                    modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(modifier = Modifier.size(8.dp))
            }
            items(state.recentActivity) { run ->
                RecentActivityRow(run)
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    workflow: PlannedWorkflow,
    summary: WorkflowRunSummary?,
    onClick: () -> Unit
) {
    val iconColor = triggerIconColor(workflow)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(HomeCardBg, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(iconColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(triggerEmoji(workflow), style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(workflow.name, style = MaterialTheme.typography.titleSmall,
                color = Color.White, maxLines = 1)
            Text(triggerSubtitle(workflow), style = MaterialTheme.typography.labelSmall,
                color = HomeSectionLabel, maxLines = 1)
        }
        if (summary != null && summary.recentHistory.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                summary.recentHistory.forEach { ok ->
                    Box(modifier = Modifier.size(7.dp).background(
                        if (ok) Color(0xFF34C759) else Color(0xFFFF3B30), CircleShape))
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null,
            tint = HomeSectionLabel, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RecentActivityRow(run: RecentRun) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(HomeCardBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp)
                .background(if (run.success) Color(0xFF34C759) else Color(0xFFFF3B30), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (run.success) "\u2713" else "\u2717", color = Color.White,
                style = MaterialTheme.typography.labelSmall)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(run.workflowName, style = MaterialTheme.typography.bodyMedium,
                color = Color.White, maxLines = 1)
            Text(formatRelativeTime(run.timestampMillis),
                style = MaterialTheme.typography.labelSmall, color = HomeSectionLabel)
        }
    }
}

@Composable
private fun ModelStatusPill(inferenceState: InferenceState) {
    val (label, color) = when (inferenceState) {
        is InferenceState.Ready -> "\u25CF GPU" to Color(0xFF34C759)
        is InferenceState.Loading -> "\u25CF Loading" to Color(0xFFFF9500)
        else -> "\u25CF Offline" to Color(0xFFFF3B30)
    }
    Box(
        modifier = Modifier
            .background(Color(0xFF2C2C2E), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun LibraryTab(padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().background(HomeBackground).padding(padding),
        contentAlignment = Alignment.Center) {
        Text("Action Library", color = HomeSectionLabel, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HistoryTab(state: WorkflowGenerationUiState, padding: PaddingValues) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(HomeBackground).padding(padding),
        contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text("RECENT ACTIVITY", style = MaterialTheme.typography.labelMedium,
                color = HomeSectionLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        }
        if (state.recentActivity.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No runs yet", color = HomeSectionLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(state.recentActivity) { run -> RecentActivityRow(run) }
        }
    }
}

@Composable
private fun SettingsTab(padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().background(HomeBackground).padding(padding),
        contentAlignment = Alignment.Center) {
        Text("Settings", color = HomeSectionLabel, style = MaterialTheme.typography.titleMedium)
    }
}

// \u2500\u2500 Generate / review screen \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

@Composable
private fun GenerateScreen(
    state: WorkflowGenerationUiState,
    viewModel: WorkflowGenerationViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("\u2190 Back", color = MaterialTheme.colorScheme.primary)
            }
            if (state.isBusy) {
                Text("${state.elapsedSeconds}s", style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            if (state.isBusy) "Generating\u2026" else if (state.workflowPreview != null) "Review Workflow" else "New Workflow",
            style = MaterialTheme.typography.headlineSmall
        )

        // Request display
        if (state.prompt.isNotBlank() && (state.isBusy || state.workflowPreview != null)) {
            Text("YOUR REQUEST", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.prompt, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Box(modifier = Modifier
                        .background(Color(0xFFE5F1FF), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("On-device", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF007AFF))
                    }
                }
            }
        } else if (!state.isBusy && state.workflowPreview == null) {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::updatePrompt,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("What should GemmaWorkflow do?") },
                supportingText = { Text("e.g. \"Every morning at 9, open Maps to nearest coffee shop\"") }
            )
            Button(
                enabled = state.canGenerate,
                onClick = viewModel::generate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) { Text("Generate Workflow") }
        }

        // Inference card (shown during generation)
        if (state.isBusy) {
            InferenceCard(state)
        }

        // Error
        state.error?.let { err ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(err, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Workflow review (WHEN / THEN DO)
        state.workflowPreview?.let { workflow ->
            ReviewWorkflowCard(workflow, state, viewModel)
        }

        // Run results
        if (state.runResults.isNotEmpty()) {
            Text("RESULTS", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    state.runResults.forEachIndexed { i, result ->
                        RunResultRow(result)
                        if (i < state.runResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun InferenceCard(state: WorkflowGenerationUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ON-DEVICE INFERENCE", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0F)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Gemma", style = MaterialTheme.typography.titleLarge,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier
                        .background(Color(0xFF1C3A1C), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("\u25CF GPU \u00B7 LiteRT-LM", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34C759))
                    }
                }
                Text("0 packets leaving device",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF636366))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF007AFF), trackColor = Color(0xFF1C1C1E))
                // Pipeline steps
                Text("PIPELINE", style = MaterialTheme.typography.labelMedium, color = Color(0xFF636366))
                state.stageTimeline.forEach { stage ->
                    val (icon, color) = when (stage.status) {
                        StageStatus.Done -> "\u2713" to Color(0xFF34C759)
                        StageStatus.Running -> "\u25B6" to Color(0xFF007AFF)
                        StageStatus.Pending -> "\u25CB" to Color(0xFF636366)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(icon, color = color, style = MaterialTheme.typography.bodySmall)
                        Text(stage.label, color = if (stage.status == StageStatus.Pending)
                            Color(0xFF636366) else Color.White,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewWorkflowCard(
    workflow: PlannedWorkflow,
    state: WorkflowGenerationUiState,
    viewModel: WorkflowGenerationViewModel
) {
    // Workflow name + model badge
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(44.dp)
                .background(triggerIconColor(workflow), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Text(triggerEmoji(workflow), style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(workflow.name, style = MaterialTheme.typography.titleMedium)
                if (workflow.summary.isNotBlank()) {
                    Text(workflow.summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
        Box(modifier = Modifier
            .padding(horizontal = 16.dp).padding(bottom = 12.dp)
            .background(Color(0xFF1C1C1E), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text("\u25CF Gemma \u00B7 GPU \u00B7 LiteRT-LM", style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF34C759))
        }
    }

    // WHEN section
    Text("WHEN", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(36.dp)
                    .background(triggerIconColor(workflow), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(triggerEmoji(workflow), style = MaterialTheme.typography.bodyMedium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(triggerTypeLabel(workflow), style = MaterialTheme.typography.bodyMedium)
                    Text(triggerSubtitle(workflow), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("OR TRIGGER BY", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("NFC" to "\u2717", "Share" to "\u27F3", "Manual" to "\u25B6").forEach { (label, icon) ->
                    Box(modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("$icon $label", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // THEN DO section
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text("THEN DO", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${workflow.actions.size} step${if (workflow.actions.size != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            workflow.actions.forEachIndexed { index, step ->
                val spec = ActionSpecRegistry.find(step.id)
                val label = spec?.label ?: step.id
                val icon = stepIcon(step.id)
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(icon, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("STEP ${index + 1} \u00B7 ${step.id.substringBefore('.').uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        step.params.entries.take(2).forEach { (k, v) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(k, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(80.dp))
                                Text(v.toString(), style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (step.requiresConfirmation) {
                            Box(modifier = Modifier
                                .padding(top = 4.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("Asks first", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (index < workflow.actions.lastIndex)
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
            }
        }
    }

    // Validation errors
    if (state.validationErrors.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Validation errors:", style = MaterialTheme.typography.labelMedium)
                state.validationErrors.forEach { err ->
                    Text("\u2022 $err", color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Actions: Save + Run
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = viewModel::saveWorkflow,
            enabled = !state.saved,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50)
        ) { Text(if (state.saved) "Saved \u2713" else "Save") }
        Button(
            onClick = viewModel::runWorkflow,
            enabled = state.isValid && !state.isBusy,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50)
        ) { Text("Run Now") }
    }
}

@Composable
private fun ModelStatusCard(state: InferenceState) {
    val (label, color) = when (state) {
        is InferenceState.Idle -> "Idle" to MaterialTheme.colorScheme.outline
        is InferenceState.Loading -> "Loading model\u2026" to MaterialTheme.colorScheme.primary
        is InferenceState.Ready -> "Ready \u2014 GPU (LiteRT-LM)" to MaterialTheme.colorScheme.secondary
        is InferenceState.MissingModel -> "Model not found" to MaterialTheme.colorScheme.error
        is InferenceState.GpuUnavailable -> "GPU unavailable: ${state.reason}" to MaterialTheme.colorScheme.error
        is InferenceState.Error -> "Error: ${state.message}" to MaterialTheme.colorScheme.error
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("\u25cf", color = color, style = MaterialTheme.typography.bodySmall)
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RunResultRow(result: ExecutionResult) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (result.success) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (result.success) "\u2713" else "\u2717",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(result.stepId, style = MaterialTheme.typography.bodyMedium)
            if (result.message.isNotBlank()) {
                Text(result.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun triggerEmoji(workflow: com.gemmaworkflow.domain.model.PlannedWorkflow): String =
    when (workflow.trigger) {
        is com.gemmaworkflow.domain.model.TriggerConfig.Time -> "⏰"
        is com.gemmaworkflow.domain.model.TriggerConfig.Nfc -> "📱"
        is com.gemmaworkflow.domain.model.TriggerConfig.ShareSheet -> "📤"
        is com.gemmaworkflow.domain.model.TriggerConfig.TaskerRequired -> "⚡"
        is com.gemmaworkflow.domain.model.TriggerConfig.Manual -> "▶️"
    }

private fun triggerIconColor(workflow: PlannedWorkflow): Color = when (workflow.trigger) {
    is TriggerConfig.Time -> Color(0xFFFF9500)
    is TriggerConfig.Nfc -> Color(0xFFAF52DE)
    is TriggerConfig.ShareSheet -> Color(0xFF34C759)
    is TriggerConfig.TaskerRequired -> Color(0xFFFF3B30)
    is TriggerConfig.Manual -> Color(0xFF007AFF)
}

private fun triggerSubtitle(workflow: PlannedWorkflow): String = when (val t = workflow.trigger) {
    is TriggerConfig.Time -> formatTriggerSummary(t)
    is TriggerConfig.Nfc -> "NFC tag"
    is TriggerConfig.ShareSheet -> "Share sheet"
    is TriggerConfig.TaskerRequired -> "Tasker"
    is TriggerConfig.Manual -> "Manual"
}

private fun triggerTypeLabel(workflow: PlannedWorkflow): String = when (workflow.trigger) {
    is TriggerConfig.Time -> "Schedule"
    is TriggerConfig.Nfc -> "NFC Tag"
    is TriggerConfig.ShareSheet -> "Share Sheet"
    is TriggerConfig.TaskerRequired -> "Tasker"
    is TriggerConfig.Manual -> "Manual"
}

private fun formatRelativeTime(timestampMillis: Long): String {
    val diff = System.currentTimeMillis() - timestampMillis
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("\u2190 Workflows", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        Text(workflow.name, style = MaterialTheme.typography.headlineSmall)

        if (workflow.summary.isNotBlank()) {
            Text(
                workflow.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            "TRIGGER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(triggerEmoji(workflow), style = MaterialTheme.typography.titleMedium)
                Text(triggerLabel(workflow), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            "ACTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                workflow.actions.forEachIndexed { index, step ->
                    val spec = ActionSpecRegistry.find(step.id)
                    val icon = stepIcon(step.id)
                    val label = spec?.label ?: step.id
                    val params = step.params.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (index < workflow.actions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }

        Button(
            onClick = onRun,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text(if (isBusy) "Running\u2026" else "Run Now")
        }

        val triggerConfig = workflow.trigger
        val showScheduleButton = triggerConfig is TriggerConfig.Manual || triggerConfig is TriggerConfig.Time
        val showShareSheetSetupButton = triggerConfig is TriggerConfig.ShareSheet

        if (showScheduleButton) {
            val label = if (triggerConfig is TriggerConfig.Time) "\u23F0 Edit Schedule" else "\u23F0 Schedule"
            OutlinedButton(
                onClick = onSetupTrigger,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text(label)
            }
        }

        if (showShareSheetSetupButton) {
            OutlinedButton(
                onClick = onSetupTrigger,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
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
            TextButton(onClick = onDismiss) {
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
