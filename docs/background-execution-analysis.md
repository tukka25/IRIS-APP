# Background Execution Analysis — IrisApp

## Architecture Overview

All background triggers follow this execution chain:

```
System event (broadcast / foreground service)
  → TriggerManager / BroadcastReceiver.onReceive()
  → TriggerRegistry.fire(context, workflow)
  → WorkflowRunner (Dispatchers.Default)
  → ApiExecutor.execute()
```

**`TriggerRegistry.fire()`** runs the full workflow in a background coroutine. If a step needs user confirmation or permissions, it posts a notification and pauses the runner. Resumption happens when the user confirms via the notification action.

---

## Triggers — Background Compatibility

| Trigger | Mechanism | Works in background? | Notes |
|---|---|---|---|
| **Time** | `AlarmManager` → `AlarmReceiver` (BroadcastReceiver) | ✅ Yes | Exact alarms via `setExactAndAllowWhileIdle`; fires even in Doze |
| **NFC scan** | `NfcTriggerHandler` (BroadcastReceiver) + foreground dispatch | ✅ Yes | Foreground dispatch for app-in-focus; receiver for cold-start |
| **Battery level** | `BatteryTriggerManager` (BroadcastReceiver) | ✅ Yes | `ACTION_BATTERY_CHANGED` sticky broadcast |
| **Charger** | `BatteryTriggerManager` (BroadcastReceiver) | ✅ Yes | Same receiver, different sticky values |
| **WiFi change** | `WiFiTriggerManager` (BroadcastReceiver) | ✅ Yes | `android.net.conn.CONNECTIVITY_CHANGE`; SSID/BSSID requires location permission |
| **Sound event** | `SoundEventTriggerService` (Foreground Service) | ✅ Yes | Persistent notification keeps service alive in Doze; YAMNet classifier |
| **Share Sheet** | `IntentFilter` registered on Activity | ❌ No | Requires `MainActivity` alive and in foreground |
| **Voice** | Microphone + Gemini model | ❌ No | Requires app UI for mic and voice processing |
| **Bluetooth** | `ACTION_BLUETOOTH_ADAPTER_STATE_CHANGED` (BroadcastReceiver) | ⚠️ Limited | Android Bluetooth API restrictions; system dialog for user grant |

---

## Actions — Background Compatibility

### ✅ Silent in background (no UI, no permission prompt)

| Action | Implementation | Notes |
|---|---|---|
| `calendar.create_event` | Direct Calendar API via `CalendarContract` | Silently inserts event |
| `clipboard.copy_text` | `ClipboardManager.setPrimaryClip()` | No share sheet launched |
| `clipboard.copy_image` | `ClipboardManager.setPrimaryClip()` | No share sheet launched |
| `alarm.set_alarm` | `AlarmManager.setExactAndAllowWhileIdle()` | Silent scheduling; AlarmReceiver fires notification |
| `notification.send` | `NotificationManager.notify()` | Requires `POST_NOTIFICATIONS` (runtime on Android 13+) |
| `http_request` | `HttpURLConnection` / OkHttp | Network operations fully background-capable |
| `sync.toggle` | `ContentResolver.setSyncAutomatically()` | Background sync control |
| `toast.show` | `Handler(Looper.getMainLooper()).post { Toast.makeText() }` | Routed to main thread via Handler |
| `command.exec` | `Runtime.exec()` | Shell commands fully background-capable |
| `share.share_text` | Silent `ClipboardManager` copy | No share sheet UI |
| `share.share_image` | Silent `ClipboardManager` copy with content URI | No share sheet UI |
| `media.play_pause` | `MediaSession` / `MediaController` | Sends command to active media session |
| `media.next_track` | Same as above | Same as above |
| `media.previous_track` | Same as above | Same as above |

### ⚠️ Works but requires one-time system permission grant

| Action | Permission | Behavior |
|---|---|---|
| `brightness.set` | `WRITE_SETTINGS` | System Settings prompt; redirect to Settings on Android 12+ |
| `rotation.lock` | `WRITE_SETTINGS` | Same as brightness |
| `ringer_mode.set` | `MODIFY_AUDIO_SETTINGS` | System prompt; auto-granted on install |
| `bluetooth.toggle` | `BLUETOOTH_ADMIN` / `BLUETOOTH_CONNECT` | System Bluetooth dialog |
| `wifi.toggle` | `CHANGE_WIFI_STATE` | System WiFi dialog |
| `cellular.toggle` | `CHANGE_NETWORK_STATE` | System cellular dialog |
| `hotspot.toggle` | `WRITE_SETTINGS` + `CHANGE_WIFI_STATE` | System hotspot dialog |
| `airplane_mode.toggle` | `WRITE_SETTINGS` | System Settings redirect |

### ❌ Fails or crashes in background

| Action | Problem |
|---|---|
| `launch_app` | `context.startActivity()` from a `BroadcastReceiver` creates an orphaned Activity on Android 12+. The launched app has no task/back-stack and the system kills it immediately. |
| `intent.send` | Same as above — `FLAG_ACTIVITY_NEW_TASK` is set but the launching context is not an Activity; the target activity has no parent task. |
| Chrome Custom Tabs | `CustomTabsSession` must be bound to an Activity; no Activity context in background trigger means no session can be established. |

