package com.gemmaworkflow.domain.runner

import android.content.Context
import com.gemmaworkflow.domain.model.ExecutionResult
import com.gemmaworkflow.domain.model.PlannedWorkflow

/**
 * Executes a validated PlannedWorkflow step by step.
 */
class WorkflowRunner(
    private val context: Context,
    private val intentDispatcher: IntentDispatcher,
    private val urlDispatcher: UrlDispatcher
) {
    suspend fun run(workflow: PlannedWorkflow): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        for (step in workflow.actions) {
            val result = executeStep(step)
            results.add(result)
            if (!result.success) break
        }
        return results
    }

    private fun executeStep(step: com.gemmaworkflow.domain.model.WorkflowStep): ExecutionResult {
        return when (step.id) {
            "browser.open_url" -> urlDispatcher.openUrl(step.params["url"] ?: "")
            "maps.open_place" -> urlDispatcher.openMaps(step.params["query"] ?: "")
            "share.share_text" -> intentDispatcher.shareText(step.params["text"] ?: "")
            "share.share_image" -> intentDispatcher.shareImage(step.params["uri"] ?: "")
            "spotify.play_search" -> urlDispatcher.openSpotifySearch(step.params["query"] ?: "")
            "notes.save_text" -> intentDispatcher.shareText(
                "${step.params["title"] ?: ""}\n\n${step.params["content"] ?: ""}")
            else -> ExecutionResult(stepId = step.id, success = false, message = "Unknown action")
        }
    }
}
