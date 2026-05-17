# IrisApp Architecture And Technology Plan

**Last updated:** 2026-05-17

---

## Purpose

IrisApp is an Android app that turns a natural-language request into a runnable cross-app workflow using an on-device SLM (Gemma 4 2B via LiteRT-LM).

```
User prompt -> RetoWorkflowPlanner (3-stage) -> validated JSON workflow -> save -> trigger -> execute
```

The app proves three things:

- A small local model (LiteRT-LM with GPU acceleration) can convert plain English into structured automation.
- The generated plan can be validated before execution.
- Android can execute cross-app actions through intents, URL schemes, and system APIs.

---

## System Architecture

```
+------------------------------------------------------------------+
|                         Android App                              |
|                                                                  |
|   +-------------+    +---------------+    +-------------------+  |
|   | Home Screen |    | Workflow List |    | Workflow Detail   |  |
|   +------+------+    +-------+-------+    +---------+---------+  |
|          |                   |                      |            |
|          +-------------------+----------+-----------+            |
|                                         |                        |
|                         +---------------v---------------+        |
|                         | WorkflowGenerationViewModel   |        |
|                         | StateFlow + UI events         |        |
|                         +---------------+---------------+        |
|                                         |                        |
|        +----------------+---------------+---------------+        |
|        |                |                               |        |
| +------v------+  +------v--------+              +-------v------+  |
| | RetoWorkflow |  | Workflow     |              | Runner        |  |
| | Planner      |  | Repository   |              | Intents/APIs  |  |
| +------+------+  | (JSON files)  |              +-------+------+  |
|        |         +---------------+                      |         |
| +------v------+  +---------------+              +-------v------+  |
| | PromptBuilder|  | Parser/Router |              | Android OS   |  |
| | RequestAnalysis|  | Validator    |              | Apps/Services|  |
| +------+------+  +---------------+              +--------------+  |
|        |                                                         |
| +------v------+                                                  |
| | InferenceManager                                               |
| | LitertLmEngine (LiteRT-LM Kotlin API)                          |
| | Backend.GPU() — OpenCL/Vulkan on device GPU                    |
| | Model: gemma-4-E2B-it.litertlm                                 |
| +-------------+                                                  |
+------------------------------------------------------------------+
```

---

## Core Runtime Flow

1. User enters a request on the Home/Generate screen.
2. `PromptBuilder` creates a compact system prompt containing the JSON schema, supported action IDs, and trigger types.
3. `RetoWorkflowPlanner` calls the 3-stage pipeline using the loaded LiteRT-LM engine:
   - **Stage 1 — Analysis:** `RequestAnalysis` extracts goal, trigger hint, app categories.
   - **Stage 2 — Grounding:** `ActionSpecRegistry` + `PackageManager` ground against available capabilities.
   - **Stage 3 — Action Plan:** Selects action IDs and fills typed parameters → outputs strict JSON.
4. `WorkflowJsonParser` extracts and decodes the returned JSON into typed Kotlin models.
5. `WorkflowValidator` validates every action, parameter, URL, and trigger against `ActionSpecRegistry`. On failure, retries once via the planner.
6. The UI shows a workflow preview (step list + params) and raw JSON.
7. User saves the workflow — stored as JSON via `JsonFileStorage` (file-based).
8. User taps "Run Now" or attaches a trigger (NFC, Time, Share Sheet, Battery, WiFi, etc.).
9. `WorkflowRunner` dispatches steps sequentially — via `IntentFactory` (AndroidIntent), `ChromeCustomTabOpener` (CustomTab), or direct platform executors (Calendar, Alarm, Clipboard, etc.).
10. `ExecutionHistoryRepository` appends `ExecutionLogEntry` to the on-disk history log.

---

## Technology Choices

