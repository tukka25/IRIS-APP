package com.gemmaworkflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.gemmaworkflow.R
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.ui.theme.GemmaWorkflowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkflowWidgetConfigActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Cancel result so backing out doesn't add the widget
        setResult(RESULT_CANCELED)

        setContent {
            val workflowNames by produceState<List<String>>(initialValue = emptyList()) {
                value = withContext(Dispatchers.IO) {
                    WorkflowRepository(this@WorkflowWidgetConfigActivity).listNames()
                }
            }
            GemmaWorkflowTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.widget_configure_title)) })
                    }
                ) { innerPadding ->
                    if (workflowNames.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.widget_configure_empty))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(innerPadding)
                        ) {
                            items(workflowNames) { name ->
                                Text(
                                    text = name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onWorkflowSelected(appWidgetId, name) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onWorkflowSelected(appWidgetId: Int, workflowName: String) {
        WidgetPreferences.setWorkflowName(this, appWidgetId, workflowName)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WorkflowWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)
            WorkflowWidgetGlance().update(this@WorkflowWidgetConfigActivity, glanceId)
        }
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
