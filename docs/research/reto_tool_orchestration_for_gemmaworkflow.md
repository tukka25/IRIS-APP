# RETO-Inspired Tool Orchestration For IrisApp

Date: 2026-05-08

Status: implementation plan for another agent

Primary paper: [Robust and Efficient Tool Orchestration via Layered Execution Structures with Reflective Correction](https://arxiv.org/abs/2602.18968), Tao Zhe et al., arXiv:2602.18968.

## Goal

Implement a RETO-inspired orchestration layer for IrisApp so the local LiteRT SLM can use tools more reliably without fine-tuning.

The current app already has:

- A local LiteRT-LM inference engine in `InferenceManager`.
- A multi-agent workflow generation pipeline in `WorkflowGenerationViewModel`.
- A tool loop in `ToolAwareGenerator`.
- A dynamic tool registry in `ToolRegistry`.
- Prompt helpers in `PromptBuilder`.
- Android/device tools such as `resolve_datetime`, `get_contact`, `list_installed_apps`, `resolve_intent`, `validate_json`, and execution-oriented tools.

The missing piece is stronger orchestration. Right now the SLM sees a set of tools and decides step-by-step. RETO suggests reducing this burden by:

- Predicting a coarse execution structure first.
- Restricting the SLM to only the tools needed for the current layer.
- Validating and repairing failed calls locally instead of restarting the whole plan.

## Paper Analysis

RETO reframes tool use as an orchestration problem, not just a tool-selection problem. The authors argue that failures often come from wrong ordering and ungrounded intermediate results, especially for SLMs with limited context.

The paper identifies two key challenges:

- Fast induction of a coarse execution sketch.
- Faithful execution with local repair when tools fail.

RETO has three stages:

1. Learn: Layer Execution Sketch

   A lightweight predictor assigns each tool to an execution layer. A tool in layer `k` may depend on results from layers `< k`. The system does not need an exact dependency graph. It only needs a rough ordering.

2. Execute: Context-Constrained Invocation

   The model invokes tools layer by layer. At each layer, the prompt only contains:

   - current layer objective
   - allowed tools for this layer
   - validated observations from previous layers
   - strict output rules

   This reduces context size and decision complexity.

3. Reflect and Repair: Local Correction

   When a tool call has bad params, bad schema, empty output, unavailable permission, or execution failure, RETO repairs that specific call under a budget. It does not replan the whole workflow unless local repair fails.

The paper reports major gains for non-tool-tuned SLMs, especially Qwen2.5-7B, and notes that constrained execution plus local repair keeps smaller models functional. This is directly relevant to IrisApp because the app uses an on-device SLM and has tight context, latency, and reliability constraints.

## Adaptation Decision

Do not implement the full learned neural layer predictor in the first version.

Instead, implement a practical RETO-inspired orchestrator with:

- deterministic layer hints from tool metadata
- optional SLM layer-sketch prompt
- strict schema gates
- local repair prompts
- layer-specific tool prompts
- compact observation memory

This gives most of the engineering value now and keeps the door open for a trained layer predictor later.

## Current Flow

Current pipeline:

```text
User request
-> RequestAnalysisAgent
-> Kotlin capability grounding
-> ActionPlanAgent
-> WorkflowJsonAgent
-> WorkflowJsonParser
-> WorkflowValidator
-> user preview / run
```

Current tool loop:

```text
Prompt
-> SLM output
-> ToolCallParser.findToolCall()
-> ToolRegistry.execute()
-> append TOOL_RESULT
-> SLM again
```

Problems:

- The SLM may see too many tools at once.
- The order of tool calls is implicit.
- Tool outputs are appended into a growing transcript, which can pollute context.
- Failed tool calls are not repaired with a dedicated local repair prompt.
- Planning tools and side-effect tools are mixed in the same agent.
- The system does not distinguish read-only, validation, and effectful tools strongly enough.

## Target RETO-Inspired Flow

Target pipeline:

```text
User request
-> Stage A: Request analysis
-> Stage B: Candidate tool retrieval
-> Stage C: Layer sketch
-> Stage D: Layered tool execution with schema gate
-> Stage E: Reflect and repair failed calls
-> Stage F: Action plan from validated observations
-> Stage G: Final workflow JSON
-> Stage H: Workflow validation
-> Stage I: User preview / run
```

For the example:

```text
send message to Maya saying hi, and invite him to meeting on 6 oclock on next friday and then add it to my calendar.
```

Expected layers:

```text
Layer 0: Grounding facts
- get_current_time
- resolve_datetime("next friday at 6pm")
- get_contact("Maya")
- list_installed_apps if app list is missing or stale

Layer 1: Capability checks
- resolve_intent for SMS/message compose
- resolve_intent for calendar insert

Layer 2: Workflow action construction
- no side-effect execution
- map validated facts into ActionSpec steps:
  - sms.compose or share/send message action
  - calendar.create_event

Layer 3: Final JSON validation
- validate_json
```

## Critical Safety Rule

During workflow generation, the SLM must not execute real side effects.

The following tools should not be available to generation agents in effectful mode:

- `send_intent`
- `share_text`
- `set_alarm`
- `create_calendar_event`
- any future tool that sends, posts, deletes, purchases, opens external UI, or mutates device state

Instead, generation should use dry-run or capability tools:

- `resolve_intent`
- `build_intent_preview`
- `validate_action_params`
- `get_contact`
- `resolve_datetime`
- `validate_json`

Real side effects should happen only in `WorkflowRunner` after the user presses Run/Turn On and after confirmation rules are satisfied.

## Proposed Architecture

Add a new package:

```text
app/src/main/java/com/iris/platform/tools/reto/
```

Files:

```text
ToolMetadata.kt
ToolObservation.kt
ToolSchemaGate.kt
RetoLayerSketch.kt
RetoLayerPlanner.kt
RetoLayerExecutor.kt
RetoRepairAgent.kt
RetoOrchestrator.kt
RetoPromptBuilder.kt
RetoTrace.kt
```

Add a domain-facing planner:

```text
app/src/main/java/com/iris/domain/planner/RetoWorkflowPlanner.kt
```

Keep existing classes initially:

- `ToolRegistry`
- `ToolCallParser`
- `ToolAwareGenerator`
- `PromptBuilder`
- `WorkflowJsonParser`
- `WorkflowValidator`

The RETO implementation should wrap and gradually replace the current direct calls in `WorkflowGenerationViewModel`.

## Data Models

### ToolMode

```kotlin
enum class ToolMode {
    READ_ONLY,
    VALIDATION,
    DRY_RUN,
    EFFECTFUL
}
```

Meaning:

- `READ_ONLY`: reads device/local info; safe during generation.
- `VALIDATION`: checks schema or generated JSON; safe during generation.
- `DRY_RUN`: builds previews, checks resolvability, no side effects.
- `EFFECTFUL`: mutates device state or opens/sends something; not allowed during generation.

### ToolLayerHint

```kotlin
enum class ToolLayerHint {
    FACT_GROUNDING,
    CAPABILITY_CHECK,
    PARAMETER_RESOLUTION,
    ACTION_CONSTRUCTION,
    FINAL_VALIDATION,
    EXECUTION
}
```

### ToolMetadata

```kotlin
data class ToolMetadata(
    val name: String,
    val mode: ToolMode,
    val layerHints: Set<ToolLayerHint>,
    val produces: Set<String>,
    val requires: Set<String> = emptySet(),
    val failureSignals: Set<String> = emptySet(),
    val repairable: Boolean = true,
    val maxRepairAttempts: Int = 2,
    val generationAllowed: Boolean = mode != ToolMode.EFFECTFUL
)
```

Example metadata:

```kotlin
ToolMetadata(
    name = "get_contact",
    mode = ToolMode.READ_ONLY,
    layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
    produces = setOf("contact.name", "contact.phone", "contact.email"),
    failureSignals = setOf("READ_CONTACTS permission is not granted", "No contacts matching"),
    maxRepairAttempts = 1
)
```

```kotlin
ToolMetadata(
    name = "resolve_datetime",
    mode = ToolMode.READ_ONLY,
    layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
    produces = setOf("datetime.iso", "datetime.date", "datetime.time", "datetime.unix_ms")
)
```

```kotlin
ToolMetadata(
    name = "create_calendar_event",
    mode = ToolMode.EFFECTFUL,
    layerHints = setOf(ToolLayerHint.EXECUTION),
    produces = setOf("calendar.event.created"),
    generationAllowed = false
)
```

### RetoLayerSketch

```kotlin
data class RetoLayerSketch(
    val request: String,
    val layers: List<RetoLayer>
)

data class RetoLayer(
    val index: Int,
    val objective: String,
    val allowedTools: Set<String>,
    val requiredObservations: Set<String> = emptySet(),
    val outputContract: String
)
```

### ToolObservation

```kotlin
data class ToolObservation(
    val toolName: String,
    val params: JsonObject,
    val success: Boolean,
    val output: String,
    val parsedFacts: Map<String, String> = emptyMap(),
    val error: String? = null,
    val layerIndex: Int,
    val attempt: Int
)
```

### RetoTrace

```kotlin
data class RetoTrace(
    val request: String,
    val layerSketch: RetoLayerSketch,
    val observations: List<ToolObservation>,
    val repairs: List<RetoRepairRecord>,
    val finalWorkflowJson: String?
)
```

This trace should be printed in the debug UI and Logcat.

## Layer Planning Design

### MVP Layer Planner

Implement deterministic layer planning first.

Input:

- user request
- request analysis JSON
- installed apps summary
- available tool metadata
- available ActionSpecs

Output:

- `RetoLayerSketch`

Rules:

- If request contains date/time phrase, include `resolve_datetime` in Layer 0.
- If request includes named person/contact or message/invite/call/email, include `get_contact` in Layer 0.
- If request references app/category and installed apps were not already injected, include `list_installed_apps` in Layer 0.
- If final actions require Android intent availability, include `resolve_intent` in Layer 1.
- Always include `validate_json` in final validation layer.
- Exclude `ToolMode.EFFECTFUL` from generation layers.

Example deterministic output:

```json
{
  "layers": [
    {
      "index": 0,
      "objective": "Resolve missing factual inputs: time and contact.",
      "allowed_tools": ["resolve_datetime", "get_contact"],
      "output_contract": "facts needed for workflow parameters"
    },
    {
      "index": 1,
      "objective": "Check Android capability availability.",
      "allowed_tools": ["resolve_intent"],
      "required_observations": ["datetime.iso", "contact.phone"],
      "output_contract": "available handlers for message and calendar intents"
    },
    {
      "index": 2,
      "objective": "Validate final workflow JSON.",
      "allowed_tools": ["validate_json"],
      "output_contract": "valid JSON or validation error"
    }
  ]
}
```

### Optional SLM Layer Planner

After deterministic MVP works, add an optional SLM layer-sketch prompt.

Important: the SLM should not choose arbitrary tools. It should receive candidate tools and metadata only, then assign layers.

Prompt contract:

```text
You are a RETO layer planner for IrisApp.
Group tools into coarse execution layers.
Do not output tool arguments.
Do not use effectful tools during generation.
Return JSON only.
```

Output schema:

```json
{
  "layers": [
    {
      "index": 0,
      "objective": "resolve contact and time facts",
      "allowed_tools": ["get_contact", "resolve_datetime"],
      "reason": "contact phone and exact datetime are prerequisites"
    }
  ]
}
```

Validate this output with Kotlin. If invalid, fall back to deterministic planner.

## Layer Execution Design

Add `RetoLayerExecutor`.

Responsibilities:

- Execute one layer at a time.
- Only expose tools from `RetoLayer.allowedTools`.
- Carry forward validated observations from previous layers.
- Avoid passing full raw transcript.
- Enforce `maxCallsPerLayer`.
- Enforce `maxRepairsPerTool`.

Pseudo-flow:

```text
for layer in sketch.layers:
    prompt = buildLayerExecutionPrompt(layer, compactObservations)
    while calls < maxCallsPerLayer:
        output = model(prompt)
        if output is final layer summary:
            break
        toolCall = parse(output)
        schemaGate.validate(toolCall, layer.allowedTools)
        result = ToolRegistry.execute(toolCall.name, toolCall.params)
        if result invalid:
            repair = RetoRepairAgent.repair(toolCall, result.error, schema)
            retry same tool
        save ToolObservation
    continue next layer
```

Layer prompt should say:

```text
[RETO LAYER 1/3]
Objective: Resolve contact and datetime facts.

Allowed tools for this layer:
Tool: get_contact
Parameters:
  name (string, required): Contact name or partial match

Tool: resolve_datetime
Parameters:
  expression (string, required): e.g. "next Friday at 6pm"

Validated observations from earlier layers:
none

Rules:
- Call only tools listed in this layer.
- Use at most one tool per response.
- You may call multiple tools across responses if needed.
- Do not invent tool outputs.
- When the layer objective is complete, output:
  LAYER_DONE: {"facts": {...}, "missing": [...]}
```

## Schema Gate Design

Add `ToolSchemaGate`.

Validate before execution:

- tool exists in `ToolRegistry`
- tool is allowed in current layer
- tool is allowed during generation
- all required params present
- no unknown params
- param values are parseable to declared type
- side-effect tools are blocked unless mode is execution
- Android permission is available or error is explicit

Output:

```kotlin
sealed interface SchemaGateResult {
    data object Valid : SchemaGateResult
    data class Invalid(val error: String, val repairable: Boolean) : SchemaGateResult
}
```

If invalid and repairable, call `RetoRepairAgent`.

## Reflect And Repair Design

Add `RetoRepairAgent`.

Input:

- failed tool name
- tool schema
- previous args
- schema gate error or tool execution error
- compact observations
- repair attempt number

Output:

```json
{
  "tool_calls": [
    {
      "name": "resolve_datetime",
      "arguments": {
        "expression": "next Friday at 6pm"
      }
    }
  ]
}
```

Rules:

- Repair must call the same tool unless fallback is explicitly allowed.
- Repair must only use schema keys.
- Repair must not call effectful tools.
- Repair has a strict budget, default 2 attempts.
- If repair fails, mark the observation as failed and move missing info into final workflow `missing_setup`.

Example repair:

Original:

```text
TOOL: resolve_datetime {"date":"next friday", "time":"6"}
```

Schema gate error:

```text
Unknown params: date, time. Required param missing: expression.
```

Repair output:

```json
{
  "tool_calls": [
    {
      "name": "resolve_datetime",
      "arguments": {
        "expression": "next Friday at 6pm"
      }
    }
  ]
}
```

## Observation Memory

Do not append every raw prompt and model output forever.

Maintain compact typed observations:

```text
Observation 1:
- tool: get_contact
- params: {"name":"Maya"}
- success: true
- facts:
  contact.name = Maya Chen
  contact.phone = +15550101001
  contact.email = maya.chen@example.com

Observation 2:
- tool: resolve_datetime
- params: {"expression":"next Friday at 6pm"}
- success: true
- facts:
  datetime.iso = 2026-05-15T18:00:00+04:00
  datetime.unix_ms = ...
```

Final agents should see only this compact summary, not the whole tool transcript.

## Tool Output Parsing

Some current tools return plain strings. For RETO, add optional structured output parsing.

Add:

```kotlin
interface StructuredTool : Tool {
    fun parseFacts(result: ToolResult): Map<String, String>
}
```

MVP implementation:

- `ResolveDatetimeTool.parseFacts`
  - parse `iso`, `date`, `time`, `unix_ms`, `day_of_week`
- `LookupContactTool.parseFacts`
  - parse first result into `contact.name`, `contact.phone`, `contact.email`
- `ListInstalledAppsTool.parseFacts`
  - store app labels/packages as summary
- `ResolveIntentTool.parseFacts`
  - store handler package labels
- `ValidateJsonTool.parseFacts`
  - store `json.valid=true/false`

If modifying `Tool` interface is too broad, add a `ToolFactParserRegistry`.

Recommended MVP:

```text
ToolFactParserRegistry
```

This avoids breaking all tools at once.

## Prompt Builder Changes

Add `RetoPromptBuilder.kt`.

Functions:

```kotlin
fun buildLayerSketchPrompt(...)
fun buildLayerExecutionPrompt(...)
fun buildRepairPrompt(...)
fun buildFinalWorkflowPrompt(...)
fun buildFinishPrompt(...)
```

Keep `PromptBuilder` for existing flow until RETO is stable.

## ViewModel Integration

Add a feature flag first:

```kotlin
private const val USE_RETO_ORCHESTRATION = true
```

In `WorkflowGenerationViewModel.generate()`:

Current:

```kotlin
val agents = PlannerAgents(engine)
...
agents.requestAnalysis(...)
...
agents.actionPlan(...)
...
agents.workflowJson(...)
```

Target:

```kotlin
val planner = RetoWorkflowPlanner(
    engine = engine,
    context = getApplication(),
    capabilityScanner = capabilityScanner,
    actionRegistry = ActionSpecRegistry
)

val result = planner.generateWorkflow(
    userRequest = prompt,
    onTrace = { appendRetoDebug(it) }
)
```

Then parse/validate `result.workflowJson` using existing `WorkflowJsonParser` and `WorkflowValidator`.

## Logging And Debug UI

Add RETO log tags:

```text
RetoOrchestrator
RetoLayerPlanner
RetoLayerExecutor
RetoSchemaGate
RetoRepair
RetoObservation
```

Update `scripts/run_medium_emulator_app.sh` default filter to include:

```text
Reto|RetoOrchestrator|RetoLayer|RetoRepair|RetoSchemaGate|RetoObservation
```

Debug panel should show:

- layer sketch
- allowed tools per layer
- each model raw output
- parsed tool call
- schema gate result
- tool result
- repair attempt
- final compact observation summary
- final JSON

## End-To-End Example

User:

```text
send message to Maya saying hi, and invite him to meeting on 6 oclock on next friday and then add it to my calendar.
```

Expected RETO trace:

```text
Layer 0: Resolve facts
Allowed tools: get_contact, resolve_datetime

Call 1:
TOOL: get_contact {"name":"Maya"}

Result:
Maya Chen | phone: +15550101001 | email: maya.chen@example.com

Call 2:
TOOL: resolve_datetime {"expression":"next Friday at 6pm"}

Result:
iso: 2026-05-15T18:00:00+04:00
date: 2026-05-15
time: 18:00
unix_ms: ...
day_of_week: friday

Layer 1: Capability checks
Allowed tools: resolve_intent

Call 3:
TOOL: resolve_intent {"action":"android.intent.action.SENDTO","data_uri":"smsto:+15550101001"}

Call 4:
TOOL: resolve_intent {"action":"android.intent.action.INSERT","mime_type":"vnd.android.cursor.item/event"}

Layer 2: Final workflow
Allowed tools: validate_json

Final workflow:
{
  "name": "Message Maya and Add Meeting",
  "summary": "Prepares a message to Maya and creates a calendar meeting for next Friday at 6 PM.",
  "trigger": {
    "type": "manual",
    "setup_state": "ready",
    "schedule": null
  },
  "actions": [
    {
      "id": "sms.compose",
      "params": {
        "phone": "+15550101001",
        "message": "hi"
      },
      "requires_confirmation": true
    },
    {
      "id": "calendar.create_event",
      "params": {
        "title": "Meeting with Maya",
        "begin_time_millis": 1778846400000,
        "location": ""
      },
      "requires_confirmation": true
    }
  ],
  "missing_setup": []
}
```

## Implementation Tasks

### Phase 1: Metadata And Safety

- [ ] Add `ToolMode`, `ToolLayerHint`, and `ToolMetadata`.
- [ ] Add `ToolMetadataRegistry`.
- [ ] Register metadata for all current tools:
  - `get_current_time`
  - `resolve_datetime`
  - `compute_duration`
  - `get_day_of_week`
  - `list_installed_apps`
  - `resolve_intent`
  - `get_device_location`
  - `web_search`
  - `search_places`
  - `get_contact`
  - `lookup_contact`
  - `calculator`
  - `validate_json`
  - `send_intent`
  - `open_uri`
  - `share_text`
  - `set_alarm`
  - `create_calendar_event`
- [ ] Mark side-effect tools as `ToolMode.EFFECTFUL`.
- [ ] Remove effectful tools from generation agent prompts or block them with `ToolSchemaGate`.
- [ ] Add tests proving effectful tools are blocked during generation.

### Phase 2: Schema Gate

- [ ] Add `ToolSchemaGate`.
- [ ] Validate allowed layer tools.
- [ ] Validate required params.
- [ ] Validate unknown params.
- [ ] Validate type coercion for `string`, `int`, `float`, `boolean`.
- [ ] Validate permission-related prerequisites where possible.
- [ ] Return structured `SchemaGateResult`.
- [ ] Add unit tests for valid, missing, unknown, wrong-type, disallowed-tool, and effectful-tool cases.

### Phase 3: Observation Store

- [ ] Add `ToolObservation`.
- [ ] Add `ObservationStore`.
- [ ] Add compact observation renderer for prompts.
- [ ] Add `ToolFactParserRegistry`.
- [ ] Parse facts for datetime, contacts, intent handlers, installed apps, and JSON validation.
- [ ] Add tests for fact parsing.

### Phase 4: Layer Planner

- [ ] Add `RetoLayerSketch` and `RetoLayer`.
- [ ] Add deterministic `RetoLayerPlanner`.
- [ ] Input: request analysis, tool metadata, action specs, installed apps.
- [ ] Output: safe layered sketch.
- [ ] Add rules for contact, datetime, app, place, intent, and final JSON cases.
- [ ] Add fallback behavior when no tools are needed.
- [ ] Add unit tests for:
  - contact + calendar request
  - place/navigation request
  - note-taking request
  - pure manual workflow request
  - request with missing app

### Phase 5: Layer Executor

- [ ] Add `RetoLayerExecutor`.
- [ ] Build layer-specific prompts with only allowed tools.
- [ ] Use `ToolCallParser`.
- [ ] Run `ToolSchemaGate` before tool execution.
- [ ] Execute read-only/validation/dry-run tools only.
- [ ] Store observations.
- [ ] Stop a layer when the model emits `LAYER_DONE`.
- [ ] Enforce max calls per layer.
- [ ] Add tests with fake model outputs and fake tools.

### Phase 6: Reflect And Repair

- [ ] Add `RetoRepairAgent`.
- [ ] Add `buildRepairPrompt`.
- [ ] Repair schema-gate failures.
- [ ] Repair tool execution failures.
- [ ] Retry same tool with repaired args.
- [ ] Limit attempts per tool.
- [ ] Convert unrepaired failures into `missing_setup`.
- [ ] Add tests:
  - wrong param name repaired
  - missing required param repaired
  - unparseable datetime repaired
  - contact not found becomes missing setup
  - permission denied becomes missing setup

### Phase 7: Final Workflow Generation

- [ ] Add `RetoWorkflowPlanner`.
- [ ] Generate final action plan from compact observations.
- [ ] Generate final workflow JSON.
- [ ] Run `validate_json` as a validation layer.
- [ ] Run existing `WorkflowJsonParser`.
- [ ] Run existing `WorkflowValidator`.
- [ ] Ensure final actions use `ActionSpecRegistry` action IDs only.
- [ ] Ensure no Android raw intent strings leak into final workflow JSON unless the action spec explicitly supports it.

### Phase 8: UI And Logs

- [ ] Add `RetoTrace`.
- [ ] Add ViewModel debug rendering for:
  - layer sketch
  - layer start/end
  - tool call
  - tool result
  - repair attempt
  - final observations
- [ ] Update Logcat filter in `scripts/run_medium_emulator_app.sh`.
- [ ] Show layer progress in UI timeline.

### Phase 9: Tests And Smoke Scenarios

- [ ] Unit test `ToolMetadataRegistry`.
- [ ] Unit test `ToolSchemaGate`.
- [ ] Unit test `RetoLayerPlanner`.
- [ ] Unit test `ToolFactParserRegistry`.
- [ ] Unit test `RetoRepairAgent` with fake model.
- [ ] Unit test `RetoLayerExecutor` with fake tools/model.
- [ ] Device smoke test: resolve `Maya`, resolve `next Friday at 6pm`, create workflow preview.
- [ ] Device smoke test: missing contact produces `missing_setup`.
- [ ] Device smoke test: denied contacts permission produces clear error.
- [ ] Device smoke test: no effectful tool runs during Generate.
- [ ] Device smoke test: user clicks Run and only then side-effect actions execute.

## Acceptance Criteria

The implementation is ready when:

- Generation agents cannot call effectful tools.
- A multi-tool request triggers a layer sketch.
- Each layer prompt only includes allowed tools for that layer.
- Tool calls are validated before execution.
- Failed tool calls get local repair attempts.
- The final JSON uses only `ActionSpecRegistry` action IDs.
- Debug logs show RETO layers, calls, repairs, observations, and final JSON.
- The example request involving Maya, next Friday, message, and calendar produces a valid workflow preview.
- The app builds with:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

## Important Implementation Notes

- Keep the old pipeline behind a fallback flag until RETO is stable.
- Do not fine-tune a model in the MVP.
- Do not train a neural layer predictor in the MVP.
- Do not expose all tools to every agent.
- Do not let the SLM hallucinate contact phone numbers, package names, IDs, or Android extras.
- Do not let workflow generation perform real actions.
- Keep prompts short and layer-local.
- Keep observations compact and typed.
- Treat `missing_setup` as a normal safe outcome, not a failure.

## Future Upgrade: Learned Layer Predictor

Once the deterministic RETO pipeline works, a future agent can add a lightweight learned layer predictor:

- Textualize request and tool docs.
- Generate synthetic training examples from successful traces.
- Train a small classifier or ordinal regressor off-device.
- Export a tiny model or rules table for Android.
- Compare deterministic vs learned layer sketches.

For the hackathon, deterministic RETO is more feasible and safer.

