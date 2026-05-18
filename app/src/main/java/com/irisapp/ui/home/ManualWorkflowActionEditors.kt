package com.irisapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irisapp.domain.catalog.*
import com.irisapp.domain.model.WorkflowStep
import com.irisapp.ui.components.GlassmorphicCard
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
internal fun ActionStepCard(
    step: WorkflowStep,
    stepNumber: Int,
    label: String,
    isValid: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "$stepNumber.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                if (step.params.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            step.params.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionEditDialog(
    step: WorkflowStep,
    onSave: (WorkflowStep) -> Unit,
    onDismiss: () -> Unit
) {
    val allActions = remember { ActionSpecRegistry.all.filter { it.execution.isRunnable() }.sortedBy { it.label } }
    var selectedActionId by remember { mutableStateOf(step.id) }
    var params by remember {
        mutableStateOf(
            step.params.entries.associate { it.key to (it.value.toString().removeSurrounding("\"")) }
                .toMutableMap()
        )
    }
    val validationErrors = remember(selectedActionId, params) {
        val preview = WorkflowStep(id = selectedActionId, params = buildJsonObject {
            params.forEach { (k, v) -> put(k, v) }
        })
        validateStep(preview)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (validationErrors.isEmpty()) "Edit Action" else "Edit Action — ${validationErrors.size} issue${if (validationErrors.size != 1) "s" else ""}")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Action picker
                var expanded by remember { mutableStateOf(false) }
                val selectedSpec = ActionSpecRegistry.find(selectedActionId)

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedSpec?.label ?: selectedActionId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        allActions.forEach { spec ->
                            DropdownMenuItem(
                                text = { Text(spec.label) },
                                onClick = {
                                    selectedActionId = spec.id
                                    params = spec.params.filter { p -> p.required }
                                        .associate { it.name to "" }
                                        .toMutableMap()
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Params
                val spec = ActionSpecRegistry.find(selectedActionId)
                val context = LocalContext.current
                if (spec != null) {
                    spec.params.forEach { param ->
                        val paramName = param.name
                        val paramErrors = validationErrors.filter { it.paramName == paramName }

                        when (param.type) {
                            ParamType.AppPicker -> {
                                var appExpanded by remember { mutableStateOf(false) }
                                var searchQuery by remember { mutableStateOf(params[paramName] ?: "") }
                                val providerId = spec.installedAppListProviderId ?: "installed_apps"
                                val allApps = remember { ActionSpecRegistry.getInstalledAppList(providerId, context) }
                                val filteredApps = remember(searchQuery) {
                                    if (searchQuery.isBlank()) allApps
                                    else allApps.filter { (label, pkg) ->
                                        label.contains(searchQuery, ignoreCase = true) ||
                                                pkg.contains(searchQuery, ignoreCase = true)
                                    }
                                }
                                val selectedLabel = allApps.find { it.second == params[paramName] }?.first
                                    ?: params[paramName] ?: "Select an app"

                                ExposedDropdownMenuBox(expanded = appExpanded, onExpandedChange = { appExpanded = it }) {
                                    OutlinedTextField(
                                        value = selectedLabel,
                                        onValueChange = { searchQuery = it },
                                        label = { Text(paramName) },
                                        placeholder = { Text("Search installed apps...") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        singleLine = true,
                                        isError = paramErrors.isNotEmpty(),
                                        supportingText = {
                                            if (paramErrors.isNotEmpty()) {
                                                Text(paramErrors.joinToString { it.message }, color = MaterialTheme.colorScheme.error)
                                            } else {
                                                Text("Type to filter — tap a result to select", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    )
                                    val appsToShow = filteredApps.take(50)
                                    if (appsToShow.isNotEmpty()) {
                                        ExposedDropdownMenu(
                                            expanded = appExpanded,
                                            onDismissRequest = { appExpanded = false }
                                        ) {
                                            appsToShow.forEach { (label, pkg) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                                                        }
                                                    },
                                                    onClick = {
                                                        params = params.toMutableMap().apply { put(paramName, pkg) }
                                                        searchQuery = label
                                                        appExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                OutlinedTextField(
                                    value = params[paramName] ?: "",
                                    onValueChange = { newValue ->
                                        params = params.toMutableMap().apply { put(paramName, newValue) }
                                    },
                                    label = {
                                        Text(
                                            paramName + if (!param.required) " (optional)" else ""
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    isError = paramErrors.isNotEmpty(),
                                    supportingText = {
                                        if (paramErrors.isNotEmpty()) {
                                            Text(paramErrors.joinToString { it.message }, color = MaterialTheme.colorScheme.error)
                                        } else if (param.description.isNotBlank()) {
                                            Text(param.description, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                // Show ID-level errors (unknown action)
                val idErrors = validationErrors.filter { it.paramName == "id" }
                idErrors.forEach { error ->
                    Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            val canSave = validationErrors.isEmpty()
            com.irisapp.ui.components.GradientButton(
                text = "Save",
                onClick = {
                    val json = buildJsonObject {
                        params.forEach { (k, v) -> put(k, v) }
                    }
                    onSave(WorkflowStep(id = selectedActionId, params = json))
                },
                enabled = canSave,
                fillWidth = false
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun ExecutionSpec.isRunnable(): Boolean = this !is ExecutionSpec.PackageLaunch

internal data class ActionValidationError(val paramName: String, val message: String)

/** Validates a WorkflowStep against its ActionSpec. Returns empty list if valid. */
internal fun validateStep(step: WorkflowStep): List<ActionValidationError> {
    val spec = ActionSpecRegistry.find(step.id)
    if (spec == null) return listOf(ActionValidationError("id", "Unknown action: ${step.id}"))

    val errors = mutableListOf<ActionValidationError>()
    val params = step.params.entries.associate { it.key to it.value.toString().removeSurrounding("\"") }

    // Check required params are present and non-empty
    spec.params.filter { it.required }.forEach { param ->
        val value = params[param.name]
        if (value.isNullOrBlank()) {
            errors.add(ActionValidationError(param.name, "${param.name} is required"))
        }
    }

    // Validate param types
    spec.params.forEach { param ->
        val value = params[param.name] ?: return@forEach
        if (value.isBlank()) return@forEach

        when (param.type) {
            com.irisapp.domain.catalog.ParamType.Url -> {
                if (!value.startsWith("http://") && !value.startsWith("https://") && !value.startsWith("content://")) {
                    errors.add(ActionValidationError(param.name, "Must be a valid URL (http:// or https://)"))
                }
            }
            com.irisapp.domain.catalog.ParamType.Int -> {
                if (value.toIntOrNull() == null) {
                    errors.add(ActionValidationError(param.name, "Must be a whole number"))
                }
            }
            com.irisapp.domain.catalog.ParamType.Long,
            com.irisapp.domain.catalog.ParamType.DateTimeMillis -> {
                if (value.toLongOrLongOrNull() == null) {
                    errors.add(ActionValidationError(param.name, "Must be a number"))
                }
            }
            else -> { /* string/uri/enum — no type check */ }
        }
    }

    return errors
}

private fun String.toLongOrLongOrNull(): Long? = this.toLongOrNull()

/** True if the step has no validation errors. */
internal fun isStepValid(step: WorkflowStep): Boolean = validateStep(step).isEmpty()

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AppPatternEditor(
    patterns: List<String>,
    onPatternsChange: (List<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val allApps = remember { ActionSpecRegistry.getInstalledAppList("installed_apps", context) }
    val filteredApps = remember(searchQuery) {
        if (searchQuery.isBlank()) allApps.take(50)
        else allApps.filter { (label, pkg) ->
            label.contains(searchQuery, ignoreCase = true) || pkg.contains(searchQuery, ignoreCase = true)
        }.take(50)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Show selected patterns as chips
        if (patterns.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                patterns.forEach { pattern ->
                    InputChip(
                        label = { Text(pattern, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = false,
                        onClick = { onPatternsChange(patterns - pattern) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }

        // Search + add from installed apps
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Add app pattern") },
                placeholder = { Text("Search installed apps...") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true
            )
            if (filteredApps.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    filteredApps.forEach { (label, pkg) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pkg, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                                }
                            },
                            onClick = {
                                if (!patterns.contains(pkg)) {
                                    onPatternsChange(patterns + pkg)
                                }
                                searchQuery = ""
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Manual text input for additional patterns
        var manualInput by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = { Text("Or enter package pattern") },
                placeholder = { Text("e.g. com.instagram") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(
                onClick = {
                    val trimmed = manualInput.trim()
                    if (trimmed.isNotEmpty() && !patterns.contains(trimmed)) {
                        onPatternsChange(patterns + trimmed)
                    }
                    manualInput = ""
                },
                enabled = manualInput.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add pattern")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputChip(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        trailingIcon = trailingIcon
    )
}
