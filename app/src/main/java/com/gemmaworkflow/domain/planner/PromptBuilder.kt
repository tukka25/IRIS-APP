package com.gemmaworkflow.domain.planner

/**
 * Builds system prompts for each agent stage.
 * Ensures the model only sees the allowed capability surface.
 *
 * v4: Entity type classification via tool selection.
 * The model identifies what TYPE each entity is (contact, playlist,
 * file, note, SMS, calendar event) and calls the matching domain tool.
 * No preprocessor — the model's tool selection IS the classification.
 */
object PromptBuilder {

    fun buildToolUseInstructions(toolSchemas: String): String = """
Tool use:
- You are a model that can do function calling with the following functions.
- You have access to the tools listed below.
- Read the tool list and use tools whenever they can provide facts, device state, validation, or safer parameters than guessing.
- Use tools for current time/date, relative schedules, installed apps, contacts, places, intent availability, calculations, or JSON validation.
- You may use multiple tools in sequence when the workflow needs multiple facts.
- Call at most one tool per model response. Kotlin will execute it and return TOOL_RESULT, then you may call another tool if needed.
- Tool call format:
  TOOL: tool_name {"param_name":"value"}
- For tools with no parameters, use:
  TOOL: tool_name {}
- After Kotlin returns TOOL_RESULT, update your plan with that result.
- Never invent tool names or parameter names. Use only the listed tools.
- Never claim that a tool was called unless you output a TOOL call.
- Do not stop after one tool if another listed tool is still needed to complete the workflow correctly.


$toolSchemas
""".trimIndent()

