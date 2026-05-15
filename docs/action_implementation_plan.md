# Action Implementation Plan — P1 to P4
Based on Easer reference: `docs/easer_action_catalog.md`

---

## Architecture Summary

Each new action follows a 3-file pattern:

```
platform/<category>/
  <Category>ApiExecutor.kt     # Silent execution logic + ExecutionResult
domain/catalog/
  ActionSpecRegistry.kt        # +1 ActionSpec entry (BuiltIn execution)
domain/runner/
  WorkflowRunner.kt            # +1 if-block dispatching to the executor
```

**Current dispatching style** — all BuiltIn actions are handled via explicit `if (step.id == "...")` blocks in `WorkflowRunner.executeStep()`. There is no polymorphic dispatch.

---

## Android Permissions Checklist

Add new permissions to `app/src/main/AndroidManifest.xml` as each action is implemented:

| Permission | Action | When needed |
|---|---|---|
| `MODIFY_AUDIO_SETTINGS` | `volume.set`, `ringer_mode.set` | All streams — add once |
| `INTERNET` | `http_request` | Already declared |
| `QUERY_ALL_PACKAGES` | `launch_app` | Android 11+ (API 30+) |
| `POST_NOTIFICATIONS` | `notification.send` | Android 13+ (API 33+) |
| `WRITE_SETTINGS` | `brightness.set`, `rotation.lock` | Must be granted by user via Settings |
| `ACCESS_NOTIFICATION_POLICY` | `ringer_mode.set` (DND) | API 26+ for `setInterruptionFilter` |
| `BLUETOOTH_CONNECT` | `bluetooth.toggle` | Android 12+ |
| `BLUETOOTH_ADMIN` | `bluetooth.toggle` | Android < 12 |
| `CHANGE_WIFI_STATE` | `wifi.toggle` | Android < 10 (blocked on 10+) |
| `SEND_NOTIFICATIONS` | `notification.send` | Android < 13 |

---

## P1 — Silent, zero-permission (beyond what's already declared)

### P1.1 `media.play_pause`
**What it does:** Play or pause whatever media is currently active (Spotify, YouTube, etc.)
**API:** `MediaSessionManager.getActiveSessions()` → `MediaController.dispatchMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)`
**File:** `platform/media/MediaControlApiExecutor.kt`
**Params:** none
**Permission:** `MEDIA_CONTENT_CONTROL` + NotificationListenerService (system-level signature, rarely granted) OR `sendOrderedBroadcast(Intent.ACTION_MEDIA_BUTTON)` — the fallback works without special permission
**Pattern (from Easer):**
```kotlin
// API 21+: MediaSessionManager path
val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
val listener = ComponentName(context, MediaControlHelperNotificationListenerService::class.java)
mgr.getActiveSessions(listener).firstOrNull()?.let { controller ->
    controller.dispatchMediaButtonEvent(KeyEvent(KEY_EVENT.ACTION_DOWN, KEYCODE_MEDIA_PLAY_PAUSE))
    controller.dispatchMediaButtonEvent(KeyEvent(KEY_EVENT.ACTION_UP, KEYCODE_MEDIA_PLAY_PAUSE))
}

// Fallback (all API levels): send ordered broadcast
val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
context.sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, down), null)
val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
context.sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, up), null)
```
**Registration:** BroadcastReceiver for `Intent.ACTION_MEDIA_BUTTON` (system broadcast, no permission needed to send to it)
**Fallback:** return failure if no active media session found

### P1.2 `media.next_track`
**What it does:** Skip to next track in current media session
**API:** Same as above, `KEYCODE_MEDIA_NEXT`
**File:** same `MediaControlApiExecutor.kt` (add `executeNext(params)` method)
**Params:** none
**Requires:** same NotificationListener path; fallback uses `sendOrderedBroadcast`

### P1.3 `media.previous_track`
**What it does:** Skip to previous track
**API:** `KEYCODE_MEDIA_PREVIOUS`
**File:** same `MediaControlApiExecutor.kt` (add `executePrevious(params)` method)
**Params:** none

