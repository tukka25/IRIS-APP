# IrisApp — Project Context

**Last updated:** 2026-05-19
**Branch:** `main`
**Status:** Active development

---

## What is Iris?

Iris is an on-device AI workflow automation app for Android. Users describe what they want in plain language and Iris builds a runnable automation — then lets them trigger it via NFC tag, schedule, share sheet, or manual tap.

Example:
```
"Send a message to Maya saying hi, and add a meeting with her to my calendar next Friday at 6."
```

Pipeline: understand → split into tasks → choose actions → resolve facts → build workflow → preview → save → trigger → execute.

---

## Technical Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (including Glance for widgets) |
| On-device model | LiteRT-LM with Gemma `.litertlm` file |
| Persistence | Local JSON files + Firebase Realtime Database (marketplace) |
| Execution | Android APIs: AlarmManager, CalendarContract, ClipboardManager, MediaSession, etc. |

The project no longer uses `llama.cpp`. Inference is through `platform/inference/InferenceManager.kt` + `litert/LitertLmEngine.kt`.

**Package:** `com.irisapp`

---

## Source Structure

```
app/src/main/java/com/irisapp/
├── app/
│   └── IrisApp.kt                    ← Application.onCreate()
├── data/
│   ├── local/storage/
│   │   └── JsonFileStorage.kt
│   └── repository/
│       ├── ExecutionHistoryRepository.kt
│       ├── MarketplaceRepository.kt  ← Firebase RTDB anonymous marketplace
│       ├── WorkflowRepository.kt
│       └── WorkflowShareRepository.kt  ← upload/fetch via Firebase deep-link sharing
├── domain/
│   ├── catalog/
│   │   └── ActionSpecRegistry.kt    ← 35+ actions
│   ├── model/
│   │   ├── SharedContent.kt         ← sealed Text/Image for share intent
│   │   └── WorkflowModels.kt         ← PlannedWorkflow, WorkflowStep, TriggerConfig, etc.
│   ├── parser/
│   │   └── WorkflowJsonParser.kt     ← parse + validate + retry once on failure
│   ├── planner/
│   │   ├── PromptBuilder.kt
│   │   ├── RequestAnalysis.kt
│   │   └── RetoWorkflowPlanner.kt    ← main planner entry point
│   ├── runner/
│   │   ├── FallbackParamMapper.kt
│   │   ├── IntentFactory.kt
│   │   └── WorkflowRunner.kt         ← step-by-step execution
│   ├── safety/
│   │   └── WorkflowValidator.kt
│   └── triggers/
│       └── TriggerCatalog.kt        ← register/unregister workflows by trigger type
├── platform/
│   ├── airplane/    AirplaneModeApiExecutor.kt
│   ├── alarm/       AlarmApiExecutor.kt, AlarmReceiver.kt, BootReceiver.kt,
│   │                AlarmDismissReceiver.kt, AlarmFireReceiver.kt, AlarmSnoozeReceiver.kt,
│   │                TimeTriggerScheduler.kt, TimeTriggerReceiver.kt, AlarmTriggerManager.kt
│   ├── app/         LaunchAppService.kt        ← FGS with appLaunch subtype (milestone 7)
│   │                LaunchAppApiExecutor.kt
│   ├── bluetooth/   BluetoothApiExecutor.kt
│   ├── calendar/    CalendarApiExecutor.kt
│   ├── capability/ ChromeCustomTabOpener.kt, IntentDiscoveryEngine.kt,
│   │               PackageCapabilityScanner.kt
│   ├── cellular/   CellularApiExecutor.kt
│   ├── clipboard/   ClipboardApiExecutor.kt
│   ├── command/     CommandApiExecutor.kt
│   ├── display/    BrightnessApiExecutor.kt, RotationApiExecutor.kt
│   ├── hotspot/     HotspotApiExecutor.kt
│   ├── http/        HttpRequestApiExecutor.kt
│   ├── inference/   InferenceManager.kt
│   │               litert/LitertLmEngine.kt, ModelFileLocator.kt
│   ├── intent/      GenericIntentApiExecutor.kt
│   ├── location/    GeofenceBroadcastReceiver.kt, GeofenceManager.kt
│   ├── media/       MediaControlApiExecutor.kt
│   ├── nfc/
│   │   ├── DeepLinkRouter.kt         ← writeMode state + deep-link event bus (milestone 7)
│   │   ├── NfcTriggerHandler.kt      ← cold-start NFC routing (milestone 7)
│   │   └── NfcSetupScreen.kt
│   ├── notification/ NotificationApiExecutor.kt
│   ├── share/       ShareSheetTriggerHandler.kt
│   ├── sms/         SmsNotificationListener.kt, SmsTriggerManager.kt, SmsTriggerReceiver.kt
│   ├── sound/       YamnetClassifier.kt, SoundEventTriggerService.kt
│   ├── sync/        SyncApiExecutor.kt
│   ├── tools/
│   │   ├── Tool.kt, ToolRegistry.kt, ToolInitializer.kt, ToolAliasRegistry.kt
│   │   └── impl/
│   │       ├── ClipboardTools.kt, DeviceTools.kt, DomainSearchTools.kt
│   │       ├── ExecutionTools.kt, ReasoningTools.kt
│   │       ├── ReminderTools.kt, SearchTools.kt, SettingsTools.kt, TemporalTools.kt
│   │       └── reto/
│   │           ├── RetoOrchestrator.kt, CapabilityBinder.kt, SlotGroundingPlanner.kt
│   │           ├── RequirementBuilder.kt, ResolverRegistry.kt, ToolMetadataRegistry.kt
│   ├── trigger/
│   │   ├── TriggerRegistry.kt         ← fire() / confirmAndResume() / dismissConfirmation()
│   │   ├── BatteryTriggerManager.kt, BatteryTriggerReceiver.kt
│   │   ├── ChargerTriggerManager.kt, ChargerTriggerReceiver.kt
│   │   ├── BluetoothTriggerManager.kt, DndTriggerManager.kt, SleepTriggerManager.kt, WiFiTriggerManager.kt
│   │   ├── AppMonitorAccessibilityService.kt
│   │   ├── voice/
│   │   │   ├── VoiceTriggerHandler.kt, VoiceIntentTrigger.kt, VoiceTriggerFab.kt
│   │   │   └── VoiceRecognitionContract.kt
│   │   └── sound/
│   │       ├── SoundEventTriggerService.kt, SoundEventTriggerRegistry.kt
│   │       └── YamnetClassifier.kt
│   ├── volume/      RingerModeApiExecutor.kt, VolumeApiExecutor.kt
│   └── wifi/        WifiApiExecutor.kt
├── ui/
│   ├── MainActivity.kt               ← PermissionDialog (with step-by-step grant instructions)
│   ├── components/
│   │   ├── AmbientBackground.kt     ← animated dark orb background
│   │   ├── BlobPersona.kt            ← animated blob character (IDLE/LISTENING/PROCESSING/DONE)
│   │   ├── GlassmorphicCard.kt, GradientButton.kt, LivingInputConsole.kt, SceneChip.kt
│   │   └── HexHeroIcon.kt            ← present but no longer rendered
│   ├── home/
│   │   ├── GenerateScreen.kt, ManualWorkflowEditorScreen.kt
│   │   ├── NfcTriggerSetupScreen.kt, OsmMapPicker.kt, ShareSheetSetupScreen.kt
│   │   ├── SoundEventTriggerSetupScreen.kt, TimeTriggerSetupScreen.kt
│   │   ├── WorkflowGenerationUiState.kt, WorkflowGenerationViewModel.kt
│   │   └── ImportWorkflowScreen.kt   ← Firebase RTDB import via deep-link (PR #37)
│   ├── marketplace/
│   │   ├── MarketplaceScreen.kt, MarketplaceViewModel.kt, MarketplaceUiState.kt
│   │   └── MarketplaceRepository.kt
│   ├── nfc/
│   │   └── NfcSetupScreen.kt         ← NFC tag write UI + WriteState machine
│   ├── theme/
│   │   └── IrisTheme.kt
│   └── trigger/
│       ├── TimeTriggerConfirmationActivity.kt, TimeTriggerNotification.kt
│       └── TimeTriggerPicker.kt
└── widget/
    ├── IrisWidgetState.kt, IrisWidgetStateRepository.kt
    ├── WorkflowWidgetGlance.kt, WorkflowWidgetReceiver.kt
    ├── WorkflowWidgetConfigActivity.kt
    ├── WidgetPreferences.kt, TriggerWorkflowAction.kt, SlmExecutionService.kt
```

