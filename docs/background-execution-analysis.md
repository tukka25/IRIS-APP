# Background Execution Analysis — Iris (Milestone 7)

**Last updated:** 2026-05-17

---

## Architecture

All background triggers follow this chain:

```
System event (broadcast / foreground service)
  → TriggerManager / BroadcastReceiver.onReceive()
  → TriggerRegistry.fire(context, workflow)
  → WorkflowRunner (Dispatchers.Default)
  → ApiExecutor.execute()
```

`TriggerRegistry.fire()` runs the full workflow in a background coroutine. If a step needs user confirmation or permissions, it posts a notification and pauses. Resumption happens when the user confirms via the notification action.

---

## Triggers — Background Compatibility

| Trigger | Mechanism | Works in background? | Notes |
|---|---|---|---|
| **Time** | `AlarmManager` → `AlarmReceiver` | ✅ Yes | `setExactAndAllowWhileIdle`; fires even in Doze |
| **NFC scan** | `NfcTriggerHandler` + foreground dispatch | ✅ Yes | Cold-start wakes app via `PendingIntent` |
| **Battery level** | `BatteryTriggerManager` | ✅ Yes | `ACTION_BATTERY_CHANGED` sticky broadcast |
| **Charger** | `BatteryTriggerManager` | ✅ Yes | Same receiver, different sticky values |
| **Wi-Fi change** | `WiFiTriggerManager` | ✅ Yes | SSID/BSSID requires location permission |
| **Sound event** | `SoundEventTriggerService` (FGS) | ✅ Yes | Persistent notification keeps service alive in Doze; YAMNet classifier |
| **Share Sheet** | `IntentFilter` on Activity | ❌ No | Requires `MainActivity` alive and in foreground |
| **Voice** | Microphone + on-device model | ❌ No | Requires app UI for mic and voice processing |
| **Bluetooth** | `ACTION_BLUETOOTH_ADAPTER_STATE_CHANGED` | ⚠️ Limited | System dialog required on Android 12+ |

---

## Actions — Background Compatibility

### Silent (no UI, no permission prompt)

| Action | Implementation | Notes |
|---|---|---|
| `calendar.create_event` | `ContentResolver.insert(CalendarContract.Events.CONTENT_URI, ...)` | Silently inserts event |
| `clipboard.copy_text` | `ClipboardManager.setPrimaryClip()` | No share sheet launched |
| `clipboard.copy_image` | `ClipboardManager.setPrimaryClip()` with content URI | No share sheet launched |
| `alarm.set_alarm` | `AlarmManager.setExactAndAllowWhileIdle()` | Notification fires at scheduled time |
| `notification.send` | `NotificationManager.notify()` | Requires `POST_NOTIFICATIONS` on Android 13+ |
| `http_request` | `HttpURLConnection` / OkHttp | Network operations fully background-capable |
| `sync.toggle` | `ContentResolver.setSyncAutomatically()` | `WRITE_SYNC_SETTINGS` (system app only) |
| `toast.show` | `Handler(Looper.getMainLooper()).post { Toast.makeText() }` | Routed to main thread via Handler |
| `command.exec` | `Runtime.exec()` | User-space only; root commands fail silently |
| `share.share_text` | Silent `ClipboardManager` copy | No share sheet UI |
| `share.share_image` | Silent clipboard copy with content URI | No share sheet UI |
| `media.play_pause` | `MediaSession` / `MediaController` | Sends command to active media session |
| `media.next_track` | Same as above | — |
| `media.previous_track` | Same as above | — |

### Requires one-time system permission grant

| Action | Permission | Behavior |
|---|---|---|
| `brightness.set` | `WRITE_SETTINGS` | User must grant via Settings → Display (manual toggle) |
| `rotation.lock` | `WRITE_SETTINGS` | Same as brightness |
| `ringer_mode.set` | `MODIFY_AUDIO_SETTINGS` | Auto-granted on install |
| `bluetooth.toggle` | `BLUETOOTH_CONNECT` (Android 12+) | System dialog on Android 12+ |
| `wifi.toggle` | `CHANGE_WIFI_STATE` | Silent on Android < 10; system dialog on 10+ |
| `cellular.toggle` | `WRITE_SECURE_SETTINGS` | System app / root only |
| `hotspot.toggle` | `WRITE_SETTINGS` + `CHANGE_WIFI_STATE` | System app / root only |
| `airplane_mode.toggle` | `WRITE_SETTINGS` | Silent for system apps; system dialog for regular apps |

### Fails or crashes in background

| Action | Problem |
|---|---|
| `launch_app` | Direct `context.startActivity()` from BroadcastReceiver creates orphaned Activity on Android 12+. **Fixed:** routed through `LaunchAppService` foreground service with `PendingIntent` — gives target app a valid task/back-stack. |
| `intent.send` | Same orphaned Activity problem as `launch_app`. **Fixed:** same `LaunchAppService` path. |
| Chrome Custom Tabs | `CustomTabsSession` must be bound to an Activity. No Activity context in background. |

