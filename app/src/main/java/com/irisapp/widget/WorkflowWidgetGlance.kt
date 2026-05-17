package com.irisapp.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.irisapp.R
import com.irisapp.ui.theme.AmberWarning
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.GreenSuccess
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ObsidianDark = Color(0xFF050509.toInt())

class WorkflowWidgetGlance : GlanceAppWidget() {

    override val stateDefinition = WidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { MiniDashboard() }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(WorkflowWidgetGlance::class.java).forEach { glanceId ->
                WorkflowWidgetGlance().update(context, glanceId)
            }
        }
    }
}

@Composable
private fun MiniDashboard() {
    val state = currentState<IrisWidgetState>()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(ObsidianDark))
            .padding(10.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "IRIS",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorProvider(CyanAccent))
            )
            Text(
                text = if (state.activeWorkflowName != null && state.slmState != SlmProcessState.IDLE)
                    "  ·  ${state.activeWorkflowName}"
                else
                    "  Mini Dashboard",
                style = TextStyle(fontSize = 11.sp, color = ColorProvider(TextSecondary)),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // ── SLM Monitor Bar ───────────────────────────────────────────────────
        SlmMonitorBar(state)

        // ── Dynamic content based on state ────────────────────────────────────
        when (state.slmState) {
            SlmProcessState.IDLE -> {
                if (state.suggestions.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    SuggestionCarousel(state.suggestions)
                }
                if (state.recentResults.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    HistoryStrip(state.recentResults)
                }
            }

            SlmProcessState.ANALYZING, SlmProcessState.TOOL_CALL -> {
                // Show live step progress
                if (state.currentStep != null) {
                    Spacer(modifier = GlanceModifier.height(5.dp))
                    CurrentStepRow(state.currentStep)
                }
                if (state.stepLogs.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    StepLogsPanel(state.stepLogs)
                }
            }

            SlmProcessState.SUCCESS, SlmProcessState.ERROR -> {
                // Show completed run summary
                if (state.stepLogs.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(5.dp))
                    StepLogsPanel(state.stepLogs)
                }
                if (state.recentResults.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    HistoryStrip(state.recentResults)
                }
            }
        }
    }
}

// ── SLM Monitor Bar ───────────────────────────────────────────────────────────

@Composable
private fun SlmMonitorBar(state: IrisWidgetState) {
    val bgDrawable = when (state.slmState) {
        SlmProcessState.IDLE      -> R.drawable.widget_state_idle
        SlmProcessState.ANALYZING -> R.drawable.widget_state_cyan
        SlmProcessState.TOOL_CALL -> R.drawable.widget_state_violet
        SlmProcessState.SUCCESS   -> R.drawable.widget_state_success
        SlmProcessState.ERROR     -> R.drawable.widget_state_error
    }
    val dotColor = Color(state.slmState.colorHex.toInt())

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(bgDrawable))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text("●  ", style = TextStyle(fontSize = 11.sp, color = ColorProvider(dotColor)))
        Text(
            text = state.slmState.displayString,
            style = TextStyle(fontSize = 11.sp, color = ColorProvider(TextPrimary)),
            maxLines = 1
        )
    }
}

// ── Live step indicator ───────────────────────────────────────────────────────

@Composable
private fun CurrentStepRow(stepName: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_state_cyan))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text("⋯  ", style = TextStyle(fontSize = 10.sp, color = ColorProvider(CyanAccent)))
        Text(
            text = stepName,
            style = TextStyle(fontSize = 10.sp, color = ColorProvider(CyanAccent)),
            maxLines = 1
        )
    }
}

// ── Step log panel ────────────────────────────────────────────────────────────

@Composable
private fun StepLogsPanel(logs: List<StepLog>) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        logs.takeLast(5).forEach { log ->
            StepLogRow(log)
        }
    }
}

@Composable
private fun StepLogRow(log: StepLog) {
    val iconColor = ColorProvider(if (log.success) GreenSuccess else AmberWarning)
    val icon = if (log.success) "✓" else "✗"

    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 3.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "$icon  ",
                style = TextStyle(fontSize = 10.sp, color = iconColor)
            )
            Text(
                text = log.stepName,
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, color = ColorProvider(TextPrimary)),
                maxLines = 1,
                modifier = GlanceModifier.width(0.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            // Wall-clock time of completion + duration
            Text(
                text = "${formatTime(log.timestampMs)}  ${formatDuration(log.durationMs)}",
                style = TextStyle(fontSize = 9.sp, color = ColorProvider(TextSecondary))
            )
        }
        if (log.output.isNotBlank()) {
            Text(
                text = "     → ${log.output}",
                style = TextStyle(fontSize = 9.sp, color = ColorProvider(TextSecondary)),
                maxLines = 1,
                modifier = GlanceModifier.padding(bottom = 1.dp)
            )
        }
    }
}

// ── Suggestion carousel ───────────────────────────────────────────────────────

@Composable
private fun SuggestionCarousel(suggestions: List<String>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        suggestions.take(3).forEachIndexed { index, name ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
            SuggestionPill(name)
        }
    }
}

@Composable
private fun SuggestionPill(name: String) {
    Text(
        text = name,
        style = TextStyle(fontSize = 9.sp, color = ColorProvider(TextPrimary)),
        maxLines = 1,
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.glass_pill_background))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(
                actionRunCallback<TriggerWorkflowAction>(
                    actionParametersOf(TriggerWorkflowAction.KEY_WORKFLOW_NAME to name)
                )
            )
    )
}

// ── History strip ─────────────────────────────────────────────────────────────

@Composable
private fun HistoryStrip(recentResults: List<Boolean>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text("History  ", style = TextStyle(fontSize = 9.sp, color = ColorProvider(TextSecondary)))
        recentResults.forEach { ok ->
            Text(
                text = "●",
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(if (ok) GreenSuccess else AmberWarning)),
                modifier = GlanceModifier.padding(end = 3.dp)
            )
        }
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(ms: Long): String =
    if (ms > 0L) timeFmt.format(Date(ms)) else ""

private fun formatDuration(ms: Long): String = when {
    ms <= 0L  -> ""
    ms < 1000 -> "${ms}ms"
    else      -> "${"%.1f".format(ms / 1000.0)}s"
}
