package com.gemmaworkflow.data.seed

import android.content.Context
import com.gemmaworkflow.data.repository.WorkflowRepository
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.model.WorkflowStep
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Seeds demo workflows into storage on first launch.
 * Ensures the app has something to show even before the AI generates anything.
 */
object DemoWorkflowSeeder {

    private const val PREF_SEEDED = "demo_workflows_seeded"

    fun seedIfNeeded(context: Context, repo: WorkflowRepository) {
        val prefs = context.getSharedPreferences("gemma_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_SEEDED, false)) return

        for (workflow in buildDemoWorkflows()) {
            repo.save(workflow)
        }

        prefs.edit().putBoolean(PREF_SEEDED, true).apply()
    }

    private fun buildDemoWorkflows(): List<PlannedWorkflow> = listOf(
        PlannedWorkflow(
            name = "Coffee & Note",
            summary = "Finds the nearest coffee shop on Maps, then shares a note.",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "maps.open_place",
                    params = buildJsonObject { put("query", "coffee shop near me") },
                    requiresConfirmation = false
                ),
                WorkflowStep(
                    id = "share.share_text",
                    params = buildJsonObject { put("text", "Coffee stop — found a shop nearby.") },
                    requiresConfirmation = true
                )
            )
        ),
        PlannedWorkflow(
            name = "Morning Routine",
            summary = "Sets a morning alarm and creates a calendar event for a workout.",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "alarm.set_alarm",
                    params = buildJsonObject {
                        put("hour", 7)
                        put("minutes", 30)
                        put("message", "Morning workout")
                    },
                    requiresConfirmation = true
                ),
                WorkflowStep(
                    id = "calendar.create_event",
                    params = buildJsonObject {
                        put("title", "Gym session")
                        put("begin_time_millis", 1770000000000L)
                        put("end_time_millis", 1770003600000L)
                        put("location", "Local gym")
                    },
                    requiresConfirmation = true
                ),
                WorkflowStep(
                    id = "sms.compose",
                    params = buildJsonObject { put("message", "Headed to the gym!") },
                    requiresConfirmation = true
                )
            )
        ),
        PlannedWorkflow(
            name = "Share & Browse",
            summary = "Shares an image and opens a URL in the browser.",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "share.share_image",
                    params = buildJsonObject { put("uri", "content://media/external/images/media/1") },
                    requiresConfirmation = true
                ),
                WorkflowStep(
                    id = "browser.open_url",
                    params = buildJsonObject { put("url", "https://example.com") },
                    requiresConfirmation = false
                )
            )
        )
    )
}
