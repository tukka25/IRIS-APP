# Trigger Feasibility Analysis for GemmaWorkflow

> Inspired by Apple Shortcuts, Tasker, MacroDroid, and Android automation patterns.
> Feasibility: ✅ = straightforward, ⚠️ = possible with caveats, 🔒 = restricted/not possible

## 1. Time of Day
**Feasibility:** ✅ Excellent | **API level:** 19+

**Android API:** `AlarmManager.setExact()`, `WorkManager` periodic work, `JobScheduler`
**How:** Set a repeating or one-shot alarm that broadcasts to a `BroadcastReceiver` or enqueues a `WorkManager` worker. The app receives the trigger and runs the associated workflow.
**Caveats:** Android 12+ restricts exact alarms; use `SCHEDULE_EXACT_ALARM` permission. For non-exact timing, `WorkManager` with `ExistingPeriodicWorkPolicy.KEEP` is the standard approach.
**Effort:** Low. Already partially implemented via `alarm.set_alarm`.

## 2. When Alarm is Stopped / Snoozed
**Feasibility:** ⚠️ Own alarms only | **API level:** 21+

**Android API:** `AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED`, AOSP DeskClock custom broadcasts
**Research:** The AOSP DeskClock app broadcasts `com.android.deskclock.ALARM_SNOOZE`, `ALARM_DISMISS`, `ALARM_ALERT`, and `ALARM_DONE` actions — but these are app-specific, NOT system-wide. The `AlarmClock.ACTION_DISMISS_ALARM` and `ACTION_SNOOZE_ALARM` constants are for *creating* dismiss/snooze intents (sending to a clock app), not *detecting* them. `ACTION_NEXT_ALARM_CLOCK_CHANGED` fires when the next alarm changes, but cannot distinguish snooze vs dismiss.
**How:** For GemmaWorkflow-created alarms, use `AlarmManager.setAlarmClock()` and track lifecycle internally via `PendingIntent` flags. For system clock alarms, detect `ACTION_NEXT_ALARM_CLOCK_CHANGED` and poll `AlarmManager.getNextAlarmClock()` — but you can only detect that an alarm *changed*, not what action the user took.
**Caveats:** Cannot reliably detect snooze/dismiss of third-party alarms. Each OEM clock app uses different internal broadcasts. Samsung, Xiaomi, etc. have their own clock apps with no public broadcast contract.
**Effort:** Medium for own alarms. Not feasible for third-party alarm detection.

## 3. Sleep Mode / Bedtime
**Feasibility:** ⚠️ Partial | **API level:** 29+

**Android API:** Google Play Services Sleep API (`ActivityRecognition.getClient().requestSleepSegmentUpdates()`), `ZenDeviceEffects` (Android 15+), `NotificationManager.Policy`
**Research:** Google provides a Sleep API via Play Services that uses device sensors (brightness, movement) to infer sleep/wake times. Returns `SleepSegmentEvent` and `SleepClassifyEvent` callbacks via `PendingIntent`. Requires `ACTIVITY_RECOGNITION` permission. Separate from Digital Wellbeing bedtime mode — this detects *actual sleep*, not scheduled bedtime. Android 15 introduced `ZenDeviceEffects` API, allowing third-party apps to control screen effects (grayscale, dim wallpaper, disable AOD, dark theme) when creating DND schedules — previously exclusive to Google's Digital Wellbeing app.
**How:** Register for sleep updates via `ActivityRecognition.getClient(context).requestSleepSegmentUpdates(pendingIntent, SleepSegmentRequest.getDefaultSleepSegmentRequest())`. Receive `SleepSegmentEvent` in a `BroadcastReceiver`. For bedtime mode proxy: combine DND detection with time range and charger state.
**Caveats:** Sleep API first event may take 10+ minutes after subscription. Sleep segment events arrive within 1 day. Not real-time. `ACTIVITY_RECOGNITION` requires runtime permission. `ZenDeviceEffects` is Android 15+ only (very limited device coverage in 2026).
**Effort:** Medium. Best as bedtime proxy (DND + time + charger), with Sleep API as enhancement for devices that support it.

## 4. When I Arrive / Leave (Location Geofence)
**Feasibility:** ✅ Excellent | **API level:** 19+

