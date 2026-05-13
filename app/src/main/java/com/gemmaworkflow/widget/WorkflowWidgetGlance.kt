package com.gemmaworkflow.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gemmaworkflow.R
import com.gemmaworkflow.data.repository.ExecutionHistoryRepository
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.platform.nfc.DeepLinkRouter
import com.gemmaworkflow.ui.MainActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Snapshot ──────────────────────────────────────────────────────────────────

private data class WidgetSnapshot(
    val workflowName: String,
    val triggerDisplay: String,
    val createdAtMillis: Long,
    val actionLabels: List<String>,
    val totalRuns: Int,
    val successCount: Int,
    val lastRunMillis: Long,
    val lastRunResults: List<Pair<String, Boolean>>,
    val recentHistory: List<Boolean>
) {
    val successRate: Int get() = if (totalRuns > 0) (successCount * 100) / totalRuns else 0
}

// ── Widget ────────────────────────────────────────────────────────────────────

class WorkflowWidgetGlance : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val workflowName = WidgetPreferences.getWorkflowName(context, appWidgetId)

        val snapshot: WidgetSnapshot? = if (workflowName == null) null else {
            withContext(Dispatchers.IO) { buildSnapshot(context, workflowName) }
        }

        provideContent {
            if (snapshot == null) UnconfiguredContent() else DashboardContent(snapshot)
        }
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

private fun buildSnapshot(context: Context, workflowName: String): WidgetSnapshot {
    val workflow = WorkflowRepository(context).get(workflowName)
    val history = ExecutionHistoryRepository(context).forWorkflow(workflowName)

    val createdAt = File(context.filesDir, "workflows/$workflowName.json")
        .let { if (it.exists()) it.lastModified() else 0L }

    val triggerDisplay = when (val t = workflow?.trigger) {
        is TriggerConfig.Time -> {
            val days = if (t.repeatDays.isEmpty()) context.getString(R.string.widget_trigger_daily)
                       else t.repeatDays.joinToString("·") { dayAbbrev(it) }
            "⏰ %02d:%02d %s".format(t.hour, t.minute, days)
        }
        is TriggerConfig.Nfc -> context.getString(R.string.widget_trigger_nfc)
        is TriggerConfig.ShareSheet -> context.getString(R.string.widget_trigger_share_sheet)
        is TriggerConfig.Manual, null -> context.getString(R.string.widget_trigger_manual)
        is TriggerConfig.AlarmStopped -> context.getString(R.string.widget_trigger_alarm_stopped)
        is TriggerConfig.AppOpened,
        is TriggerConfig.AppClosed -> context.getString(R.string.widget_trigger_app)
        is TriggerConfig.SmsReceived,
        is TriggerConfig.NotificationListenerConfig,
        is TriggerConfig.EmailReceived -> context.getString(R.string.widget_trigger_notification)
        is TriggerConfig.SleepProxy -> context.getString(R.string.widget_trigger_sleep)
        is TriggerConfig.Geofence -> context.getString(R.string.widget_trigger_geofence)
        is TriggerConfig.Battery,
        is TriggerConfig.Charger,
        is TriggerConfig.WiFi,
        is TriggerConfig.Bluetooth,
        is TriggerConfig.AirplaneMode,
        is TriggerConfig.DoNotDisturb -> context.getString(R.string.widget_trigger_system)
        else -> context.getString(R.string.widget_trigger_manual)
    }

    val actionLabels = workflow?.actions
        ?.mapNotNull { step -> ActionSpecRegistry.find(step.id)?.label }
        ?: emptyList()

    val lastEntry = history.lastOrNull()
    val lastRunResults = lastEntry?.results?.map { result ->
        val label = ActionSpecRegistry.find(result.stepId)?.label ?: result.stepId
        Pair(label, result.success)
    } ?: emptyList()

    return WidgetSnapshot(
        workflowName = workflowName,
        triggerDisplay = triggerDisplay,
        createdAtMillis = createdAt,
        actionLabels = actionLabels,
        totalRuns = history.size,
        successCount = history.count { it.allSuccess },
        lastRunMillis = lastEntry?.timestampMillis ?: 0L,
        lastRunResults = lastRunResults,
        recentHistory = history.takeLast(5).map { it.allSuccess }
    )
}