### P1.4 `volume.set`
**What it does:** Set volume level for a specific audio stream (ring, media, alarm, notification)
**API:** `AudioManager.setStreamVolume(streamType, volumeIndex, 0)`
**File:** `platform/volume/VolumeApiExecutor.kt`
**Params:**
- `stream` (enum: `ring`, `media`, `alarm`, `notification`) — required
- `level` (int: 0–100) — required — mapped to max volume via `AudioManager.getStreamMaxVolume(streamType)`
- `mute` (boolean) — optional — use `setStreamMute()` or set level to 0
**Permission:** `MODIFY_AUDIO_SETTINGS` (already available on all Android versions — no user grant needed)
**Note:** Stream constants: `STREAM_RING`, `STREAM_MUSIC`, `STREAM_ALARM`, `STREAM_NOTIFICATION`

### P1.5 `ringer_mode.set`
**What it does:** Switch phone ringer to Normal / Vibrate / Silent (DND)
**API:**
- `AudioManager.setRingerMode(RINGER_MODE_NORMAL / VIBRATE / SILENT)` for basic modes
- `NotificationManager.setInterruptionFilter()` for granular DND (API 26+) — requires `ACCESS_NOTIFICATION_POLICY`
**File:** `platform/ringer/RingerModeApiExecutor.kt`
**Params:**
- `mode` (enum: `normal`, `vibrate`, `silent`, `dnd_all`, `dnd_priority`, `dnd_none`) — required
**Permission:** `MODIFY_AUDIO_SETTINGS` (basic), `ACCESS_NOTIFICATION_POLICY` (DND API 26+)
**Android 15 note:** `setRingerMode()` affects legacy ringer only; `NotificationManager.setInterruptionFilter()` is the preferred API for DND on API 26+
**Fallback:** If `ACCESS_NOTIFICATION_POLICY` not held, fall back to `AudioManager.setRingerMode()` for basic modes

---

## P2 — Silent, requires INTERNET or QUERY_ALL_PACKAGES

