# P2 & P3 Trigger Implementation Plan (Revised)

**Date:** 2026-05-12
**Branch:** `22-trigger-feasibility-implementation`
**Issue:** #22

## How This Doc Is Organized

This revises the initial `P2_P3_TRIGGERS_PLAN.md` based on actual codebase analysis. Every section now references the actual patterns in use:
- `object` singleton managers in `platform/<category>/`
- `TriggerConfig` sealed class (data class per trigger, added to `WorkflowModels.kt`)
- `TriggerCatalog` (single source of truth for trigger metadata + `SetupState`)
- `WorkflowGenerationViewModel` `when` block + `registerWorkflow()/unregisterWorkflow()`
- `ManualWorkflowEditorScreen` — `TRIGGER_TYPES` list + per-trigger state + `buildTrigger()` + `triggerConfigMatches()`
- `WorkflowValidator` and `MainActivity` `when` branches
- `TriggerRegistry.fire(context, workflow)` for firing

---

## Integration Pattern Reference

All triggers implement the same pattern. Each new trigger needs:

```
WorkflowModels.kt          → TriggerConfig.<NewTrigger>(params)
TriggerCatalog.kt          → TriggerInfo("type", label, setupState)
WorkflowGenerationViewModel.kt → when branch, registerWorkflow() / unregisterWorkflow()
ManualWorkflowEditorScreen.kt → TRIGGER_TYPES entry, state vars, card UI, buildTrigger(), triggerConfigMatches()
WorkflowValidator.kt       → when branch → typeId
MainActivity.kt            → when branch → triggerLabel
AndroidManifest.xml        → receiver/service declaration + permissions
platform/<category>/*      → Manager (object singleton) + optional BroadcastReceiver
```

---

## P2.1 — Sleep / Bedtime Proxy

### Research Summary

DND state is already captured via `DndTriggerManager` (uses `ACTION_INTERRUPTION_FILTER_CHANGED` broadcast). Sleep proxy is not a separate Android system — it is a **DND trigger with time-window constraints plus optional charger-state filter**. No new Android API needed; just logic on top of existing DND receiver.

### Architecture Decision

**Do NOT create a separate service.** Sleep proxy is a condition layer on top of DND:
- `DndTriggerManager` already listens for DND state changes
- Extend it with a `SleepTriggerConditions` config object
- When DND fires, check: is current time within [startTime, endTime]? Was charger just disconnected (if configured)?
- If all conditions met → fire sleep workflow

This avoids a second `BroadcastReceiver` doing the same DND listen. The tradeoff is that a sleep workflow won't fire if DND is already active before the time window starts — but that's acceptable (the user is already in DND).

### Data Model

```kotlin
@Serializable
data class TriggerConfig.SleepProxy(
    val startTimeHour: Int = 22,         // e.g. 22 (10 PM)
    val startTimeMinute: Int = 0,       // e.g. 0
    val endTimeHour: Int = 7,           // e.g. 7 (7 AM)
    val endTimeMinute: Int = 0,          // e.g. 0
    val requireChargerDisconnected: Boolean = true,  // don't fire while charging
    val requireDndActive: Boolean = true  // only fire when DND is ON
) : TriggerConfig()
```

**Key design decision:** `requireDndActive = true` is the correct default. Sleep proxy should only fire when DND is actually active. If user wants "fire when I go to sleep regardless of DND", the time window alone suffices.

### TriggerCatalog Entry

```kotlin
TriggerInfo(
    type = "sleep_proxy",
    label = "Sleep / Bedtime",
    description = "Run when Do Not Disturb activates within your configured bedtime window. Optional: only fire when charger is disconnected.",
    setupState = SetupState.NeedsSetup  // requires ACCESS_NOTIFICATION_POLICY
)
```

### Files to Create

- `platform/trigger/SleepTriggerManager.kt` — extends DND logic; stores `SleepProxy` configs in `activeSleepWorkflows`; checks time window + charger state; fires when DND matches

### Files to Modify

