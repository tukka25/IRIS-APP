# P1 Triggers — Implementation Plan

**Issue:** #22 — *"Implement missing trigger types from trigger feasibility analysis"*
**Scope:** Geofence, SMS Received, Own Alarm Snooze/Dismiss
**Status:** **APPROVED** — decisions incorporated below

---

## Decisions Log

| # | Question | Decision |
|---|----------|----------|
| 1 | Geofence: guide user or use foreground service for background location? | **Guide the user.** Standard Android permission flow with a dedicated screen for "Allow all the time" on Android 10+. No foreground service. |
| 2 | SMS on Android 14+: skip, or NotificationListenerService fallback? | **Both.** Primary: `SMS_RECEIVED_ACTION` broadcast (Android < 14). Fallback: `NotificationListenerService` watching SMS app notifications (Android 14+). |
| 3 | Alarm snooze duration: user-configured or system default? | **User-configured.** `SNOOZE_DURATION_MINUTES` in app settings, default 10 minutes. |

---

## 1. Geofence (Arrive / Leave)

### Research Summary
- **API:** `GeofencingClient` (Google Play Services) + `LocationServices`
- **Transitions:** `GEOFENCE_TRANSITION_ENTER`, `GEOFENCE_TRANSITION_EXIT`, `GEOFENCE_TRANSITION_DWELL`
- **Permissions:** `ACCESS_FINE_LOCATION` (runtime), `ACCESS_BACKGROUND_LOCATION` (Android 10+)
- **Battery:** Low if only enter/exit; higher if dwell monitoring is used
- **Limit:** 100 geofences per app per user (Android limit)

### Data Model

```kotlin
// WorkflowModels.kt

/** Run when device enters/exits a geographic zone. */
@Serializable
data class Geofence(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    val transitionType: GeofenceTransition = GeofenceTransition.ENTER_EXIT,
    val dwellDelaySeconds: Int = 0,          // 0 = instant, >0 = dwell trigger
    val expirationMillis: Long = -1,          // -1 = never expire
    val name: String? = null                 // optional label e.g. "Home"
) : TriggerConfig()

@Serializable
enum class GeofenceTransition {
    ENTER,
    EXIT,
    DWELL,
    ENTER_EXIT   // fires on either
}
```

### Architecture

```
GeofenceManager (singleton, object)
  ├── activeGeofences: MutableMap<String, WorkflowGeofenceConfig>
  ├── geofencingClient: GeofencingClient
  ├── pendingIntent: PendingIntent  →  GeofenceBroadcastReceiver
  │
  ├── registerAll(context)          ← called from IrisAppApp.onCreate
  ├── registerWorkflow(ctx, name, trigger)
  └── unregisterWorkflow(name)

GeofenceBroadcastReceiver : BroadcastReceiver
  → reads GEOFENCE_TRANSITION_ENTER/EXIT/DWELL from intent
  → matches against activeGeofences (by requestId = workflowName)
  → calls TriggerRegistry.fire(context, workflow)
```

**Why PendingIntent → BroadcastReceiver instead of direct service:**
- `GeofencingClient` requires `PendingIntent` — the system fires it when geofence transitions occur
- The `PendingIntent` must point to a `BroadcastReceiver` or `Service`
- Using `BroadcastReceiver` keeps the pattern consistent with all other managers
- Must include `Intent.FLAG_ACTIVITY_NEW_TASK` when launching from receiver

### Permission Flow

```
Workflow saved with Geofence trigger
  → check: Context.checkSelfPermission(ACCESS_FINE_LOCATION)
  → if denied: return SetupState.NeedsSetup with message directing user to Settings
  → if granted (Android 10+): also check ACCESS_BACKGROUND_LOCATION
  → if background denied: guide user to Settings → Location → Permission → "Allow all the time"
  → once both granted: addGeofences() with GeofencingRequest
```

### Key Implementation Notes

