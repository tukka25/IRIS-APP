# Easer Action Catalog Reference

Source: [renyuneyun/Easer](https://github.com/renyuneyun/Easer) — `app/src/main/java/ryey/easer/skills/operation/`

---

## Currently Implemented in gemma4good_android

| Action ID | Name | Silent | Notes |
|---|---|---|---|
| `call.call` | Make Phone Call | No | UI required |
| `sms.send` | Send SMS | No | Hard restriction — must be default SMS app |
| `calendar.insert_event` | Insert Calendar Event | Yes | `ContentResolver.insert(CalendarContract.Events.CONTENT_URI)` |
| `alarm.set` | Set Alarm | Yes | `AlarmManager.setExactAndAllowWhileIdle()` + notification |
| `share.share_text` | Share Text | Yes | Redirected to ClipboardApiExecutor |
| `share.share_image` | Share Image | Yes | Redirected to ClipboardApiExecutor |
| `browser.open_url` | Open URL | Yes | Chrome Custom Tabs (in-app) |
| `clipboard.set_text` | Set Clipboard Text | Yes | `ClipboardManager.setPrimaryClip()` |
| `clipboard.set_image` | Set Clipboard Image | Yes | `ClipData.newUri()` + `ContentResolver.takePersistableUriPermission` |

---

## Easer Operations — Full Reference (26 categories)

### 1. `airplane_mode`
Toggle Airplane Mode on/off.
- **Permission**: `Settings.ACTION_AIRPLANE_MODE_SETTINGS` (write secure settings, requires root or system app on Android 4.2+)
- **Silent**: No — opens system Settings screen
- **Notes**: Highly restricted on Android 4.2+. Rarely usable by third-party apps.

### 2. `alarm`
Set a one-shot or repeating alarm.
- **Permission**: `SCHEDULE_EXACT_ALARM` (Android 12+), `USE_EXACT_ALARM`
- **API**: `AlarmManager.setExactAndAllowWhileIdle()`, `setRepeating()`, `setInexactRepeating()`
- **Silent**: Yes
- **Equivalent in gemma4good**: `alarm.set` ✅ implemented

### 3. `bluetooth`
Toggle Bluetooth on/off.
- **Permission**: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` (Android 12+)
- **API**: `BluetoothAdapter.enable()` / `disable()`
- **Silent**: Partial — `BluetoothAdapter` methods require system app or user confirmation on Android 12+
- **Notes**: Most Bluetooth operations are restricted on Android 12+. Most reliable approach is launching the Bluetooth system Settings panel.

### 4. `bluetooth_connect`
Connect to a paired Bluetooth device.
- **Permission**: `BLUETOOTH_CONNECT`, `BLUETOOTH`
- **API**: `BluetoothDevice.createRfcommSocketToServiceRecord()`, `BluetoothAdapter.getBondedDevices()`
- **Silent**: Yes (if already paired)
- **Notes**: Only connects to already-bonded devices. Requires `BLUETOOTH_CONNECT` permission (Android 12+).

### 5. `brightness`
Set screen brightness level or auto-brightness.
- **Permission**: `WRITE_SETTINGS` (to modify system brightness)
- **API**: `Settings.System.SCREEN_BRIGHTNESS`, `Settings.System.SCREEN_BRIGHTNESS_MODE`
- **Silent**: Yes — `Settings.System.putInt()` for system brightness (requires `WRITE_SETTINGS` permission)
- **UI required**: User must grant `WRITE_SETTINGS` via Settings screen first time
- **Notes**: Brightness range 0–255. Auto mode uses `AUTOMATIC_BRIGHTNESS`.

### 6. `cellular`
Toggle mobile data on/off, or toggle data roaming.
- **Permission**: `WRITE_SECURE_SETTINGS` (system app) or root; or user manually grants `WRITE_SETTINGS` via adb
- **API**: `Settings.Global.MOBILE_DATA` — `Settings.Global.putInt()`
- **Silent**: Yes (for system/root apps)
- **UI required**: Third-party apps cannot toggle mobile data without root/ROM mods on Android 4.0+
- **Notes**: Android has restricted mobile data toggling since ~Android 4.0. Best-effort only.

### 7. `command`
Execute a shell command.
- **Permission**: None special (user-space commands only)
- **API**: `Runtime.getRuntime().exec()`
- **Silent**: Yes
- **Notes**: Only user-space commands work without root. Useful for `am start`, `input text`, `monkey`, etc.
- **Security note**: Should require confirmation before execution.

### 8. `hotspot`
Toggle mobile hotspot (Wi-Fi AP) on/off.
- **Permission**: `WRITE_SETTINGS` + system app signature or root
- **API**: `WifiManager.setWifiApEnabled()`
- **Silent**: Yes (for system/root apps)
- **Notes**: Third-party apps cannot reliably toggle hotspot. Most common approach is launching Settings.

### 9. `http_request`
Send an HTTP GET/POST/PUT/DELETE request with custom headers and body.
- **Permission**: `INTERNET`
- **API**: `HttpURLConnection` or `OkHttp`
- **Silent**: Yes
- **Params**: URL, method, headers (map), body (string or file), content-type
- **Notes**: Very powerful for webhooks, API calls, IoT device control. Should require confirmation (network call).

### 10. `intent` (generic)
Send a raw Android Intent (Activity, Broadcast, or Service).
- **Sub-types**:
  - `ActivityLoader` — `startActivity()`
  - `BroadcastLoader` — `sendBroadcast()`
  - `ServiceLoader` — `startService()`
- **Permission**: Depends on the intent action; may require permission to send to protected receivers
- **Params**: action, data URI, MIME type, categories, extras
- **Silent**: Depends on target intent
- **Notes**: Most flexible operation — can trigger any app's exported/nonexported components. Should require confirmation.

### 11. `launch_app`
Launch any installed application by package name.
- **Permission**: `QUERY_ALL_PACKAGES` (Android 11+)
- **API**: `getPackageManager().getLaunchIntentForPackage(packageName)` → `startActivity()`
- **Silent**: Yes — foreground activity launch
- **Notes**: Widely used. Works reliably. Optionally supports launching a specific Activity class within an app via `ComponentName`.

### 12. `media_control`
Play/Pause, Previous, Next, for the current media session.
- **Permission**: `MEDIA_CONTENT_CONTROL` or NotificationListenerService
- **API**: `MediaSessionManager.getActiveSessions()` + `MediaController.dispatchMediaButtonEvent()` (API 21+)
  - Fallback: `sendOrderedBroadcast(Intent.ACTION_MEDIA_BUTTON)`
- **Actions**: `play_pause`, `play`, `pause`, `previous`, `next`
- **Silent**: Yes
- **Equivalent in gemma4good**: Not yet implemented — **priority candidate**

### 13. `network_transmission`
Generic network operation (HTTP GET/POST, TCP send, etc.)
- **Permission**: `INTERNET`
- **API**: `HttpURLConnection`, `Socket`
- **Silent**: Yes
- **Notes**: Overlaps with `http_request`. May be a more general/flexible version.

### 14. `play_media`
Play a local audio file or stream URL.
- **Permission**: `INTERNET` (for streams), `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` (for local files)
- **API**: `MediaPlayer.setDataSource()`, `exoPlayer`, or `Intent(Intent.ACTION_VIEW)` with `EXTRA_FLOAT`
- **Silent**: Yes (if using MediaPlayer directly) or opens music player (if using intent)
- **Notes**: Supports local files or streaming URLs.

### 15. `ringer_mode`
Switch ringer to Normal / Vibrate / Silent (Do Not Disturb).
- **Permission**: `MODIFY_AUDIO_SETTINGS`
- **API**: `AudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL/VIBRATE/SILENT)`
  - Or `NotificationManager.setInterruptionFilter()` on API 26+ (DND)
- **Silent**: Yes
- **Equivalent in gemma4good**: Not yet implemented — **priority candidate**

### 16. `rotation`
Lock/unlock screen rotation, or force portrait/landscape.
- **Permission**: `WRITE_SETTINGS`
- **API**: `Settings.System.SCREEN_ROTATION` — `Settings.System.putInt()` for system rotation
  - Or `ActivityInfo.SCREEN_ORIENTATION_*` for per-activity (requires activity context)
- **Silent**: Yes (requires `WRITE_SETTINGS`)
- **UI required**: User must grant `WRITE_SETTINGS` permission first time

### 17. `send_notification`
Send a custom notification with title and body text.
- **Permission**: `POST_NOTIFICATIONS` (Android 13+)
- **API**: `NotificationCompat.Builder` → `NotificationManager.notify()`
- **Silent**: Yes
- **Notes**: Already implemented via `TimeTriggerNotification.kt` and `AlarmApiExecutor.kt`. Could be generalized as a user-visible notification with custom title/text.

### 18. `send_sms`
Send an SMS to a phone number with optional message.
- **Permission**: `SEND_SMS`
- **API**: `SmsManager.sendTextMessage()`
- **Silent**: Yes (but highly restricted — see gemma4good notes)
- **Equivalent in gemma4good**: `sms.send` ⚠️ UI-required (Google Play restrictions)

### 19. `state_control`
Toggle system settings states (launches system Settings screens for user to toggle).
- **Permission**: Varies by target settings screen
- **API**: `Intent(Settings.ACTION_*)` — opens Settings app to specific screen
- **Silent**: No — always UI (opens Settings)
- **Notes**: Fallback for settings that cannot be toggled silently.

### 20. `synchronization`
Toggle sync on/off for an account.
- **Permission**: `WRITE_SYNC_SETTINGS`
- **API**: `ContentResolver.setSyncAutomatically()`, `AccountManager.addAccountExplicitly()`
- **Silent**: Yes (for accounts already on device)
- **Notes**: Rarely used. Works with Google account sync settings.

### 21. `toast`
Show a transient on-screen message.
- **Permission**: None
- **API**: `Toast.makeText(context, message, Toast.LENGTH_SHORT).show()`
- **Silent**: Yes
- **Silent**: Yes
- **Notes**: Lightweight feedback to user. `Duration` can be `SHORT` or `LONG`. No user interaction needed.

### 22. `ui_mode`
Switch UI mode: Car mode, Desk mode, Normal mode.
- **Permission**: `WRITE_SETTINGS` or `ACCESS_NOTIFICATION_POLICY` for DND
- **API**: `UiModeManager.enableCarMode()`, `AppOpsManager.startOps()` for DND
- **Silent**: Partial
- **Notes**: Car mode is deprecated on newer Android versions. DND is controlled via `NotificationManager.setInterruptionFilter()`.

### 23. `volume`
Adjust volume for specific audio streams (Ring, Media, Alarm, Notification, Bluetooth).
- **Permission**: `MODIFY_AUDIO_SETTINGS`
- **API**: `AudioManager.setStreamVolume()` for each `AudioManager.STREAM_*`
- **Silent**: Yes
- **Equivalent in gemma4good**: Not yet implemented — **priority candidate**

### 24. `wifi`
Toggle Wi-Fi on/off.
- **Permission**: `CHANGE_WIFI_STATE`
- **API**: `WifiManager.setWifiEnabled(boolean)`
- **Silent**: Yes (but restricted on Android 10+ for third-party apps — user consent required via Settings tile)
- **Notes**: On Android 10+, third-party apps cannot toggle Wi-Fi without user interaction. Best to open Wi-Fi Settings panel.

### 25. `wireguard`
Interact with WireGuard VPN interface (up/down, send/receive packets).
- **Permission**: `NET_ADMIN` (system app signature) or root
- **API**: WireGuard native library interface
- **Silent**: Yes (for system/root)
- **Notes**: Highly specialized. Only useful for WireGuard VPN users. Requires system-level access.

---

## Recommended Additions for gemma4good (Prioritized)

| Priority | Action ID | Name | Silent | Reason |
|---|---|---|---|---|
| P1 | `media.play_pause` | Media Play/Pause | Yes | `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE` via MediaSessionManager |
| P1 | `media.next` | Media Next Track | Yes | `KeyEvent.KEYCODE_MEDIA_NEXT` |
| P1 | `media.previous` | Media Previous Track | Yes | `KeyEvent.KEYCODE_MEDIA_PREVIOUS` |
| P1 | `volume.set` | Set Volume | Yes | `AudioManager.setStreamVolume()` for ring/media/alarm/notif |
| P1 | `ringer_mode.set` | Set Ringer Mode | Yes | `AudioManager.setRingerMode()` or DND via `NotificationManager` |
| P2 | `http_request` | HTTP Request | Yes | Webhook/API calls — `HttpURLConnection` or `OkHttp` |
| P2 | `brightness.set` | Set Brightness | Yes | `Settings.System.putInt()` (requires `WRITE_SETTINGS`) |
| P2 | `launch_app` | Launch App | Yes | `getLaunchIntentForPackage()` |
| P2 | `toast.show` | Show Toast | Yes | `Toast.makeText().show()` |
| P2 | `bluetooth.toggle` | Toggle Bluetooth | Partial | Best-effort; may open Settings on Android 12+ |
| P3 | `wifi.toggle` | Toggle Wi-Fi | Partial | Best-effort; may open Settings on Android 10+ |
| P3 | `intent.send` | Send Intent | Depends | Generic intent — should require confirmation |
| P3 | `notification.send` | Send Notification | Yes | General-purpose notification (title + text) |
| P3 | `rotation.lock` | Lock Screen Rotation | Yes | `Settings.System.putInt()` |
| P4 | `hotspot.toggle` | Toggle Hotspot | Partial | System/root only |
| P4 | `cellular.toggle` | Toggle Mobile Data | No | Restricted on non-root |
| P4 | `command.exec` | Execute Shell Command | Yes | Requires confirmation |
| P4 | `sync.toggle` | Toggle Sync | Yes | `ContentResolver.setSyncAutomatically()` |
| P5 | `airplane_mode.toggle` | Toggle Airplane Mode | No | Requires root or system app |
| P5 | `wireguard.toggle` | Toggle WireGuard | Partial | System/root only |

---

## Notes on Android Permission Restrictions (Android 10–14)

| Setting | Silent Toggle | Notes |
|---|---|---|
| Wi-Fi | ❌ Third-party blocked | `WifiManager.setWifiEnabled()` requires system/root on Android 10+ |
| Bluetooth | ⚠️ Partial | `BluetoothAdapter.enable/disable` blocked on Android 12+; best-effort via Settings |
| Mobile Data | ❌ System-only | No third-party toggle since Android 4.0 |
| Hotspot | ❌ System-only | `WifiManager.setWifiApEnabled()` requires system/root |
| Airplane Mode | ❌ System-only | No third-party toggle since Android 4.2 |
| Brightness | ⚠️ Needs `WRITE_SETTINGS` | Silent via `Settings.System.putInt()` after user grants permission |
| Rotation | ⚠️ Needs `WRITE_SETTINGS` | Silent via `Settings.System.putInt()` after user grants permission |
| Ringer Mode | ✅ Yes | `AudioManager.setRingerMode()` works with `MODIFY_AUDIO_SETTINGS` |
| Volume | ✅ Yes | `AudioManager.setStreamVolume()` works with `MODIFY_AUDIO_SETTINGS` |
| Notifications | ✅ Yes | `NotificationManager` works with `POST_NOTIFICATIONS` (granted) |
| Media Control | ✅ Yes | `MediaSessionManager.getActiveSessions()` with NotificationListener permission |
| Calendar | ✅ Yes | `ContentResolver.insert()` works with `WRITE_CALENDAR` permission |
| Alarm | ✅ Yes | `AlarmManager` works with `SCHEDULE_EXACT_ALARM` permission |
| Clipboard | ✅ Yes | `ClipboardManager.setPrimaryClip()` — no permission needed on Android 10+ |