| Area | Choice | Reason |
|---|---|---|
| Language | Kotlin | Native Android with coroutines, sealed classes, null safety |
| UI | Jetpack Compose + Material 3 | Fast iteration, modern Android patterns |
| State | ViewModel + StateFlow | Simple observable state without overbuilding |
| Architecture | MVVM with MVI-style events | Predictable UI flow and easy testing |
| Dependency injection | Manual providers | Fast iteration while the app is small |
| Persistence | JSON file storage (`JsonFileStorage`) | No Room dependency; workflows stored as JSON in `filesDir/workflows/` |
| Preferences | DataStore | Model path, selected backend, widget config |
| JSON | kotlinx.serialization | Typed decoding for workflow data and planner output |
| Inference | LiteRT-LM Kotlin API (Google) | On-device LLM with first-class GPU acceleration via OpenCL/Vulkan |
| Model format | `.litertlm` | LiteRT-LM native format; pre-converted Gemma models from HuggingFace litert-community |
| Model target | Gemma 4 2B IT `.litertlm` | Fits on device, GPU-accelerated via LiteRT-LM |
| Build | Gradle Kotlin DSL | Pure Kotlin build — LiteRT-LM ships as an AAR from Google Maven |
| Marketplace | Firebase Realtime Database | Anonymous workflow sharing via deep-link |
| Widget | Jetpack Glance | Modern Compose-based widget (minSdk 26) |

---

## Package Structure

```
app/src/main/java/com/irisapp/
├── app/           IrisApp.kt         ← onCreate: registerAll, reschedule
├── data/
│   ├── local/storage/   JsonFileStorage.kt
│   └── repository/       WorkflowRepository, ExecutionHistoryRepository,
│                         MarketplaceRepository, WorkflowShareRepository
├── domain/
│   ├── catalog/     ActionSpecRegistry.kt   ← 64 ActionSpecs
│   ├── model/       WorkflowModels, SharedContent
│   ├── parser/      WorkflowJsonParser.kt
│   ├── planner/     PromptBuilder, RequestAnalysis, RetoWorkflowPlanner
│   ├── runner/      WorkflowRunner, IntentFactory, FallbackParamMapper
│   ├── safety/      WorkflowValidator.kt
│   └── triggers/    TriggerCatalog.kt
├── platform/
│   ├── alarm/      ← AlarmManager scheduling, BootReceiver, TimeTriggerReceiver
│   ├── app/        ← LaunchAppService (FGS), LaunchAppApiExecutor
│   ├── bluetooth/  ← BluetoothApiExecutor
│   ├── calendar/   ← CalendarApiExecutor
│   ├── capability/ ← ChromeCustomTabOpener, ClipboardApiExecutor,
│   │                 IntentDiscoveryEngine, PackageCapabilityScanner
│   ├── cellular/   ← CellularApiExecutor
│   ├── command/    ← CommandApiExecutor
│   ├── display/    ← BrightnessApiExecutor, RotationApiExecutor
│   ├── hotspot/    ← HotspotApiExecutor
│   ├── http/       ← HttpRequestApiExecutor
│   ├── inference/  ← InferenceManager, ModelFileLocator, litert/LitertLmEngine
│   ├── intent/     ← GenericIntentApiExecutor
│   ├── location/   ← GeofenceManager, GeofenceBroadcastReceiver
│   ├── media/      ← MediaControlApiExecutor
│   ├── nfc/        ← DeepLinkRouter, NfcTriggerHandler, NfcSetupScreen
│   ├── notification/ ← NotificationApiExecutor
│   ├── share/      ← ShareSheetTriggerHandler
│   ├── sms/        ← SmsTriggerManager, SmsTriggerReceiver, SmsNotificationListener
│   ├── sound/      ← YamnetClassifier, SoundEventTriggerService
│   ├── sync/       ← SyncApiExecutor
│   ├── tools/      ← Tool, ToolRegistry, ToolInitializer, ToolAliasRegistry
│   │   └── impl/   ← ClipboardTools, DeviceTools, DomainSearchTools,
│   │                 ExecutionTools, NotificationTools, ReasoningTools,
│   │                 ReminderTools, SearchTools, SettingsTools, TemporalTools
│   │   └── reto/   ← RetoOrchestrator, CapabilityBinder, SlotGroundingPlanner,
│   │                 RequirementBuilder, ResolverRegistry, ToolMetadataRegistry
│   ├── trigger/    ← TriggerRegistry, Battery/Charger/WiFi/Bluetooth/AirplaneMode/
│   │                 Dnd/Sleep/App/Voice trigger managers and receivers
│   ├── volume/     ← RingerModeApiExecutor, VolumeApiExecutor
│   └── wifi/        ← WifiApiExecutor
├── ui/
│   ├── MainActivity.kt              ← PermissionDialog, deep-link routing
│   ├── components/                  ← AmbientBackground, BlobPersona, GlassmorphicCard,
│   │                                 GradientButton, LivingInputConsole, SceneChip
│   ├── home/                        ← GenerateScreen, ManualWorkflowEditorScreen,
│   │                                 WorkflowGenerationUiState, WorkflowGenerationViewModel,
│   │                                 ImportWorkflowScreen, Trigger setup screens
│   ├── marketplace/                 ← MarketplaceScreen, MarketplaceViewModel
│   ├── nfc/                         ← NfcSetupScreen (write state machine)
│   ├── theme/                       ← IrisTheme
│   └── trigger/                     ← TimeTriggerConfirmationActivity,
│                                     TimeTriggerNotification, TimeTriggerPicker
└── widget/                          ← WorkflowWidgetGlance, WorkflowWidgetReceiver,
                                        WorkflowWidgetConfigActivity, IrisWidgetStateRepository,
                                        WidgetPreferences, TriggerWorkflowAction, SlmExecutionService
```