- **Request ID = workflow name** — used to match fired geofence back to the correct workflow
- **Dwell detection:** `initialTrigger = GEOFENCE_TRANSITION_DWELL` with `loiteringDelayMs` in the request
- **Expiration:** use `Geofence.NEVER_EXPIRE` (-1) by default
- **Battery:** use `GeofencingClient.addGeofences()` with `setCallback()` to monitor validity
- **Android 12+ (API 31+):** `ACCESS_BACKGROUND_LOCATION` is required for background geofence monitoring
- **Register in IrisAppApp.onCreate:** loads all geofence workflows, calls `addGeofences()`
- **Register per workflow on save:** `GeofencingClient.addGeofences()` is idempotent — safe to call multiple times
- **Permission guidance:** When `ACCESS_BACKGROUND_LOCATION` is denied on Android 10+, show a dedicated screen explaining why background location is needed and guide the user to Settings → Location → Permission → "Allow all the time"

### Files to Create/Modify

| File | Action |
|------|--------|
| `platform/location/GeofenceManager.kt` | Create — singleton manager |
| `platform/location/GeofenceBroadcastReceiver.kt` | Create — receives geofence transitions |
| `domain/model/WorkflowModels.kt` | Modify — add `Geofence` + `GeofenceTransition` |
| `domain/triggers/TriggerCatalog.kt` | Modify — add geofence `TriggerInfo` |
| `ui/home/WorkflowGenerationViewModel.kt` | Modify — call `GeofenceManager.registerWorkflow` on save |
| `app/IrisAppApp.kt` | Modify — call `GeofenceManager.registerAll` on create |
| `AndroidManifest.xml` | Modify — add `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, declare receiver |

---

## 2. SMS Received

### Research Summary
- **Primary (Android < 14):** `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` broadcast
- **Fallback (Android 14+):** `NotificationListenerService` watching SMS app notifications
- **Why both:** `SMS_RECEIVED_ACTION` is a limited broadcast on Android 14+ — only the default SMS app receives it. `NotificationListenerService` works differently — user grants Notification Access, and the app receives a stream of notifications from observed apps. This works on Android 14+ regardless of default SMS app status.
- **Caveat (NotificationListenerService):** Notification text may be redacted on Android 12+ depending on the SMS app (some apps show "You have a new message" without details)
- **Permissions:** `READ_SMS`, `RECEIVE_SMS`, `READ_PHONE_STATE` (runtime), plus Notification Access for the fallback

### Data Model

```kotlin
// WorkflowModels.kt

/** Run when an SMS is received. */
@Serializable
data class SmsReceived(
    val senderPattern: String? = null,   // regex match on sender address/number; null = any
    val bodyPattern: String? = null,       // regex match on message body; null = any
    val compareOp: String = "contains"     // "contains" | "matches" (regex)
) : TriggerConfig()
```

### Architecture

```
SmsTriggerManager (singleton, object)
  ├── activeWorkflows: MutableMap<String, TriggerConfig.SmsReceived>
  ├── registerAll(context)
  ├── registerWorkflow(ctx, name, trigger)
  └── unregisterWorkflow(name)

SmsTriggerReceiver : BroadcastReceiver    ← Primary (Android < 14)
  → extracts: sender (originating address), body text, timestamp
  → filters each workflow against senderPattern / bodyPattern
  → calls TriggerRegistry.fire(context, workflow)

SmsNotificationListener : NotificationListenerService  ← Fallback (Android 14+)
  → onNotificationPosted(sbn)
  → extracts: package name (must be SMS app), sender, body from extras
  → filters each workflow against senderPattern / bodyPattern
  → calls TriggerRegistry.fire(context, workflow)
```

**Which listener fires?** On Android < 14: `SmsTriggerReceiver` receives `SMS_RECEIVED_ACTION`. On Android 14+: `SmsNotificationListener` detects SMS app notifications. Both are registered and both filter independently — Android routes each notification/broadcast to the appropriate registered component.

### Permission Flow

```
Workflow saved with SmsReceived trigger
  → check: Context.checkSelfPermission(RECEIVE_SMS)
  → if denied: return SetupState.NeedsSetup with message directing to grant SMS permission

  → Also check NotificationListenerService access:
  → if !notificationListenerService.isEnabled():
      return SetupState.NeedsSetup with message directing user to
        Settings → Notification Access → enable IrisApp
      (required for Android 14+ fallback)

  → Both permissions granted: SMS trigger is active
