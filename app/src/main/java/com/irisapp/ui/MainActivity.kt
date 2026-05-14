package com.irisapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.irisapp.ui.components.AmbientBackground
import com.irisapp.ui.components.BlobPersona
import com.irisapp.ui.components.BlobState
import com.irisapp.ui.components.GlassmorphicCard
import com.irisapp.ui.components.GradientButton
import com.irisapp.ui.components.LivingInputConsole
import com.irisapp.ui.components.SceneChip
import com.irisapp.ui.components.SceneChipStrip
import com.irisapp.ui.components.SceneChipData
import com.irisapp.ui.theme.BackgroundDark
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.ElectricCyan
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.ui.theme.GlassSurface
import com.irisapp.ui.theme.LiquidViolet
import com.irisapp.ui.theme.ObsidianDark
import com.irisapp.ui.theme.SurfaceDark
import com.irisapp.ui.theme.SurfaceVariantDark
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary
import com.irisapp.ui.theme.VioletAccent
import kotlinx.coroutines.launch
import com.irisapp.domain.catalog.ActionSpecRegistry
import com.irisapp.domain.model.ExecutionResult
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.SharedContent
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.platform.inference.InferenceState
import com.irisapp.platform.nfc.DeepLink
import com.irisapp.platform.nfc.DeepLinkRouter
import com.irisapp.platform.nfc.NfcTriggerWriter
import com.irisapp.platform.share.ShareSheetTriggerHandler
import com.irisapp.platform.trigger.TriggerRegistry
import com.irisapp.ui.home.ConfirmationRequest
import com.irisapp.ui.home.PermissionRequest
import com.irisapp.ui.home.StageStatus
import com.irisapp.ui.home.TimeTriggerSetupScreen
import com.irisapp.ui.home.ShareSheetSetupScreen
import com.irisapp.ui.home.SoundEventTriggerSetupScreen
import com.irisapp.ui.home.ManualWorkflowEditorScreen
import com.irisapp.ui.home.WorkflowGenerationViewModel
import com.irisapp.ui.home.GenerateTabContent
import com.irisapp.ui.marketplace.MarketplaceScreen
import com.irisapp.ui.marketplace.MarketplaceViewModel
import com.irisapp.ui.home.WorkflowGenerationUiState
import com.irisapp.ui.nfc.NfcSetupScreen
import com.irisapp.ui.theme.IrisTheme
import com.irisapp.ui.trigger.formatTriggerSummary
import com.irisapp.platform.trigger.voice.VoiceTriggerFab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private val viewModel: WorkflowGenerationViewModel by viewModels()
    private val marketplaceViewModel: MarketplaceViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications will work
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureRequiredRuntimePermissions()
        handleIntent(intent)
        observeDeepLinkEvents()
        checkNotificationPermission()
        setContent {
            // Edge-to-edge with dark system bars — must be after setContent
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.decorView.post {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
            IrisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkflowGenerationScreen(
                        viewModel = viewModel,
                        marketplaceViewModel = marketplaceViewModel
                    )
                }
            }
        }
    }

    private fun ensureRequiredRuntimePermissions() {
        val missingPermissions = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        ).filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                RUNTIME_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private companion object {
        const val RUNTIME_PERMISSIONS_REQUEST_CODE = 1001
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

            // Handle confirmation/dismiss from TriggerRegistry notification (background triggers).
            val action = intent.getStringExtra(TriggerRegistry.EXTRA_ACTION)
            if (action == TriggerRegistry.ACTION_CONFIRM || action == TriggerRegistry.ACTION_DISMISS) {
                val workflowName = intent.getStringExtra(TriggerRegistry.EXTRA_WORKFLOW_NAME) ?: return@launch
                if (action == TriggerRegistry.ACTION_CONFIRM) {
                    TriggerRegistry.confirmAndResume(this@MainActivity, workflowName)
                } else {
                    TriggerRegistry.dismissConfirmation(this@MainActivity, workflowName)
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
private fun WorkflowGenerationScreen(
    viewModel: WorkflowGenerationViewModel,
    marketplaceViewModel: MarketplaceViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle system back button — intercept to dismiss overlays before default finish
    BackHandler(enabled = true) {
        when {
            state.editingWorkflow != null -> viewModel.cancelEditWorkflow()
            state.selectedWorkflowDetail != null -> viewModel.clearWorkflowDetail()
            state.timeTriggerSetupWorkflow != null -> viewModel.cancelTimeTriggerSetup()
            state.shareSheetSetupWorkflow != null -> viewModel.cancelShareSheetSetup()
            state.soundEventTriggerSetupWorkflow != null -> viewModel.cancelSoundEventTriggerSetup()
            else -> { /* let Activity finish normally */ }
        }
    }

    // Show the manual editor if open
    state.editingWorkflow?.let { workflow ->
        ManualWorkflowEditorScreen(
            initialWorkflow = if (state.isNewWorkflow) null else workflow,
            onSave = { viewModel.saveEditedWorkflow(it) },
            onCancel = viewModel::cancelEditWorkflow
        )
        return
    }

    // Show a confirmation dialog whenever a step requires user consent before executing.
    // This is placed before the early return so it shows even on the detail screen.
    state.pendingConfirmation?.let { request ->
        ConfirmationDialog(
            request = request,
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending)
    }

    state.pendingPermission?.let { request ->
        PermissionDialog(
            request = request,
            onGrant = viewModel::grantPendingPermissions,
            onDismiss = viewModel::dismissPending)
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
                    is TriggerConfig.SoundEvent -> viewModel.showSoundEventTriggerSetup(detail)
                    else -> { /* other triggers not yet supported */ }
                }
            },
            onEdit = { viewModel.openEditWorkflowEditor(detail) }
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
            onSave = { viewModel.saveShareSheetTrigger(workflow.name, TriggerConfig.ShareSheet(setupState = com.irisapp.domain.model.SetupState.Ready)) },
            onCancel = viewModel::cancelShareSheetSetup
        )
        return
    }

    // Show Sound Event trigger setup screen
    state.soundEventTriggerSetupWorkflow?.let { workflow ->
        SoundEventTriggerSetupScreen(
            savedWorkflowNames = state.savedWorkflows.map { it.name },
            currentMappings = if (workflow.trigger is TriggerConfig.SoundEvent) {
                (workflow.trigger as TriggerConfig.SoundEvent).soundClasses.associateWith { workflow.name }
            } else emptyMap(),
            onSave = { targetWorkflow, soundClasses ->
                viewModel.saveSoundEventTrigger(targetWorkflow, soundClasses.toList())
            },
            onCancel = viewModel::cancelSoundEventTriggerSetup
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

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 80.dp)
            ) {
                when (state.selectedTab) {
                    0 -> GenerateTabContent(viewModel, state)
                    1 -> WorkflowsTabContent(
                        workflows = state.savedWorkflows,
                        onSelect = { wf -> viewModel.loadWorkflowDetail(wf.name) },
                        onEdit = viewModel::openEditWorkflowEditor,
                        onNew = viewModel::openNewWorkflowEditor
                    )
                    2 -> {
                        MarketplaceScreen(
                            viewModel = marketplaceViewModel,
                            onBack = { }
                        )
                    }
                    3 -> DebugTabContent(state)
                }
            }
            FloatingPillNavigationDock(
                selectedTab = state.selectedTab,
                onTabSelected = { viewModel.selectTab(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        }
    }
}


@Composable
private fun MarketplaceComingSoon() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🛒",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Coming Soon",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight(700)),
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The marketplace will let you discover and share workflow templates.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun WorkflowsTabContent(
    workflows: List<PlannedWorkflow>,
    onSelect: (PlannedWorkflow) -> Unit,
    onEdit: (PlannedWorkflow) -> Unit,
    onNew: () -> Unit
) {
    // Scene selection state — null means "show all"
    var selectedSceneId by remember { mutableStateOf<String?>(null) }

    val scenes = remember {
        listOf(
            SceneChipData("morning", "Morning", 3, Color(0xFFFFC15E)),
            SceneChipData("work", "Work", 5, CyanAccent),
            SceneChipData("dinner", "Dinner", 4, Color(0xFFFF6BD6)),
            SceneChipData("sleep", "Sleep", 6, Color(0xFFB57BFF))
        )
    }

    // Filter workflows when a scene is selected; null = show all
    val displayedWorkflows = remember(selectedSceneId, workflows) {
        if (selectedSceneId == null) workflows
        else workflows.filter { it.scene == selectedSceneId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Workflows",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight(700),
                fontSize = 28.sp,
                color = TextPrimary
            ),
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // Scene chip strip
        SceneChipStrip(
            scenes = scenes,
            selectedSceneId = selectedSceneId,
            onChipClick = { scene ->
                selectedSceneId = if (selectedSceneId == scene.id) null else scene.id
            },
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerLabel = if (selectedSceneId != null) {
                val sceneName = scenes.find { it.id == selectedSceneId }?.name ?: ""
                "$sceneName Workflows"
            } else "Saved Workflows"

            Text(
                headerLabel,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = TextSecondary,
                    fontWeight = FontWeight(600)
                )
            )
            GradientButton(text = "+ New", onClick = onNew)
        }

        val isFiltering = selectedSceneId != null
            val isEmpty = workflows.isEmpty()
            val isFilteredEmpty = isFiltering && displayedWorkflows.isEmpty() && !isEmpty

            when {
                isEmpty -> {
                    // No workflows at all
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassSurface, MaterialTheme.shapes.medium)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "No workflows yet",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Create one with AI or build manually",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                val infiniteTransition = rememberInfiniteTransition(label = "arrow")
                                val offsetX by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 6f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "arrow"
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Create workflow",
                                    tint = CyanAccent,
                                    modifier = Modifier
                                        .offset(x = offsetX.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                }
                isFilteredEmpty -> {
                    // Workflows exist but none match this scene
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassSurface, MaterialTheme.shapes.medium)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sceneName = scenes.find { it.id == selectedSceneId }?.name ?: ""
                            Text(
                                "No $sceneName workflows",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                            )
                            TextButton(onClick = { selectedSceneId = null }) {
                                Text("Show all workflows", color = CyanAccent)
                            }
                        }
                    }
                }
                else -> {
                    displayedWorkflows.forEach { workflow ->
                        WorkflowListCard(
                            workflow = workflow,
                            onSelect = { onSelect(workflow) },
                            onEdit = { onEdit(workflow) }
                        )
                    }
                }
            }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DebugTabContent(state: WorkflowGenerationUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Debug Trace", style = MaterialTheme.typography.headlineMedium)

        if (state.debugMessages.isEmpty()) {
            Text("No debug messages yet.", style = MaterialTheme.typography.bodyMedium)
            return
        }

        // Group by label prefix
        val grouped = state.debugMessages.groupBy { msg ->
            msg.label.substringBefore(":").ifBlank { msg.label }
        }

        grouped.forEach { (group, messages) ->
            GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(group, style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    messages.forEach { msg ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                msg.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SelectionContainer {
                                Text(
                                    msg.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
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

        if (state.stageTimeline.isNotEmpty()) {
            HorizontalDivider()
            Text("Stage Timeline", style = MaterialTheme.typography.titleMedium)
            state.stageTimeline.forEachIndexed { index, stage ->
                val tokenInfo = state.stageTokenUsage.getOrNull(index)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val (icon, iconColor) = when (stage.status) {
                        StageStatus.Done -> Icons.Default.CheckCircle to CyanAccent
                        StageStatus.Running -> Icons.Default.PlayArrow to VioletAccent
                        StageStatus.Pending -> Icons.Default.RadioButtonUnchecked to TextSecondary
                    }
                    val description = when (stage.status) {
                        StageStatus.Done -> "Done"
                        StageStatus.Running -> "Running"
                        StageStatus.Pending -> "Pending"
                    }
                    Icon(icon, contentDescription = description, tint = iconColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stage.label, color = iconColor, style = MaterialTheme.typography.bodyMedium)
                    if (tokenInfo != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${tokenInfo.estimatedTokens} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun WorkflowListCard(
    workflow: PlannedWorkflow,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workflow.name,
                    style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                )
                if (workflow.summary.isNotBlank()) {
                    Text(
                        workflow.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                Text(
                    triggerLabel(workflow),
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent
                )
            }
            OutlinedButton(
                onClick = onSelect,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
            ) {
                Text("View")
            }
            OutlinedButton(
                onClick = onEdit,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletAccent)
            ) {
                Text("Edit")
            }
        }
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

/**
 * Displays the step label and the list of permissions that need to be granted
 * before the step can execute. Calls [onGrant] if the user accepts, or [onDismiss]
 * if they skip the step.
 */
@Composable
private fun PermissionDialog(
    request: PermissionRequest,
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Human-readable permission descriptions for common Android runtime permissions.
    val permissionDescriptions = request.permissions.map { permission ->
        val description = when (permission) {
            Manifest.permission.READ_CONTACTS -> "Read contacts"
            Manifest.permission.WRITE_CONTACTS -> "Write contacts"
            Manifest.permission.READ_SMS -> "Read SMS messages"
            Manifest.permission.SEND_SMS -> "Send SMS messages"
            Manifest.permission.RECEIVE_SMS -> "Receive SMS messages"
            Manifest.permission.READ_CALL_LOG -> "Read call log"
            Manifest.permission.CALL_PHONE -> "Make phone calls"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Read storage"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Write storage"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Precise location"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate location"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.READ_PHONE_STATE -> "Phone state"
            else -> permission.substringAfterLast(".")
        }
        description to permission
    }

    AlertDialog(
        onDismissRequest = { /* Force explicit action */ },
        title = { Text("Permission Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Action: ${request.stepLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("This action needs the following permissions to run:")
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        permissionDescriptions.forEach { (desc, _) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text("\uD83D\uDD12", style = MaterialTheme.typography.bodySmall) // lock emoji
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(desc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Text(
                    "Granting allows this workflow to execute without errors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val activity = context as? android.app.Activity
                    if (request.permissions.isNotEmpty() && activity != null) {
                        ActivityCompat.requestPermissions(
                            activity,
                            request.permissions.toTypedArray(),
                            /* requestCode = */ 2001
                        )
                    }
                    // Resume regardless — runner re-checks perms when execution continues.
                    onGrant()
                }
            ) {
                Text("Grant Permissions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip Step")
            }
        }
    )
}

private fun triggerLabel(workflow: com.irisapp.domain.model.PlannedWorkflow): String {
    return when (val t = workflow.trigger) {
        is com.irisapp.domain.model.TriggerConfig.Manual -> "Manual"
        is com.irisapp.domain.model.TriggerConfig.Time -> formatTriggerSummary(t)
        is com.irisapp.domain.model.TriggerConfig.Nfc -> "NFC"
        is TriggerConfig.ShareSheet -> "Share Sheet (${t.setupState})"
        is TriggerConfig.Battery -> "Battery ${t.condition.name.lowercase()} ${t.levelThreshold}%"
        is com.irisapp.domain.model.TriggerConfig.Charger -> "Charger (${t.connectionType.name})"
        is com.irisapp.domain.model.TriggerConfig.WiFi -> if (t.ssid.isNullOrBlank()) "WiFi" else "WiFi (${t.ssid})"
        is com.irisapp.domain.model.TriggerConfig.Bluetooth -> if (t.deviceAddress.isNullOrBlank()) "Bluetooth" else "Bluetooth (${t.deviceAddress})"
        is com.irisapp.domain.model.TriggerConfig.AirplaneMode -> "Airplane Mode (${if (t.enabled) "on" else "off"})"
        is com.irisapp.domain.model.TriggerConfig.DoNotDisturb -> "Do Not Disturb"
        is com.irisapp.domain.model.TriggerConfig.Geofence -> {
            val loc = "(${String.format("%.4f", t.latitude)}, ${String.format("%.4f", t.longitude)})"
            "Geofence $loc"
        }
        is com.irisapp.domain.model.TriggerConfig.AlarmStopped -> "Alarm Stopped"
        is com.irisapp.domain.model.TriggerConfig.AppOpened -> "App Opened"
        is com.irisapp.domain.model.TriggerConfig.AppClosed -> "App Closed"
        is com.irisapp.domain.model.TriggerConfig.SmsReceived -> "SMS Received"
        is com.irisapp.domain.model.TriggerConfig.NotificationListenerConfig -> "Messaging Notification"
        is com.irisapp.domain.model.TriggerConfig.EmailReceived -> "Email Received"
        is com.irisapp.domain.model.TriggerConfig.SleepProxy -> "Sleep Proxy"
        else -> "Unknown trigger"
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
    onSetupTrigger: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Workflow Detail", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Text("Edit")
                }
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
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
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Share to IrisApp", style = MaterialTheme.typography.headlineSmall)
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
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// Floating Pill Navigation Dock (Gemini-style)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun FloatingPillNavigationDock(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabIcons = listOf(
        Icons.Filled.AutoAwesome,
        Icons.Filled.AccountTree,
        Icons.Filled.Store,
        Icons.Filled.BugReport
    )
    val tabLabels = listOf("Generate", "Workflows", "Market", "Debug")
    val tabCount  = tabIcons.size

    val indicatorFraction by animateFloatAsState(
        targetValue = selectedTab.toFloat() / (tabCount - 1),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "nav_indicator"
    )

    val dockShape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .clip(dockShape)
            .background(Color(0xFF0A0A14).copy(alpha = 0.90f))
            .border(0.5.dp, GlassBorder, dockShape)
            .height(60.dp)
    ) {
        // Sliding active indicator
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tabWidth = size.width / tabCount
            val indicatorW = tabWidth * 0.85f
            val xOffset = indicatorFraction * (size.width - tabWidth) + (tabWidth - indicatorW) / 2f
            drawRoundRect(
                color = CyanAccent.copy(alpha = 0.15f),
                topLeft = Offset(xOffset, 4f),
                size = Size(indicatorW, this.size.height - 8f),
                cornerRadius = CornerRadius(28.dp.toPx())
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabIcons.forEachIndexed { index, icon ->
                val isSelected = selectedTab == index
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) CyanAccent else TextSecondary.copy(alpha = 0.5f),
                    animationSpec = tween(250),
                    label = "icon_color_$index"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tabLabels[index],
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tabLabels[index],
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight(600) else FontWeight(400)
                        ),
                        color = iconColor
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════
// Dark Navigation Bar — kept for reference, no longer used
// ═══════════════════════════════════════════════════════════════
@Composable
private fun DarkNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabLabels: List<String>,
    tabIcons: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = SurfaceDark,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceDark.copy(alpha = 0.95f),
                            BackgroundDark.copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(vertical = 8.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabLabels.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                val icon = tabIcons.getOrElse(index) { "" }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) CyanAccent.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = icon,
                        fontSize = 18.sp,
                        color = if (isSelected) CyanAccent else TextSecondary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight(600) else FontWeight(400)
                        ),
                        color = if (isSelected) CyanAccent else TextSecondary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
