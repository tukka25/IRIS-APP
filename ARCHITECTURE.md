# IrisApp Architecture And Technology Plan

## Purpose

IrisApp is a hackathon Android app that turns a natural-language request into a runnable cross-app workflow. The strongest demo path is:

```text
User prompt -> on-device planner -> validated JSON workflow -> save -> run now or attach trigger
```

The app should prove three things:

- A small local model (LiteRT-LM with GPU acceleration) can convert plain English into structured automation.
- The generated plan can be validated before execution.
- Android can execute useful cross-app actions through intents, URL schemes, and triggers.
- On-device inference runs on phone GPU via LiteRT-LM, not CPU-bound llama.cpp.

## Hackathon Scope

### MVP

- Android app built with Kotlin and Jetpack Compose.
- Prompt input screen with a mock planner and a LiteRT-LM GPU planner option.
- JSON workflow preview.
- Workflow parser and allowlist validator.
- Workflow library using local persistence.
- Manual "Run Now" execution for a small set of Android-real actions.
- NFC trigger proof of concept or Tasker-assisted trigger setup.
- Demo fallback that works even if on-device inference is slow.

### Not MVP

- Full app marketplace of actions.
- Silent background automation across every app.
- Play Store-ready AccessibilityService automation.
- Location triggers, app-open triggers, and Termux scripting unless the MVP is already stable.
- iOS/macOS-only apps such as Things, Bear, Drafts, and Scriptable as Android demo targets.

## System Architecture

```text
+------------------------------------------------------------------+
|                         Android App                              |
|                                                                  |
|   +-------------+    +---------------+    +-------------------+   |
|   | Home Screen |    | Workflow List |    | Workflow Detail   |   |
|   +------+------+    +-------+-------+    +---------+---------+   |
|          |                   |                      |             |
|          +-------------------+----------+-----------+             |
|                                         |                         |
|                         +---------------v---------------+         |
|                         | WorkflowViewModel             |         |
|                         | StateFlow + UI events         |         |
|                         +---------------+---------------+         |
|                                         |                         |
|        +----------------+---------------+---------------+         |
|        |                |                               |         |
| +------v------+  +------v--------+              +-------v------+  |
| | Planner     |  | Workflow      |              | Runner       |  |
| | Service     |  | Store         |              | Intents/URLs |  |
| +------+------+  | Room/DataStore|              +-------+------+  |
|        |         +---------------+                      |         |
| +------v------+  +---------------+              +-------v------+  |
| | Mock Planner|  | Parser/Router |              | Android OS   |  |
| | Llama       |  | JSON allowlist |              | Apps/Tasker  |  |
| +------+------+  +---------------+              +--------------+  |
|        |                                                         |
| +------v------+                                                  |
| | LitertLmEngine |                                                  |
| | LiteRT-LM API  |                                                  |
| | .litertlm model|                                                  |
| +-------------+                                                  |
+------------------------------------------------------------------+
```

## Core Runtime Flow

1. User enters a request on the Home screen.
2. `PromptBuilder` creates a compact system prompt containing the JSON schema, supported actions, and trigger types.
3. `PlannerService` calls the 4-stage planner pipeline using the loaded LiteRT-LM engine:
   - **Stage 1:** `RequestAnalysisAgent` — extracts goal, trigger hint, app categories.
   - **Stage 2:** `CapabilityResolverAgent` — grounds request against `ActionSpecRegistry` + `PackageManager`.
   - **Stage 3:** `ActionPlanAgent` — selects action IDs and fills params.
   - **Stage 4:** `JsonBuildingAgent` — produces final validated JSON.