### P2.1 `http_request`
**What it does:** Send an HTTP GET/POST/PUT/DELETE with custom headers and optional body
**API:** `HttpURLConnection` (stdlib, no extra dependency) — or `OkHttp` if already in deps
**File:** `platform/http/HttpRequestApiExecutor.kt`
**Params:**
- `url` (string, required) — full URL
- `method` (enum: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`) — default `GET`
- `headers` (object, optional) — map of header name → value
- `body` (string, optional) — request body for POST/PUT/PATCH
- `content_type` (string, optional) — defaults to `application/x-www-form-urlencoded` for POST
**Permission:** `INTERNET` (already declared)
**Response:** `ExecutionResult.message` = HTTP status code + first 200 chars of response body
**Error:** timeout after 10s, connection errors captured in `ExecutionResult.message`
**Security:** Should require confirmation (`requiresConfirmation = true`) since it makes arbitrary network calls
**Requires confirmation:** true

### P2.2 `brightness.set`
**What it does:** Set screen brightness level or toggle auto-brightness
**API:** `Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)` — needs `WRITE_SETTINGS`
- `value` = 0–255 (absolute brightness)
- `Settings.System.SCREEN_BRIGHTNESS_MODE` = `AUTOMATIC_BRIGHTNESS` for auto
**File:** `platform/display/BrightnessApiExecutor.kt`
**Params:**
- `level` (int: 0–100, optional) — brightness as percentage; omit for auto-brightness
- `auto` (boolean, optional) — if true, enable auto-brightness instead of setting level
**Permission:** `WRITE_SETTINGS` — user must grant once via Settings (no runtime request possible)
**UX:** On first execution, if `WRITE_SETTINGS` not granted, return failure with message directing user to Settings
**Fallback:** `Intent(Settings.ACTION_DISPLAY_SETTINGS)` to open Settings for manual toggle

### P2.3 `launch_app`
**What it does:** Launch any installed app by package name
**API:** `context.packageManager.getLaunchIntentForPackage(packageName)` → `context.startActivity(intent)`
**File:** `platform/app/LaunchAppApiExecutor.kt`
**Params:**
- `package_name` (string, required) — e.g. `com.spotify.music`
- `class_name` (string, optional) — specific Activity within the app, e.g. `com.spotify.music.MainActivity`
**Permission:** `QUERY_ALL_PACKAGES` (Android 11+) — declared in manifest, no runtime grant needed
**Error:** `ExecutionResult.success = false` if package not found
**Note:** `getLaunchIntentForPackage()` returns null for apps with no launcher activity

### P2.4 `toast.show`
**What it does:** Display a transient on-screen text message
**API:** `Toast.makeText(context, message, Toast.LENGTH_SHORT).show()`
**File:** `platform/ui/ToastApiExecutor.kt` (or reuse in a general `UiApiExecutor.kt`)
**Params:**
- `message` (string, required) — text to show
- `duration` (enum: `short`, `long`) — default `short`
**Permission:** none
**Note:** Lightweight feedback-only action. Message is user-visible only while Toast is on screen.

### P2.5 `notification.send`
**What it does:** Send a custom notification with title and body text
**API:** `NotificationCompat.Builder` + `NotificationManager.notify()`
**File:** `platform/notification/NotificationApiExecutor.kt`
**Params:**
- `title` (string, required) — notification title
- `body` (string, required) — notification body text
- `channel` (string, optional) — notification channel ID, default `notification_default`
- `priority` (enum: `low`, `normal`, `high`) — default `normal`
**Permission:** `POST_NOTIFICATIONS` (Android 13+) — runtime request on Android 13+
**Note:** Channel (category in Android 13 terms) must be created via `NotificationChannel` for API 26+

---

## P3 — Partial/best-effort, requires Settings interaction or system app

### P3.1 `bluetooth.toggle`
**What it does:** Toggle Bluetooth on or off
**API:**
- `BluetoothAdapter.isEnabled()` / `BluetoothAdapter.enable()` / `disable()`
- On Android 12+: requires `BLUETOOTH_CONNECT` permission (runtime granted)
- On Android 10–11: `BluetoothAdapter.enable()` still works for third-party apps
- On Android 10+: system Settings tile is the guaranteed fallback
**File:** `platform/bluetooth/BluetoothApiExecutor.kt`
**Params:**
- `state` (enum: `on`, `off`, `toggle`) — `toggle` inverts current state
**Permission:** `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` (Android 12+)
**Fallback:** Open Bluetooth Settings via `Intent(Settings.ACTION_BLUETOOTH_SETTINGS)`
**Silent:** `enable()`/`disable()` works silently on Android 10–11; on Android 12+ it opens a system dialog — treat as "requires confirmation on Android 12+"

### P3.2 `wifi.toggle`
**What it does:** Toggle Wi-Fi on or off
**API:**
- `WifiManager.setWifiEnabled(true/false)` — works silently on Android < 10
- On Android 10+: requires user interaction via Settings tile (system dialog, not suppressible)
**File:** `platform/wifi/WifiApiExecutor.kt`
**Params:**
- `state` (enum: `on`, `off`, `toggle`)
**Permission:** `CHANGE_WIFI_STATE`, `ACCESS_WIFI_STATE`
**Fallback:** Open Wi-Fi Settings via `Intent(Settings.ACTION_WIFI_SETTINGS)`
**Silent:** Only on Android 9 and below

### P3.3 `rotation.lock`
**What it does:** Lock screen rotation to portrait/landscape or restore auto-rotate
**API:**
- `Settings.System.putInt(SCREEN_ROTATION, value)` — requires `WRITE_SETTINGS`
- Portrait: `1`, Landscape: `0`, Auto: `0` (with AUTOMATIC_ROTATION mode)
**File:** `platform/display/RotationApiExecutor.kt`
**Params:**
- `mode` (enum: `portrait`, `landscape`, `auto`) — required
**Permission:** `WRITE_SETTINGS` — user must grant via Settings first
**Fallback:** Open Display Settings

### P3.4 `intent.send`
**What it does:** Send a raw Android Intent (action, data, type, extras) — generic power-user action
**API:** `Intent()` + `context.sendBroadcast()` or `context.startActivity()` or `context.startService()`
**File:** `platform/intent/GenericIntentApiExecutor.kt`
**Params:**
- `action` (string, required) — e.g. `android.intent.action.VIEW`
- `data` (string, optional) — URI string
- `type` (string, optional) — MIME type
- `extras` (object, optional) — key-value pairs (values can be string, int, bool)
- `target` (enum: `broadcast`, `activity`, `service`) — default `broadcast`
**Permission:** varies by intent action — some require signature/system permission
**Requires confirmation:** `true` — raw intent sending is a security risk
**Note:** This is the Easer `intent` operation generalized. Useful for power users who want to send specific intents.

---

## P4 — System-app-only, root-only, or highly restricted

### P4.1 `hotspot.toggle`
**What it does:** Toggle mobile hotspot (Wi-Fi AP) on/off
**API:** `WifiManager.setWifiApEnabled(wifiConfig, enabled)` — requires system app or root
**File:** `platform/hotspot/HotspotApiExecutor.kt`
**Params:**
- `state` (enum: `on`, `off`)
**Permission:** `WRITE_SETTINGS` + system/root
**Fallback:** Open Hotspot Settings — `Intent(Settings.ACTION_WIFI_AP_SETTINGS)` or `ACTION_NETWORK_OPERATOR_SETTINGS`
**Silent:** Only for system apps / root — otherwise open Settings

### P4.2 `cellular.toggle`
**What it does:** Toggle mobile data on/off
**API:** `Settings.Global.putInt(MOBILE_DATA, 1/0)` — requires system app or root
**File:** `platform/cellular/CellularApiExecutor.kt`
**Params:**
- `state` (enum: `on`, `off`)
**Permission:** `WRITE_SECURE_SETTINGS` (system/root) or `WRITE_SETTINGS` (root via ADB)
**Fallback:** Open Mobile Data Settings
**Silent:** Only for system apps / root

### P4.3 `command.exec`
**What it does:** Execute a shell command (user-space only)
**API:** `Runtime.getRuntime().exec(commandString)` → read stdout/stderr
**File:** `platform/command/CommandApiExecutor.kt`
**Params:**
- `command` (string, required) — shell command to execute
- `timeout_ms` (int, optional) — default 5000ms, max 30000ms
**Permission:** none for user-space commands
**Requires confirmation:** `true` — arbitrary command execution is a major security risk
**Note:** Only user-space commands work. Commands requiring root will fail silently. Dangerous by design — keep `requiresConfirmation = true`.

### P4.4 `sync.toggle`
**What it does:** Enable or disable automatic sync for a Google account
**API:** `ContentResolver.setSyncAutomatically(account, authority, enable)`
**File:** `platform/sync/SyncApiExecutor.kt`
**Params:**
- `account_type` (string, required) — e.g. `com.google`
- `authority` (string, required) — e.g. `com.google.android.gms.calendar` for Calendar sync
- `enable` (boolean, required)
**Permission:** `WRITE_SYNC_SETTINGS`
**Note:** Rarely needed; complex account/authority setup. Lower priority.

### P4.5 `airplane_mode.toggle`
**What it does:** Toggle Airplane Mode on/off
**API:** `Settings.Global.putInt(AIRPLANE_MODE_ON, 1/0)` + broadcast `AIRPLANE_MODE_CHANGED` — requires system app or root
**File:** `platform/airplane/AirplaneModeApiExecutor.kt`
**Params:**
- `state` (enum: `on`, `off`)
**Permission:** `WRITE_SECURE_SETTINGS` (system/root)
**Fallback:** Open Airplane Mode Settings
**Silent:** Only for system apps / root

### P4.6 `wireguard.toggle`
**What it does:** Bring WireGuard VPN interface up or down
**API:** WireGuard native library (`com.wireguard.android`)
**File:** `platform/wireguard/WireGuardApiExecutor.kt`
**Params:**
- `tunnel` (string, required) — tunnel interface name
- `state` (enum: `up`, `down`)
**Permission:** `NET_ADMIN` (system app signature)
**Note:** Requires WireGuard app installed; highly specialized. Lowest priority.

---

## Execution Order & Dependencies

### Phase 1 (P1 — pure silent, no new permissions)
```
MediaControlApiExecutor.kt          → media.play_pause, media.next_track, media.previous_track
VolumeApiExecutor.kt                → volume.set
RingerModeApiExecutor.kt             → ringer_mode.set

