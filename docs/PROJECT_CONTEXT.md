# IrisApp Project Context

Last updated: 2026-05-16

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
- Jetpack Compose (UI + Glance widgets)
- LiteRT-LM for local on-device SLM inference
- Gemma `.litertlm` model stored on-device
- Local JSON file persistence
- Android intents, ContentResolver APIs, AlarmManager, broadcast receivers, and geofencing for execution

The project is no longer using `llama.cpp`. The current inference path is LiteRT-LM.

## Package Name

```text
com.irisapp
```

All source paths below are relative to `app/src/main/java/com/irisapp/`.

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
- voice command
- sound/audio event
- app opened/closed
- SMS/notification received
- alarm stopped/snoozed
- sleep detection

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
app/src/main/java/com/irisapp/
├── app/
│   └── IrisApp.kt
├── data/
│   ├── local/storage/
│   │   └── JsonFileStorage.kt
│   ├── repository/
│   │   ├── ExecutionHistoryRepository.kt
│   │   ├── MarketplaceRepository.kt
│   │   └── WorkflowRepository.kt
│   └── seed/
│       └── DemoWorkflowSeeder.kt
├── domain/
│   ├── catalog/
│   │   └── ActionSpecRegistry.kt
│   ├── model/
│   │   ├── SharedContent.kt
│   │   └── WorkflowModels.kt
│   ├── parser/
│   │   └── WorkflowJsonParser.kt
│   ├── planner/
│   │   ├── PromptBuilder.kt
│   │   ├── RequestAnalysis.kt
│   │   └── RetoWorkflowPlanner.kt
│   ├── runner/
│   │   ├── FallbackParamMapper.kt
│   │   ├── IntentFactory.kt
│   │   └── WorkflowRunner.kt
│   ├── safety/
│   │   └── WorkflowValidator.kt
│   └── triggers/
│       └── TriggerCatalog.kt
├── platform/
│   ├── airplane/
│   │   └── AirplaneModeApiExecutor.kt
│   ├── alarm/
│   │   ├── AlarmApiExecutor.kt
│   │   ├── AlarmDismissReceiver.kt
│   │   ├── AlarmFireReceiver.kt
│   │   ├── AlarmReceiver.kt
│   │   ├── AlarmSnoozeReceiver.kt
│   │   ├── AlarmTriggerManager.kt
│   │   ├── BootReceiver.kt
│   │   └── TimeTriggerScheduler.kt
│   ├── app/
│   │   └── LaunchAppApiExecutor.kt
│   ├── bluetooth/
│   │   └── BluetoothApiExecutor.kt
│   ├── calendar/
│   │   └── CalendarApiExecutor.kt
│   ├── capability/
│   │   ├── ChromeCustomTabOpener.kt
│   │   ├── IntentDiscoveryEngine.kt
│   │   └── PackageCapabilityScanner.kt
│   ├── cellular/
│   │   └── CellularApiExecutor.kt
│   ├── clipboard/
│   │   └── ClipboardApiExecutor.kt
│   ├── command/
│   │   └── CommandApiExecutor.kt
│   ├── display/
│   │   ├── BrightnessApiExecutor.kt
│   │   └── RotationApiExecutor.kt
│   ├── hotspot/
│   │   └── HotspotApiExecutor.kt
│   ├── http/
│   │   └── HttpRequestApiExecutor.kt
│   ├── inference/
│   │   ├── InferenceManager.kt
│   │   └── litert/
│   │       ├── LitertLmEngine.kt
│   │       └── ModelFileLocator.kt
│   ├── intent/
│   │   └── GenericIntentApiExecutor.kt
│   ├── location/
│   │   ├── GeofenceBroadcastReceiver.kt
│   │   └── GeofenceManager.kt
│   ├── media/
│   │   └── MediaControlApiExecutor.kt
│   ├── nfc/
│   │   ├── DeepLinkRouter.kt
│   │   ├── NfcTriggerHandler.kt
│   │   └── NfcTriggerWriter.kt
│   ├── notification/
│   │   └── NotificationApiExecutor.kt
│   ├── share/
│   │   └── ShareSheetTriggerHandler.kt
│   ├── sms/
│   │   ├── SmsNotificationListener.kt
│   │   ├── SmsTriggerManager.kt
│   │   └── SmsTriggerReceiver.kt
│   ├── sound/
│   │   └── YamnetClassifier.kt
│   ├── sync/
│   │   └── SyncApiExecutor.kt
│   ├── tools/
│   │   ├── FindSkill.kt
│   │   ├── Tool.kt
│   │   ├── ToolAliasRegistry.kt
│   │   ├── ToolInitializer.kt
│   │   ├── ToolRegistry.kt
│   │   ├── impl/
│   │   │   ├── ClipboardTools.kt
│   │   │   ├── DeviceTools.kt
│   │   │   ├── DomainSearchTools.kt
│   │   │   ├── ExecutionTools.kt
│   │   │   ├── NotificationTools.kt
│   │   │   ├── ReasoningTools.kt
│   │   │   ├── ReminderTools.kt
│   │   │   ├── SearchTools.kt
│   │   │   ├── SettingsTools.kt
│   │   │   └── TemporalTools.kt
│   │   └── reto/
│   │       ├── CapabilityBinder.kt
│   │       ├── CoverageValidator.kt
│   │       ├── FactRequirement.kt
│   │       ├── RequirementBuilder.kt
│   │       ├── ResolverRegistry.kt
│   │       ├── RetoModels.kt
│   │       ├── RetoOrchestrator.kt
│   │       ├── SlotGroundingPlanner.kt
│   │       ├── TaskDecomposer.kt
│   │       ├── ToolFactParserRegistry.kt
│   │       ├── ToolMetadata.kt
│   │       ├── ToolMetadataRegistry.kt
│   │       └── ToolMode.kt
│   ├── trigger/
│   │   ├── AirplaneModeTriggerManager.kt
│   │   ├── AirplaneModeTriggerReceiver.kt
│   │   ├── AppMonitorAccessibilityService.kt
│   │   ├── BatteryTriggerManager.kt
│   │   ├── BatteryTriggerReceiver.kt
│   │   ├── BluetoothTriggerManager.kt
│   │   ├── BluetoothTriggerReceiver.kt
│   │   ├── ChargerTriggerManager.kt
│   │   ├── ChargerTriggerReceiver.kt
│   │   ├── DndTriggerManager.kt
│   │   ├── DndTriggerReceiver.kt
│   │   ├── SleepTriggerManager.kt
│   │   ├── TriggerRegistry.kt
│   │   ├── WiFiTriggerManager.kt
│   │   ├── sound/
│   │   │   ├── SoundEventTriggerRegistry.kt
│   │   │   └── SoundEventTriggerService.kt
│   │   └── voice/
│   │       ├── VoiceIntentTrigger.kt
│   │       ├── VoiceRecognitionContract.kt
│   │       ├── VoiceTriggerFab.kt
│   │       └── VoiceTriggerHandler.kt
│   ├── ui/
│   │   └── ToastApiExecutor.kt
│   ├── volume/
│   │   ├── RingerModeApiExecutor.kt
│   │   └── VolumeApiExecutor.kt
│   └── wifi/
│       └── WifiApiExecutor.kt
├── ui/
│   ├── MainActivity.kt
│   ├── components/
│   │   ├── AmbientBackground.kt
│   │   ├── BlobPersona.kt
│   │   ├── GlassmorphicCard.kt
│   │   ├── GradientButton.kt
│   │   ├── HexHeroIcon.kt          ← kept but no longer rendered
│   │   ├── LivingInputConsole.kt
│   │   └── SceneChip.kt
│   ├── home/
│   │   ├── GenerateScreen.kt
│   │   ├── ManualWorkflowEditorScreen.kt
│   │   ├── NfcTriggerSetupScreen.kt
│   │   ├── OsmMapPicker.kt
│   │   ├── ShareSheetSetupScreen.kt
│   │   ├── SoundEventTriggerSetupScreen.kt
│   │   ├── TimeTriggerSetupScreen.kt
│   │   ├── WorkflowGenerationUiState.kt
│   │   └── WorkflowGenerationViewModel.kt
│   ├── marketplace/
│   │   ├── MarketplaceScreen.kt
│   │   ├── MarketplaceUiState.kt
│   │   └── MarketplaceViewModel.kt
│   ├── nfc/
│   │   └── NfcSetupScreen.kt
│   ├── theme/
│   │   └── IrisTheme.kt
│   └── trigger/
│       ├── TimeTriggerConfirmationActivity.kt
│       ├── TimeTriggerNotification.kt
│       └── TimeTriggerPicker.kt
└── widget/
    ├── IrisWidgetState.kt
    ├── IrisWidgetStateRepository.kt
    ├── SlmExecutionService.kt
    ├── TriggerWorkflowAction.kt
    ├── WidgetPreferences.kt
    ├── WidgetStateDefinition.kt
    ├── WorkflowWidgetConfigActivity.kt
    ├── WorkflowWidgetGlance.kt
    └── WorkflowWidgetReceiver.kt