**Android API:** `GeofencingClient` (Google Play Services), `LocationManager.addProximityAlert()`
**How:** Create geofences with `Geofence.Builder()` — specify lat/lng, radius (meters), dwell time, expiration. Register via `GeofencingClient.addGeofences()` with a `PendingIntent`. The intent fires when the device enters/exits/dwells. Can also use `FusedLocationProviderClient` for periodic location checks without Play Services.
**Caveats:** Requires `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (Android 10+). Google Play Services required for geofencing API. Battery impact if using continuous GPS; use `GEOFENCE_TRANSITION_ENTER` only to conserve.
**Effort:** Medium. Already partially available via `get_device_location` tool.

**Feasible with Time Range constraint:** ✅ Yes. Combine geofence with `AlarmManager` time-window check in the broadcast receiver — only trigger if current time is within the specified range.

## 5. Email Received
**Feasibility:** 🔒 Very limited | **API level:** —

**Android API:** No public API to read user email content. `NotificationListenerService` can detect email notifications but cannot read body/content reliably since Android 12.
**How:** `NotificationListenerService` can detect notifications from Gmail/Outlook and extract sender/subject from the notification extras (when available). Cannot trigger on email content.
**Caveats:** Requires `BIND_NOTIFICATION_LISTENER_SERVICE` permission — user must manually enable in Settings → Notification Access. Email content is not accessible. OEM mail apps may not expose any readable extras.
**Effort:** High, with very limited capability. Mark as **needs setup** in UI.

## 6. Message Received (SMS / WhatsApp / Telegram)
**Feasibility:** ⚠️ Partial | **API level:** 21+

**Android API:**
- **SMS:** `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` broadcast (API 19+)
- **WhatsApp/Telegram:** `NotificationListenerService` (API 18+)
**How:** For SMS, register `BroadcastReceiver` with `SMS_RECEIVED_ACTION`. For messaging apps, use `NotificationListenerService` to detect incoming message notifications and extract sender/text from `Notification.extras`.
**Caveats:** SMS broadcast may be restricted on Android 14+. NotificationListenerService requires user to manually enable in Settings. Content extraction from notifications is unreliable across app updates. WhatsApp end-to-end encryption means content is only in the notification payload, not accessible via API.
**Effort:** Medium (SMS) / High (messaging apps). SMS trigger is feasible; WhatsApp/Telegram is fragile.

## 7. Airplane Mode
**Feasibility:** ✅ Excellent | **API level:** 17+

**Android API:** `Intent.ACTION_AIRPLANE_MODE_CHANGED`
**How:** Register a `BroadcastReceiver` in `AndroidManifest.xml` for `ACTION_AIRPLANE_MODE_CHANGED`. The receiver gets a boolean extra `state` — true = airplane mode on.
**Caveats:** Apps cannot toggle airplane mode on Android 10+ (requires system privilege). Trigger-only (detection works fine).
**Effort:** Trivial. One broadcast receiver.

## 8. WiFi — Connected to Network
**Feasibility:** ✅ Excellent | **API level:** 21+

**Android API:** `ConnectivityManager.NetworkCallback`, `WifiManager`
**How:** Register `NetworkCallback` with `ConnectivityManager.registerNetworkCallback()`. Filter by `NetworkCapabilities.TRANSPORT_WIFI`. Can also detect specific SSID via `WifiManager.getConnectionInfo()`.
**Caveats:** Android 10+ restricts access to WiFi SSID without `ACCESS_FINE_LOCATION`. For SSID-specific triggers, user must grant location permission. `NetworkCallback` runs as long as the app process is alive — use a foreground service for background detection.
**Effort:** Low. Well-documented pattern.

## 9. Bluetooth — Connected to Device
**Feasibility:** ✅ Excellent | **API level:** 18+

**Android API:** `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED`, `BluetoothDevice.ACTION_ACL_CONNECTED`
**How:** Register `BroadcastReceiver` for Bluetooth connection state changes. Filter by device name or address. Can trigger on connect/disconnect to specific devices (car hands-free, headphones, watch).
**Caveats:** Android 12+ requires `BLUETOOTH_CONNECT` permission. Device name may not be available for paired-but-not-connected devices.
**Effort:** Low.

## 10. NFC Tag Scanned
**Feasibility:** ✅ Excellent | **API level:** 10+

**Android API:** `NfcAdapter.ACTION_NDEF_DISCOVERED`, `ACTION_TAG_DISCOVERED`, `ACTION_TECH_DISCOVERED`
**How:** Register intent filters in `AndroidManifest.xml` for NFC intents. Write NDEF messages to NFC tags with a custom AAR (Android Application Record) that launches GemmaWorkflow. The app receives the tag data and triggers the associated workflow.
**Caveats:** Requires `NFC` permission. App must be in foreground or use foreground dispatch for reliable detection. Tag must contain NDEF data readable by the app.
**Effort:** Medium. Already partially supported via `nfc` trigger in `TriggerCatalog`.

## 11. Android Auto / Driving Mode
**Feasibility:** ⚠️ Partial | **API level:** 29+

**Android API:** `UiModeManager` (car mode detection), `CarConnection` API (Android Auto)
**How:** Detect `UI_MODE_TYPE_CAR` via `UiModeManager.getCurrentModeType()`. Listen for `ACTION_ENTER_CAR_MODE` / `ACTION_EXIT_CAR_MODE` broadcasts. For Android Auto specifically, `CarConnection` API from `androidx.car.app` can detect connection to car head unit.
**Caveats:** Car mode detection is OEM-dependent. Some phones don't broadcast car mode changes. `CarConnection` API requires a car app project setup.
**Effort:** Medium. Simpler: detect Bluetooth connection to known car devices as proxy.

## 12. App Opened / Closed
**Feasibility:** ⚠️ Requires special permission, Android 14 broken | **API level:** 21+

**Android API:** `UsageStatsManager` (polling — BROKEN on Android 14+), `AccessibilityService` (real-time — works reliably)
**Research:** Major finding: `UsageStatsManager.queryUsageStats()` / `queryEvents()` is confirmed broken on Android 14 (API 34). Google intentionally restricted it for privacy — the platform no longer returns the most recent foreground app reliably. Bug report filed in Google Issue Tracker with reproducible example. `AccessibilityService` with `TYPE_WINDOW_STATE_CHANGED` remains the only reliable method, but requires user to manually enable in Settings → Accessibility and carries privacy concerns (Google Play reviews accessibility service usage strictly).
**How:** `AccessibilityService` with `accessibilityEventTypes = typeWindowStateChanged`. Gets `event.packageName` in real-time when any app opens/closes. No polling needed. Declare service in manifest with `BIND_ACCESSIBILITY_SERVICE` permission and `accessibility_service_config.xml`.
**Caveats:** `UsageStatsManager` approach is dead on Android 14+. `AccessibilityService` requires user to manually navigate to Settings → Accessibility → GemmaWorkflow and toggle ON. Google Play may reject or require justification for apps using AccessibilityService if not genuinely for accessibility. Many users won't enable it.
**Effort:** Medium (AccessibilityService). High rejection risk on Play Store. Best used as opt-in trigger that clearly explains why it's needed.

## 13. Wallet / Payment
**Feasibility:** 🔒 Very limited | **API level:** —

**Android API:** Google Pay API (restricted), no public transaction-reading API
**How:** Can detect NFC payment via `HCE` (Host Card Emulation) service if the app is the active payment app. Cannot listen to Google Wallet transactions — they use secure element isolation.
**Caveats:** No feasible trigger for detecting third-party payments. Only useful if GemmaWorkflow itself implements payment triggering.
**Effort:** High. Skip for MVP.

## 14. Battery Level
**Feasibility:** ✅ Excellent | **API level:** 5+

**Android API:** `Intent.ACTION_BATTERY_CHANGED` (sticky broadcast), `BatteryManager`
**How:** Register `BroadcastReceiver` for `ACTION_BATTERY_CHANGED`. Get `BatteryManager.EXTRA_LEVEL` and `EXTRA_SCALE` — trigger at threshold (e.g., "below 20%", "above 80%"). Can also use `BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)`.
**Caveats:** `ACTION_BATTERY_CHANGED` is a sticky broadcast — cannot register in manifest, must register programmatically. Need a running service.
**Effort:** Trivial.

## 15. Charger Connected / Disconnected
**Feasibility:** ✅ Excellent | **API level:** 5+

**Android API:** `Intent.ACTION_POWER_CONNECTED`, `Intent.ACTION_POWER_DISCONNECTED`
**How:** Register `BroadcastReceiver` in manifest for both intents. Trigger when charger is plugged in (wireless, USB, AC) or unplugged.
**Caveats:** Cannot distinguish USB vs AC vs wireless charger type without `BatteryManager` query on Android 8+.
**Effort:** Trivial.

## 16. Do Not Disturb
**Feasibility:** ✅ Good | **API level:** 23+

**Android API:** `NotificationManager.addInterruptionFilterChangedListener()`, `NotificationManager.getCurrentInterruptionFilter()`
**How:** Call `addInterruptionFilterChangedListener()` with an `Executor` and listener. Trigger when DND activates (filter = `INTERRUPTION_FILTER_PRIORITY`, `INTERRUPTION_FILTER_NONE`, `INTERRUPTION_FILTER_ALARMS`). Detects DND on/off transitions in real-time.
**Caveats:** Listener runs only while app process is alive. Need foreground service for background detection. Android 10+ restricts apps from toggling DND directly (requires `NOTIFICATION_POLICY_ACCESS`).
**Effort:** Low.

## 17. Sound Recognition
**Feasibility:** 🔒 Not feasible for third-party apps | **API level:** System-only

**Android API:** `SoundTriggerDetectionService` (hidden `@SystemApi`), `RecognitionService` (speech only, not sound events)
**Research:** `SoundTriggerDetectionService` is the correct API for generic sound detection (baby crying, glass breaking, doorbell) — but it's annotated `@SystemApi` and `@hide`, meaning it's only accessible to privileged system apps. The `SoundTrigger` HAL is a vendor-specific hardware integration layer. `RecognitionService` is for *speech recognition*, not sound event detection. `SpeechRecognizer` streams audio to servers — not suitable for continuous background listening. The only practical approach is custom `AudioRecord` capture + on-device ML (TensorFlow Lite), but this requires foreground service, `RECORD_AUDIO` permission, and continuous battery drain.
**Caveats:** No public API for sound event detection. Custom ML approach is impractical for background use — Android kills long-running audio capture in background. Pixel devices have "Now Playing" and "Sound Notifications" in Accessibility settings, but these are not exposed to third-party apps via any API. Google's Android 14 `RecognitionService` API for sound events was discussed but not shipped as a public API.
**Effort:** Extremely high. Not practical for MVP or near-term releases. Defer indefinitely.

---

## Implementation Priority Matrix

| Tier | Triggers | Reason |
|------|----------|--------|
| **P0** (MVP) | Time of Day, Battery Level, Charger, WiFi, Bluetooth, Airplane Mode, DND, NFC | All trivially feasible, low effort, broad use |
| **P1** (Next) | Arrive/Leave (geofence), Message Received (SMS), Alarm stopped/snoozed (own alarms) | Medium effort, high user demand |
| **P2** (Later) | Sleep mode (proxy via DND+time), Android Auto/Driving, Message Received (NotificationListener) | Requires workarounds or manual user setup |
| **P3** (Defer) | App opened/closed (AccessibilityService), Email received, Wallet, Sound recognition | Restricted APIs, Play Store rejection risk, or no public API |

---

## Key Changes from Online Research

| Trigger | Original Assessment | Updated | Why |
|---------|-------------------|---------|-----|
| Alarm snooze/dismiss | ⚠️ Limited | ⚠️ Own alarms only | No system-wide broadcast exists. OEM clock apps don't share a contract |
| Sleep mode | ⚠️ Partial | ⚠️ Partial (updated) | Google Sleep API exists but is sensor-based, not bedtime-based. `ZenDeviceEffects` is Android 15+ only |
| App opened/closed | ⚠️ Medium effort | ⚠️ Broken on A14 | `UsageStatsManager` confirmed broken on Android 14+. Only `AccessibilityService` works — Play Store rejection risk |
| Sound recognition | ⚠️ Complex | 🔒 Not feasible | `SoundTriggerDetectionService` is hidden `@SystemApi`. No public API exists. Custom ML impractical for background |

---

## Architecture Pattern

All triggers follow the same pattern in GemmaWorkflow:

```
Trigger Source (Android broadcast / API callback)
  → TriggerReceiver (BroadcastReceiver / Service)
    → TriggerRegistry (in-memory lookup by trigger type + conditions)
      → WorkflowRunner (execute associated workflow actions)
```

Each trigger is registered in `TriggerCatalog.kt` with:
- `type`: enum (TimeOfDay, BatteryLevel, WiFi, etc.)
- `conditions`: map of trigger-specific parameters (threshold, SSID, device address, etc.)
- `setupState`: whether the user has configured the required permissions

When a workflow is saved with a trigger, the app:
1. Validates trigger feasibility against device capabilities
2. Registers the trigger with Android APIs
3. Stores the trigger ↔ workflow mapping in `TriggerRegistry`
4. When triggered, runs the workflow through `WorkflowRunner`

Triggers that require special permissions (Notification Listener, Accessibility, Usage Stats) show a "needs setup" screen guiding the user to the relevant Settings page.

---

## What's Already Implemented

| Trigger type | Status in `TriggerCatalog.kt` |
|-------------|-------------------------------|
| manual | ✅ Ready (user taps button in app) |
| time | ✅ Ready (alarm-based scheduling) |
| nfc | ✅ Ready (NFC intent filter) |
| share_sheet | ✅ Ready (share intent receiver) |
| tasker_setup_required | ✅ Ready (shown as "needs setup") |
