package com.gemmaworkflow.domain.planner

import com.gemmaworkflow.domain.catalog.ActionCatalog

/**
 * Builds system prompts for each agent stage.
 * Ensures the model only sees the allowed capability surface.
 */
object PromptBuilder {

    /**
     * Stage 1: Analyze the user request.
     */
    fun buildRequestAnalysisPrompt(userRequest: String): String = """
You are a request analyzer for an Android automation app called GemmaWorkflow.

Analyze the user's request and extract structured information. Return JSON only.

User request: "$userRequest"

Output schema:
{
  "goal": "concise goal statement",
  "trigger_hint": "manual" | "time" | "nfc" | "share_sheet",
  "schedule_hints": { "hour": 9, "minute": 0, "repeat_days": [] } or null,
  "candidate_app_categories": ["productivity", "music", "navigation", "sharing", "notes"],
  "missing_info": ["what the user hasn't specified"]
}

Rules:
- trigger_hint should be "manual" unless the request clearly suggests a schedule, location, NFC tag, or share action.
- Only suggest categories that make sense for this request.
- missing_info lists anything the user didn't specify that would be needed to build a complete workflow.
""".trimIndent()

    /**
     * Stage 3: Select concrete actions from the available capability list.
     */
    fun buildActionPlanPrompt(
        goal: String,
        triggerHint: String,
        availableActions: String
    ): String = """
You are an action planner for GemmaWorkflow, an Android automation app.

Goal: $goal
Suggested trigger type: $triggerHint

$availableActions

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
    "type": "manual" | "time" | "nfc" | "share_sheet",
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
- requires_confirmation should be true for actions that send data externally (share, post).
- missing_setup lists real prerequisites the user must handle.
""".trimIndent()
}