```

## Inference

Inference is managed by:

```text
platform/inference/InferenceManager.kt
platform/inference/litert/LitertLmEngine.kt
platform/inference/litert/ModelFileLocator.kt
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
domain/planner/RetoWorkflowPlanner.kt
```

It calls:

```text
platform/tools/reto/RetoOrchestrator.kt
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

## Workflow Model

Core model file:

```text
domain/model/WorkflowModels.kt
```

Important classes:

```kotlin
data class PlannedWorkflow(
    val name: String,
    val summary: String,
    val trigger: TriggerConfig,
    val actions: List<WorkflowStep>,
    val scene: String?,
    val missingSetup: List<String>,
    val rawModelOutput: String
)

data class WorkflowStep(
    val id: String,           // e.g. "browser.open_url"
    val params: JsonObject,
    val requiresConfirmation: Boolean = false
)

data class ExecutionResult(
    val stepId: String,
    val success: Boolean,
    val message: String = "",
    val output: String = "",  // used for $step[N].output chaining
    val timestampMillis: Long
)

data class ExecutionLogEntry(
    val workflowName: String,
    val timestampMillis: Long,
    val results: List<ExecutionResult>,
    val allSuccess: Boolean
)
```

Enums: `GeofenceTransition`, `BatteryCondition`, `ChargerType`, `SetupState`, `WorkflowStatus`

