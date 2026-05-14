package com.irisapp.ui.trigger

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.irisapp.data.repository.ExecutionHistoryRepository
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.runner.WorkflowRunner
import com.irisapp.widget.WorkflowWidgetGlance
import com.irisapp.platform.nfc.DeepLinkRouter
import com.irisapp.platform.nfc.DeepLink
import com.irisapp.ui.theme.IrisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity displayed when a scheduled time trigger fires.
 *
 * It is launched via [TimeTriggerNotification.buildConfirmationIntent] from
 * the notification shown by [TimeTriggerNotification.post], or via
 * [TimeTriggerNotification.buildRunDirectIntent] for the "Run Now" action.
 *
 * This Activity:
 * - Loads the named workflow from the repository
 * - Shows a confirmation screen listing the workflow name, trigger type, and steps
 * - On "Run Now" → executes the workflow via [WorkflowRunner] and shows results
 * - On "View"   → posts a deep-link event via [DeepLinkRouter] so [com.irisapp.ui.MainActivity]
 *                 navigates to the workflow detail screen
 *
 * Registered in AndroidManifest.xml for:
 * - [TimeTriggerNotification.ACTION_SHOW_CONFIRMATION]
 * - [TimeTriggerNotification.ACTION_RUN_DIRECT]
 * - [DeepLinkRouter.ACTION_RUN_WORKFLOW] (for direct-run shortcut)
 */
class TimeTriggerConfirmationActivity : ComponentActivity() {

    private val workflowRepository by lazy { WorkflowRepository(this@TimeTriggerConfirmationActivity) }

    companion object {
        private const val TAG = "TimeTriggerConfirmation"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val workflowName = intent.getStringExtra(TimeTriggerNotification.EXTRA_WORKFLOW_NAME)
            ?: run {
                Log.e(TAG, "No workflow name in intent — closing")
                finish()
                return
            }

        val action = intent.action

        // If launched via "Run Now" (direct run shortcut from notification), run immediately.
        if (action == TimeTriggerNotification.ACTION_RUN_DIRECT) {
            runWorkflowAndFinish(workflowName)
            return
        }

        setContent {
            var state by remember { mutableStateOf<ConfirmationScreenState>(ConfirmationScreenState.Loading) }
            LaunchedEffect(workflowName) {
                val workflow = withContext(Dispatchers.IO) {
                    workflowRepository.get(workflowName)
                }
                state = if (workflow != null) {
                    ConfirmationScreenState.Ready(workflow)
                } else {
                    ConfirmationScreenState.Error("Workflow '$workflowName' not found")
                }
            }

            IrisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val current = state) {
                        ConfirmationScreenState.Loading -> LoadingConfirmationScreen()
                        is ConfirmationScreenState.Error -> ErrorConfirmationScreen(
                            message = current.message,
                            onDismiss = { finish() }
                        )
                        is ConfirmationScreenState.Ready -> TimeTriggerConfirmationScreen(
                            workflow = current.workflow,
                            onRun = { runWorkflowAndFinish(workflowName) },
                            onView = {
                                // Emit a deep-link event so MainActivity navigates to detail.
                                DeepLinkRouter.emitDeepLink(
                                    DeepLink.ShowDetail(workflowName)
                                )
                                finish()
                            },
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun runWorkflowAndFinish(workflowName: String) {
        lifecycleScope.launch {
            val workflow = withContext(Dispatchers.IO) {
                workflowRepository.get(workflowName)
            }
            if (workflow == null) {
                Log.e(TAG, "Workflow not found: '$workflowName'")
                finish()
                return@launch
            }
            try {
                val results = withContext(Dispatchers.Default) {
                    val runner = WorkflowRunner(this@TimeTriggerConfirmationActivity)
                    runner.run(workflow) { _, _ -> }
                }
                val allSuccess = results.all { it.success }
                Log.i(TAG, "Time trigger workflow '$workflowName' completed — allSuccess=$allSuccess")
                withContext(Dispatchers.IO) {
                    ExecutionHistoryRepository(this@TimeTriggerConfirmationActivity)
                        .log(workflowName, results)
                }
                WorkflowWidgetGlance.updateAll(this@TimeTriggerConfirmationActivity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run workflow '$workflowName'", e)
            }
            finish()
        }
    }
}

private sealed interface ConfirmationScreenState {
    data object Loading : ConfirmationScreenState
    data class Ready(val workflow: PlannedWorkflow) : ConfirmationScreenState
    data class Error(val message: String) : ConfirmationScreenState
}

@Composable
private fun LoadingConfirmationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading scheduled workflow…")
    }
}

@Composable
private fun ErrorConfirmationScreen(
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onDismiss) {
            Text("Dismiss")
        }
    }
}

/**
 * Composable confirmation screen shown when a time trigger fires.
 */
@Composable
private fun TimeTriggerConfirmationScreen(
    workflow: PlannedWorkflow,
    onRun: () -> Unit,
    onView: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Scheduled Workflow", style = MaterialTheme.typography.headlineSmall)

        // Workflow summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(workflow.name, style = MaterialTheme.typography.titleMedium)
                if (workflow.summary.isNotBlank()) {
                    Text(workflow.summary, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "\u23f0 ${formatTriggerTime(workflow)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            "ACTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                workflow.actions.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.id, style = MaterialTheme.typography.bodyMedium)
                            if (step.params.isNotEmpty()) {
                                Text(
                                    step.params.entries.joinToString(", ") { "${it.key}=${it.value}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (index < workflow.actions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 40.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons — run after confirming, View navigates to detail, Dismiss closes.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Text("Dismiss")
            }
            OutlinedButton(
                onClick = onView,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Text("View")
            }
            Button(
                onClick = onRun,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Text("Run Now")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatTriggerTime(workflow: PlannedWorkflow): String {
    val t = workflow.trigger
    return if (t is com.irisapp.domain.model.TriggerConfig.Time) {
        "${formatTimeLabel(t.hour, t.minute)} \u2022 ${formatRepeatLabel(t.repeatDays)}"
    } else {
        "Scheduled"
    }
}
