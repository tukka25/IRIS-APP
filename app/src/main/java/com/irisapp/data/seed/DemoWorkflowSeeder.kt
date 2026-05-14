package com.irisapp.data.seed

import android.content.Context
import com.irisapp.data.repository.WorkflowRepository
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.domain.model.WorkflowStep
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Seeds demo workflows into storage on first launch.
 * Ensures the app has something to show even before the AI generates anything.
 *
 * Step chaining: use "$step[N].output" in a param value to inject the raw output
 * of step N into the current step's parameters before execution.
 * Example: a clipboard step with text="$step[0].output" copies step 0's result.
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

        // ─── Coffee & Note ───────────────────────────────────────────────────────
        // Step 1 opens Maps and returns "Opened Google Maps" as output.
        // Step 2 copies that output to the clipboard.
        // Once a real search tool is added (one that returns actual results),
        // step 1 can be swapped for it and step 2 will automatically use the real data.
        PlannedWorkflow(
            name = "Coffee & Note",
            summary = "Opens Maps and copies the result to clipboard. Demonstrates step chaining.",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "maps.open_place",
                    params = buildJsonObject { put("query", "coffee shop near me") },
                    requiresConfirmation = false
                ),
                WorkflowStep(
                    id = "clipboard.copy_text",
                    params = buildJsonObject { put("text", "${'$'}step[0].output") },
                    requiresConfirmation = false
                )
            )
        ),

        // ─── Morning Routine ────────────────────────────────────────────────────
        // Alarm fires, calendar event is created, then the event details are
        // copied to clipboard using the chain.
        PlannedWorkflow(
            name = "Morning Routine",
            summary = "Sets an alarm, creates a calendar event, then copies the event to clipboard.",
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
                    id = "clipboard.copy_text",
                    params = buildJsonObject { put("text", "${'$'}step[1].output") },
                    requiresConfirmation = false
                )
            )
        ),

        // ─── Share & Browse ─────────────────────────────────────────────────────
        // The URL param uses step 0's output — so when share.share_text is given
        // a text that contains "$step[0].output", it resolves to the clipboard text,
        // which is then used as the URL to open.
        // This demonstrates chaining where step 0 feeds into step 1.
        PlannedWorkflow(
            name = "Share & Browse",
            summary = "Copies text to clipboard, then opens it as a URL. Demonstrates chained params.",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "clipboard.copy_text",
                    params = buildJsonObject {
                        put("text", "https://example.com?ref=gemma-workflow")
                    },
                    requiresConfirmation = true
                ),
                WorkflowStep(
                    id = "browser.open_url",
                    params = buildJsonObject { put("url", "${'$'}step[0].output") },
                    requiresConfirmation = false
                )
            )
        ),

// ─── Quick Alarm ───────────────────────────────────────────────────────
// Simple single-step timer for quick testing.
 PlannedWorkflow(
            name = "Quick Alarm",
            summary = "Sets a 30-second countdown timer for testing. Fast confirmation.",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "alarm.set_timer",
                    params = buildJsonObject {
                        // Duration in seconds for alarm.set_timer action.
                        put("seconds", 30)
                        put("message", "Quick test alarm")
                    },
                    requiresConfirmation = true
                )
            )
        )
    )
}