## TriggerConfig Sealed Hierarchy

`TriggerConfig` is a sealed class with 20 variants:

| Variant | Status |
|---|---|
| Manual | supported |
| Time | supported |
| Nfc | supported |
| ShareSheet | supported |
| Battery | runtime manager exists |
| Charger | runtime manager exists |
| WiFi | runtime manager exists |
| Bluetooth | runtime manager exists |
| AirplaneMode | runtime manager exists |
| DoNotDisturb | runtime manager exists |
| Geofence | runtime manager exists; requires location setup |
| AlarmStopped | planned |
| AppOpened | accessibility service monitors this |
| AppClosed | accessibility service monitors this |
| SmsReceived | SmsTriggerManager exists |
| NotificationListenerConfig | SmsNotificationListener exists |
| EmailReceived | planned |
| SleepProxy | SleepTriggerManager exists |
| Voice | VoiceTriggerHandler exists |
| SoundEvent | SoundEventTriggerService (YAMNet) exists |

## ActionSpec Registry

The canonical action registry is at:

```text
domain/catalog/ActionSpecRegistry.kt
```

There are currently **64 registered actions**. Each `ActionSpec` owns:

- action ID
- user-facing label
- description
- typed params (with ParamType, required flag, fact resolver)
- execution model (AndroidIntent / PackageLaunch / InternalTool / CustomTab / BuiltIn)
- availability rule
- trigger compatibility set
- confirmation requirement
- logical action mapping
- tool bindings
- examples (JSON)
- fallback action IDs

