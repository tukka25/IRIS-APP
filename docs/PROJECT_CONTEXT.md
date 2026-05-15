# IrisApp Project Context

Last updated: 2026-05-11

## Product Summary

IrisApp is an Android app that turns a user's natural-language request into a runnable phone workflow.

The user can say something like:

```text
Send a message to Maya saying hi, and add a meeting with her to my calendar next Friday at 6.
```

The app should:

1. Understand the user's goal.
2. Split the request into logical tasks.
3. Choose supported phone actions.
4. Resolve needed facts such as contacts, dates, places, files, or installed apps.
5. Build a validated workflow.
6. Let the user preview, save, and run it.
7. Optionally attach the workflow to a supported trigger.

The core idea is similar to a local, on-device AI version of Apple Shortcuts, Tasker, or MacroDroid, but designed for non-technical users.

## Current Technical Direction

The current app is Android-native:

- Kotlin
- Jetpack Compose
- LiteRT-LM for local on-device SLM inference
- Gemma `.litertlm` model stored on-device
- Local JSON file persistence
- Android intents, ContentResolver APIs, AlarmManager, broadcast receivers, and geofencing for execution

The project is no longer using `llama.cpp`. The current inference path is LiteRT-LM.

## Product Vocabulary

### Workflow

A saved automation created from a user request.

In product/design docs this may also be called a **Routine**.

### Action

A single executable step in a workflow, such as:

- compose SMS
- create calendar event
- open URL
- open maps
- copy text
- set alarm

### Trigger

The event that starts a workflow, such as:

- manual run
- time
- NFC
- share sheet
- battery level
- charger connected
- Wi-Fi state
- Bluetooth device
- airplane mode
- Do Not Disturb
- geofence

### ActionSpec

The canonical Kotlin source of truth for supported actions.

The SLM is not allowed to invent Android APIs. It may only choose action IDs from `ActionSpecRegistry`.

## High-Level Architecture

```text
User request
  -> LiteRT-LM planner pipeline
  -> RETO task decomposition / capability binding / slot grounding
  -> Kotlin tool resolution
  -> final workflow JSON
  -> parser + validator
  -> PlannedWorkflow
  -> preview / save / run
  -> WorkflowRunner
  -> Android execution layer
```

## Main Source Areas

```text
app/src/main/java/com/iris/
├── app/
│   └── IrisAppApp.kt
├── data/
│   ├── local/storage/
│   ├── repository/
│   └── seed/
├── domain/
│   ├── catalog/
│   ├── model/
│   ├── parser/
│   ├── planner/
│   ├── runner/
│   ├── safety/
│   └── triggers/
├── platform/
│   ├── alarm/
│   ├── calendar/
│   ├── capability/
│   ├── clipboard/
│   ├── inference/
│   ├── location/
│   ├── nfc/
│   ├── share/
│   ├── tools/
│   └── trigger/
└── ui/
    ├── MainActivity.kt
    ├── home/
    ├── nfc/
    └── trigger/
```

## Inference

Inference is managed by:

```text
app/src/main/java/com/iris/platform/inference/InferenceManager.kt
```

Responsibilities:

- Locate the default `.litertlm` model file.
- Load the LiteRT-LM engine once.
- Prefer GPU if configured.
- Fall back to CPU when GPU is not forced.
- Expose loading state to UI.
- Initialize tools after the model is ready.

The model file is expected under the app external files model directory, usually:

```text
/sdcard/Android/data/com.irisapp/files/models/gemma-4-E2B-it.litertlm
```

The local repo model path is:

```text
local-models/gemma-4-E2B-it.litertlm
```

## Planner Pipeline

The main planner entry point is:

```text
app/src/main/java/com/iris/domain/planner/RetoWorkflowPlanner.kt
```

It calls:

```text
app/src/main/java/com/iris/platform/tools/reto/RetoOrchestrator.kt
```

Current normal AI call sequence:

1. `TaskDecomposer`
2. `CapabilityBinder`
3. `SlotGroundingPlanner`
4. request analysis from facts
5. action plan from facts
6. final workflow JSON
7. optional JSON repair retry if validation fails

## RETO Flow

The RETO-inspired flow is:

```text
Phase 0: TaskDecomposer
  -> classify user request into logical tasks
  -> examples: send_message, create_event, navigate, share

Phase 1: CapabilityBinder
  -> map each logical task to supported ActionSpecs
  -> only uses actions available on this device

Phase 2: SlotGroundingPlanner
  -> inspect required params for selected actions
  -> decide whether each param is literal, needs a tool, missing, or unused

Phase 3: ResolverRegistry
  -> Kotlin executes approved tools
  -> examples: get_contact, resolve_datetime, search_places

Phase 4: Final workflow JSON
  -> model formats a workflow contract
  -> Kotlin parses, repairs small syntax issues, validates, and optionally retries once
```

## Logical Actions

The current logical action categories are defined in `ActionSpecRegistry.kt`:

```text
send_message
make_call
create_event
set_reminder
set_alarm
open_app
search
share
navigate
play_media
open_file
take_note
check_notification
get_info
other
```

These are not Android APIs. They are high-level task categories used by the planner.

## ActionSpec Registry

The canonical action registry is:

```text
app/src/main/java/com/iris/domain/catalog/ActionSpecRegistry.kt
```

Each `ActionSpec` owns:

- action ID
- user-facing label
- description
- typed params
- execution method
- availability rule
- trigger compatibility
- confirmation requirement
- logical action mapping
- tool bindings
- examples
- fallback action IDs

The model sees only prompt-safe summaries. Kotlin owns the Android details.

## Current Action Surface

Implemented or cataloged actions include:

- `browser.open_url`
- `browser.search`
- `maps.open_place`
- `maps.navigate`
- `share.share_text`
- `share.share_image`
- `sms.compose`
- `phone.dial`
- `alarm.set_alarm`
- `alarm.set_timer`
- `clipboard.copy_text`
- `calendar.create_event`
- `internal.reminder.create`
- `app.open`
- `file.open`
- `note.create`
- `media.play_from_search`
- optional app-specific actions such as WhatsApp or Spotify when available

Exact availability depends on:

- installed apps
- Android package visibility
- `PackageManager` resolution
- `ActionSpec` availability policy

## Why ActionSpecs Exist

Android does not expose a safe universal schema of every installed app's private intents and extras.

So IrisApp uses a curated, declarative action registry:

```text
SLM chooses: action_id + typed params
Kotlin validates: schema + types + availability
Kotlin executes: real Android intent/API
```

The model must not output:

- Android intent constants
- extra keys
- package-private APIs
- raw package names unless the selected action schema asks for one
- arbitrary URI templates

## Tool System

Tools are Kotlin functions exposed to the planner as controlled capabilities.

Main files:

```text
app/src/main/java/com/iris/platform/tools/Tool.kt
app/src/main/java/com/iris/platform/tools/ToolRegistry.kt
app/src/main/java/com/iris/platform/tools/ToolInitializer.kt
app/src/main/java/com/iris/platform/tools/FindSkill.kt
app/src/main/java/com/iris/platform/tools/reto/ToolMetadataRegistry.kt
```

Tool categories include:

- temporal tools
- contacts tools
- device tools
- domain search tools
- execution tools
- reasoning/validation tools
- settings tools

Important tools:

- `get_current_time`
- `resolve_datetime`
- `compute_duration`
- `get_contact`
- `list_installed_apps`
- `resolve_intent`
- `search_places`
- `search_media`
- `search_files`
- `search_notes`
- `get_calendar_events`
- `validate_json`

Tools are scoped through `ActionSpec` metadata so the model receives only tools relevant to the selected action.

## Datetime Handling

Datetime resolution is handled by:

```text
app/src/main/java/com/iris/platform/tools/impl/TemporalTools.kt
```

Important behavior:

- Relative expressions should include `reference_time_iso`.
- Timezone should be passed explicitly.
- Ambiguous times like `next Friday at 6 o'clock` should include `default_period`.
- Meetings/invitations/social plans should usually set `default_period = "pm"` unless the user says morning.

Example tool call:

```text
TOOL: resolve_datetime {
  "expression": "next Friday at 6 o'clock",
  "reference_time_iso": "2026-05-11T12:00:00+04:00",
  "timezone": "Asia/Dubai",
  "default_period": "pm"
}
```