```

### Key Implementation Notes

- **Sender matching:** normalize phone numbers before comparing (strip `+`, spaces, `-`)
- **Body matching:** use `bodyPattern` as regex with `Regex.containsMatchIn()`
- **Multiple matching workflows:** all matching workflows fire (same pattern as other triggers)
- **Idempotency:** `registerReceiver()` is safe to call multiple times; Android deduplicates
- **Android 14+ primary path:** `SMS_RECEIVED_ACTION` won't arrive — rely on `SmsNotificationListener`
- **NotificationListenerService:** needs `BIND_NOTIFICATION_LISTENER_SERVICE` permission (user enables in Settings → Notification Access). The service is declared in manifest but only activates when the user grants access.
- **SMS app package detection:** filter `SmsNotificationListener.onNotificationPosted` by package name of known SMS apps (`com.google.android.apps.messages`, `com.samsung.android.messaging`, `com.android.mms`, etc.) or by notification channel
- **Content redaction:** on Android 12+ some SMS apps redact the full body from notification extras — this is a known limitation, workflows can still match on sender

### Files to Create/Modify

| File | Action |
|------|--------|
| `platform/sms/SmsTriggerManager.kt` | Create — singleton manager |
| `platform/sms/SmsTriggerReceiver.kt` | Create — receives SMS broadcasts (Android < 14) |
| `platform/sms/SmsNotificationListener.kt` | Create — NotificationListenerService (Android 14+ fallback) |
| `domain/model/WorkflowModels.kt` | Modify — add `SmsReceived` |
| `domain/triggers/TriggerCatalog.kt` | Modify — add sms `TriggerInfo` |
| `ui/home/WorkflowGenerationViewModel.kt` | Modify — call `SmsTriggerManager.registerWorkflow` on save |
| `app/IrisAppApp.kt` | Modify — call `SmsTriggerManager.registerAll` + register NotificationListenerService on create |
| `AndroidManifest.xml` | Modify — add `RECEIVE_SMS`, `READ_SMS`, `READ_PHONE_STATE`, declare receiver and service |

---

## 3. Own Alarm Snooze / Dismiss

### Research Summary
- **No system-wide broadcast exists** for snooze/dismiss of third-party alarms
- Each OEM clock app (Samsung Clock, Xiaomi, OnePlus, etc.) uses **private broadcasts** not shared with other apps
- `AlarmManager.setAlarmClock()` provides `AlarmClockInfo` visible in the status bar but no callback on user action
- `ACTION_NEXT_ALARM_CLOCK_CHANGED` fires when the **next scheduled alarm changes** — cannot distinguish snooze from dismiss

### Strategy

**For IrisApp's own scheduled alarms:**
- Track alarm lifecycle internally: `scheduled` → `fired` → `snoozed` or `dismissed`
- When alarm fires (notification shown), start tracking it
- User taps "Snooze" on notification → `AlarmSnoozeReceiver` → re-schedules for snooze interval
- User taps "Dismiss" on notification → `AlarmDismissReceiver` → fires matching workflows
- **Snooze duration:** read from user-configured `SNOOZE_DURATION_MINUTES` setting (default: 10)

**For system/third-party alarms:**
- Cannot detect snooze/dismiss — no broadcast exists
- `ACTION_NEXT_ALARM_CLOCK_CHANGED` is listened but treated as best-effort only
- `AlarmTriggerType.ANY` workflows fire on this signal (with log caveat)

### Data Model

```kotlin
// WorkflowModels.kt

/** Run when a scheduled alarm is snoozed or dismissed. */
@Serializable
data class AlarmStopped(
    val alarmType: AlarmTriggerType = AlarmTriggerType.ANY,
    val snoozeBehavior: SnoozeTriggerBehavior = SnoozeTriggerBehavior.ANY
) : TriggerConfig()

@Serializable
enum class AlarmTriggerType {
    ANY,                    // any alarm (own or system)
    OWN_ONLY                // only IrisApp's own alarms
}

