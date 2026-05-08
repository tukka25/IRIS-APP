package com.gemmaworkflow.platform.tools.reto

import com.gemmaworkflow.platform.tools.FindSkill

/**
 * Prompt builder for RETO layer execution and repair.
 *
 * v2: Generic entity identification pattern.
 * The prompt teaches the model to MATCH any entity in the user request
 * to the tool whose description describes that type of entity.
 * No hardcoded categories — scales to any new domain by just adding a tool.
 */
object RetoPromptBuilder {

    /**
     * Build the execution prompt for a single RETO layer.
     *
     * Teaches the model a generic pattern:
     * 1. Read each tool's description — it tells you what kind of entity it resolves
     * 2. Scan the user request for entities that match those descriptions
     * 3. Call EVERY tool whose description matches something in the request
     * 4. Verify: did you call every matching tool before signaling completion?
     */
    fun buildLayerExecutionPrompt(
        layer: RetoLayer,
        observationSummary: String,
        userRequest: String,
        installedApps: String = "",
        detectedEntities: List<DetectedEntity> = emptyList()
    ): String = buildString {
        appendLine("[RETO LAYER ${layer.index + 1}]")
        appendLine("Objective: ${layer.objective}")
        appendLine()

        // User request — the source of all entities
        appendLine("User request: \"$userRequest\"")
        if (installedApps.isNotBlank()) {
            appendLine()
            appendLine(installedApps)
        }

        // Previous observations (compact)
        appendLine()
        appendLine(observationSummary)

        // ── Detected entities checklist ──
        if (detectedEntities.isNotEmpty()) {
            appendLine()
            appendLine("--- ENTITIES TO RESOLVE ---")
            appendLine("The following were detected in the user request. You MUST try to resolve each one:")
            detectedEntities.forEach { entity ->
                appendLine("  - [${entity.category}] \"${entity.text}\" → call ${entity.suggestedTool}")
            }
            appendLine()
            appendLine("Call EVERY matching tool above. Do not stop until all are resolved or confirmed missing.")
            appendLine()
        }

        // Allowed tools for this layer
        appendLine("Tools available in this layer:")
        val toolSchemas = FindSkill.schemaFor(layer.allowedTools)
        appendLine(toolSchemas)

        // ── Generic entity matching pattern ──
        appendLine("--- HOW TO USE THESE TOOLS ---")
        appendLine()
        appendLine("Each tool's DESCRIPTION tells you what kind of thing it resolves.")
        appendLine("Read the description carefully — it says what the tool finds.")
        appendLine()
        appendLine("Your job:")
        appendLine("1. Scan the user request for things that match a tool's description.")
        appendLine("   - If a tool says 'search device media: songs, playlists, artists',")
        appendLine("     and the request mentions 'playlist' or a song name → call it.")
        appendLine("   - If a tool says 'converts human time expressions to timestamps',")
        appendLine("     and the request mentions 'tomorrow' or 'next Friday' → call it.")
        appendLine("   - If a tool says 'search device contacts by name',")
        appendLine("     and the request mentions a person's name → call it.")
        appendLine()
        appendLine("2. Call EVERY tool whose description matches something in the request.")
        appendLine("   Do not stop after calling just one tool if others also match.")
        appendLine()
        appendLine("3. Before marking this layer complete, verify:")
        appendLine("   'Did I call every tool whose description matched the request?'")
        appendLine("   If no → call the next matching tool.")
        appendLine("   If yes → output LAYER_DONE with what you resolved.")
        appendLine()

        // Rules
        appendLine("--- RULES ---")
        appendLine("- Call only tools listed above for this layer.")
        appendLine("- Call at most one tool per response. Wait for TOOL_RESULT before the next call.")
        appendLine("- You may call multiple tools across responses if the descriptions match.")
        appendLine("- Tool descriptions define what entities the tool resolves.")
        appendLine("- If you're unsure whether a tool matches, CALL IT anyway — better to check than skip.")
        appendLine("- When all matching tools are done, output:")
        appendLine("  LAYER_DONE: {\"resolved\": [...], \"missing\": [...]}")
        appendLine()
        appendLine("If a tool returns an ERROR, note it as missing and move to the next tool.")
        appendLine("Do not retry a failed tool more than once.")
    }

    /**
     * Build a repair prompt for a failed tool call.
     */
    fun buildRepairPrompt(
        toolName: String,
        toolSchema: String,
        originalArgs: String,
        errorMessage: String,
        attemptNumber: Int
    ): String = buildString {
        appendLine("[RETO REPAIR — Attempt $attemptNumber]")
        appendLine("Your previous tool call failed.")
        appendLine()
        appendLine("Tool: $toolName")
        appendLine("Schema:")
        appendLine(toolSchema)
        appendLine("Your args: $originalArgs")
        appendLine("Error: $errorMessage")
        appendLine()
        appendLine("Fix the error and output a corrected TOOL call.")
        appendLine("Use only known parameter names from the schema.")
        appendLine("If the error cannot be repaired, output:")
        appendLine("  REPAIR_FAILED: reason")
    }

    /**
     * Build the final action plan prompt from compact observations.
     */
    fun buildFinalActionPlanPrompt(
        goal: String,
        observationSummary: String,
        availableActions: String
    ): String = buildString {
        appendLine("You are an action planner for GemmaWorkflow.")
        appendLine("Goal: $goal")
        appendLine()
        appendLine("Gathered facts:")
        appendLine(observationSummary)
        appendLine()
        appendLine(availableActions)
        appendLine()
        appendLine("Select concrete actions using the gathered facts. Return JSON only.")
    }
}
