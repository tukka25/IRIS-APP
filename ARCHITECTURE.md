# GemmaWorkflow Architecture And Technology Plan

## Purpose

GemmaWorkflow is a hackathon Android app that turns a natural-language request into a runnable cross-app workflow. The strongest demo path is:

```text
User prompt -> on-device planner -> validated JSON workflow -> save -> run now or attach trigger
```

The app should prove three things:

- A small local model can convert plain English into structured automation.
- The generated plan can be validated before execution.
- Android can execute useful cross-app actions through intents, URL schemes, and triggers.

## Hackathon Scope

### MVP

- Android app built with Kotlin and Jetpack Compose.
- Prompt input screen with a mock planner and a llama.cpp planner option.
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
| | JNI bridge  |                                                  |
| | llama.cpp   |                                                  |
| | GGUF model  |                                                  |
| +-------------+                                                  |
+------------------------------------------------------------------+
```

## Core Runtime Flow

1. User enters a request on the Home screen.
2. `PromptBuilder` creates a compact system prompt containing the JSON schema, supported actions, and trigger types.
3. `PlannerService` calls either `MockPlannerEngine` or `LlamaCppEngine`.
4. `WorkflowJsonParser` extracts and decodes the returned JSON into typed Kotlin models.
5. `SafeActionRouter` validates every app, action, parameter, URL, and package name against an allowlist.
6. The UI shows the workflow preview and raw JSON.
7. User saves the workflow to Room.
8. User taps "Run Now" or creates a trigger.
9. `WorkflowRunner` dispatches steps sequentially and writes execution results to history.

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
| Inference | llama.cpp through JNI/NDK | Runs GGUF models locally on Android. |
| Model format | GGUF | Supported by llama.cpp and suitable for quantized edge inference. |
| Model target | Gemma 3 1B instruction GGUF, Q4_K_M or faster fallback | Small enough for phone demo, stronger than a pure rule mock. |
| Build | Gradle Kotlin DSL + CMake | Standard Android + native build path. |
| Primary actions | Android intents and URL schemes | Best chance of working across installed apps. |
| Triggers | Manual run, NFC, optional Tasker plugin | Keeps demo focused and avoids heavy permissions early. |

## Package Structure

```text
app/src/main/java/com/gemmaworkflow/
+-- GemmaWorkflowApp.kt
+-- di/
|   +-- AppContainer.kt
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
|   +-- components/
|   |   +-- PromptInput.kt
|   |   +-- WorkflowCard.kt
|   |   +-- ActionRow.kt
|   |   +-- JsonPreview.kt
|   +-- theme/
+-- data/
|   +-- local/
|   |   +-- AppDatabase.kt
|   |   +-- WorkflowDao.kt
|   |   +-- ExecutionHistoryDao.kt
|   +-- model/
|   |   +-- PlannedWorkflow.kt
|   |   +-- WorkflowEntity.kt
|   |   +-- WorkflowStep.kt
|   |   +-- TriggerConfig.kt
|   |   +-- ExecutionResult.kt
|   +-- repository/
|       +-- WorkflowRepository.kt
+-- domain/
|   +-- planner/
|   |   +-- PlannerService.kt
|   |   +-- PlannerEngine.kt
|   |   +-- MockPlannerEngine.kt
|   |   +-- LlamaCppEngine.kt
|   |   +-- PromptBuilder.kt
|   |   +-- ModelAssetManager.kt
|   +-- parser/
|   |   +-- WorkflowJsonParser.kt
|   +-- safety/
|   |   +-- ActionCatalog.kt
|   |   +-- SafeActionRouter.kt
|   +-- runner/
|       +-- WorkflowRunner.kt
|       +-- IntentDispatcher.kt
|       +-- UrlDispatcher.kt
|       +-- RunnerResultMapper.kt
+-- automation/
|   +-- nfc/
|   |   +-- NfcTriggerWriter.kt
|   |   +-- NfcWorkflowReceiver.kt
|   +-- tasker/
|       +-- TaskerPluginEditActivity.kt
|       +-- TaskerPluginFireReceiver.kt
+-- native/
    +-- NativeLog.kt