The model sees only prompt-safe summaries. Kotlin owns the Android details.

## Current Action Surface

Partial list of registered action IDs:

```text
browser.open_url
browser.search
maps.open_place
maps.navigate
share.share_text
share.share_image
sms.compose
whatsapp.send_text
phone.dial
alarm.set_alarm
alarm.set_timer
clipboard.copy_text
calendar.create_event
internal.reminder.create
app.open
file.open
note.create
media.play_from_search
media.play_pause
media.next_track
media.previous_track
volume.set
ringer_mode.set
toast.show
notification.send
brightness.set
http_request
launch_app
bluetooth.toggle
wifi.toggle
rotation.lock
intent.send
hotspot.toggle
cellular.toggle
sync.toggle
display.set_brightness
command.run
```

Exact availability depends on installed apps, Android package visibility, `PackageManager` resolution, and `ActionSpec` availability policy.

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
platform/tools/Tool.kt
platform/tools/ToolRegistry.kt
platform/tools/ToolInitializer.kt
platform/tools/FindSkill.kt
platform/tools/reto/ToolMetadataRegistry.kt
```

Tool categories and key tools:

```text
Temporal:
  get_current_time, resolve_datetime, compute_duration

Contacts:
  get_contact

Device/System:
  list_installed_apps, resolve_intent, get_device_settings

Domain Search:
  search_places, search_media, search_files, search_notes, get_calendar_events

Notifications:
  list_notifications, read_notification

Reminders:
  create_reminder, list_reminders

Clipboard:
  read_clipboard

Reasoning/Validation:
  validate_json, check_availability

Settings:
  get_battery_level, get_wifi_state
```

Tools are scoped through `ActionSpec` metadata so the model receives only tools relevant to the selected action.

## Datetime Handling

Datetime resolution is handled by:

```text
platform/tools/impl/TemporalTools.kt
```

Important behavior:

- Relative expressions should include `reference_time_iso`.
- Timezone should be passed explicitly.
- Ambiguous times like `next Friday at 6 o'clock` should include `default_period`.
- Meetings/invitations/social plans should usually set `default_period = "pm"` unless the user says morning.

Example tool call:

```json
{
  "tool": "resolve_datetime",
  "expression": "next Friday at 6 o'clock",
  "reference_time_iso": "2026-05-16T12:00:00+04:00",
  "timezone": "Asia/Dubai",
  "default_period": "pm"
}
```

## JSON Safety

The parser is:

```text
domain/parser/WorkflowJsonParser.kt
```

The validator is:

```text
domain/safety/WorkflowValidator.kt
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

## Execution Pipeline

Main runner:

```text
domain/runner/WorkflowRunner.kt
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
- `BluetoothApiExecutor`, `WifiApiExecutor`, `AirplaneModeApiExecutor`
- `BrightnessApiExecutor`, `RotationApiExecutor`
- `VolumeApiExecutor`, `RingerModeApiExecutor`
- `HttpRequestApiExecutor`
- `MediaControlApiExecutor`
- `NotificationApiExecutor`
- `ToastApiExecutor`
- `HotspotApiExecutor`
- `CellularApiExecutor`
- `SyncApiExecutor`
- `CommandApiExecutor`
- `GenericIntentApiExecutor`
- `LaunchAppApiExecutor`

## Confirmation Flow

Some actions require user confirmation before execution.

If a workflow is running in the foreground, the UI shows a confirmation dialog.

If a workflow is fired by a background trigger, `TriggerRegistry` catches `ConfirmationRequired`, stores pending execution, and posts a notification.