- `domain/model/WorkflowModels.kt` — add `TriggerConfig.SleepProxy`
- `domain/triggers/TriggerCatalog.kt` — add `sleep_proxy` TriggerInfo
- `ui/home/WorkflowGenerationViewModel.kt` — `is TriggerConfig.SleepProxy` branch calling `SleepTriggerManager`
- `ui/home/ManualWorkflowEditorScreen.kt` — add "Sleep" to `TRIGGER_TYPES`; state vars for time window + booleans; Sleep card UI with time pickers + checkboxes
- `domain/safety/WorkflowValidator.kt` — `is TriggerConfig.SleepProxy → "sleep_proxy"`
- `ui/MainActivity.kt` — `is TriggerConfig.SleepProxy` branch
- `AndroidManifest.xml` — `ACCESS_NOTIFICATION_POLICY` already present for DND

### Trigger Flow

```
DndTriggerManager.dndReceiver fires (DND state changes)
  → for each SleepProxy workflow
      → is current time within [startTime, endTime]? NO → skip
      → requireChargerDisconnected? YES → is device charging? YES → skip
      → requireDndActive? YES → is DND currently on? NO → skip
      → all checks pass → TriggerRegistry.fire(context, workflow)
```

---

## P2.2 — Messaging NotificationListener

### Research Summary

`SmsNotificationListener` (NotificationListenerService) is already implemented for Android 14+ SMS fallback. It monitors `StatusBarNotification` from known SMS apps, extracts sender + body, matches against `SmsReceived` patterns, and fires via `TriggerRegistry.fire()`. This service can be generalized to also handle WhatsApp, Telegram, Signal, and other messaging apps without creating new infrastructure.

### Architecture Decision

**Extend the existing `SmsNotificationListener` into a general `MessagingNotificationListener`.** The current service filters on `SMS_APP_PACKAGES` set. Instead, generalize it to:

1. Maintain a `WHITELISTED_MESSAGING_PACKAGES` set (WhatsApp, Telegram, Signal, etc.)
2. Accept a `TriggerConfig.MessagingNotification` with `appPackagePatterns`, `senderPattern`, `bodyPattern`
3. When a notification arrives: check if package is in whitelist, then match sender/body regex

**Why not a separate service?** `NotificationListenerService` is a system-bound service — there can only be one active at a time. Creating a second one would conflict with the existing `SmsNotificationListener`. Extending the existing service to handle all messaging apps is the correct approach.

**Rename is NOT required** — the class name `SmsNotificationListener` is fine for backward compatibility. The internal logic becomes more general.

### Data Model

```kotlin
@Serializable
data class TriggerConfig.MessagingNotification(
    val appPackagePatterns: List<String> = emptyList(),  // e.g. ["com.whatsapp", "org.telegram.messenger"]
    val senderPattern: String? = null,                   // regex on sender name, null = any
    val bodyPattern: String? = null,                     // regex on body text, null = any
    val triggerOnDismiss: Boolean = false                // true = fire when notification is removed (read)
) : TriggerConfig()
```