app/src/main/cpp/
+-- CMakeLists.txt
+-- llama_android.cpp
+-- llama_android.h

app/src/main/assets/
+-- planner-json.gbnf
+-- models/
    +-- gemma-planner.Q4_K_M.gguf
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

Use a small Android-real catalog first.

| App | Action | Dispatch path | Notes |
|-----|--------|---------------|-------|
| Spotify | `play_search(query)` | Intent or URI fallback | Requires Spotify installed; test on device. |
| Obsidian | `create_note(title, content)` | Android share intent or app URL if verified | Keep as demo target only after testing. |
| Maps | `open_place(query)` | `geo:` or Google Maps URL | Reliable Android fallback action. |
| Browser | `open_url(url)` | `ACTION_VIEW` | Useful universal fallback. |
| ShareSheet | `share_text(text)` | `ACTION_SEND` | Reliable for creating visible cross-app behavior. |

Avoid iOS-only examples in the Android prompt catalog. If the demo needs note creation and Obsidian is not reliable, use `ACTION_SEND` to send text into any installed notes app through the Android share sheet.

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

### llama.cpp Planner

Use the llama.cpp path after the mock flow works end to end:

- Copy the GGUF and grammar from assets to app-private files on first run.
- Load the model once and reuse the native handle.
- Generate on `Dispatchers.Default`.
- Keep prompt and output small.
- Start with context size 512 or 1024.
- Cap generation around 128 tokens for the demo.
- Use grammar-constrained JSON generation.
- Show model load and generation errors in the UI.

## Native Layer

JNI contract:

```cpp
extern "C" JNIEXPORT jlong JNICALL
Java_com_gemmaworkflow_domain_planner_LlamaCppEngine_nativeInit(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath,
    jstring grammarPath,
    jint contextSize,
    jint maxTokens,
    jint gpuLayers
);

extern "C" JNIEXPORT jstring JNICALL
Java_com_gemmaworkflow_domain_planner_LlamaCppEngine_nativeGenerate(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jstring prompt
);

extern "C" JNIEXPORT void JNICALL
Java_com_gemmaworkflow_domain_planner_LlamaCppEngine_nativeFree(
    JNIEnv* env,
    jobject thiz,
    jlong handle
);
```

Native rules:

- Return clear error strings or throw Java exceptions for model load failures.
- Free native resources from ViewModel/application shutdown paths.
- Do not reload the model for every prompt.
- Log token timing to Logcat for demo tuning.

## Trigger Architecture

### Manual Run

Manual run is the first trigger and must always work.

```text
Workflow Detail -> Run Now -> WorkflowRunner -> dispatch steps -> history
```

### NFC

NFC is the best physical demo trigger:

- Write an NDEF record containing a deep link like `gemmaworkflow://run/{workflowId}`.
- Add an Android intent filter for the deep link.
- When the tag is scanned, route into the app and run the matching workflow.
- Keep a foreground write screen for writing the tag.

This path is more controllable than background NFC automation and more demo-friendly.

### Tasker

Treat Tasker as assisted automation, not as a guaranteed silent profile creator. The safer architecture is to expose GemmaWorkflow as a Tasker/Locale plugin:

- `TaskerPluginEditActivity` lets Tasker configure which workflow should run.
- `TaskerPluginFireReceiver` receives Tasker's fire intent and starts the workflow.
- Tasker owns the profile trigger; GemmaWorkflow owns workflow execution.

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
- `llama`: loads llama.cpp and GGUF assets.

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
| Native | Device smoke test | Model loads and returns valid JSON. |

## Demo Reliability Rules

- Build mock mode first.
- Keep one known-good prompt in the app.
- Keep one known-good saved workflow seeded or easy to create.
- Test all target app intents on the physical demo phone.
- Record a backup video after the first full successful run.
- Keep the model response visible, but never depend on live model quality for the only demo path.