The user can confirm and resume, or dismiss and stop.

## Persistence

Persistence is local file-based JSON storage.

```text
data/local/storage/JsonFileStorage.kt
data/repository/WorkflowRepository.kt       — workflows stored in app/files/workflows/
data/repository/ExecutionHistoryRepository.kt — last 100 entries in app/files/history/
data/repository/MarketplaceRepository.kt    — Firebase Realtime DB (anonymous)
```

### WorkflowRepository API

```kotlin
fun listNames(): List<String>
fun loadAll(): List<PlannedWorkflow>
fun get(name: String): PlannedWorkflow?
fun save(workflow: PlannedWorkflow)
fun delete(name: String): Boolean
fun exists(name: String): Boolean
```

### ExecutionHistoryRepository API

```kotlin
fun log(workflowName: String, results: List<ExecutionResult>)
fun getAll(): List<ExecutionLogEntry>
fun recent(limit: Int = 20): List<ExecutionLogEntry>
fun forWorkflow(name: String): List<ExecutionLogEntry>
fun clear()
```

## Trigger System

Runtime trigger managers live in:

```text
platform/trigger/
platform/location/
platform/alarm/
platform/nfc/
platform/share/
platform/sms/
```

| Trigger | Runtime component |
|---|---|
| Manual | UI-only |
| Time | `TimeTriggerScheduler` + `AlarmManager` |
| NFC | `NfcTriggerHandler` deep-link |
| Share Sheet | `ShareSheetTriggerHandler` via ACTION_SEND |
| Battery | `BatteryTriggerManager/Receiver` |
| Charger | `ChargerTriggerManager/Receiver` |
| Wi-Fi | `WiFiTriggerManager` |
| Bluetooth | `BluetoothTriggerManager/Receiver` |
| Airplane Mode | `AirplaneModeTriggerManager/Receiver` |
| Do Not Disturb | `DndTriggerManager/Receiver` |
| Geofence | `GeofenceManager` + `GeofenceBroadcastReceiver` |
| App Opened/Closed | `AppMonitorAccessibilityService` |
| SMS Received | `SmsTriggerManager` + `SmsTriggerReceiver` |
| Notification | `SmsNotificationListener` |
| Sleep | `SleepTriggerManager` |
| Voice | `VoiceTriggerHandler` + `VoiceIntentTrigger` |
| Sound/Audio | `SoundEventTriggerService` (YAMNet on-device classifier) |

## Known Gap: Parser vs New Triggers

`TriggerConfig` and the runtime managers support the full trigger set, but `WorkflowJsonParser` currently only parses:

- `time`
- `nfc`
- `share_sheet`
- `tasker_setup_required`

Everything else falls back to `Manual`.

`PromptBuilder.buildWorkflowJsonPrompt()` still lists the older, smaller trigger set.

Many `ActionSpec.triggerCompatible` sets need updating for the newer trigger IDs.

**Next integration task:**

```text
Teach PromptBuilder + WorkflowJsonParser + ActionSpec triggerCompatible about:
battery, charger, wifi, bluetooth, airplane_mode, dnd, geofence,
app_opened, app_closed, sms_received, voice, sound_event, sleep
```

## UI

Main entry:

```text
ui/MainActivity.kt
```

Main ViewModel:

```text
ui/home/WorkflowGenerationViewModel.kt
```

### Navigation Structure

**Bottom nav — 3 tabs:**
1. **Generate** (`GenerateTabContent`) — prompt input, RETO pipeline, workflow preview
2. **Workflows** — saved workflow list, run summary, detail view
3. **Manual Editor** (`ManualWorkflowEditorScreen`)

**Overlay screens (shown over tabs):**
- `TimeTriggerSetupScreen`
- `ShareSheetSetupScreen`
- `SoundEventTriggerSetupScreen`
- `NfcSetupScreen`
- `ConfirmationDialog` (step confirmation gate)
- `PermissionDialog` (runtime permission request)