**Note:** `triggerOnDismiss` requires tracking posted notifications and firing on `onNotificationRemoved`. This is partially implemented already (the method exists but doesn't fire). Full implementation is low effort.

### TriggerCatalog Entry

```kotlin
TriggerInfo(
    type = "messaging_notification",
    label = "Messaging notification",
    description = "Run when a notification arrives from WhatsApp, Telegram, Signal, or other messaging apps. Match by sender or message content.",
    setupState = SetupState.NeedsSetup  // requires notification access in Settings
)
```

### Files to Create

- None — extends existing service

### Files to Modify

- `domain/model/WorkflowModels.kt` — add `TriggerConfig.MessagingNotification`
- `domain/triggers/TriggerCatalog.kt` — add `messaging_notification` TriggerInfo
- `platform/sms/SmsNotificationListener.kt` — generalize to accept `MessagingNotification` configs; match on package patterns + sender/body regex; handle `triggerOnDismiss`
- `ui/home/WorkflowGenerationViewModel.kt` — `is TriggerConfig.MessagingNotification` branch
- `ui/home/ManualWorkflowEditorScreen.kt` — add "Messaging" to `TRIGGER_TYPES`; state vars for app packages + sender/body patterns + dismiss flag; Messaging card UI with app checkboxes and text fields
- `domain/safety/WorkflowValidator.kt` — `is TriggerConfig.MessagingNotification → "messaging_notification"`
- `ui/MainActivity.kt` — `is TriggerConfig.MessagingNotification` branch
- `AndroidManifest.xml` — no changes (service already declared)

**Do NOT create** a second `NotificationListenerService`. Extend `SmsNotificationListener`.

---

## P2.3 — Driving Mode (Android Auto / Car Mode)

### Research Summary

Car mode broadcast (`ACTION_ENTER_CAR_MODE`) is OEM-dependent and unreliable. `CarConnection` API from `androidx.car.app` requires car app setup. The most reliable driving mode proxy is **Bluetooth connection to a car audio/hands-free device**. `BluetoothTriggerManager` already listens for `ACL_CONNECTED/DISCONNECTED`. Extending it with a device-address filter and a new trigger type is the cleanest path.

### Architecture Decision

**Extend `BluetoothTriggerManager` with a `DrivingMode` condition layer.** Do NOT create a separate manager. The Bluetooth manager already handles `ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED`. Add a `DrivingModeTriggerConfig` that uses `deviceAddress` to identify a car, then:

- If `DrivingMode` trigger has `deviceAddress` set → Bluetooth event fires only if device matches
- If `DrivingMode` trigger has `deviceAddress == null` → fires on any Bluetooth connect (with warning in UI)

A separate `CarModeBroadcastReceiver` can optionally listen for `ACTION_ENTER_CAR_MODE` as a secondary signal, but it should not be the primary path (OEM-dependent).

### Data Model

```kotlin
@Serializable
data class TriggerConfig.DrivingMode(
    val bluetoothDeviceAddress: String? = null,  // null = any Bluetooth device
    val triggerOnConnect: Boolean = true,        // true = connect triggers, false = disconnect triggers
    val label: String? = null
) : TriggerConfig()
```

### TriggerCatalog Entry

```kotlin
TriggerInfo(
    type = "driving_mode",
    label = "Driving mode",
    description = "Run when a Bluetooth device connects or disconnects (e.g., car audio). Optionally filter by a specific car device.",
    setupState = SetupState.NeedsSetup  // requires BLUETOOTH_CONNECT permission
)
```

### Files to Create

- None — extends existing manager

### Files to Modify

- `domain/model/WorkflowModels.kt` — add `TriggerConfig.DrivingMode`
- `domain/triggers/TriggerCatalog.kt` — add `driving_mode` TriggerInfo
- `ui/home/WorkflowGenerationViewModel.kt` — `is TriggerConfig.DrivingMode` branch (reuses `BluetoothTriggerManager.registerWorkflow()`)
- `ui/home/ManualWorkflowEditorScreen.kt` — add "Driving Mode" to `TRIGGER_TYPES`; state for device address + connect/disconnect toggle; DrivingMode card with device picker
- `domain/safety/WorkflowValidator.kt` — `is TriggerConfig.DrivingMode → "driving_mode"`
- `ui/MainActivity.kt` — `is TriggerConfig.DrivingMode` branch
- `AndroidManifest.xml` — `BLUETOOTH_CONNECT` already declared for Bluetooth trigger

**Key insight:** No new manager needed. `BluetoothTriggerManager.fireWorkflows()` already has device address filtering. `DrivingMode` just adds a new `TriggerConfig` variant that gets checked in the same `fireWorkflows()` logic. The `when` in `WorkflowGenerationViewModel` dispatches to `registerWorkflow()`, which stores the `DrivingMode` config alongside `Bluetooth` configs in `activeWorkflows` (or we use a separate `activeDrivingWorkflows` map if we want strict separation).

---

## P3.1 — App Opened / App Closed

### Research Summary

`UsageStatsManager` is broken on Android 14+. `AccessibilityService` with `TYPE_WINDOW_STATE_CHANGED` is the only reliable path but requires user to manually enable and carries Play Store rejection risk. This trigger should be implemented but documented as requiring "Special access" setup.

### Architecture Decision

**Create `AppTriggerAccessibilityService`** (new AccessibilityService). This is the correct approach because no other method works reliably on Android 14+. The Play Store risk is real but manageable if the app's accessibility use case is clearly explained (automation, not accessibility circumvention).

### Data Model

```kotlin
@Serializable
data class TriggerConfig.AppOpened(
    val appPackagePatterns: List<String> = emptyList(),  // e.g. ["com.instagram.android"]
    val triggerOnOpen: Boolean = true,
    val triggerOnClose: Boolean = false
) : TriggerConfig()
```

### TriggerCatalog Entry

```kotlin
TriggerInfo(
    type = "app_opened",
    label = "App opened / closed",
    description = "Run when an app opens or closes. Requires enabling Special Accessibility access in Settings. Note: Google Play may require justification for AccessibilityService usage.",
    setupState = SetupState.NeedsSetup
)
```

### Files to Create

- `platform/trigger/AppTriggerAccessibilityService.kt` — `AccessibilityService`, listens for `TYPE_WINDOW_STATE_CHANGED`, fires matching workflows
- `res/xml/app_trigger_accessibility_service_config.xml` — accessibility service XML config

### Files to Modify

- `domain/model/WorkflowModels.kt` — add `TriggerConfig.AppOpened`
- `domain/triggers/TriggerCatalog.kt` — add `app_opened` TriggerInfo
- `ui/home/WorkflowGenerationViewModel.kt` — `is TriggerConfig.AppOpened` branch (no manager registration needed — AccessibilityService is system-bound)
- `ui/home/ManualWorkflowEditorScreen.kt` — add "App Event" to `TRIGGER_TYPES`; state for app packages + open/close booleans; AppEvent card with app picker
- `domain/safety/WorkflowValidator.kt` — `is TriggerConfig.AppOpened → "app_opened"`
- `ui/MainActivity.kt` — `is TriggerConfig.AppOpened` branch
- `AndroidManifest.xml` — declare `AppTriggerAccessibilityService` with `BIND_ACCESSIBILITY_SERVICE`

**Play Store risk mitigation:** Document clearly in the UI that the AccessibilityService is used for app automation and that the user must enable it. The app description in the Play Store listing should explain this need.

---

## P3.2 — Email Received

### Research Summary

No public email API. `NotificationListenerService` can detect Gmail/Outlook notifications and extract sender/subject from notification extras, but cannot access email body. Reuses the same infrastructure as P2 Messaging.

### Architecture Decision

**Reuse `SmsNotificationListener` / `MessagingNotificationListener`** (the generalized service from P2.2) for email detection. Filter by Gmail (`com.google.android.gm`) and Outlook (`com.microsoft.office.outlook`) package names. The `MessagingNotification` trigger with `appPackagePatterns` handles this.

**No new service, no new trigger config needed** — `TriggerConfig.MessagingNotification` with `appPackagePatterns = listOf("com.google.android.gm")` covers email.

### What This Requires

- A separate UI card in `ManualWorkflowEditorScreen` labeled "Email received" with a friendly UX
- A `TriggerConfig.EmailReceived` data class could be added for UI clarity (friendly label), but internally maps to the same `MessagingNotification` pattern
- `TriggerCatalog` gets a separate `email_received` entry with a distinct type ID so the UI can show it independently

### Data Model (if using separate config)

```kotlin
@Serializable
data class TriggerConfig.EmailReceived(
    val senderPattern: String? = null,
    val subjectPattern: String? = null,
    val appPackage: String = "com.google.android.gm"  // default Gmail
) : TriggerConfig()
```

This is a separate `TriggerConfig` from `MessagingNotification` for UI clarity, but the firing logic can share the same `NotificationListenerService` filtering by package.

### TriggerCatalog Entry

```kotlin
TriggerInfo(
    type = "email_received",
    label = "Email received",
    description = "Run when a new email arrives in Gmail or Outlook. Match by sender or subject.",
    setupState = SetupState.NeedsSetup  // requires notification access
)
```

### Files to Create

- None new (reuses NotificationListenerService infrastructure)

### Files to Modify

- `domain/model/WorkflowModels.kt` — add `TriggerConfig.EmailReceived`
- `domain/triggers/TriggerCatalog.kt` — add `email_received` TriggerInfo
- `ui/home/ManualWorkflowEditorScreen.kt` — add "Email" to `TRIGGER_TYPES`; Email card UI with sender/subject regex fields
- `domain/safety/WorkflowValidator.kt` — `is TriggerConfig.EmailReceived → "email_received"`
- `ui/MainActivity.kt` — `is TriggerConfig.EmailReceived` branch

---

## P3.3 — Wallet / Payment

**Status: Unsupported**

No public Android API for detecting payment events. `Google Pay` and `Samsung Pay` use secure element isolation inaccessible to third-party apps.

### Implementation

```kotlin
TriggerInfo(
    type = "wallet_payment",
    label = "Wallet / Payment",
    description = "Detecting payment events is not possible with public Android APIs. Google Pay and Samsung Pay use secure isolation that third-party apps cannot access.",
    setupState = SetupState.Unsupported
)
```

No code implementation. Just the catalog entry.

---

## P3.4 — Sound Recognition

**Status: Unsupported**

`SoundTriggerDetectionService` is `@SystemApi` (hidden). No public API. Custom ML is impractical for background use (battery drain + process kill).

### Implementation

```kotlin
TriggerInfo(
    type = "sound_recognition",
    label = "Sound Recognition",
    description = "Sound event detection (baby crying, doorbell, glass breaking) is not available via public Android APIs. The SoundTrigger HAL is vendor-specific and not accessible to third-party apps.",
    setupState = SetupState.Unsupported
)
```

No code implementation. Just the catalog entry.

---

## Revised Implementation Order

| # | Trigger | Approach | Why First |
|---|---------|----------|-----------|
| 1 | Sleep Proxy | Extends `DndTriggerManager` | Low effort, leverages existing DND receiver, no new service |
| 2 | Messaging NotificationListener | Extends `SmsNotificationListener` | Low effort, generalize existing service, no conflict |
| 3 | Driving Mode | Extends `BluetoothTriggerManager` | Low effort, reuse BT manager with device-address filter |
| 4 | Email Received | Reuses NotificationListener | Very low effort, just new TriggerConfig + UI |
| 5 | App Opened/Closed | New AccessibilityService | Medium effort, requires new service + config XML |
| 6 | Wallet / Payment | Unsupported catalog entry | Zero effort, just a TriggerInfo |
| 7 | Sound Recognition | Unsupported catalog entry | Zero effort, just a TriggerInfo |

---

## Shared Concerns

### NotificationListenerService — One Service Rule

Android allows only one active `NotificationListenerService` at a time. `SmsNotificationListener` is already declared. The generalization in step 2 merges all notification-triggering logic into that single service. If the user has multiple trigger types using notification listening (SMS + Messaging + Email), they all share the same service. This is fine — the service checks package names and patterns to route to the correct trigger.

### AccessibilityService — Play Store Review

App Opened/Closed uses AccessibilityService. Google Play rejects apps that use AccessibilityService for non-accessibility purposes. Mitigation:
- The Play Store listing must clearly explain why accessibility access is needed
- The in-app setup screen must explain what data is collected and why
- The service only reads `packageName` from `TYPE_WINDOW_STATE_CHANGED` events — no keystroke logging or content extraction
- Alternative: offer a note in the UI that this trigger may affect Play Store eligibility

### Foreground Service Consideration

Sleep Proxy and Driving Mode use `BroadcastReceiver` (registered dynamically). These only fire when the app process is alive. For background detection while the app is not in memory, a foreground service would be needed. This is a known limitation for MVP — the triggers fire when the app is open. Foreground service implementation can be a follow-up.

### Confirmation Gate

All P2/P3 triggers fired from background (when app not open) go through `TriggerRegistry.fire()` which already handles the `ConfirmationRequired` flow — it shows a notification and waits for user confirmation before proceeding. No new confirmation logic needed.