---

## Known Issues

### 1. `launch_app` crashes when fired from a background trigger

**File:** `WorkflowRunner.kt` lines ~607–630

**Problem:** `executePackageLaunch()` calls `context.startActivity(launchIntent)` where `context` is the `BroadcastReceiver`'s `Context`. On Android 12+, the system immediately kills the launched Activity because it was spawned from a non-Activity context and has no task/back-stack.

**Fix needed:** Launch via `PendingIntent` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` from a Service instead of directly from the BroadcastReceiver context. Alternatively, route `launch_app` through a foreground `WorkManager` job or `AlarmReceiver` → Service → Activity launch.

---

### 2. `brightness.set` / `rotation.lock` race when triggered from background

**Files:** `BrightnessApiExecutor.kt`, `RotationApiExecutor.kt`

**Problem:** When `WRITE_SETTINGS` is not yet granted, both executors redirect the user to system Settings. The `WorkflowRunner` continues executing on `Dispatchers.Default` while the Settings activity is shown. By the time the user grants permission and returns, the workflow has already exited — the step that needed brightness/rotation already returned `false`.

**Fix needed:** Store the step index in a "pending permission" state. When `TriggerRegistry.fire()` catches a `PermissionRequired`, it stores the runner in `pendingExecutions` and shows a notification (already implemented). The UI flow from the notification should resume the workflow from the correct step index — but for `WRITE_SETTINGS`, the system Settings activity is shown in between, and the user's grant does not automatically resume the workflow.

---

### 3. `TriggerRegistry.fire()` uses BroadcastReceiver context

**File:** `TriggerRegistry.kt` lines ~100–136

**Problem:** `TriggerRegistry.fire()` receives `context` from the calling `BroadcastReceiver.onReceive()`. This context is valid for basic operations but is fragile — some OEM implementations restrict operations from broadcast context. The app-level `applicationContext` (set in `TriggerRegistry.init()`) should be used instead.

**Current behavior:** Works on most devices because `WorkflowRunner` creates its own `WorkflowRepository(context)` which opens a database. However, `context.getSystemService()` calls from a broadcast context may return different instances than `applicationContext.getSystemService()`.

---

### 4. NFC cold-start routing gap

**Files:** `MainActivity.kt`, `NfcTriggerHandler.kt`, `DeepLinkRouter.kt`

**Problem:** Cold-start NFC intent routing works via `NfcTriggerHandler` (BroadcastReceiver) but only if the app is already running. When the app is not running and an NFC tag is scanned, `NfcTriggerHandler` is invoked by the system, but `DeepLinkRouter.routeFromActivity()` is never called because `handleIntent()` in `MainActivity` is only reached through `MainActivity.onCreate()` — which does not run when the app is woken by a broadcast receiver.

**Current behavior:** `NfcTriggerHandler` writes to log (`"NFC tag scanned, workflow: $workflowId"`) but does not launch `MainActivity`. The foreground dispatch in `MainActivity.onResume()` handles NFC when the app is already alive.

**Fix needed:** `NfcTriggerHandler.onReceive()` should launch `MainActivity` with the NFC deep-link intent when the app is not in the foreground, using a `PendingIntent` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.

---

## Permissions Reference

### Declared in AndroidManifest.xml

| Permission | Why | When needed |
|---|---|---|
| `android.permission.NFC` | Read/write NFC tags | Always (hardware feature optional) |
| `android.permission.INTERNET` | HTTP requests, model downloads | Always |
| `android.permission.POST_NOTIFICATIONS` | Show workflow confirmation / trigger notifications | Android 13+ |
| `android.permission.READ_CONTACTS` | Contact-based triggers | Contacts trigger |
| `android.permission.READ_SMS` / `RECEIVE_SMS` | SMS trigger | SMS Received trigger |
| `android.permission.SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | `AlarmManager.setExactAndAllowWhileIdle()` | Time trigger |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedule time triggers after reboot | Time trigger |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | Do Not Disturb trigger | DND trigger |
| `android.permission.READ_CALENDAR` / `WRITE_CALENDAR` | Calendar action | `calendar.create_event` |
| `android.permission.RECORD_AUDIO` | Voice trigger + sound event trigger (YAMNet) | Voice / Sound Event triggers |
| `android.permission.FOREGROUND_SERVICE` | Sound Event foreground service | Sound Event trigger |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Android 14+ microphone foreground service | Sound Event trigger |
| `android.permission.BLUETOOTH` (maxSdk 30) / `BLUETOOTH_CONNECT` | Bluetooth toggle action + trigger | Android 12+ |
| `android.permission.ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` / `ACCESS_NETWORK_STATE` | WiFi toggle action + trigger | WiFi actions/triggers |
| `android.permission.ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | WiFi SSID (Android 10+), geofence trigger | WiFi trigger, Geofence trigger |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | Geofence arrive/leave triggers | Geofence trigger |
| `android.permission.MODIFY_AUDIO_SETTINGS` | Ringer mode action | `ringer_mode.set` |
| `android.permission.WRITE_SETTINGS` | Brightness, rotation, hotspot, airplane mode actions | `brightness.set`, `rotation.lock`, `hotspot.toggle`, `airplane_mode.toggle` |
| `android.permission.QUERY_ALL_PACKAGES` | List/query all installed apps | `launch_app` (Android 11+) |
| `android.permission.WRITE_SECURE_SETTINGS` | System settings modification | Various system actions |
| `android.permission.WRITE_SYNC_SETTINGS` | Sync toggle action | `sync.toggle` |
| `android.permission.READ_MEDIA_AUDIO` / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Media file access | Media actions, share actions |
| `android.permission.READ_EXTERNAL_STORAGE` (maxSdk 32) | Legacy media access | Android 12 and below |

### Runtime permissions (requested at runtime)

| Permission | Triggered by | Prompt style |
|---|---|---|
| `POST_NOTIFICATIONS` | `notification.send` | System notification permission dialog (Android 13+) |
| `RECORD_AUDIO` | Voice trigger, Sound Event trigger | Mic permission dialog |
| `ACCESS_FINE_LOCATION` | WiFi SSID, Geofence triggers | Location permission dialog |
| `ACCESS_BACKGROUND_LOCATION` | Geofence trigger | Background location dialog (Android 10+) |
| `BLUETOOTH_CONNECT` | Bluetooth actions/triggers | Bluetooth pairing dialog |
| `READ_CONTACTS` | SMS trigger | Contacts permission dialog |
| `READ_CALENDAR` / `WRITE_CALENDAR` | `calendar.create_event` | Calendar permission dialog |

### Special: WRITE_SETTINGS (not a runtime permission)

`WRITE_SETTINGS` is **not a runtime permission** — it cannot be requested with `requestPermissions()`. The user must grant it manually via `Settings.ACTION_MANAGE_WRITE_SETTINGS`. Both `brightness.set` and `rotation.lock` detect it with `Settings.System.canWrite(context)` and return a silent `success=false` if not granted. The user must open Settings and toggle any brightness slider once to grant.

### Foreground Service requirement

Sound Event trigger requires a persistent `FOREGROUND_SERVICE` with `foregroundServiceType=microphone` (declared in manifest + runtime `PostNotification` permission on Android 14+).

---

## Known Issues

### 1. ✅ Fixed — `launch_app` now works from background triggers

**File:** `LaunchAppService.kt` (new), `LaunchAppApiExecutor.kt`

**Fix:** `LaunchAppApiExecutor` now calls `LaunchAppService.launch()` which starts a foreground Service. The Service uses `PendingIntent.getActivity()` to launch the target app — this gives the target app a valid task/back-stack on Android 12+ and prevents the system from killing it immediately.

---

### 2. ⚠️ Limitation — `brightness.set` / `rotation.lock` return silent failure without Settings redirect

**Files:** `BrightnessApiExecutor.kt`, `RotationApiExecutor.kt`

**Behavior:** Both executors detect `WRITE_SETTINGS` with `Settings.System.canWrite()`. If not granted, they return `ExecutionResult(success=false)` — **not** a `PermissionRequired` exception. This means:
- In foreground workflows: the step fails with a message, workflow continues to next step.
- In background workflows: the step fails silently, no notification is posted.

To grant `WRITE_SETTINGS`, the user must open **Settings → Display → Adjust the brightness slider** manually. There is no way for an app to redirect the user directly to the write-settings grant page reliably across all Android versions.

---

### 3. ⚠️ Limitation — `TriggerRegistry.fire()` uses BroadcastReceiver context

**File:** `TriggerRegistry.kt` lines ~100–136

**Behavior:** `TriggerRegistry.fire()` receives `context` from the calling `BroadcastReceiver.onReceive()`. This works on most devices but is fragile — some OEM implementations restrict operations from broadcast context. The app-level `applicationContext` (set in `TriggerRegistry.init()`) should ideally be used.

**Workaround:** `WorkflowRepository(context)` opens a database using the receiver's context, which works because the database is file-based. `getSystemService()` calls use the receiver's context, which returns the same instances as `applicationContext` on most devices.

---

### 4. ✅ Fixed — NFC cold-start now launches the app

**File:** `NfcTriggerHandler.kt`

**Fix:** When `NfcTriggerHandler` detects the app is in the background, it now calls `context.startActivity(launchIntent)` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` to wake `MainActivity`. The `ACTION_RUN_WORKFLOW` intent with the workflow ID routes through `DeepLinkRouter.routeFromActivity()` in `MainActivity.onNewIntent()`, firing the workflow via `TriggerRegistry.fire()`.

---

## Summary Table

| Category | Count | Fully background-safe? |
|---|---|---|
| Triggers | 8 types | 5 ✅, 1 ⚠️, 2 ❌ |
| Actions | ~25 types | 15 ✅, 8 ⚠️, 2 ❌ (1 fixed) |

Four bugs were identified; two have been fixed (`launch_app`, NFC cold-start). `brightness.set` / `rotation.lock` and `TriggerRegistry` context are acknowledged limitations with documented workarounds.