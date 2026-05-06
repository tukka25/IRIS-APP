package com.gemmaworkflow.domain.planner

/**
 * Builds system prompts for each agent stage.
 * Ensures the model only sees the allowed capability surface.
 */
object PromptBuilder {

    /**
     * Stage 1: Analyze the user request.
     */
    fun buildRequestAnalysisPrompt(
        userRequest: String,
        installedApps: String = ""
    ): String = """
You are a request analyzer for an Android automation app called GemmaWorkflow.

Analyze the user's request and extract structured information. Return JSON only.

User request: "$userRequest"

${installedApps.ifBlank { "Installed launchable apps from Android PackageManager: not provided" }}

Output schema:
{
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
}

Rules:
- Use "manual" for trigger_hint when the user does not clearly specify when or how the workflow should run.
- Use "time" only when the request clearly includes a schedule, date, time, recurrence, or time-based phrase.
- Use "nfc", "share_sheet", or "tasker_setup_required" only when the request clearly implies that trigger type.
- applications must choose only from the installed app list provided above.
- selected_app_label and package_name must exactly match one row from the installed app list.
- If the user names a generic app category like "messaging app", "calendar app", "maps app", or "browser", choose the closest installed app from the list.
- If no installed app is a reasonable match, use an empty applications array. Do not invent app labels or package names.
- Only suggest categories that make sense for this request.
- missing_info lists anything the user didn't specify that would be needed to build a complete workflow.
""".trimIndent()

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
        catalogSummary: String
    ): String = """
You are a JSON formatter for GemmaWorkflow. Convert the action plan into the final workflow JSON contract.

Goal: $goal

Available actions (ONLY use these IDs):
$catalogSummary

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
- Every action id MUST come from the available actions list.
- Every param key MUST come from the chosen action's schema.
- Preserve numeric and boolean params as JSON numbers/booleans, not strings.
- Do not output Android intent actions, extra keys, package names, or URI templates.
- requires_confirmation should be true for actions that send data externally (share, post).
- missing_setup lists real prerequisites the user must handle.
""".trimIndent()
}