**Marketplace tab** — `MarketplaceScreen` / `MarketplaceViewModel` / `MarketplaceRepository`

### UI Theme

```text
ui/theme/IrisTheme.kt
```

Key color constants:

```kotlin
BackgroundDark   = Color(0xFF06080D)
CyanAccent       = Color(0xFF5EF2FF)
VioletAccent     = Color(0xFFB57BFF)
GlassSurface     = Color(0xFF12141C)
GlassBorder      = Color(0xFF2A2D3E)
GreenSuccess     = Color(0xFF7CF0A8)
AmberWarning     = Color(0xFFFFC15E)
TextPrimary      = Color(0xFFFFFFFF)
TextSecondary    = Color(0xFF999999)
ElectricCyan     = Color(0xFF00F2FE)
DeepPurple       = Color(0xFF6F00FF)
LiquidViolet     = Color(0xFFB200FF)
ObsidianDark     = Color(0xFF050509)
```

### Shared UI Components

```text
ui/components/AmbientBackground.kt    — animated radial gradient orb background
ui/components/BlobPersona.kt          — animated 3D blob character (IDLE/LISTENING/PROCESSING/DONE states)
ui/components/GlassmorphicCard.kt     — glassmorphic card + GlowingGlassmorphicCard variant
ui/components/GradientButton.kt       — transparent pill with animated hue-morphing border
ui/components/LivingInputConsole.kt   — animated input field with spinning glow when generating
ui/components/SceneChip.kt            — filter chip with shimmer when active
```

### GenerateScreen Layout

The main Generate tab renders as layers:

1. `AmbientBackground` — full-screen animated dark background with cyan/violet orbs
2. Floating pill navigation dock at bottom (spring-animated indicator)
3. Wordmark "IRIS" header
4. `BlobPersona` — animated blob character that reflects inference state
5. Stage pipeline (RETO phases progress)
6. `LivingInputConsole` — natural-language prompt field
7. `GradientButton` — Generate / Save / Run Now buttons
8. `WorkflowResultPanel` — dismissible preview panel with ✕ button
9. Suggestion chips row — quick-launch saved workflows (flat horizontal row)

### WorkflowGenerationViewModel State

Key state fields:

```kotlin
val prompt: String
val isBusy: Boolean
val isModelReady: Boolean
val workflowPreview: PlannedWorkflow?
val rawJson: String?
val validationErrors: List<String>
val runResults: List<ExecutionResult>
val savedWorkflows: List<PlannedWorkflow>
val pendingConfirmation: ConfirmationRequest?
val pendingPermission: PermissionRequest?
val selectedTab: Int                      // 0=Generate, 1=Workflows, 2=ManualEditor
val stageTimeline: List<StageProgress>
val debugMessages: List<DebugMessage>
val inferenceState: InferenceState
val timeTriggerSetupWorkflow: PlannedWorkflow?
val shareSheetSetupWorkflow: PlannedWorkflow?
val soundEventTriggerSetupWorkflow: PlannedWorkflow?
val editingWorkflow: PlannedWorkflow?
```

## Widget System

The home-screen widget is a Glance AppWidget Mini Dashboard backed by DataStore.

### Files

```text
widget/IrisWidgetState.kt           — state model (DataStore-serialized)
widget/WidgetStateDefinition.kt     — GlanceStateDefinition, per-instance DataStore
widget/WorkflowWidgetGlance.kt      — Glance UI composables
widget/WorkflowWidgetReceiver.kt    — GlanceAppWidgetReceiver
widget/WorkflowWidgetConfigActivity.kt — widget configuration screen
widget/WidgetPreferences.kt         — legacy per-widget prefs
widget/IrisWidgetStateRepository.kt — push state to all widget instances
widget/TriggerWorkflowAction.kt     — Glance ActionCallback for pill taps
widget/SlmExecutionService.kt       — foreground service for on-device inference
```

