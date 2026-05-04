package com.gemmaworkflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemmaworkflow.ui.home.LlamaSmokeViewModel
import com.gemmaworkflow.ui.theme.GemmaWorkflowTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LlamaSmokeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GemmaWorkflowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LlamaSmokeScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun LlamaSmokeScreen(viewModel: LlamaSmokeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Gemma model smoke test", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Push the GGUF to the path below, load it, then send a short prompt.",
            style = MaterialTheme.typography.bodyMedium
        )

        SelectionContainer {
            Text(
                text = state.modelPath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !state.isBusy,
                onClick = viewModel::loadModel
            ) {
                Text("Load model")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(state.loadStatus, style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Prompt") }
        )

        Button(
            enabled = state.canGenerate,
            onClick = viewModel::generate
        ) {
            Text("Generate")
        }

        if (state.error != null) {
            Text(
                text = state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text("Response", style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(
                text = state.response.ifBlank { "No response yet." },
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