    /**
     * Stage 1: Analyze the user request. v4 — entity type classification via tools.
     *
     * The model learns to:
     * 1. Identify what TYPE each entity is (contact, playlist, file, note, SMS, calendar, location)
     * 2. Call the matching domain tool to resolve it
     * 3. Use pre-resolved results in the analysis JSON
     *
     * No regex preprocessor — the model's tool selection IS the classification.
     */
    fun buildRequestAnalysisPrompt(
        userRequest: String,
        installedApps: String = "",
        resolvedContacts: Map<String, String> = emptyMap()
    ): String = buildString {
        appendLine("You are a request analyzer for GemmaWorkflow, an Android automation app.")
        appendLine()
        appendLine("IMPORTANT: Identify what TYPE each entity is, then call the matching tool.")
        appendLine()

        // ── Entity type guide ──
        appendLine("--- ENTITY TYPE GUIDE ---")
        appendLine("To decide which tool to call, classify the entity:")
        appendLine()
        appendLine("  NAME (person, contact)       → get_contact or search_sms")
        appendLine("    Examples: \"Maya\", \"Mom\", \"John from work\"")
        appendLine("    Tool: TOOL: get_contact {\"name\": \"Maya\"}")
        appendLine()
        appendLine("  PLAYLIST / SONG / ARTIST     → search_media")
        appendLine("    Examples: \"workout playlist\", \"song by Adele\", \"my jazz mix\"")
        appendLine("    Tool: TOOL: search_media {\"query\": \"workout\", \"type\": \"playlist\"}")
        appendLine()
        appendLine("  FILE / DOCUMENT / SPREADSHEET → search_files")
        appendLine("    Examples: \"budget spreadsheet\", \"resume PDF\", \"shopping list\"")
        appendLine("    Tool: TOOL: search_files {\"query\": \"budget\"}")
        appendLine()
        appendLine("  NOTE / LIST                  → search_notes")
        appendLine("    Examples: \"grocery list\", \"meeting notes\", \"my todo\"")
        appendLine("    Tool: TOOL: search_notes {\"query\": \"grocery\"}")
        appendLine()
        appendLine("  TEXT MESSAGE / SMS           → search_sms")
        appendLine("    Examples: \"message from Mom\", \"text about the meeting\"")
        appendLine("    Tool: TOOL: search_sms {\"query\": \"Mom\"}")
        appendLine()
        appendLine("  CALENDAR EVENT / APPOINTMENT → get_calendar_events")
        appendLine("    Examples: \"dentist appointment\", \"team meeting\", \"lunch with Sarah\"")
        appendLine("    Tool: TOOL: get_calendar_events {\"query\": \"dentist\"}")
        appendLine()
        appendLine("  TIME / DATE expression       → get_current_time or resolve_datetime")
        appendLine("    Examples: \"tomorrow at 9am\", \"next Friday\", \"in 2 hours\"")
        appendLine("    Tool: TOOL: resolve_datetime {\"expression\": \"next Friday at 6 o'clock\", \"default_period\": \"pm\"}")
        appendLine("    Use default_period only when the user gives an ambiguous 1-12 hour without AM/PM.")
        appendLine()
        appendLine("If you're unsure which type, call the tool that best matches the user's intent.")
        appendLine("If no entity is present, skip entity tools and go directly to analysis.")
        appendLine()

        // ── Audit-first structure ──
        appendLine("--- WORKFLOW ---")
        appendLine("STEP 1 — CLASSIFY: For each entity in the request, decide its TYPE using the guide above.")
        appendLine("STEP 2 — RESOLVE: Call the matching tool(s) to get the real data.")
        appendLine("STEP 3 — ANALYZE: With all resolved facts, produce the analysis JSON.")
        appendLine()

        // ── Examples ──
        appendLine("--- EXAMPLES ---")
        appendLine()
        appendLine("Example 1 — Contact message with time:")
        appendLine("  User: \"send message to Maya saying hi tomorrow\"")
        appendLine("  Entities: Maya (NAME → contact), tomorrow (TIME → date)")
        appendLine("  → TOOL: get_contact {\"name\": \"Maya\"}")
        appendLine("  ← TOOL_RESULT: +971501234567")
        appendLine("  → TOOL: resolve_datetime {\"expression\": \"tomorrow\"}")
        appendLine("  ← TOOL_RESULT: 2026-05-09T00:00:00")
        appendLine("  → {\"goal\": \"Send message to Maya\", \"trigger_hint\": \"manual\", ...}")
        appendLine()
        appendLine("Example 2 — Playlist with timer:")
        appendLine("  User: \"play my workout playlist in 30 minutes\"")
        appendLine("  Entities: workout (PLAYLIST → media), 30 minutes (TIME → duration)")
        appendLine("  → TOOL: search_media {\"query\": \"workout\", \"type\": \"playlist\"}")
        appendLine("  ← TOOL_RESULT: [playlist] Workout Mix")
        appendLine("  → TOOL: resolve_datetime {\"expression\": \"in 30 minutes\"}")
        appendLine("  ← TOOL_RESULT: 2026-05-08T12:30:00")
        appendLine("  → {\"goal\": \"Play Workout Mix playlist\", \"trigger_hint\": \"time\", ...}")
        appendLine()
        appendLine("Example 3 — Calendar event lookup:")
        appendLine("  User: \"remind me about my dentist appointment next week\"")
        appendLine("  Entities: dentist (CALENDAR → event), next week (TIME → date)")
        appendLine("  → TOOL: get_calendar_events {\"query\": \"dentist\"}")
        appendLine("  ← TOOL_RESULT: Dentist — May 15, 14:00 @ Dr. Smith's Office")
        appendLine("  → TOOL: resolve_datetime {\"expression\": \"next week\"}")
        appendLine("  ← TOOL_RESULT: 2026-05-15")
        appendLine("  → {\"goal\": \"Remind about dentist\", \"trigger_hint\": \"time\", ...}")
        appendLine()
        appendLine("Example 4 — Ambiguous meeting time:")
        appendLine("  User: \"invite Maya to a meeting next Friday at 6 o'clock\"")
        appendLine("  Entities: Maya (NAME → contact), next Friday at 6 o'clock (TIME → date/time)")
        appendLine("  → TOOL: get_contact {\"name\": \"Maya\"}")
        appendLine("  ← TOOL_RESULT: +971501234567")
        appendLine("  → TOOL: resolve_datetime {\"expression\": \"next Friday at 6 o'clock\", \"default_period\": \"pm\"}")
        appendLine("  ← TOOL_RESULT: 2026-05-15T18:00:00+04:00")
        appendLine("  → {\"goal\": \"Invite Maya to a meeting\", \"trigger_hint\": \"manual\", ...}")
        appendLine()

        // ── User's request ──
        appendLine("--- YOUR TASK ---")
        appendLine()
        appendLine("USER REQUEST: \"$userRequest\"")
        appendLine()

        // ── Installed apps ──
        appendLine(installedApps.ifBlank { "Installed launchable apps from Android PackageManager: not provided" })
        appendLine()

        // ── Output schema ──
        appendLine("--- OUTPUT SCHEMA ---")
        appendLine("After resolving all entities, produce this JSON:")
        appendLine("""{
  "goal": "concise goal statement",
  "trigger_hint": "manual" | "time" | "nfc" | "share_sheet" | "tasker_setup_required",
  "schedule_hints": { "hour": 9, "minute": 0, "repeat_days": [] } or null,
  "applications": [
    {
      "requested_name": "app name or app category from the user request",
      "selected_app_label": "exact app label copied from the installed app list",
      "package_name": "exact package name copied from the installed app list",
      "confidence": "high" | "medium" | "low"
    }
  ],
  "candidate_app_categories": ["productivity", "music", "navigation", "sharing", "notes"],
  "missing_info": ["what the user hasn't specified"]
}""")
        appendLine()

        // ── Rules ──
        appendLine("--- RULES ---")
        appendLine("- CLASSIFY entities FIRST using the entity type guide above. Then call tools to RESOLVE them.")
        appendLine("- Call tools BEFORE writing the JSON output.")
        appendLine("- If a tool returns ERROR, try rephrasing and calling again, or use your best estimate and note it as missing_info.")
        appendLine("- If a tool finds nothing, note the entity as missing_info — don't invent data.")
        appendLine("- If the user's request has no entities to resolve, skip tools and go directly to JSON.")
        appendLine("- Use \"manual\" for trigger_hint when the user does not clearly specify when or how the workflow should run.")
        appendLine("- Use \"time\" only when the request clearly includes a schedule, date, time, recurrence, or time-based phrase.")
        appendLine("- applications must choose only from the installed app list provided above.")
        appendLine("- selected_app_label and package_name must exactly match one row from the installed app list.")
        appendLine("- If no installed app is a reasonable match, use an empty applications array.")
        appendLine("- missing_info lists entities that couldn't be resolved or were missing from the request.")
    }