private fun dayAbbrev(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Su"; 2 -> "Mo"; 3 -> "Tu"; 4 -> "We"
    5 -> "Th"; 6 -> "Fr"; 7 -> "Sa"
    else -> "$dayOfWeek"
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun UnconfiguredContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFF2F2F7)))
            .padding(12.dp)
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ColorProvider(Color(0xFF000000))
            )
        )
        Text(
            text = context.getString(R.string.widget_unconfigured_instruction),
            style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color.Gray)),
            modifier = GlanceModifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun DashboardContent(s: WidgetSnapshot) {
    val context = LocalContext.current
    val tapAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = DeepLinkRouter.ACTION_SHOW_WORKFLOW_DETAIL
            putExtra(DeepLinkRouter.EXTRA_WORKFLOW_ID, s.workflowName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFF2F2F7)))
            .padding(10.dp)
            .clickable(tapAction)
    ) {

        // ── Header: name ──────────────────────────────────────────────────────
        Text(
            text = s.workflowName,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ColorProvider(Color(0xFF000000))
            ),
            maxLines = 1
        )

        // Trigger + created subtitle
        val createdStr = if (s.createdAtMillis > 0L) {
            "  ·  " + context.getString(
                R.string.widget_created_ago,
                formatRelativeTime(context, s.createdAtMillis)
            )
        } else ""
        Text(
            text = s.triggerDisplay + createdStr,
            style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF007AFF))),
            modifier = GlanceModifier.padding(top = 1.dp),
            maxLines = 1
        )

        // ── Stats bar ─────────────────────────────────────────────────────────
        val successColor = when {
            s.totalRuns == 0 -> Color.Gray
            s.successRate >= 80 -> Color(0xFF34C759)
            else -> Color(0xFFFF3B30)
        }
        val statsText = buildString {
            append(context.getString(R.string.widget_stats_runs, s.totalRuns))
            if (s.totalRuns > 0) append("  ·  ${context.getString(R.string.widget_stats_success, s.successRate)}")
            if (s.lastRunMillis > 0L) {
                append("  ·  ${context.getString(R.string.widget_stats_last, formatRelativeTime(context, s.lastRunMillis))}")
            }
        }
        Text(
            text = statsText,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = ColorProvider(successColor)
            ),
            modifier = GlanceModifier.padding(top = 6.dp),
            maxLines = 1
        )

        // ── Defined actions ───────────────────────────────────────────────────
        if (s.actionLabels.isNotEmpty()) {
            Text(
                text = s.actionLabels.joinToString("  ·  "),
                style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF636366))),
                modifier = GlanceModifier.padding(top = 3.dp),
                maxLines = 1
            )
        }

        // ── Last run detail ───────────────────────────────────────────────────
        if (s.lastRunResults.isNotEmpty()) {
            Text(
                text = context.getString(
                    R.string.widget_last_run_ago,
                    formatRelativeTime(context, s.lastRunMillis)
                ),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = ColorProvider(Color(0xFF000000))
                ),
                modifier = GlanceModifier.padding(top = 6.dp)
            )
            s.lastRunResults.take(3).forEach { (label, success) ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = if (success) "✓ " else "✗ ",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = ColorProvider(
                                if (success) Color(0xFF34C759) else Color(0xFFFF3B30)
                            )
                        )
                    )
                    Text(
                        text = label,
                        style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF636366))),
                        maxLines = 1
                    )
                }
            }
        } else {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color.Gray)),
                modifier = GlanceModifier.padding(top = 6.dp)
            )
        }

        // ── History strip: last 5 runs as colored dots ────────────────────────
        if (s.recentHistory.isNotEmpty()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.widget_history_label),
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.Gray))
                )
                s.recentHistory.forEach { ok ->
                    Text(
                        text = "● ",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = ColorProvider(if (ok) Color(0xFF34C759) else Color(0xFFFF3B30))
                        )
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatRelativeTime(context: Context, timestampMillis: Long): String {
    val diffMs = System.currentTimeMillis() - timestampMillis
    val mins = diffMs / 60_000
    val hours = mins / 60
    val days = hours / 24
    return when {
        mins < 1 -> context.getString(R.string.widget_time_just_now)
        mins < 60 -> "${mins}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}