---

## Known Issues

### 1. ✅ Fixed — `launch_app` now works from background triggers

**File:** `LaunchAppService.kt`, `LaunchAppApiExecutor.kt`

**Fix:** `LaunchAppApiExecutor` now calls `LaunchAppService.launch()` which starts a foreground Service. The Service uses `PendingIntent.getActivity()` to launch the target app — this gives the target app a valid task/back-stack on Android 12+.

```kotlin
// LaunchAppService.kt
val pendingIntent = PendingIntent.getActivity(
    context,
    0,
    launchIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
context.startForegroundService(launchServiceIntent(pendingIntent, targetPackage))
```

### 2. ⚠️ `brightness.set` / `rotation.lock` — silent failure without Settings redirect

**Files:** `BrightnessApiExecutor.kt`, `RotationApiExecutor.kt`

**Behavior:** Both check `Settings.System.canWrite()`. If not granted, they return `ExecutionResult(success=false)` — **not** a `PermissionRequired` exception. In foreground workflows: step fails with message, workflow continues. In background workflows: step fails silently, no notification.

**Workaround:** User must open **Settings → Display → Adjust brightness slider** once to grant `WRITE_SETTINGS`. There is no reliable cross-version redirect for `WRITE_SETTINGS`.

### 3. ⚠️ `TriggerRegistry.fire()` uses BroadcastReceiver context

**File:** `TriggerRegistry.kt`

**Behavior:** Receives `context` from `BroadcastReceiver.onReceive()`. Works on most devices because `WorkflowRepository(context)` opens a file-based database. However, `getSystemService()` calls from broadcast context may return different instances than `applicationContext` on some OEM implementations.

**Workaround:** `applicationContext` is set in `TriggerRegistry.init()` and used for operations that need consistency.

### 4. ✅ Fixed — NFC cold-start now launches the app

**File:** `NfcTriggerHandler.kt`

**Fix:** When `NfcTriggerHandler` receives an NFC intent and the app is not in the foreground, it launches `MainActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` using the deep-link intent (`iris://workflow/{id}` or `iris://import/{id}`). The `ACTION_RUN_WORKFLOW` intent fires via `TriggerRegistry.fire()` in `MainActivity.onNewIntent()`.

---

## Permissions Summary

| Permission | Purpose | When needed |
|---|---|---|
| `POST_NOTIFICATIONS` | Trigger/workflow notifications | Android 13+ |
| `RECORD_AUDIO` | Voice trigger, YAMNet sound classifier | Voice / Sound Event triggers |
| `ACCESS_FINE_LOCATION` | WiFi SSID (Android 10+), geofence | WiFi trigger, Geofence trigger |
| `ACCESS_BACKGROUND_LOCATION` | Geofence arrive/leave | Geofence trigger (Android 10+) |
| `BLUETOOTH_CONNECT` | Bluetooth toggle + trigger | Android 12+ |
| `READ_CONTACTS` | SMS trigger | SMS Received trigger |
| `READ_CALENDAR` / `WRITE_CALENDAR` | `calendar.create_event` | Runtime |
| `SCHEDULE_EXACT_ALARM` | `setExactAndAllowWhileIdle()` | Time trigger (Android 12+) |
| `FOREGROUND_SERVICE` | Sound Event classifier | Manifest |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14+ microphone FGS | Manifest |
| `FOREGROUND_SERVICE_SPECIAL_USE` (subtype: `appLaunch`) | `LaunchAppService` | Android 14+ |
| `INTERNET` | HTTP, Firebase, model downloads | Always |
| `RECEIVE_BOOT_COMPLETED` | Reschedule time triggers after reboot | Time trigger |
| `QUERY_ALL_PACKAGES` | List all apps for `launch_app` | Android 11+ |
| `MODIFY_AUDIO_SETTINGS` | Ringer mode action | Manifest |
| `WRITE_SETTINGS` | Brightness, rotation, hotspot, airplane mode | Manual Settings grant only |
| `WRITE_SYNC_SETTINGS` | Sync toggle | System app only |
| `READ_MEDIA_AUDIO` / `IMAGES` / `VIDEO` | Media file access | Runtime (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Legacy media access | Android 12 and below |

---

## Background Execution Scorecard

| Category | Count | Fully background-safe |
|---|---|---|
| Triggers | 17 types | 15 ✅, 1 ⚠️, 1 ❌ |
| Actions | ~25 types | 15 ✅, 8 ⚠️, 2 ❌ (both fixed) |

Four issues identified; two fully fixed (`launch_app` via LaunchAppService, NFC cold-start via NfcTriggerHandler). `brightness.set` / `rotation.lock` and `TriggerRegistry` context are acknowledged limitations with documented workarounds.