    /**
     * Stage 3: Select concrete actions from the available capability list.
     */
    fun buildActionPlanPrompt(
        goal: String,
        triggerHint: String,
        availableActions: String,
        nativeDiscovery: String = ""
    ): String = """
You are an action planner for GemmaWorkflow, an Android automation app.

Goal: $goal
Suggested trigger type: $triggerHint

$availableActions

${nativeDiscovery.ifBlank { "Native Android discovery: not provided" }}

Select the concrete actions needed to achieve the goal. Return JSON only.

Output schema:
{
  "actions": [
    {
      "id": "action.id.from.catalog",
      "params": { "param_name": "value" },
      "reason": "why this action is needed"
    }
  ],
  "trigger": {
    "type": "$triggerHint",
    "reasoning": "why this trigger fits"
  },
  "missing_setup": ["anything the user must configure before this works"]
}

Rules:
- ONLY use action IDs from the catalog above. Do not invent actions.
- Include ALL required params for each action.
- Use the exact param names and types shown in the catalog.
- Do not output Android intent actions, extra keys, package names, or URI templates.
- Prefer simple actions over complex ones when both would work.
- If the trigger is not "manual", include what setup the user needs.
""".trimIndent()

    /**
     * Stage 4: Output the final strict JSON contract.
     */
    fun buildWorkflowJsonPrompt(
        goal: String,
        actionPlanJson: String,
        catalogSummary: String,
        groundedFacts: String = ""
    ): String = """
You are a JSON formatter for GemmaWorkflow. Convert the action plan into the final workflow JSON contract.

Goal: $goal

Available actions (ONLY use these IDs):
$catalogSummary

Grounded facts and action params already resolved by Kotlin:
${groundedFacts.ifBlank { "No grounded facts were provided." }}

Action plan to format:
$actionPlanJson

Output schema (strict):
{
  "name": "short workflow name",
  "summary": "one sentence describing what this does",
  "trigger": {
    "type": "manual" | "time" | "nfc" | "share_sheet" | "tasker_setup_required",
    "setup_state": "ready" | "needs_setup",
    "schedule": { "hour": 9, "minute": 0, "repeat_days": [] } or null
  },
  "actions": [
    {
      "id": "catalog.action.id",
      "params": { "key": "value" },
      "requires_confirmation": true or false
    }
  ],
  "missing_setup": ["list anything the user must configure"]
}

Rules:
- Output ONLY valid JSON, no markdown, no explanation.
- Do not use trailing commas.
- Every action id MUST come from the available actions list.
- Every param key MUST come from the chosen action's schema.
- Preserve numeric and boolean params as JSON numbers/booleans, not strings.
- Never output arithmetic expressions such as 1777810000000 + 3600000. Compute the final value and output a single JSON number.
- If "Grounded action params" contains action_id.param = value, include that value in the matching action's params.
- Do not put an item in missing_setup for a value that is already present in Grounded action params or already filled in the final params.
- Do not output Android intent actions, extra keys, package names, or URI templates.
- requires_confirmation should be true for actions that send data externally (share, post).
- missing_setup lists real prerequisites the user must handle.
""".trimIndent()
}