---

## Trigger System

`TriggerConfig` is a sealed class with these variants:

| Variant | Status | Component |
|---|---|---|
| `Manual` | ✅ supported | UI button |
| `Time` | ✅ supported | `TimeTriggerScheduler` + `AlarmManager` |
| `Nfc` | ✅ supported | `NfcTriggerHandler` + `DeepLinkRouter` |
| `ShareSheet` | ✅ supported | `ShareSheetTriggerHandler` via `ACTION_SEND` |
| `Battery` | ✅ runtime | `BatteryTriggerManager` / `BatteryTriggerReceiver` |
| `Charger` | ✅ runtime | `ChargerTriggerManager` / `ChargerTriggerReceiver` |
| `WiFi` | ✅ runtime | `WiFiTriggerManager` |
| `Bluetooth` | ✅ runtime | `BluetoothTriggerManager` / `BluetoothTriggerReceiver` |
| `AirplaneMode` | ✅ runtime | `AirplaneModeTriggerManager` / `AirplaneModeTriggerReceiver` |
| `DoNotDisturb` | ✅ runtime | `DndTriggerManager` / `DndTriggerReceiver` |
| `Geofence` | ✅ runtime | `GeofenceManager` + `GeofenceBroadcastReceiver` |
| `AppOpened` | ✅ runtime | `AppMonitorAccessibilityService` |
| `AppClosed` | ✅ runtime | `AppMonitorAccessibilityService` |
| `SmsReceived` | ✅ runtime | `SmsTriggerManager` / `SmsTriggerReceiver` |
| `SleepProxy` | ✅ runtime | `SleepTriggerManager` |
| `Voice` | ✅ runtime | `VoiceTriggerHandler` + `VoiceIntentTrigger` |
| `SoundEvent` | ✅ runtime | `SoundEventTriggerService` (YAMNet on-device) |