4. `WorkflowJsonParser` extracts and decodes the returned JSON into typed Kotlin models.
5. `WorkflowValidator` validates every action, parameter, URL, and trigger against `ActionSpecRegistry`.
6. The UI shows the workflow preview (step list + params) and raw JSON.
7. User saves the workflow — stored as JSON via `JsonFileStorage` (file-based, not Room).
8. User taps "Run Now" or attaches a trigger (NFC, Time, Share Sheet).
9. `WorkflowRunner` dispatches steps sequentially — either via `IntentFactory` (AndroidIntent), `ChromeCustomTabOpener` (CustomTab), or platform executors (Clipboard, Calendar, Alarm).
10. `ExecutionHistoryRepository` appends `ExecutionLogEntry` to the on-disk history log.

## Technology Choices

| Area | Choice | Reason |
|------|--------|--------|
| Language | Kotlin | Native Android language with coroutines, sealed classes, and null safety. |
| UI | Jetpack Compose + Material 3 | Fast hackathon UI iteration and modern Android patterns. |
| State | ViewModel + StateFlow | Simple observable UI state without overbuilding. |
| Architecture | MVVM with small MVI-style events | Predictable UI flow and easy testing. |
| Dependency injection | Manual providers first, Hilt later | Manual DI is faster while the app is small. |
| Persistence | Room | Reliable local storage for workflows and history. |
| Preferences | DataStore | Store selected backend, model path, and demo settings. |
| JSON | kotlinx.serialization | Typed decoding for workflow data and planner output. |
|| Inference | LiteRT-LM Kotlin API (Google) | On-device LLM inference with first-class GPU acceleration via OpenCL/Vulkan. No JNI bridge needed. |
|| Model format | .litertlm | LiteRT-LM native model format. Convert from HuggingFace or use pre-converted models from litert-community. |
|| Model target | Gemma 3 1B IT .litertlm | Small enough for phone demo, GPU-accelerated via LiteRT-LM. |
|| Build | Gradle Kotlin DSL (no CMake) | Pure Kotlin build — LiteRT-LM ships as an AAR from Google Maven. |
| Primary actions | Android intents and URL schemes | Best chance of working across installed apps. |
| Triggers | Manual run, NFC, optional Tasker plugin | Keeps demo focused and avoids heavy permissions early. |

## Package Structure

```text
app/src/main/java/com/iris/
+-- app/
|   +-- IrisAppApp.kt
|   +-- AppContainer.kt
+-- core/
|   +-- catalog/
|   +-- error/
|   +-- model/
|   +-- permissions/
+-- ui/
|   +-- MainActivity.kt
|   +-- navigation/
|   |   +-- AppNavGraph.kt
|   +-- home/
|   |   +-- HomeScreen.kt
|   |   +-- HomeViewModel.kt
|   |   +-- HomeUiState.kt
|   +-- workflows/
|   |   +-- WorkflowListScreen.kt
|   |   +-- WorkflowDetailScreen.kt
|   +-- triggers/
|   |   +-- TriggerSetupScreen.kt
|   +-- history/
|   +-- components/
|   |   +-- PromptInput.kt
|   |   +-- WorkflowCard.kt
|   |   +-- ActionRow.kt
|   |   +-- JsonPreview.kt
|   +-- theme/
+-- data/
|   +-- local/
|   |   +-- database/
|   |   +-- dao/
|   |   +-- entity/
|   +-- repository/
|   +-- settings/
+-- domain/
|   +-- planner/
|   |   +-- PlannerService.kt
|   |   +-- PlannerEngine.kt
|   |   +-- PromptBuilder.kt
|   +-- parser/
|   |   +-- WorkflowJsonParser.kt
|   +-- safety/
|   |   +-- ActionCatalog.kt
|   |   +-- SafeActionRouter.kt
|   +-- runner/
|       +-- WorkflowRunner.kt
|       +-- RunnerResultMapper.kt
|   +-- triggers/
+-- platform/
|   +-- dispatch/
|   |   +-- IntentDispatcher.kt
|   |   +-- UrlDispatcher.kt
||   +-- inference/
||   |   +-- litert/
||   |       +-- LitertLmEngine.kt
||   |       +-- ModelFileLocator.kt
|   +-- logging/
|   +-- nfc/
|   |   +-- NfcTriggerWriter.kt
|   |   +-- NfcWorkflowReceiver.kt
|   +-- tasker/
|       +-- TaskerPluginEditActivity.kt
|       +-- TaskerPluginFireReceiver.kt

app/src/main/assets/
+-- grammars/
|   +-- planner-json.gbnf
+-- models/
|   +-- gemma3-1b-it.litertlm

LiteRT-LM/                (cloned sibling repo for GPU libs & tools)
+-- prebuilt/android_arm64/

app/src/test/java/com/iris/
+-- domain/
|   +-- parser/
|   +-- runner/
|   +-- safety/

app/src/androidTest/java/com/iris/
+-- data/local/
+-- ui/
```