@Serializable
enum class SnoozeTriggerBehavior {
    ANY,                    // any stop action (snooze OR dismiss)
    SNOOZED,                // specifically snoozed
    DISMISSED               // specifically dismissed
}
```

### Architecture (Own Alarms Only)

```
AlarmTracker (singleton, object)
  ├── activeAlarms: MutableMap<alarmId, AlarmState>
  │
  ├── schedule(workflowName, trigger)
  │     → creates AlarmManager.setAlarmClock() with two PendingIntents:
  │       1. Alarm fire → AlarmFireReceiver → show notification → mark as FIRD
  │       2. Dismiss intent → AlarmDismissReceiver → mark DISMISSED → fire matching workflows
  │       3. Snooze intent → AlarmSnoozeReceiver → mark SNOOZED → re-schedule + fire matching workflows
  │
  └── cancel(workflowName)

AlarmDismissReceiver : BroadcastReceiver
  → called when user taps "Dismiss" on IrisApp alarm notification
  → updates AlarmTracker state → fires OWN alarm workflows with alarmType=OWN_ONLY

AlarmSnoozeReceiver : BroadcastReceiver
  → called when user taps "Snooze" on IrisApp alarm notification
  → re-schedules alarm for snooze interval
  → fires OWN alarm workflows with alarmType=OWN_ONLY
```

### System Alarm Detection (Limited)

```
SystemAlarmMonitor (singleton)
  → listens to ACTION_NEXT_ALARM_CLOCK_CHANGED
  → tracks previous AlarmClockInfo
  → when alarm disappears without IrisApp firing → user may have dismissed a system alarm
  → fires ANY workflows (with appropriate caveats in logs)