ActionSpecRegistry.kt               → +6 ActionSpecs (all BuiltIn)
WorkflowRunner.kt                   → +6 if-blocks
AndroidManifest.xml                 → +0 new permissions (MODIFY_AUDIO_SETTINGS already implied)
```

### Phase 2 (P2 — silent with INTERNET/QUERY_ALL_PACKAGES)
```
HttpRequestApiExecutor.kt            → http_request
LaunchAppApiExecutor.kt               → launch_app
NotificationApiExecutor.kt           → notification.send
BrightnessApiExecutor.kt             → brightness.set
ToastApiExecutor.kt                  → toast.show

ActionSpecRegistry.kt               → +5 ActionSpecs (http_request = confirmation required)
WorkflowRunner.kt                   → +5 if-blocks
AndroidManifest.xml                 → +QUERY_ALL_PACKAGES (if not already present)
```

### Phase 3 (P3 — best-effort, Settings fallback)
```
BluetoothApiExecutor.kt              → bluetooth.toggle
WifiApiExecutor.kt                   → wifi.toggle
RotationApiExecutor.kt               → rotation.lock
GenericIntentApiExecutor.kt          → intent.send (confirmation required)

AndroidManifest.xml                 → +BLUETOOTH_CONNECT, +BLUETOOTH_ADMIN, +CHANGE_WIFI_STATE, +ACCESS_NOTIFICATION_POLICY
```

### Phase 4 (P4 — system/root only)
```
HotspotApiExecutor.kt                → hotspot.toggle
CellularApiExecutor.kt               → cellular.toggle
CommandApiExecutor.kt                → command.exec (confirmation required)
SyncApiExecutor.kt                   → sync.toggle
AirplaneModeApiExecutor.kt           → airplane_mode.toggle
WireGuardApiExecutor.kt              → wireguard.toggle (lowest priority)