### Widget State Model

```kotlin
@Serializable
data class StepLog(
    val stepName: String,
    val output: String,
    val success: Boolean,
    val durationMs: Long = 0L,
    val timestampMs: Long = 0L
)

@Serializable
data class IrisWidgetState(
    val activeWorkflowName: String? = null,
    val slmState: SlmProcessState = SlmProcessState.IDLE,
    val recentResults: List<Boolean> = listOf(true, true, false, true, true),
    val suggestions: List<String> = listOf(...),
    val stepLogs: List<StepLog> = emptyList(),   // real-time per-step log, capped at 8
    val currentStep: String? = null              // step currently executing
)

@Serializable
enum class SlmProcessState(val displayString: String, val colorHex: Long) {
    IDLE      ("Ready",                  0xFF888888L),
    ANALYZING ("Interpreting intent...", 0xFF00F2FEL),
    TOOL_CALL ("Executing actions...",   0xFFB200FFL),
    SUCCESS   ("Completed",             0xFF7CF0A8L),
    ERROR     ("Failed",                0xFFFF6B6BL)
}
```

### Widget Layout (by state)

| `SlmProcessState` | Widget shows |
|---|---|
| IDLE | Header · Monitor Bar · Suggestion Carousel · History Strip |
| ANALYZING / TOOL_CALL | Header · Monitor Bar · Current Step indicator · Live step log |
| SUCCESS / ERROR | Header · Monitor Bar · Completed step log · History Strip |

### Widget Execution Flow

1. User taps a suggestion pill → `TriggerWorkflowAction.onAction()`
2. Optimistic state update → `ANALYZING`, `stepLogs = []`, `currentStep = null`
3. Widget re-renders immediately
4. `SlmExecutionService` starts as foreground service (mandatory API 26+)
5. Service loads workflow, transitions to `TOOL_CALL`
6. For each `WorkflowStep`:
   - `setCurrentStep(stepName)` → widget shows "⋯ stepName" live
   - Executes step (real runner TODO; currently simulated with `delay`)
   - `logStep(StepLog(...))` → widget appends row with `✓/✗`, output, `HH:mm:ss`, duration
7. `recordSuccess` or `recordFailure` → clears `currentStep`, updates history strip
8. Service stops itself (`stopSelf`)

### IrisWidgetStateRepository API

```kotlin
suspend fun pushStateToAll(context: Context, update: (IrisWidgetState) -> IrisWidgetState)
suspend fun setSlmState(context: Context, slmState: SlmProcessState, workflowName: String? = null)
suspend fun recordSuccess(context: Context, workflowName: String)
suspend fun recordFailure(context: Context, workflowName: String)
suspend fun setCurrentStep(context: Context, stepName: String?)
suspend fun logStep(context: Context, log: StepLog)         // capped at 8 entries
suspend fun updateSuggestions(context: Context, suggestionNames: List<String>)
suspend fun resetToIdle(context: Context)
```

### Widget XML Drawables

```text
res/drawable/glass_pill_background.xml   — suggestion pill: dark fill, violet border, 24dp radius
res/drawable/outline_pill.xml            — badge pill: dark fill, cyan border, 24dp radius
res/drawable/widget_state_idle.xml       — gray border
res/drawable/widget_state_cyan.xml       — cyan border (ANALYZING)
res/drawable/widget_state_violet.xml     — violet border (TOOL_CALL)
res/drawable/widget_state_success.xml    — green border (SUCCESS)
res/drawable/widget_state_error.xml      — red border (ERROR)
```

### SlmExecutionService Manifest Requirements

```xml
<service
    android:name=".widget.SlmExecutionService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

Both are present in `AndroidManifest.xml`.

## Build Configuration

```text
app/build.gradle.kts
```

Key dependencies:

```kotlin
// Compose
implementation("androidx.activity:activity-compose:1.9.3")
implementation(platform("androidx.compose:compose-bom:2024.12.01"))
implementation("androidx.compose.material3:material3")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

