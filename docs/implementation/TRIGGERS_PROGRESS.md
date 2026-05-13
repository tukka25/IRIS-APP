# Trigger Implementation Progress

**Last updated:** 2026-05-11
**Branches:** `p1-geofence-in-progress` (current working branch)
**Issue:** #22 — *Implement missing trigger types from trigger feasibility analysis*

---

## Trigger Roadmap

| Priority | Trigger | Status | Notes |
|----------|---------|--------|-------|
| **P0** | Manual | ✅ Done | Default trigger in editor |
| P0 | Time | ✅ Done | `TimeTriggerScheduler` + `TimeTriggerReceiver` |
| P0 | NFC | ✅ Done | `NfcTriggerManager` + `NfcWorkflowTriggerActivity` |
| P0 | Share Sheet | ✅ Done | `ShareReceiver` activity |
| P0 | Tasker Required | ✅ Done | Confirmation gate |
| P0 | Battery Level | ✅ Done | `BatteryTriggerManager` |
| P0 | Charger | ✅ Done | `ChargerTriggerManager` |
| P0 | Wi-Fi Connected | ✅ Done | `WiFiTriggerManager` |
| P0 | Bluetooth | ✅ Done | `BluetoothTriggerManager` |
| P0 | Airplane Mode | ✅ Done | `AirplaneModeTriggerManager` |
| P0 | Do Not Disturb | ✅ Done | `DndTriggerManager` |
| **P1** | **Geofence** | 🔄 In Progress | Data model + manager + map UI done; build failing |
| P1 | SMS Received | ⬜ Pending | Plan documented in `P1_TRIGGERS_PLAN.md` |
| P1 | Own Alarm | ⬜ Pending | Plan documented in `P1_TRIGGERS_PLAN.md` |
| P2 | Sleep as Proxy | ⬜ Pending | |
| P2 | Driving Mode | ⬜ Pending | |
| P2 | Notification Listener | ⬜ Pending | (messaging apps) |
| P3 | Headphone State | ⬜ Pending | |
| P3 | Timezone Change | ⬜ Pending | |
| P3 | Power Save Mode | ⬜ Pending | |
| P3 | Siri & Shortcuts | ⬜ Pending | |

---

## P1 — Geofence (In Progress)

**Branch:** `p1-geofence-in-progress`

### What's Done

| File | Change | Status |
|------|--------|--------|
| `domain/model/WorkflowModels.kt` | Added `GeofenceTransition` enum + `TriggerConfig.Geofence` data class | ✅ |
| `domain/triggers/TriggerCatalog.kt` | Added geofence `TriggerInfo` with `SetupState.NeedsPermission` | ✅ |
| `platform/location/GeofenceManager.kt` | Singleton with `GeofencingClient`, `registerAll()` / `registerWorkflow()` / `unregisterWorkflow()` | ✅ |
| `platform/location/GeofenceBroadcastReceiver.kt` | Receives geofence transitions, fires via `TriggerRegistry.fire()` | ✅ |
| `ui/home/WorkflowGenerationViewModel.kt` | `Geofence` branch in `when` — calls `GeofenceManager.registerWorkflow()` | ✅ |
| `app/GemmaWorkflowApp.kt` | Calls `GeofenceManager.registerAll()` on startup | ✅ |
| `AndroidManifest.xml` | Added `ACCESS_BACKGROUND_LOCATION`, declared `GeofenceBroadcastReceiver` | ✅ |
| `app/build.gradle.kts` | Added `play-services-location:21.3.0` + `osmdroid-android:6.1.18` | ✅ |
| `ui/home/OsmMapPicker.kt` | New: in-page tap-to-set OpenStreetMap with circle overlay | ✅ |
| `ui/home/ManualWorkflowEditorScreen.kt` | Added Geofence card in editor with map, radius slider, trigger type chips | ✅ |
| `domain/safety/WorkflowValidator.kt` | Added `Geofence` case in `triggerTypeName()` when | ✅ |
| `ui/MainActivity.kt` | Added `Geofence` branch in `triggerLabel` when | ✅ |

### Data Model

```kotlin
enum class GeofenceTransition { ENTER, EXIT, DWELL }

data class TriggerConfig.Geofence(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val transitionType: GeofenceTransition,
    val requestId: String,        // = workflow name
    val dwellDurationSeconds: Int, // 0 = instant
    val label: String?             // optional display name
)
```

