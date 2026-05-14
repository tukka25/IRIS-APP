package com.gemmaworkflow.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.sound.YamnetClassifier
import com.gemmaworkflow.platform.trigger.sound.SoundEventTriggerRegistry
import com.gemmaworkflow.platform.trigger.sound.SoundEventTriggerService

/**
 * Screen for configuring a sound-event trigger.
 *
 * Shows a searchable list of all 521 YAMNet AudioSet classes.
 * User selects one or more sounds and assigns a saved workflow to fire
 * when any of those sounds are detected.
 *
 * Navigation: opened from [WorkflowDetailScreen] when a workflow's trigger
 * is [TriggerConfig.SoundEvent], or via the "Add Sound Trigger" button.
 *
 * @param savedWorkflowNames  All saved workflow names — user picks one as the target.
 * @param currentMappings    Currently registered sound→workflow pairs (for edit mode).
 * @param onSave             Called with the selected workflow name and sound classes on save.
 * @param onCancel           Dismiss without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundEventTriggerSetupScreen(
    savedWorkflowNames: List<String>,
    currentMappings: Map<String, String> = emptyMap(),
    onSave: (targetWorkflow: String, soundClasses: Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // All available YAMNet sound class names
    val allSounds = remember { YamnetClassifier.AUDIOSET_CLASSES.toList() }

    // Selected sound classes (toggle on/off)
    var selectedSounds by remember { mutableStateOf(currentMappings.keys) }

    // Search query
    var searchQuery by remember { mutableStateOf("") }

    // Target workflow name
    var selectedWorkflow by remember {
        mutableStateOf(currentMappings.values.firstOrNull() ?: savedWorkflowNames.firstOrNull() ?: "")
    }

    // Filtered sound list
    val filteredSounds = remember(searchQuery) {
        if (searchQuery.isBlank()) allSounds
        else allSounds.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sound Event Trigger") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedSounds.isNotEmpty() && selectedWorkflow.isNotBlank()) {
                                onSave(selectedWorkflow, selectedSounds)
                            }
                        },
                        enabled = selectedSounds.isNotEmpty() && selectedWorkflow.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Target workflow selector ─────────────────────────────────────────
            Text(
                text = "When these sounds are detected, run:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Workflow chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                savedWorkflowNames.take(5).forEach { name ->
                    FilterChip(
                        selected = selectedWorkflow == name,
                        onClick = { selectedWorkflow = name },
                        label = { Text(name, maxLines = 1) },
                        leadingIcon = if (selectedWorkflow == name) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.height(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Registered sounds summary ───────────────────────────────────────
            if (selectedSounds.isNotEmpty()) {
                Text(
                    text = "${selectedSounds.size} sound(s) selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Search ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search sounds") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Sound list ──────────────────────────────────────────────────────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredSounds, key = { it }) { soundClass ->
                    val isSelected = soundClass in selectedSounds
                    SoundClassRow(
                        soundClass = soundClass,
                        isSelected = isSelected,
                        registeredWorkflow = currentMappings[soundClass],
                        onToggle = {
                            selectedSounds = if (isSelected) {
                                selectedSounds - soundClass
                            } else {
                                selectedSounds + soundClass
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundClassRow(
    soundClass: String,
    isSelected: Boolean,
    registeredWorkflow: String?,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = soundClass,
                    style = MaterialTheme.typography.bodyMedium
                )
                registeredWorkflow?.let {
                    Text(
                        text = "→ $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