AndroidManifest.xml                 → +WRITE_SECURE_SETTINGS, +NET_ADMIN (system/root only)
```

---

## File Manifest

```
app/src/main/java/com/iris/platform/

media/
  MediaControlApiExecutor.kt          [NEW] — play/pause, next, previous
  MediaControlHelperService.kt        [NEW] — NotificationListenerService (optional, for API 21+ MediaSession path)

volume/
  VolumeApiExecutor.kt                [NEW] — set stream volume levels

ringer/
  RingerModeApiExecutor.kt             [NEW] — set ringer mode / DND

http/
  HttpRequestApiExecutor.kt            [NEW] — HTTP GET/POST/PUT/DELETE

app/
  LaunchAppApiExecutor.kt             [NEW] — launch by package name

notification/
  NotificationApiExecutor.kt           [NEW] — send custom notification

display/
  BrightnessApiExecutor.kt             [NEW] — set brightness / auto-brightness
  RotationApiExecutor.kt              [NEW] — lock screen rotation

ui/
  ToastApiExecutor.kt                 [NEW] — show toast

bluetooth/
  BluetoothApiExecutor.kt             [NEW] — toggle Bluetooth on/off

wifi/
  WifiApiExecutor.kt                   [NEW] — toggle Wi-Fi on/off

intent/
  GenericIntentApiExecutor.kt         [NEW] — raw intent sender (confirmation required)

hotspot/       (P4 — system/root only)
  HotspotApiExecutor.kt               [NEW]

cellular/      (P4 — system/root only)
  CellularApiExecutor.kt              [NEW]

command/       (P4 — confirmation required)
  CommandApiExecutor.kt               [NEW]

sync/          (P4 — rarely needed)
  SyncApiExecutor.kt                  [NEW]

airplane/      (P4 — system/root only)
  AirplaneModeApiExecutor.kt          [NEW]

wireguard/     (P4 — specialized)
  WireGuardApiExecutor.kt             [NEW]
```

---

## Backward Compatibility Notes

| API Level | Key restrictions |
|---|---|
| API 21+ | `MediaSessionManager.getActiveSessions()` available |
| API 23+ | Runtime permission requests required for dangerous permissions |
| API 26+ | DND via `NotificationManager.setInterruptionFilter()`, notification channels required |
| API 29+ | Background location restricted, Bluetooth scanning requires location |
| API 30+ | `QUERY_ALL_PACKAGES` needed to list/query all apps |
| API 31+ | `SCHEDULE_EXACT_ALARM` gate, Bluetooth `BLUETOOTH_CONNECT` permission |
| API 33+ | `POST_NOTIFICATIONS` runtime permission |
| API 34+ | Background Bluetooth scanning further restricted |

---

## Testing Notes

Each `*ApiExecutor` should be unit-testable with a mock `Context`:
- `MediaControlApiExecutor` — mock `MediaSessionManager`, verify `dispatchMediaButtonEvent` called with correct keycode
- `VolumeApiExecutor` — mock `AudioManager`, verify `setStreamVolume` called with correct index
- `RingerModeApiExecutor` — mock `AudioManager` + `NotificationManager`, verify correct mode set
- `HttpRequestApiExecutor` — mock `HttpURLConnection`, verify correct method/headers/body sent
- `LaunchAppApiExecutor` — mock `PackageManager`, verify correct intent built and started
- `NotificationApiExecutor` — mock `NotificationManager`, verify `notify()` called
- `BluetoothApiExecutor` — mock `BluetoothAdapter`, verify `enable()`/`disable()` called
- `WifiApiExecutor` — mock `WifiManager`, verify `setWifiEnabled()` called