## JSON Safety

The parser is:

```text
app/src/main/java/com/iris/domain/parser/WorkflowJsonParser.kt
```

The validator is:

```text
app/src/main/java/com/iris/domain/safety/WorkflowValidator.kt
```

Safety layers:

- Extract JSON object from model output.
- Repair common syntax mistakes.
- Strip trailing commas.
- Fold simple integer arithmetic like `1777810000000 + 3600000`.
- Parse to `PlannedWorkflow`.
- Validate:
  - workflow name
  - known action IDs
  - action availability
  - trigger compatibility
  - required params
  - param types
  - URL/URI schemes
  - enum values
  - unknown params
  - confirmation requirements
- If final JSON fails, `RetoWorkflowPlanner` can ask the model for one repair attempt.

## Workflow Model

Core model file:

```text
app/src/main/java/com/iris/domain/model/WorkflowModels.kt
```

Important classes:

- `PlannedWorkflow`
- `WorkflowStep`
- `TriggerConfig`
- `SetupState`
- `WorkflowStatus`
- `ExecutionResult`
- `ExecutionLogEntry`

`TriggerConfig` is a sealed class with variants for:

- manual
- time
- NFC
- share sheet
- Tasker required
- battery
- charger
- Wi-Fi
- Bluetooth
- airplane mode
- Do Not Disturb
- geofence

## Execution Pipeline

Detailed doc:

```text
docs/implementation/EXECUTION_PIPELINE.md
```

Main runner:

```text
app/src/main/java/com/iris/domain/runner/WorkflowRunner.kt
```

Execution flow:

```text
PlannedWorkflow
  -> WorkflowRunner.run()
  -> for each WorkflowStep:
       resolve $step[N].output references
       find ActionSpec
       check confirmation gate
       execute via built-in executor, internal tool, Custom Tab, PackageManager, or Android intent
       collect ExecutionResult
```

Supported execution mechanisms:

- `CalendarApiExecutor`
- `AlarmApiExecutor`
- `ClipboardApiExecutor`
- `ChromeCustomTabOpener`
- `IntentFactory`
- `PackageManager` launch
- `ToolRegistry` internal tool execution

## Confirmation Flow

Some actions require user confirmation before execution.

If a workflow is running in the foreground, the UI can show a confirmation dialog.

If a workflow is fired by a background trigger, `platform.trigger.TriggerRegistry` catches `ConfirmationRequired`, stores pending execution, and posts a notification.

The user can:

- confirm and resume
- dismiss and stop

## Persistence

Persistence is local file-based JSON storage.

Main files:

```text
app/src/main/java/com/iris/data/local/storage/JsonFileStorage.kt
app/src/main/java/com/iris/data/repository/WorkflowRepository.kt
app/src/main/java/com/iris/data/repository/ExecutionHistoryRepository.kt
```

No backend is currently required for workflow generation or execution.

## Trigger System

Research doc:

```text
docs/research/trigger_feasibility.md
```

Runtime trigger managers live in:

```text
app/src/main/java/com/iris/platform/trigger/
app/src/main/java/com/iris/platform/location/
app/src/main/java/com/iris/platform/alarm/
app/src/main/java/com/iris/platform/nfc/
app/src/main/java/com/iris/platform/share/
```

Current trigger runtime surface:

| Trigger | Runtime status |
|---|---|
| Manual | supported |
| Time | supported through AlarmManager |
| NFC | supported through deep link / NDEF flow |
| Share Sheet | supported through Android share intent |
| Battery | runtime manager exists |
| Charger | runtime manager exists |
| Wi-Fi | runtime manager exists |
| Bluetooth | runtime manager exists |
| Airplane Mode | runtime manager exists |
| Do Not Disturb | runtime manager exists |
| Geofence | runtime manager exists; requires location setup |
| Notification/message received | researched but not fully implemented |
| SMS received | planned |
| Alarm stopped/snoozed | planned for own alarms only |

## Important Current Gap

The runtime and `TriggerConfig` support the new trigger types, but generation/parser support is not fully aligned yet.

Current gap:

- `TriggerConfig` includes `battery`, `charger`, `wifi`, `bluetooth`, `airplane_mode`, `dnd`, and `geofence`.
- `WorkflowValidator` maps those trigger classes to trigger IDs.
- But `WorkflowJsonParser` currently parses only:
  - `time`
  - `nfc`
  - `share_sheet`
  - `tasker_setup_required`
  - everything else falls back to manual.
- `PromptBuilder.buildWorkflowJsonPrompt()` still lists the older trigger set.
- Many `ActionSpec.triggerCompatible` sets still need the new trigger IDs where appropriate.

If the model outputs:

```json
{
  "trigger": {
    "type": "wifi"
  }
}
```

the current parser will likely turn it into `TriggerConfig.Manual`.

The next integration task is:

```text
Teach PromptBuilder + WorkflowJsonParser + ActionSpec trigger compatibility about the new triggers.
```

## Current UI

Main UI entry:

```text
app/src/main/java/com/iris/ui/MainActivity.kt
```

Main ViewModel:

```text
app/src/main/java/com/iris/ui/home/WorkflowGenerationViewModel.kt
```

The UI currently supports:

- model loading state
- natural-language prompt
- generation progress
- debug logs
- parsed workflow preview
- validation errors
- saving workflows
- running workflows manually
- confirmation dialog
- saved workflow selection
- time trigger setup
- NFC trigger setup
- share sheet setup
- manual workflow editor
- trigger-specific fields in manual editor for new trigger classes

## Android Permissions And Setup

Permissions in `AndroidManifest.xml` include:

- contacts
- SMS read
- notifications
- internet
- media read
- exact alarms
- boot completed
- DND policy access
- calendar read/write
- NFC
- Bluetooth
- Wi-Fi/network state
- fine/coarse/background location

Some permissions still require runtime request or settings setup, especially:

- calendar
- contacts
- notifications
- location
- background location
- notification policy access
- exact alarms
- Bluetooth connect on Android 12+

## Running On Emulator Or Phone

Common scripts:

```text
scripts/run_medium_emulator_app.sh
scripts/run_litert_lm_android.sh
scripts/setup_litert_lm.sh
```

Useful logs:

```bash
adb logcat | grep -Ei "WorkflowGeneration|WorkflowRunner|InferenceManager|TriggerRegistry|Tool call|Tool result|Reto|CapabilityBinder|SlotGrounding|TaskDecomposer"
```

Model push path:

```bash
adb shell mkdir -p /sdcard/Android/data/com.irisapp/files/models
adb push local-models/gemma-4-E2B-it.litertlm /sdcard/Android/data/com.irisapp/files/models/gemma-4-E2B-it.litertlm
```

## Design/Product Docs

Important docs:

```text
docs/design/gemmaos_wireframe_features.md
docs/design/wireframe_ai_helper_prompt.md
docs/implementation/EXECUTION_PIPELINE.md
docs/implementation/P1_TRIGGERS_PLAN.md
docs/implementation/TRIGGERS_PROGRESS.md
docs/research/trigger_feasibility.md
docs/research/reto_tool_orchestration_for_iris.md
INTENTS.md
WORKFLOW_FEATURE.md
TODO.md
```

## Development Principles

- Keep `ActionSpecRegistry` as the source of truth for supported actions.
- Do not let the SLM invent raw Android APIs.
- Prefer deterministic Kotlin validation and execution over trusting model output.
- Keep final workflow execution behind validation.
- Use tools for grounded facts instead of model guessing.
- Use background trigger confirmation notifications when actions require confirmation.
- Do not make unsupported triggers appear active.
- If a trigger requires setup, save as setup-needed or manual-only until setup is complete.

## Recommended Next Tasks

1. Update `WorkflowJsonParser` to parse new trigger configs.
2. Update `PromptBuilder` final schema to include supported trigger types.
3. Update `ActionSpec.triggerCompatible` for new trigger IDs.
4. Add trigger validation for trigger-specific required fields.
5. Add UI setup flows or clear setup states for new triggers.
6. Add tests for parsing and validating each trigger type.
7. Consider reducing AI calls by letting Kotlin canonicalize the final workflow object instead of asking the SLM to format the final JSON.