## Data Contracts

### Planner Output

The model should produce JSON only:

```json
{
  "name": "Focus session",
  "trigger": {
    "type": "manual"
  },
  "actions": [
    {
      "app": "spotify",
      "action": "play_search",
      "params": {
        "query": "deep focus playlist"
      }
    },
    {
      "app": "obsidian",
      "action": "create_note",
      "params": {
        "title": "Deep work",
        "content": "Focus session started."
      }
    }
  ]
}
```

### Kotlin Model Shape

```kotlin
@Serializable
data class PlannedWorkflow(
    val name: String,
    val trigger: TriggerConfig = TriggerConfig.Manual,
    val actions: List<WorkflowStep>
)

@Serializable
sealed interface TriggerConfig {
    @Serializable data object Manual : TriggerConfig
    @Serializable data class Nfc(val tagId: String? = null) : TriggerConfig
    @Serializable data class Time(val hour: Int, val minute: Int) : TriggerConfig
}

@Serializable
data class WorkflowStep(
    val app: String,
    val action: String,
    val params: Map<String, String> = emptyMap()
)
```

For the hackathon, keep the persisted workflow entity simple:

```kotlin
@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val triggerJson: String,
    val actionsJson: String,
    val rawPlannerJson: String,
    val createdAtMillis: Long
)
```

## Action Catalog

| App | Action | Dispatch path |
|-----|--------|---------------|
| Browser | `open_url` | Chrome Custom Tab |
| Maps | `open_place` | `ACTION_VIEW` + geo: URI |
| Share | `share_text` | ClipboardManager |
| Share | `share_image` | ClipboardManager |
| SMS | `compose` | `ACTION_SENDTO` |
| Alarm | `set_alarm` | `AlarmManager.setExactAndAllowWhileIdle()` |
| Clipboard | `copy_text` | `ClipboardManager.setPrimaryClip()` |
| Calendar | `create_event` | `ContentResolver.insert(CalendarContract)` |

> **ExecutionSpec variants:** `AndroidIntent` (generic Intent), `CustomTab` (Chrome Custom Tabs), `BuiltIn` (direct platform API — clipboard, calendar, alarm).

## Planner Layer

### Interfaces

```kotlin
interface PlannerEngine {
    suspend fun generate(prompt: String): String
}

class PlannerService(
    private val promptBuilder: PromptBuilder,
    private val parser: WorkflowJsonParser,
    private val router: SafeActionRouter,
    private val engine: PlannerEngine
) {
    suspend fun plan(userRequest: String): PlannedWorkflow {
        val raw = engine.generate(promptBuilder.build(userRequest))
        val workflow = parser.parse(raw)
        return router.validate(workflow)
    }
}
```

### Mock Planner

The mock planner is required, not optional. It gives the team a stable demo while JNI and model performance are still moving.

### LiteRT-LM Planner

Use the LiteRT-LM path after the mock flow works end to end:

- Push a `.litertlm` model to the device (pre-converted Gemma models available on HuggingFace litert-community).
- Use `LitertLmEngine` which wraps LiteRT-LM's Kotlin API — no JNI bridge needed.
- GPU acceleration via OpenCL/Vulkan through `Backend.GPU()`.
- Generate on `Dispatchers.Default` with coroutines.
- Keep prompt and output small.
- Cap generation with sampler config (topK, topP, temperature).
- Show model load and generation errors in the UI.

