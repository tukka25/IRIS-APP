# Iris Actions — Silent vs. Interactive Classification

**Purpose:** Identify which actions can run in a fully silent sequential chain vs. which ones open UI or require user interaction. Useful for designing demo workflows and understanding execution flow.

---

## Silent Actions

Execute without opening any app, dialog, or system Settings panel. Best for rapid-fire chains where everything completes in seconds with no user interaction.

| Action ID | Description | Special Notes |
|---|---|---|
| `volume.set` | Sets system volume (0–100) | `AudioManager` API, no permission needed |
| `ringer_mode.set` | Sets normal / silent / vibrate / DND | Uses `AudioManager` + `NotificationManager` (API 26+ for DND) |
| `brightness.set` | Sets screen brightness (0–100) | Requires `WRITE_SETTINGS` — falls back to Settings if not granted |
| `rotation.lock` | Locks to portrait / landscape / auto | Requires `WRITE_SETTINGS` — falls back to Settings if not granted |
| `alarm.set_alarm` | Schedules exact alarm via `AlarmManager` | Fires notification at scheduled time. On Android 12+ requires `SCHEDULE_EXACT_ALARM` user grant in Settings |
| `calendar.create_event` | Writes event directly to calendar via `ContentResolver` | Requires `READ_CALENDAR` + `WRITE_CALENDAR`. No Calendar app opens |
| `clipboard.copy_text` | Copies text to system clipboard | No permissions needed on Android 10+ |
| `clipboard.copy_image` | Copies image URI to clipboard | Checks read permission for `content://` URIs |
| `notification.send` | Posts notification to system tray | Requires `POST_NOTIFICATIONS` on Android 13+ |
| `toast.show` | Shows a short toast message | Wrapped in `Handler(Looper.getMainLooper())` to work from background coroutines |
| `media.play_pause` | Sends `KEYCODE_MEDIA_PLAY_PAUSE` to active media session | Uses `MediaSessionManager.getActiveSessions()` + fallback broadcast |
| `media.next_track` | Sends `KEYCODE_MEDIA_NEXT` | Same as above |
| `media.previous_track` | Sends `KEYCODE_MEDIA_PREVIOUS` | Same as above |
| `sync.toggle` | Enables/disables auto-sync for a Google account + authority | Uses `ContentResolver.setSyncAutomatically`; requires `WRITE_SYNC_SETTINGS` (system app only) |
| `http_request` | Runs HTTP GET/POST/PUT/DELETE/PATCH silently | Returns status code + body preview (max 500 chars) |
| `command.exec` | Runs shell command and returns stdout/stderr | Requires confirmation. Root-only commands fail silently |

---

## Takes an Input

These actions accept structured parameters at execution time. They can be chained — the output of one can be templated into the input of the next (e.g., `command.exec` output → `clipboard.copy_text`).

| Action ID | Required Params | Notes |
|---|---|---|
| `alarm.set_alarm` | `hour` (int 0–23), `minutes` (int 0–59) | Optional: `message`, `_requestCode` |
| `calendar.create_event` | `title`, `begin_time_millis` | Optional: `end_time_millis`, `location`, `description` |
| `http_request` | `url` | Optional: `method` (default GET), `body`, `headers`, `content_type` |
| `command.exec` | `command` (shell string) | Optional: `timeout_ms` (default 5000ms, max 30000ms) |
| `intent.send` | `action` (string) | Optional: `target` (broadcast/activity/service), `data`, `type`, `extras` (JSON) |
| `clipboard.copy_text` | `text` | Copies raw string |
| `clipboard.copy_image` | `uri` | URI must be accessible or read permission must be grantable |

---

## Returns an Output

These actions produce data that can be consumed by a subsequent action or displayed to the user. Output is stored in `ExecutionResult.output` (string) and `ExecutionResult.message`.

| Action ID | Output |
|---|---|
| `http_request` | `"200 OK | {body preview up to 500 chars}"` |
| `command.exec` | stdout from shell command, or `"Command executed successfully (exit 0, no output)"` |
| `calendar.create_event` | Success message with event URI, e.g. `"Event created: content://..."` |
| `clipboard.copy_text` | `"Copied N characters to clipboard"` |

---

## Opens an App / UI (Not Silent)