---

## Data Contracts

### Planner Output (RetoWorkflowPlanner JSON)

```json
{
  "name": "Focus Session",
  "summary": "Sets phone to silent and opens a focus playlist.",
  "trigger": { "type": "time", "hour": 9, "minute": 0, "days": ["MON","TUE","WED","THU","FRI"] },
  "actions": [
    { "id": "ringer_mode.set", "params": { "mode": "silent" } },
    { "id": "launch_app", "params": { "package_name": "com.spotify.music" } }
  ]
}
```

### Kotlin Model Shapes

```kotlin
@Serializable
data class PlannedWorkflow(
    val name: String,
    val summary: String = "",
    val trigger: TriggerConfig = TriggerConfig.Manual,
    val actions: List<WorkflowStep> = emptyList()
)

@Serializable
sealed class TriggerConfig {
    data object Manual : TriggerConfig()
    data class Time(val hour: Int, val minute: Int, val days: List<String> = emptyList()) : TriggerConfig()
    data class Nfc(val tagId: String? = null) : TriggerConfig()
    data class ShareSheet(val contentTypes: List<String> = emptyList()) : TriggerConfig()
    data class Battery(val threshold: Int = 20, val condition: String = "below") : TriggerConfig()
    // ... 15+ trigger variants
}

@Serializable
data class WorkflowStep(
    val id: String,       // ActionSpec id e.g. "ringer_mode.set"
    val params: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false
)

@Serializable
data class ExecutionResult(
    val stepId: String,
    val success: Boolean,
    val message: String,
    val output: String = ""
)
```

### ExecutionLogEntry (persisted)

```kotlin
@Serializable
data class ExecutionLogEntry(
    val workflowName: String,
    val timestampMillis: Long,
    val results: List<ExecutionResult>,
    val allSuccess: Boolean
)
```

---

## Action Execution System

### Execution Paths

| ExecutionSpec variant | Description |
|---|---|
| `AndroidIntent` | Generic `Intent` built via `IntentFactory` — `startActivity()` dispatch |
| `CustomTab` | Chrome Custom Tabs via `ChromeCustomTabOpener` — in-app browser |
| `BuiltIn` | Direct platform API — `ClipboardApiExecutor`, `CalendarApiExecutor`, `AlarmApiExecutor`, etc. |

### IntentFactory Dispatch

`IntentFactory` converts validated `WorkflowStep` params into an `Intent` or direct API call:

```kotlin
fun buildExecutableIntent(step: WorkflowStep, params: Map<String, String>): Intent?
fun resolveActivity(intent: Intent): ResolveInfo?

// Per-action dispatch in WorkflowRunner:
is "calendar.create_event" -> CalendarApiExecutor.execute(context, params)
is "clipboard.copy_text"   -> ClipboardApiExecutor.execute(context, params)
is "browser.open_url"      -> ChromeCustomTabOpener.open(context, url)
else                       -> IntentFactory.buildExecutableIntent(step, params)?.let { startActivity(it) }
```

---

## Inference Layer