---

## NFC Write/Scan/Execute (Milestone 7)

### Tag Writing
1. User opens workflow detail → taps "Set up NFC trigger"
2. `NfcSetupScreen` — selects workflow → taps "Write to Tag"
3. `DeepLinkRouter.setWriteMode(workflowId)` enters write mode state
4. User holds phone near NFC tag → system calls `NfcAdapter.writeTag()`
5. `DeepLink.WriteComplete` event fires with success/failure

### Tag Scanning
**Foreground:** `MainActivity.onResume()` enables `NfcAdapter.enableForegroundDispatch()` → detects tag → `DeepLinkRouter.emitDeepLink(DeepLink.NfcScan(workflowId))`

**Cold start:** `NfcTriggerHandler` (BroadcastReceiver) receives NFC intent → if app not in foreground, launches `MainActivity` with deep-link intent → `MainActivity.onNewIntent()` routes via `DeepLinkRouter.routeFromActivity()`

**Execution:** `DeepLink.NfcScan` → `TriggerRegistry.fire(context, workflow)` → `WorkflowRunner.run()`

---

## Workflow Sharing (PR #37)

Workflows can be shared as Firebase Realtime Database entries via a deep-link URL.

### Share flow
1. User opens workflow detail → taps Share
2. `WorkflowShareRepository.upload()` writes JSON to RTDB under `/shared_workflows/{pushId}`
3. Share sheet sends `https://iris-23288.web.app/import/{pushId}` via `ACTION_SEND`
4. Firebase Hosting rewrites `/import/**` → redirect page with `iris://import/{id}` deep-link

### Import flow
1. `MainActivity` handles `iris://import/{id}` or `https://.../import/{id}`
2. `ImportWorkflowScreen` fetches JSON from RTDB, shows preview, prompts rename
3. `WorkflowJsonParser.parseFromExport()` parses and validates
4. User confirms → workflow saved via `WorkflowRepository.save()`

---

## Permission Dialog

`PermissionDialog` in `MainActivity` shows runtime permission requests during workflow execution. For each permission, it shows:
- A lock icon + human-readable name (e.g., "Read calendar", "Nearby devices")
- Step-by-step instructions guiding the user to the correct system settings panel

Supported permissions include: contacts, SMS, call log, camera, microphone, storage, calendar, Bluetooth, location (fine + background), notifications, and media (audio/images/video).

---

## ActionSpec Registry

**35+ actions** in `ActionSpecRegistry.kt`. Key categories:
- **Communication:** `sms.compose`, `whatsapp.send_text`, `phone.dial`
- **Calendar:** `calendar.create_event`
- **Alarm:** `alarm.set_alarm`, `alarm.set_timer`
- **Location:** `maps.open_place`, `maps.navigate`
- **Media:** `media.play_pause`, `media.next_track`, `media.previous_track`, `media.play_from_search`, `spotify.search_and_play`
- **System:** `volume.set`, `ringer_mode.set`, `brightness.set`, `rotation.lock`
- **App control:** `launch_app`, `app.open`, `browser.open_url`, `browser.search`
- **Clipboard:** `clipboard.copy_text`
- **Sharing:** `share.share_text`, `share.share_image`
- **HTTP:** `http_request`
- **Data:** `command.exec`, `intent.send`, `sync.toggle`
- **Notifications:** `notification.send`, `toast.show`
- **Productivity:** `internal.reminder.create`

The model may only output action IDs from this registry — it cannot invent Android APIs.

---

## Persistence

| Data | Storage |
|---|---|
| Saved workflows | JSON files in `app/files/workflows/` |
| Execution history | JSON files in `app/files/history/` (last 100 entries) |
| Marketplace | Firebase Realtime Database (`iris-23288-default-rtdb`) |
| Widget state | DataStore per widget instance |

---

## Widget System

Glance AppWidget Mini Dashboard with DataStore-backed state (`IrisWidgetState`). States: `IDLE`, `ANALYZING`, `TOOL_CALL`, `SUCCESS`, `ERROR`. Real-time per-step log (capped at 8 entries). Suggestion pills trigger workflow runs via `TriggerWorkflowAction.onAction()`.

---

## Permission Reference

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
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Sound Event trigger | Manifest |
| `WRITE_SETTINGS` | Brightness, rotation, hotspot, airplane mode | Manual (Settings only) |
| `INTERNET` | HTTP requests, model/Firebase downloads | Manifest |
| `RECEIVE_BOOT_COMPLETED` | Reschedule time triggers after reboot | Manifest |
| `QUERY_ALL_PACKAGES` | List apps for `launch_app` | Manifest |
| `READ_MEDIA_AUDIO/IMAGES/VIDEO` | Media file access | Runtime (Android 13+) |
| `FOREGROUND_SERVICE_SPECIAL_USE` (subtype: `appLaunch`) | `LaunchAppService` FGS | Manifest (Android 14+) |