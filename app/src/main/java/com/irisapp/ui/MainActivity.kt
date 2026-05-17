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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
import com.irisapp.ui.components.GradientOutlinedButton
import com.irisapp.ui.components.LivingInputConsole
import com.irisapp.ui.components.SceneChip
import com.irisapp.ui.components.SceneChipStrip
import com.irisapp.ui.components.SceneChipData
import com.irisapp.data.repository.ExecutionHistoryRepository
import com.irisapp.domain.model.ExecutionLogEntry
import com.irisapp.ui.theme.AmberWarning
import com.irisapp.ui.theme.BackgroundDark
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.ElectricCyan
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.ui.theme.GlassSurface
import com.irisapp.ui.theme.GreenSuccess
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
import com.irisapp.ui.home.ImportWorkflowScreen
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

            // Handle iris://import/{shareId} OR https://iris-23288.web.app/import/{shareId}
            // Both routes extract the share ID and open the import confirmation screen.
            val shareId = when (intent.data?.host) {
                "import" -> intent.data?.lastPathSegment                                           // iris://import/{id}
                "iris-23288.web.app" -> {
                    if (intent.data?.path?.startsWith("/import/") == true)
                        intent.data?.pathSegments?.lastOrNull()                                   // https://iris-23288.web.app/import/{id}
                    else null
                }
                else -> null
            }
            if (shareId != null) {
                viewModel.setPendingImport(shareId)
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
                        is DeepLink.WriteComplete -> {
                            // Write completion is handled in NfcSetupScreen; no action needed here.
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
        AmbientBackground(modifier = Modifier.fillMaxSize()) {
            ManualWorkflowEditorScreen(
                initialWorkflow = if (state.isNewWorkflow) null else workflow,
                onSave = { viewModel.saveEditedWorkflow(it) },
                onCancel = viewModel::cancelEditWorkflow
            )
        }
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
        AmbientBackground(modifier = Modifier.fillMaxSize()) {
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
        }
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

    // Show import confirmation screen when app was opened via iris://import/{id}
    state.pendingImportShareId?.let { shareId ->
        AmbientBackground(modifier = Modifier.fillMaxSize()) {
            ImportWorkflowScreen(
                shareId = shareId,
                onConfirm = viewModel::confirmImport,
                onCancel = viewModel::clearPendingImport
            )
        }
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
                        onNew = viewModel::openNewWorkflowEditor,
                        onDelete = { wf -> viewModel.deleteWorkflow(wf.name) },
                        onShare = viewModel::shareWorkflow
                    )
                    2 -> {
                        MarketplaceScreen(
                            viewModel = marketplaceViewModel,
                            onBack = { }
                        )
                    }
                    3 -> ModelTabContent(viewModel = viewModel, state = state)
                    4 -> HistoryTabContent(viewModel = viewModel)
                }
            }
            BottomNavGlow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp)
                    .navigationBarsPadding()
            )
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
private fun ModelTabContent(
    viewModel: WorkflowGenerationViewModel,
    state: WorkflowGenerationUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Model Management",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight(700),
                    fontSize = 28.sp,
                    color = TextPrimary
                ),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            if (state.isLoadingModels) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CyanAccent,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = { viewModel.refreshAvailableModels() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }
        }

        Text(
            text = "IrisApp runs locally using Gemma 4 models. Download and select a model to enable offline AI generation.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (state.availableModels.isEmpty() && !state.isLoadingModels) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No models available. Check your internet connection.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary.copy(alpha = 0.5f)
                )
            }
        }

        state.availableModels.forEach { model ->
            ModelItemRowCompact(
                model = model,
                isBusy = state.isBusy,
                onDownload = { viewModel.downloadModel(model.id) },
                onSelect = { viewModel.selectModel(model.id) },
                inferenceState = state.inferenceState
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelItemRowCompact(
    model: com.irisapp.ui.home.ModelItemUiState,
    isBusy: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    inferenceState: InferenceState
) {
    val downloadState = inferenceState as? InferenceState.Downloading
    val isThisDownloading = downloadState != null && downloadState.modelId == model.fileName
    val isAnyDownloading = downloadState != null
    val isLoading = inferenceState is InferenceState.Loading
    val isReady = inferenceState is InferenceState.Ready

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (model.isActive && isReady) GreenSuccess else if (model.isActive) CyanAccent else if (isThisDownloading) VioletAccent else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = model.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (model.isActive) CyanAccent else if (isThisDownloading) VioletAccent else TextPrimary
                        )
                        if (model.isActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isReady) GreenSuccess.copy(alpha = 0.2f) else CyanAccent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isReady) "ACTIVE" else "LOADING",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = if (isReady) GreenSuccess else CyanAccent,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (model.isDownloaded) {
                    GradientButton(
                        text = if (model.isActive) "Selected" else "Use",
                        enabled = !isBusy && !model.isActive && !isAnyDownloading && !isLoading,
                        onClick = onSelect,
                        fillWidth = false,
                        modifier = Modifier.width(90.dp)
                    )
                } else {
                    OutlinedButton(
                        onClick = onDownload,
                        enabled = !isBusy && !isAnyDownloading && !isLoading,
                        border = BorderStroke(1.dp, if (!isBusy && !isAnyDownloading && !isLoading) CyanAccent.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(if (isThisDownloading) "Wait..." else "Download", fontSize = 12.sp)
                    }
                }
            }

            if (isThisDownloading && downloadState != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = VioletAccent,
                        trackColor = GlassBorder
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Downloading: ${"%.1f".format(downloadState.progress * 100f)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VioletAccent
                        )
                        Text(
                            text = "${formatFileSize(downloadState.downloadedBytes)} / ${formatFileSize(downloadState.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Text(
                    text = if (model.isDownloaded) "Downloaded • ${model.sizeLabel}" else "Available • ${model.sizeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (model.isDownloaded) GreenSuccess else VioletAccent
                )
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun WorkflowsTabContent(
    workflows: List<PlannedWorkflow>,
    onSelect: (PlannedWorkflow) -> Unit,
    onEdit: (PlannedWorkflow) -> Unit,
    onNew: () -> Unit,
    onDelete: (PlannedWorkflow) -> Unit,
    onShare: (PlannedWorkflow) -> Unit
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
            "Routines",
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
                "$sceneName Routines"
            } else "Saved Routines"

            Text(
                headerLabel,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = TextSecondary,
                    fontWeight = FontWeight(600)
                )
            )
            GradientButton(text = "+ New", onClick = onNew, fillWidth = false, modifier = Modifier.wrapContentWidth())
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
                                "No routines yet",
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
                                "No $sceneName routines",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                            )
                            TextButton(onClick = { selectedSceneId = null }) {
                                Text("Show all routines", color = CyanAccent)
                            }
                        }
                    }
                }
                else -> {
                    displayedWorkflows.forEach { workflow ->
                        WorkflowListCard(
                            workflow = workflow,
                            onSelect = { onSelect(workflow) },
                            onEdit = { onEdit(workflow) },
                            onDelete = { onDelete(workflow) },
                            onShare = { onShare(workflow) }
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var isEnabled by rememberSaveable(workflow.name) {
        mutableStateOf(workflow.trigger !is TriggerConfig.Manual)
    }
    var menuExpanded by remember { mutableStateOf(false) }

    val switchTrackColor by animateColorAsState(
        targetValue = if (isEnabled) ElectricCyan else GlassBorder,
        animationSpec = tween(300),
        label = "switch_track"
    )

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
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF000000),
                    checkedTrackColor = switchTrackColor,
                    uncheckedTrackColor = GlassBorder
                )
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(GlassSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("View", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onSelect() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = CyanAccent) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = VioletAccent) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = VioletAccent, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = AmberWarning) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
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