```

### Key Implementation Notes

- **Own alarms only are reliable** — the dismiss/snooze PendingIntents are IrisApp's own
- **System alarms are best-effort** — `ACTION_NEXT_ALARM_CLOCK_CHANGED` cannot distinguish snooze vs dismiss
- **Notification action intents** must use `Intent.FLAG_ACTIVITY_NEW_TASK` since they launch from Notification
- **Do NOT register for snooze/dismiss of third-party alarms** — no such broadcast exists
- **Snooze interval:** use the user's configured snooze length, or default to 10 minutes

### Files to Create/Modify

| File | Action |
|------|--------|
| `platform/alarm/AlarmTracker.kt` | Create — tracks own alarm lifecycle |
| `platform/alarm/AlarmDismissReceiver.kt` | Create — receives dismiss action from notification |
| `platform/alarm/AlarmSnoozeReceiver.kt` | Create — receives snooze action from notification |
| `domain/model/WorkflowModels.kt` | Modify — add `AlarmStopped`, `AlarmTriggerType`, `SnoozeTriggerBehavior` |
| `domain/triggers/TriggerCatalog.kt` | Modify — add alarm_stopped `TriggerInfo` with caveat in description |
| `ui/home/WorkflowGenerationViewModel.kt` | Modify — call `AlarmTracker.schedule` on save |
| `app/IrisAppApp.kt` | Modify — call `AlarmTracker.init` + register receivers on create |
| `AndroidManifest.xml` | Modify — add `SCHEDULE_ALARMS` permission, declare receivers |

---

## Cross-Cutting Concerns

### 1. Pattern: Manager Registration on App Start

All three managers follow the same initialization pattern as P0 managers:

```kotlin
// IrisAppApp.kt
override fun onCreate() {
    super.onCreate()

    TriggerRegistry.init(this)  // must be first

    BatteryTriggerManager.registerAll(this)
    ChargerTriggerManager.registerAll(this)
    WiFiTriggerManager.registerAll(this)
    BluetoothTriggerManager.registerAll(this)
    AirplaneModeTriggerManager.registerAll(this)
    DndTriggerManager.registerAll(this)
    GeofenceManager.registerAll(this)      // P1: geofence
    SmsTriggerManager.registerAll(this)    // P1: SMS
    AlarmTracker.init(this)                 // P1: alarm
}
```

### 2. Pattern: Per-Workflow Registration on Save

```kotlin
// WorkflowGenerationViewModel.saveWorkflow()
when (trigger) {
    is TriggerConfig.Geofence -> GeofenceManager.registerWorkflow(ctx, name, trigger)
    is TriggerConfig.SmsReceived -> SmsTriggerManager.registerWorkflow(ctx, name, trigger)
    is TriggerConfig.AlarmStopped -> AlarmTracker.schedule(name, trigger)
    // P0 managers...
}
```

### 3. Pattern: Confirmation Required (already implemented)

Background-triggered workflows that hit a confirmation step surface a notification — same as Battery/Charger:
- `TriggerRegistry.fire()` catches `ConfirmationRequired`
- Posts notification with deep-link to `MainActivity`
- `MainActivity.onNewIntent()` routes to confirmation screen via `TriggerRegistry.confirmAndResume()`

This is already implemented and works for all P0 triggers. It will work for P1 without changes.

### 4. Manifest Receivers vs Programmatic

| Trigger | Mechanism | Manifest OK? |
|---------|-----------|-------------|
| Geofence | `PendingIntent` → `BroadcastReceiver` | Declared in manifest (required by `PendingIntent`) |
| SMS | `BroadcastReceiver` | Declared in manifest, BUT limited broadcast on Android 14+ |
| Alarm (own) | `PendingIntent` actions from notification | Not manifest-based — launched from notification actions |

---

## Build & Test Order

1. **Geofence** — most self-contained, no external dependencies
2. **SMS** — simple broadcast receiver, quick to add
3. **Alarm** — most complex, depends on existing `TimeTriggerScheduler` pattern

### Test Scenarios

**Geofence:**
- [ ] App grants location permission → geofence registered
- [ ] Walk/driving simulation crosses geofence boundary → workflow fires
- [ ] Multiple geofences → correct workflow fires per zone
- [ ] Dwell trigger fires after configured delay
- [ ] Android 12+ background location permission prompts correctly

**SMS:**
- [ ] Receive SMS with IrisApp as default SMS app (Android < 14) → workflow fires
- [ ] Sender pattern filter matches correctly
- [ ] Body pattern filter matches correctly
- [ ] Multiple matching workflows all fire
- [ ] Android 14+ non-default SMS app → trigger silently does not fire (expected)

**Alarm:**
- [ ] Own alarm fires → notification shown with Dismiss/Snooze actions
- [ ] Tap Dismiss → workflow fires with `SnoozeTriggerBehavior.DISMISSED`
- [ ] Tap Snooze → alarm re-fires after snooze interval, workflow fires with `SnoozeTriggerBehavior.SNOOZED`
- [ ] `AlarmTriggerType.OWN_ONLY` workflows do NOT fire for system alarms
- [ ] `AlarmTriggerType.ANY` fires on `ACTION_NEXT_ALARM_CLOCK_CHANGED` (best effort, with log warning)

---

### Open Questions

~~1. **Geofence background location:** Android 10+ requires `ACCESS_BACKGROUND_LOCATION` which has a different runtime permission flow. Should we use a foreground service for geofence monitoring to avoid the background location permission entirely?~~

**Answer: Guide the user.** Show a clear screen directing them to Settings → Location → Permission → "Allow all the time" when the permission is denied. Use the standard Android permission flow; do not use a foreground service.

~~2. **SMS on Android 14+:** Should we even implement the SMS trigger given it's unreliable on Android 14+? Alternatively, could we implement it as a `NotificationListenerService` fallback that watches the Messages app notification?~~

**Answer: Yes, NotificationListenerService as fallback.** `NotificationListenerService` works differently — it receives notifications from apps the user grants access to, independent of being the default SMS app. On Android 14+, `SMS_RECEIVED_ACTION` is limited to the default SMS app, but `NotificationListenerService` can still detect SMS app notifications if the user enables Notification Access. The catch: the notification text may be redacted on Android 12+ depending on the SMS app. Strategy: implement both — primary `SMS_RECEIVED_ACTION` (for Android < 14), fallback `NotificationListenerService` (for Android 14+ or as alternative).

~~3. **Alarm snooze interval:** Where does the snooze duration come from — a user-configured setting in IrisApp, or do we query the system's default snooze length?~~

**Answer: User-configured in app settings.** Add a `SnoozeDurationMinutes` setting in `AppSettings` (or `SharedPreferences`), defaulting to 10 minutes. User can change it in workflow settings or global app settings.

---