## Inference Layer

LiteRT-LM uses a pure Kotlin API — no CMake, no JNI, no NDK. The `Engine` class
handles model loading, GPU backend selection, and conversation management.

Engine initialization (call on background thread):

```kotlin
val engine = LitertLmEngine()
engine.initialize(
    modelPath = "/path/to/model.litertlm",
    cacheDir = context.cacheDir.absolutePath,
    backend = Backend.GPU()
)
```

Generation:

```kotlin
val response = engine.generate("Your prompt here")
// or streaming:
engine.generateStream(prompt).collect { token -> ... }
```

LiteRT-LM rules:

- Always close the engine (`engine.close()`) in ViewModel `onCleared()`.
- Cache directory (`cacheDir`) speeds up subsequent model loads.
- GPU requires `<uses-native-library>` entries in AndroidManifest.xml for OpenCL and Vulkan.
- Do not reload the model for every prompt — reuse the engine instance.
- Log timing to Logcat for demo tuning.

## Trigger Architecture

### Manual Run

Manual run is the first trigger and must always work.

```text
Workflow Detail -> Run Now -> WorkflowRunner -> dispatch steps -> history
```

### NFC

NFC is the best physical demo trigger:

- Write an NDEF record containing a deep link like `iris://run/{workflowId}`.
- Add an Android intent filter for the deep link.
- When the tag is scanned, route into the app and run the matching workflow.
- Keep a foreground write screen for writing the tag.

This path is more controllable than background NFC automation and more demo-friendly.

### Tasker

Treat Tasker as assisted automation, not as a guaranteed silent profile creator. The safer architecture is to expose IrisApp as a Tasker/Locale plugin:

- `TaskerPluginEditActivity` lets Tasker configure which workflow should run.
- `TaskerPluginFireReceiver` receives Tasker's fire intent and starts the workflow.
- Tasker owns the profile trigger; IrisApp owns workflow execution.

If profile creation/import is attempted, it should be a separate spike because it depends on Tasker's supported import/configuration behavior.

## Permissions

| Capability | Permission or setting | MVP status |
|------------|-----------------------|------------|
| Internet | None required for local model | Not needed unless downloading model. |
| NFC | `android.permission.NFC` | Needed for NFC demo. |
| Exact alarms | `SCHEDULE_EXACT_ALARM` | Defer. |
| Location triggers | Fine/background location | Defer. |
| Accessibility fallback | Accessibility service user approval | Defer. |
| Package visibility | `<queries>` manifest entries | Needed for checking installed demo apps. |

## Build Variants

Use two planner modes:

- `mock`: no native model required; stable for UI and runner demos.
- `llama`: loads LiteRT-LM with GPU acceleration.

This can be a runtime setting first. A dedicated Gradle flavor is useful later if model assets make builds too large.

## Testing Strategy

| Layer | Test type | Goal |
|-------|-----------|------|
| `PromptBuilder` | Unit tests | Prompt includes supported actions and schema. |
| `WorkflowJsonParser` | Unit tests | Accept valid JSON, reject invalid or partial output. |
| `SafeActionRouter` | Unit tests | Reject unknown apps, unsafe URLs, missing params. |
| `WorkflowRepository` | Room instrumentation or Robolectric | Save/load/delete works. |
| `WorkflowRunner` | Fake dispatchers | Steps run in order and errors are captured. |
| UI | Manual device pass | Demo path is smooth. |
| Native | Device smoke test | Model loads on GPU and returns valid JSON. |

## Demo Reliability Rules

- Build mock mode first.
- Keep one known-good prompt in the app.
- Keep one known-good saved workflow seeded or easy to create.
- Test all target app intents on the physical demo phone.
- Record a backup video after the first full successful run.
- Keep the model response visible, but never depend on live model quality for the only demo path.