These launch an Activity or system Settings panel. They break sequential flow and require user interaction. Not suitable for rapid-fire silent chains.

| Action ID | Behavior | Workaround |
|---|---|---|
| `launch_app` | Opens target app's main activity | None — by design |
| `wifi.toggle` | On Android 10+: opens WiFi Settings tile for user to toggle manually | `wifi.toggle` returns failure + opens Settings |
| `bluetooth.toggle` | On Android 12+: opens Bluetooth Settings for user to toggle manually | `bluetooth.toggle` returns failure + opens Settings |
| `cellular.toggle` | Opens Mobile Data Settings | Requires `WRITE_SECURE_SETTINGS` (system app/root) |
| `hotspot.toggle` | Opens hotspot Settings | Requires `WRITE_SETTINGS` or system app |
| `airplane_mode.toggle` | Silently changes if system app; otherwise opens Settings | Falls back to Settings on `SecurityException` |
| `rotation.lock` | Fails with Settings redirect if `WRITE_SETTINGS` not granted | User must grant `WRITE_SETTINGS` via Settings → Display |
| `brightness.set` | Fails with Settings redirect if `WRITE_SETTINGS` not granted | Same as above |
| `alarm.set_alarm` | On Android 12+ failure opens Settings for `SCHEDULE_EXACT_ALARM` grant | N/A — this is a one-time permission grant |

---

## Sequential Execution Demo Reference

**Fully silent chain** (all 4 fire in ~500ms, no UI):

```
NFC tag scan
  → ringer_mode.set (vibrate)
  → brightness.set (30%)
  → notification.send ("Evening mode active")
```

**Hybrid chain** (some fire silently, last step shows UI):

```
NFC tag scan
  → ringer_mode.set (silent)
  → http_request (GET weather API)
  → notification.send ("Today's forecast: sunny, 28°C")
```

**Chain that opens apps sequentially** (each step has visible result):

```
NFC tag scan
  → launch_app (Slack)
  → launch_app (Google Calendar)
  → ringer_mode.set (vibrate)
  → notification.send ("Morning setup complete")
```

**Most impressive for demo:** Silent rapid-fire chain — ringer mode, brightness, notification all firing within ~300ms of each other with no visible lag and no UI breaks. Demonstrates the core value prop of Iris.

---

## Permission Requirements Summary

| Action | Silent? | Permission | Where Declared |
|---|---|---|---|
| `volume.set` | Yes | None | — |
| `ringer_mode.set` | Yes | `MODIFY_AUDIO_SETTINGS` (auto-granted) | manifest |
| `brightness.set` | Yes* | `WRITE_SETTINGS` (user-granted via Settings) | manifest |
| `rotation.lock` | Yes* | `WRITE_SETTINGS` (user-granted via Settings) | manifest |
| `alarm.set_alarm` | Yes | `SCHEDULE_EXACT_ALARM` (user-granted via Settings on Android 12+) | manifest |
| `calendar.create_event` | Yes | `READ_CALENDAR`, `WRITE_CALENDAR` (runtime) | manifest |
| `clipboard.copy_text/image` | Yes | None (Android 10+) | — |
| `notification.send` | Yes | `POST_NOTIFICATIONS` (runtime, Android 13+) | manifest |
| `toast.show` | Yes | None | — |
| `media.*` | Yes | None | — |
| `sync.toggle` | Yes | `WRITE_SYNC_SETTINGS` (system app only) | manifest |
| `http_request` | Yes | `INTERNET` | manifest |
| `command.exec` | Yes | None (app process only) | — |
| `launch_app` | No | None | — |
| `wifi.toggle` | No** | `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | manifest |
| `bluetooth.toggle` | No** | `BLUETOOTH_CONNECT` (Android 12+ runtime) | manifest |
| `cellular.toggle` | No | `WRITE_SECURE_SETTINGS` (system app only) | manifest |
| `hotspot.toggle` | No | `WRITE_SETTINGS` or system app | manifest |
| `airplane_mode.toggle` | No*** | `WRITE_SECURE_SETTINGS` (system app only) | manifest |

* Silent when `WRITE_SETTINGS` is granted; falls back to Settings if not.
** Opens Settings on Android 10+ (API 29+) because `setWifiEnabled`/`enable`/`disable` require user toggle.
*** Silent on system apps; opens Settings for regular apps.