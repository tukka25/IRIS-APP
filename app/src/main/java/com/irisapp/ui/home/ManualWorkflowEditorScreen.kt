package com.irisapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.irisapp.domain.catalog.ActionSpecRegistry
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.WorkflowStep
import com.irisapp.ui.components.GradientButton
import com.irisapp.ui.components.GradientOutlinedButton
import com.irisapp.ui.theme.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * State holder for the Manual Workflow Editor.
 */
internal class ManualWorkflowEditorState(initialWorkflow: PlannedWorkflow?) {
    var name by mutableStateOf(initialWorkflow?.name ?: "")
    var summary by mutableStateOf(initialWorkflow?.summary ?: "")
    val triggerState = TriggerEditorState(initialWorkflow?.trigger)
    var steps by mutableStateOf(
        initialWorkflow?.actions
            ?: listOf(WorkflowStep(id = "browser.open_url", params = buildJsonObject { put("url", "") }))
    )
    var editingStepIndex by mutableIntStateOf(-1)

    fun buildWorkflow(): PlannedWorkflow {
        return PlannedWorkflow(
            name = name.ifBlank { "Untitled" },
            summary = summary,
            trigger = triggerState.build(),
            actions = steps
        )
    }
}

/**
 * Full-screen editor for building or editing a workflow manually — without AI generation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualWorkflowEditorScreen(
    initialWorkflow: PlannedWorkflow?,
    onSave: (PlannedWorkflow) -> Unit,
    onCancel: () -> Unit
) {
    val state = remember { ManualWorkflowEditorState(initialWorkflow) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        EditorTopBar(state, onSave, onCancel)

        Text(
            text = if (initialWorkflow != null) "Edit Workflow" else "New Workflow",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        // ── Basic fields ────────────────────────────────────────────────────────
        BasicInfoFields(state)

        HorizontalDivider()

        // ── Trigger Section ─────────────────────────────────────────────────────
        TriggerSection(state.triggerState)

        HorizontalDivider()

        // ── Actions Section ─────────────────────────────────────────────────────
        ActionsSection(state)

        // ── Bottom Buttons ──────────────────────────────────────────────────────
        EditorBottomButtons(state, onSave, onCancel)

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ── Action editor dialog ─────────────────────────────────────────────
    if (state.editingStepIndex >= 0) {
        ActionEditDialog(
            step = state.steps[state.editingStepIndex],
            onSave = { updated ->
                state.steps = state.steps.toMutableList().apply { set(state.editingStepIndex, updated) }
                state.editingStepIndex = -1
            },
            onDismiss = { state.editingStepIndex = -1 }
        )
    }
}

@Composable
private fun EditorTopBar(
    state: ManualWorkflowEditorState,
    onSave: (PlannedWorkflow) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        val brush = Brush.linearGradient(
                            colors = listOf(CyanVioletStart, CyanVioletEnd),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(
                                brush = brush,
                                blendMode = BlendMode.SrcIn
                            )
                        }
                    }
            )
        }
        GradientButton(
            text = "Save",
            onClick = { onSave(state.buildWorkflow()) },
            modifier = Modifier.height(36.dp),
            enabled = state.name.isNotBlank() && state.steps.isNotEmpty() && state.steps.all { isStepValid(it) },
            fillWidth = false
        )
    }
}

@Composable
private fun BasicInfoFields(state: ManualWorkflowEditorState) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { state.name = it },
        label = { Text("Workflow name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = GlassBorder,
            focusedLabelColor = CyanAccent,
            unfocusedLabelColor = TextSecondary,
            cursorColor = CyanAccent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )

    OutlinedTextField(
        value = state.summary,
        onValueChange = { state.summary = it },
        label = { Text("Summary (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = GlassBorder,
            focusedLabelColor = CyanAccent,
            unfocusedLabelColor = TextSecondary,
            cursorColor = CyanAccent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

@Composable
private fun ActionsSection(state: ManualWorkflowEditorState) {
    Text("Actions", style = MaterialTheme.typography.titleMedium)

    Text(
        text = "${state.steps.size} step${if (state.steps.size != 1) "s" else ""}",
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary
    )

    state.steps.forEachIndexed { index, step ->
        val spec = ActionSpecRegistry.find(step.id)
        val stepValid = isStepValid(step)
        ActionStepCard(
            step = step,
            stepNumber = index + 1,
            label = spec?.label ?: step.id,
            isValid = spec != null && stepValid,
            onEdit = { state.editingStepIndex = index },
            onDelete = {
                if (state.steps.size > 1) {
                    state.steps = state.steps.toMutableList().apply { removeAt(index) }
                    if (state.editingStepIndex == index) state.editingStepIndex = -1
                }
            }
        )
    }

    GradientOutlinedButton(
        text = "+ Add Action",
        onClick = {
            state.steps = state.steps + WorkflowStep(id = "browser.open_url", params = buildJsonObject { put("url", "") })
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EditorBottomButtons(
    state: ManualWorkflowEditorState,
    onSave: (PlannedWorkflow) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GradientButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        GradientButton(
            text = "Save",
            onClick = { onSave(state.buildWorkflow()) },
            modifier = Modifier.weight(1f),
            enabled = state.name.isNotBlank() && state.steps.isNotEmpty() && state.steps.all { isStepValid(it) }
        )
    }
}