/** Describes a single permission with its human-readable label and grant instructions. */
private data class PermissionItem(
    val description: String,
    val permission: String,
    val instructions: String?
)

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

    // Human-readable permission descriptions and grant instructions.
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
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background location"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.READ_PHONE_STATE -> "Phone state"
            Manifest.permission.POST_NOTIFICATIONS -> "Post notifications"
            Manifest.permission.READ_CALENDAR -> "Read calendar"
            Manifest.permission.WRITE_CALENDAR -> "Write calendar"
            Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth"
            Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth scan"
            Manifest.permission.READ_MEDIA_AUDIO -> "Read audio files"
            Manifest.permission.READ_MEDIA_IMAGES -> "Read images"
            Manifest.permission.READ_MEDIA_VIDEO -> "Read videos"
            else -> permission.substringAfterLast(".")
        }
        val instructions = when (permission) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                "1. Tap 'All files' or 'Location > Allow all the time'\n" +
                "2. Select 'Allow' to let Iris run workflows in the background"
            Manifest.permission.POST_NOTIFICATIONS ->
                "1. Tap 'Notifications'\n" +
                "2. Select 'Allow' to receive workflow run status and alerts"
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO ->
                "1. Tap 'Files and media'\n" +
                "2. Select 'Allow access' to let Iris read your media files"
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG ->
                "1. Tap 'Contacts and call log'\n" +
                "2. Select 'Allow' to let Iris interact with your contacts"
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS ->
                "1. Tap 'SMS'\n" +
                "2. Select 'Allow' to let Iris read and send SMS messages"
            Manifest.permission.CAMERA ->
                "1. Tap 'Camera'\n" +
                "2. Select 'Allow' to let Iris use the camera"
            Manifest.permission.RECORD_AUDIO ->
                "1. Tap 'Microphone'\n" +
                "2. Select 'Allow' to let Iris record audio"
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE ->
                "1. Tap 'Files and media'\n" +
                "2. Select 'Allow' to let Iris access storage"
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR ->
                "1. Tap 'Calendar'\n" +
                "2. Select 'Allow' to let Iris manage your calendar"
            Manifest.permission.BLUETOOTH_CONNECT ->
                "1. Tap 'Nearby devices'\n" +
                "2. Select 'Allow' to let Iris control Bluetooth devices"
            else -> null
        }
        PermissionItem(description, permission, instructions)
    }

    AlertDialog(
        onDismissRequest = { /* Force explicit action */ },
        title = { Text("Permission Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Column(modifier = Modifier.padding(10.dp)) {
                        permissionDescriptions.forEach { item ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text("\uD83D\uDD12", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                item.instructions?.let { instructions ->
                                    Text(
                                        instructions,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                                    )
                                }
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
            Text(
                "Routine Detail",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight(700),
                    color = TextPrimary
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientOutlinedButton(text = "Edit", onClick = onEdit)
                GradientOutlinedButton(text = "Back", onClick = onBack)
            }
        }

        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(workflow.name, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                if (workflow.summary.isNotBlank()) {
                    Text(workflow.summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Text("Trigger: ${triggerLabel(workflow)}", style = MaterialTheme.typography.bodySmall, color = CyanAccent)
            }
        }

        Text("Steps", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight(700)))
        workflow.actions.forEach { step ->
            val spec = ActionSpecRegistry.find(step.id)
            val icon = stepIcon(step.id)
            val label = spec?.label ?: step.id
            val params = step.params.entries.joinToString(", ") { "${it.key}=${it.value}" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    if (params.isNotBlank()) {
                        Text(
                            params,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
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

        GradientButton(
            text = if (isBusy) "Running\u2026" else "Run Now",
            onClick = onRun,
            enabled = !isBusy
        )

        // Show "Schedule" or "Set up trigger" button based on trigger type
        val triggerConfig = workflow.trigger
        val showScheduleButton = triggerConfig is TriggerConfig.Manual || triggerConfig is TriggerConfig.Time
        val showShareSheetSetupButton = triggerConfig is TriggerConfig.ShareSheet

        if (showScheduleButton) {
            val label = if (triggerConfig is TriggerConfig.Time) "\u23F0 Edit Schedule" else "\u23F0 Schedule"
            GradientOutlinedButton(text = label, onClick = onSetupTrigger, modifier = Modifier.fillMaxWidth())
        }

        if (showShareSheetSetupButton) {
            GradientOutlinedButton(
                text = "\uD83D\uDCE4 Set up Share Sheet",
                onClick = onSetupTrigger,
                modifier = Modifier.fillMaxWidth()
            )
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
// Bottom Nav Glow (ambient particles above dock)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BottomNavGlow(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "hill_grid")

    // Scrolls the terrain toward the viewer
    val scrollPhase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "scroll"
    )
    // Slowly cycles the light color between cyan and violet — long duration keeps it smooth
    val lightCycle by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "light"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val cols  = 16
        val rows  = 22
        val vpX   = w * 0.5f    // vanishing point — horizontal center
        val vpY   = h * 0.02f   // vanishing point — near the top (where the dock light comes from)

        // Hill displacement at a grid vertex.
        // Scrolling in the −phase direction makes hills flow toward the viewer (row = rows).
        fun hillAt(col: Int, row: Int): Float {
            val xf    = col.toFloat() / cols
            val zf    = row.toFloat() / rows
            // phase completes exactly one full 2π cycle so both sines loop seamlessly on restart
            val phase = scrollPhase * 2f * PI.toFloat()
            val primary   = sin(zf * PI.toFloat() * 4f - phase) * 0.42f
            val secondary = sin(xf * PI.toFloat() * 3f + zf * PI.toFloat() * 2f - phase) * 0.20f
            return primary + secondary
        }

        // Perspective-project a grid vertex onto screen space.
        // row 0 = far / horizon (top of canvas), row = rows = near / viewer (bottom of canvas).
        fun project(col: Int, row: Int): Offset {
            val normX = col.toFloat() / cols - 0.5f   // −0.5 … +0.5
            val normZ = row.toFloat() / rows           //  0   …  1 (near)
            val hill  = hillAt(col, row)

            // Perspective factor — boosted so the grid overfills the screen width.
            // pz ranges from 0.50 (far/horizon) to 2.10 (near/viewer).
            val pz      = 0.50f + normZ * 1.60f
            val screenX = vpX + normX * w * pz
            // Base Y marches from vpY (far) down to h (near); hills displace upward.
            val baseY   = vpY + (h - vpY) * normZ
            val screenY = baseY - hill * (h - vpY) * 0.28f * pz
            return Offset(screenX, screenY)
        }

        // Color for a grid line: rows near the top (horizon / light source) are brighter.
        // Hill peaks catch more reflected light than valleys.
        fun lineColor(row: Int, hillAvg: Float): Color {
            val normRow     = row.toFloat() / rows
            val lightAmount = (1f - normRow * 0.72f) * 0.65f + ((hillAvg + 1f) * 0.15f).coerceIn(0f, 0.30f)
            val alpha       = (0.06f + lightAmount * 0.62f).coerceIn(0.03f, 0.75f)
            // Small per-row spatial tint (no modulo — eliminates color-wrap jumps)
            val colorT      = (lightCycle + normRow * 0.08f).coerceIn(0f, 1f)
            return lerp(ElectricCyan, LiquidViolet, colorT).copy(alpha = alpha)
        }

        // ── Horizontal lines (terrain rows — the "hill contours") ────────────
        for (row in 0..rows) {
            val path = Path()
            var moved = false
            for (col in 0..cols) {
                val pt = project(col, row)
                if (!moved) { path.moveTo(pt.x, pt.y); moved = true }
                else path.lineTo(pt.x, pt.y)
            }
            val avgHill = (0..cols).sumOf { hillAt(it, row).toDouble() }.toFloat() / (cols + 1)
            // Near rows are drawn thicker (stronger reflection from the dock light above)
            val sw = (0.3f + (row.toFloat() / rows) * 1.3f).dp.toPx()
            drawPath(path, lineColor(row, avgHill), style = Stroke(sw))
        }

        // ── Vertical lines (the "slope ribs" of the tilted grid) ─────────────
        for (col in 0..cols) {
            val path = Path()
            var moved = false
            for (row in 0..rows) {
                val pt = project(col, row)
                if (!moved) { path.moveTo(pt.x, pt.y); moved = true }
                else path.lineTo(pt.x, pt.y)
            }
            val avgHill = (0..rows).sumOf { hillAt(col, it).toDouble() }.toFloat() / (rows + 1)
            val base = lineColor(rows / 2, avgHill)
            drawPath(path, base.copy(alpha = base.alpha * 0.55f), style = Stroke(0.45f.dp.toPx()))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// History Tab
// ═══════════════════════════════════════════════════════════════
@Composable
private fun HistoryTabContent(viewModel: WorkflowGenerationViewModel) {
    val context = LocalContext.current
    val entries = remember { ExecutionHistoryRepository(context).recent(20) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "History",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight(700),
                color = TextPrimary
            )
        )
        if (entries.isEmpty()) {
            Text("No executions yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
            entries.reversed().forEach { entry ->
                HistoryEntryCard(entry = entry, onSaveToWidget = { viewModel.addToWidgetSuggestions(entry.workflowName) })
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun HistoryEntryCard(entry: ExecutionLogEntry, onSaveToWidget: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.workflowName, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                    Text(
                        formatRelativeTime(entry.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (entry.allSuccess) GreenSuccess else AmberWarning,
                                CircleShape
                            )
                    )
                    IconButton(onClick = onSaveToWidget, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = "Save to widget",
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                "${entry.results.size} step${if (entry.results.size != 1) "s" else ""} · ${entry.results.count { it.success }} succeeded",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    entry.results.forEach { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (result.success) "✓" else "✗",
                                color = if (result.success) GreenSuccess else AmberWarning,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Column {
                                Text(result.stepId, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                if (result.output.isNotBlank()) {
                                    Text(
                                        "→ ${result.output}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(timestampMillis: Long): String {
    val diff = System.currentTimeMillis() - timestampMillis
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
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
        Icons.Filled.Psychology,
        Icons.Filled.History
    )
    val tabLabels = listOf("Generate", "Routines", "Marketplace", "Models", "History")
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