// Glance widget
implementation("androidx.glance:glance-appwidget:1.1.1")

// DataStore (widget per-instance state)
implementation("androidx.datastore:datastore-core:1.1.1")

// On-device inference
implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")

// Sound classification
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Location / geofencing
implementation("com.google.android.gms:play-services-location:21.3.0")

// OpenStreetMap
implementation("org.osmdroid:osmdroid-android:6.1.18")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

// Marketplace (anonymous Firebase)
implementation("com.google.firebase:firebase-database-ktx:21.0.0")

// Chrome Custom Tabs
implementation("androidx.browser:browser:1.8.0")
```

Kotlin plugins: `kotlin-android`, `kotlin-compose`, `kotlin-serialization`

## Android Permissions

Declared in `AndroidManifest.xml` (37 total). Categories:

- Audio: `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`
- Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- NFC: `NFC`
- Bluetooth: `BLUETOOTH_CONNECT`
- WiFi: `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE`
- Alarms: `SET_ALARM`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`
- Notifications: `POST_NOTIFICATIONS`, `ACCESS_NOTIFICATION_POLICY`
- Calendar: `READ_CALENDAR`, `WRITE_CALENDAR`
- SMS: `READ_SMS`, `RECEIVE_SMS`
- Media: `READ_MEDIA_AUDIO`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- System: `QUERY_ALL_PACKAGES`, `WRITE_SETTINGS`, `WRITE_SECURE_SETTINGS`
- Contacts: `READ_CONTACTS`
- Foreground services: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_DATA_SYNC`
- Network: `INTERNET`

## Running On Emulator Or Phone

Model push path:

```bash
adb shell mkdir -p /sdcard/Android/data/com.irisapp/files/models
adb push local-models/gemma-4-E2B-it.litertlm /sdcard/Android/data/com.irisapp/files/models/gemma-4-E2B-it.litertlm
```

Useful log filter:

```bash
adb logcat | grep -Ei "WorkflowGeneration|WorkflowRunner|InferenceManager|TriggerRegistry|Tool call|Tool result|Reto|CapabilityBinder|SlotGrounding|TaskDecomposer|SlmExecution|WidgetState"
```

Build:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

## Design/Product Docs

```text
docs/design/gemmaos_wireframe_features.md
docs/design/wireframe_ai_helper_prompt.md
docs/implementation/EXECUTION_PIPELINE.md
docs/implementation/P1_TRIGGERS_PLAN.md
docs/implementation/TRIGGERS_PROGRESS.md
docs/research/trigger_feasibility.md
docs/research/reto_tool_orchestration_for_gemmaworkflow.md
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

1. **Wire real ActionExecutor into `SlmExecutionService.executeStep()`** — replace `delay` simulation with `WorkflowRunner.runStep(step, applicationContext)` and pass the real `ExecutionResult.output` to `StepLog`.
2. **Update `WorkflowJsonParser`** to parse all new `TriggerConfig` variants (battery, charger, wifi, bluetooth, airplane_mode, dnd, geofence, app_opened, app_closed, sms_received, voice, sound_event, sleep).
3. **Update `PromptBuilder.buildWorkflowJsonPrompt()`** to include the supported trigger type list.
4. **Update `ActionSpec.triggerCompatible`** for new trigger IDs where appropriate.
5. **Call `IrisWidgetStateRepository.updateSuggestions()`** from `WorkflowGenerationViewModel` when workflows are saved or deleted, so the widget carousel stays in sync.
6. **Add trigger validation** for trigger-specific required fields (geofence radius, sound class, BT device name, etc.).
7. **Add setup flows or clear setup states** for new triggers in `ManualWorkflowEditorScreen`.
8. **Consider reducing AI calls** by letting Kotlin canonicalize the final workflow object rather than asking the SLM to format the final JSON.