LiteRT-LM uses a pure Kotlin API — no CMake, no JNI, no NDK. `InferenceManager` wraps `LitertLmEngine` as a singleton.

Engine initialization (background thread):

```kotlin
val engine = LitertLmEngine()
engine.initialize(
    modelPath = "/path/to/gemma-4-E2B-it.litertlm",
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

Rules:
- Always close the engine (`engine.close()`) in `ViewModel.onCleared()` or `IrisApp.onTerminate()`.
- Cache directory (`cacheDir`) speeds up subsequent model loads.
- GPU requires `<uses-native-library>` entries for OpenCL and Vulkan in AndroidManifest.xml.
- Do not reload the model for every prompt — reuse the `InferenceManager` singleton.
- Log timing to Logcat for demo tuning.

---

## Trigger Architecture

All triggers follow the same registration/execution pattern:

```
App.onCreate()
  → TriggerRegistry.init(this)
  → BatteryTriggerManager.registerAll(this)    // restore active triggers
  → TimeTriggerScheduler.rescheduleAll(this)  // restore scheduled alarms

WorkflowGenerationViewModel.saveWorkflow()
  → is TriggerConfig.Nfc    -> NfcTriggerHandler.registerWorkflow(ctx, name, trigger)
  → is TriggerConfig.Time   -> TimeTriggerScheduler.schedule(ctx, name, trigger)
  → is TriggerConfig.Battery -> BatteryTriggerManager.registerWorkflow(ctx, name, trigger)
  → ...

Trigger fires
  → BroadcastReceiver / Service / Activity
  → TriggerRegistry.fire(context, workflowName)
  → WorkflowRunner.run(workflow, startIndex=0)
  → confirmation? → notification → TriggerRegistry.confirmAndResume()
  → ExecutionResult per step
  → ExecutionHistoryRepository.append()
```

---

## Permissions

| Permission | Purpose | Grant type |
|---|---|---|
| `POST_NOTIFICATIONS` | Workflow/trigger notifications | Runtime (Android 13+) |
| `RECORD_AUDIO` | Voice trigger, YAMNet sound classifier | Runtime |
| `ACCESS_FINE_LOCATION` | WiFi SSID, geofence | Runtime |
| `ACCESS_BACKGROUND_LOCATION` | Geofence arrive/leave | Runtime (Android 10+) |
| `BLUETOOTH_CONNECT` | Bluetooth trigger/action | Runtime (Android 12+) |
| `READ_CONTACTS` | SMS trigger | Runtime |
| `READ_CALENDAR` / `WRITE_CALENDAR` | `calendar.create_event` | Runtime |
| `SCHEDULE_EXACT_ALARM` | `AlarmManager.setExactAndAllowWhileIdle()` | User-granted per app |
| `FOREGROUND_SERVICE` | Sound Event classifier | Manifest |
| `FOREGROUND_SERVICE_SPECIAL_USE` (subtype: `appLaunch`) | `LaunchAppService` FGS | Manifest (Android 14+) |
| `INTERNET` | HTTP requests, Firebase, model downloads | Manifest |
| `RECEIVE_BOOT_COMPLETED` | Reschedule time triggers after reboot | Manifest |
| `QUERY_ALL_PACKAGES` | List apps for `launch_app` | Manifest |
| `MODIFY_AUDIO_SETTINGS` | Ringer mode | Manifest |
| `WRITE_SETTINGS` | Brightness, rotation, hotspot, airplane mode | Manual (Settings only) |
| `READ_MEDIA_AUDIO` / `IMAGES` / `VIDEO` | Media file access | Runtime (Android 13+) |

---

## Testing Strategy

| Layer | Test type | Goal |
|---|---|---|
| `WorkflowJsonParser` | Unit tests | Accept valid JSON, reject invalid or partial output |
| `WorkflowValidator` | Unit tests | Reject unknown actions, unsafe URLs, missing params |
| `WorkflowRunner` | Fake dispatchers | Steps run in order, errors captured, confirmation gate fires |
| `InferenceManager` | Device smoke test | Model loads on GPU, returns valid JSON |
| UI | Manual device pass | Demo path is smooth |
| Trigger system | Manual test per trigger | Each trigger fires correctly and restores on reboot |