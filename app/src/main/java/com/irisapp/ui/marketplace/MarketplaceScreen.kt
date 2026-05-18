package com.irisapp.ui.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.irisapp.data.repository.MarketplaceEntry
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.ui.theme.BackgroundDark
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.GlassSurface
import com.irisapp.ui.theme.SurfaceDark
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary
import com.irisapp.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    // Username dialog — shown if no username set
    if (state.showUsernameDialog) {
        UsernameDialog(
            onConfirm = { name -> viewModel.setUsername(name) }
        )
    }

    // Publish dialog
    if (state.showPublishDialog) {
        PublishDialog(
            workflows = state.publishWorkflows,
            selectedWorkflow = state.selectedPublishWorkflow,
            tags = state.publishTags,
            isLoading = state.isLoading,
            onSelectWorkflow = { viewModel.selectPublishWorkflow(it) },
            onTagsChange = { viewModel.setPublishTags(it) },
            onPublish = { viewModel.publish() },
            onDismiss = { viewModel.dismissPublishDialog() }
        )
    }

    // Import confirm dialog
    if (state.showImportConfirm && state.selectedEntry != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportConfirm() },
            title = { Text("Import Workflow") },
            text = {
                Text("Import \"${state.selectedEntry!!.workflow.name}\" by ${state.selectedEntry!!.author}? It will be saved to your local workflows.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
                    Text("Import", color = CyanAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissImportConfirm() }) {
                    Text("Cancel")
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Error snackbar
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Publish success toast
    if (state.publishSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPublishSuccess() },
            title = { Text("Published!") },
            text = { Text("Your workflow is now visible in the marketplace.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPublishSuccess() }) {
                    Text("OK")
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Preview bottom sheet
    if (state.selectedEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelection() },
            sheetState = sheetState,
            containerColor = SurfaceDark
        ) {
            PreviewBottomSheet(
                entry = state.selectedEntry!!,
                onImport = { viewModel.showImportConfirm() },
                onDelete = { viewModel.deleteEntry(state.selectedEntry!!); viewModel.clearSelection() },
                isOwn = state.selectedEntry!!.author == state.username
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Marketplace",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showPublishDialog() },
                containerColor = CyanAccent
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Publish workflow",
                    tint = BackgroundDark
                )
            }
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        if (state.isLoading && state.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "Published by me" section
                if (state.myEntries.isNotEmpty()) {
                    item {
                        Text(
                            text = "📌  Published by me",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyanAccent,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(state.myEntries, key = { "my-${it.id}" }) { entry ->
                        MarketplaceCard(
                            entry = entry,
                            onClick = { viewModel.selectEntry(entry) },
                            showDelete = true,
                            onDelete = { viewModel.deleteEntry(entry) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🌐  All Workflows",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // All entries
                if (state.entries.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val emptyIconComposition by rememberLottieComposition(
                                LottieCompositionSpec.Asset("lottie_marketplace.json")
                            )
                            val emptyIconProgress by animateLottieCompositionAsState(
                                emptyIconComposition, iterations = LottieConstants.IterateForever
                            )
                            if (emptyIconComposition != null) {
                                LottieAnimation(
                                    composition = emptyIconComposition,
                                    progress = { emptyIconProgress },
                                    modifier = Modifier.size(72.dp)
                                )
                            } else {
                                Text(text = "🛒", style = MaterialTheme.typography.displayLarge)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No workflows published yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Be the first to share a workflow!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        MarketplaceCard(
                            entry = entry,
                            onClick = { viewModel.selectEntry(entry) },
                            showDelete = false,
                            onDelete = {}
                        )
                    }
                }
            }
        }
    }
}

// ── MarketplaceCard ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketplaceCard(
    entry: MarketplaceEntry,
    onClick: () -> Unit,
    showDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.workflow.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${entry.downloads}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                if (entry.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        entry.tags.forEach { tag ->
                            TagChip(tag = tag)
                        }
                    }
                }
            }
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Surface(
        color = CyanAccent.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = CyanAccent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ── PreviewBottomSheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewBottomSheet(
    entry: MarketplaceEntry,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    isOwn: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = entry.workflow.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Text(text = "by ", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(text = entry.author, style = MaterialTheme.typography.bodyMedium, color = CyanAccent)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Trigger label
        val triggerLabel = when (entry.workflow.trigger) {
            is TriggerConfig.Manual -> "Manual"
            is TriggerConfig.Time -> "Scheduled"
            is TriggerConfig.Nfc -> "NFC"
            is TriggerConfig.Voice -> "Voice"
            is TriggerConfig.SoundEvent -> "Sound Event"
            is TriggerConfig.Battery -> "Battery"
            is TriggerConfig.Charger -> "Charger"
            is TriggerConfig.WiFi -> "WiFi"
            is TriggerConfig.Bluetooth -> "Bluetooth"
            is TriggerConfig.Geofence -> "Geofence"
            is TriggerConfig.SleepProxy -> "Sleep"
            is TriggerConfig.AppOpened -> "App Opened"
            is TriggerConfig.AppClosed -> "App Closed"
            is TriggerConfig.SmsReceived -> "SMS"
            is TriggerConfig.NotificationListenerConfig -> "Notification"
            is TriggerConfig.EmailReceived -> "Email"
            is TriggerConfig.ShareSheet -> "Share Sheet"
            is TriggerConfig.AlarmStopped -> "Alarm Stopped"
            is TriggerConfig.AirplaneMode -> "Airplane Mode"
            is TriggerConfig.DoNotDisturb -> "Do Not Disturb"
        }
        DetailRow(label = "Trigger", value = triggerLabel)
        DetailRow(label = "Actions", value = "${entry.workflow.actions.size} steps")
        DetailRow(label = "Downloads", value = "${entry.downloads}")
        DetailRow(label = "Published", value = formatDate(entry.createdAt))

        if (entry.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                entry.tags.forEach { tag -> TagChip(tag = tag) }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (entry.workflow.summary.isNotEmpty()) {
            Text(
                text = entry.workflow.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isOwn) {
            // Own entry: show Delete button (removes from marketplace) and Import button (installs locally)
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove from marketplace")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = BackgroundDark
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Install locally", color = BackgroundDark)
            }
        } else {
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = BackgroundDark
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import to my workflows", color = BackgroundDark)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

// ── UsernameDialog ────────────────────────────────────────────────────────────

@Composable
private fun UsernameDialog(onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = "Choose a username",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "This name will be shown on your published workflows. No account needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = TextTertiary,
                        focusedLabelColor = CyanAccent,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = CyanAccent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().length >= 2
            ) {
                Text("Start", color = CyanAccent)
            }
        },
        dismissButton = {},
        containerColor = SurfaceDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

// ── PublishDialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PublishDialog(
    workflows: List<com.irisapp.domain.model.PlannedWorkflow>,
    selectedWorkflow: com.irisapp.domain.model.PlannedWorkflow?,
    tags: String,
    isLoading: Boolean,
    onSelectWorkflow: (com.irisapp.domain.model.PlannedWorkflow) -> Unit,
    onTagsChange: (String) -> Unit,
    onPublish: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Publish to Marketplace",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select a workflow to share with other users.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedWorkflow?.name ?: "Select workflow…",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = TextTertiary,
                            focusedLabelColor = CyanAccent,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = CyanAccent
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        workflows.forEach { workflow ->
                            DropdownMenuItem(
                                text = { Text(workflow.name, color = TextPrimary) },
                                onClick = {
                                    onSelectWorkflow(workflow)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = tags,
                    onValueChange = onTagsChange,
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = TextTertiary,
                        focusedLabelColor = CyanAccent,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = CyanAccent
                    )
                )
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onPublish,
                enabled = selectedWorkflow != null && !isLoading
            ) {
                Text("Publish", color = CyanAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = SurfaceDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun formatDate(timestamp: Long): String {
    return try {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        "Unknown"
    }
}