### Architecture

```
GeofenceManager (singleton)
  ├── GeofencingClient (Google Play Services)
  ├── GeofenceBroadcastReceiver → receives PendingIntent from system
  ├── registerAll(context)        ← on app startup: restore all saved geofences
  ├── registerWorkflow(...)       ← on workflow save: add geofence to GeofencingClient
  └── unregisterWorkflow(name)    ← on workflow delete: remove geofence

GeofenceBroadcastReceiver
  → GeofencingEvent.fromIntent(intent)
  → getTransitionGeofences()[0].requestId  (= workflowName)
  → repo.get(name) → TriggerRegistry.fire(context, workflow)
```

### Permission Flow

```
ACCESS_FINE_LOCATION (granted) → add geofence
ACCESS_FINE_LOCATION (denied) → SetupState.NeedsSetup → guide user to Settings

Android 10+ (API 29+):
ACCESS_BACKGROUND_LOCATION (granted) → full geofence monitoring
ACCESS_BACKGROUND_LOCATION (denied) → SetupState.NeedsSetup → guide to
  Settings → Location → Permission → "Allow all the time"
```

### Map UX (OsmMapPicker)

- Tap map → sets `latitude` / `longitude`
- Marker appears at tap point
- Circle overlay shows current `radiusMeters` (updates with slider)
- Radius slider: 50m – 1000m
- Transition type chips: Arriving / Leaving / Staying
- Coordinates displayed below map once set

### Build Errors (Current)

`OsmMapPicker.kt` — two remaining compile errors:
1. `fillColor` / `strokeColor` / `strokeWidth` on `Polygon` — uncertain osmdroid 6.1.18 API
2. `MapEventsReceiver` — interface method signatures don't match (non-null `GeoPoint!` vs nullable `GeoPoint?`)

Fix: use raw `Overlay.onSingleTapConfirmed` approach (done) and verify `Polygon` property names from osmdroid 6.1.18 API.

---

## P1 — SMS Received (Not Started)

**Status:** Plan documented in `docs/implementation/P1_TRIGGERS_PLAN.md`

### Plan Summary

- **Primary (Android < 14):** `SmsTriggerReceiver` listening to `SMS_RECEIVED_ACTION`
- **Fallback (Android 14+):** `SmsNotificationListener` as `NotificationListenerService`
- **Data model:** `TriggerConfig.SmsReceived(senderPattern: String?, bodyPattern: String?)`
- **Sender matching:** normalize phone numbers (strip `+`, spaces, `-`)
- **Body matching:** regex via `Regex.containsMatchIn()`

### Files to create

- `platform/sms/SmsTriggerManager.kt`
- `platform/sms/SmsTriggerReceiver.kt`
- `platform/sms/SmsNotificationListener.kt`

---

## P1 — Own Alarm (Not Started)

**Status:** Plan documented in `docs/implementation/P1_TRIGGERS_PLAN.md`

### Plan Summary

- **Own alarms only** — track via `AlarmManager.setAlarmClock()` with PendingIntent actions
- **Snooze:** `AlarmSnoozeReceiver` → re-schedule with `SNOOZE_DURATION_MINUTES` from settings
- **Dismiss:** `AlarmDismissReceiver` → fire matching workflows with `SnoozeTriggerBehavior.DISMISSED`
- **Snooze duration:** user-configured in app settings, default 10 min
- **System alarms:** `ACTION_NEXT_ALARM_CLOCK_CHANGED` as best-effort signal only

### Files to create

- `platform/alarm/AlarmTracker.kt`
- `platform/alarm/AlarmSnoozeReceiver.kt`
- `platform/alarm/AlarmDismissReceiver.kt`

---

## P0 Trigger Reference (Completed)

All P0 managers follow this pattern:

```
registerAll(context)         ← called from GemmaWorkflowApp.onCreate
  → loads workflows from repo
  → calls registerWorkflow() for each

registerWorkflow(context, workflowName, triggerConfig)
  → creates system-specific registration
  → stores in activeWorkflows[workflowName]

unregisterWorkflow(workflowName)
  → removes system-specific registration
  → removes from activeWorkflows

checkSetup(context): SetupState
  → returns Ready / NeedsSetup / NeedsPermission(permission)
```

All managers live in `com.gemmaworkflow.platform.*` (trigger/, location/, alarm/, etc.)