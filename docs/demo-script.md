# Demo Script — Iris for the Modern Worker

**DEMO TITLE:** "Your Day, Automated — Iris for the Modern Employee"
**TARGET AUDIENCE:** Employees, knowledge workers, product managers, remote workers
**DEMO LENGTH:** ~90 seconds
**TONE:** Calm, confident, slightly upbeat — premium productivity app ad
**APP:** Iris (com.irisapp)

---

## Pre-Demo Setup

- NFC tag (NTAG213/215/216) taped to a desk monitor or surface within camera frame
- Test geofence coordinates loaded (office location set in workflow config)
- Google Maps installed on demo device
- Clock set to simulate 6:00 PM for evening wind-down scene
- One saved workflow: "Morning Setup" attached to NFC tag trigger

---

## Scene Breakdown

### SCENE 1 — Opening [0:00–0:12]

**VO:** "Every morning, the same rituals. Check the calendar. Open Slack. Silence your phone. What if one tap did all of it?"

**ACTION:** Close-up of Android phone face-down on a desk. A hand reaches in from the right, taps an NFC tag taped to a monitor stand.

---

### SCENE 2 — NFC Tag Scan [0:12–0:28]

**VO:** "Tap an NFC tag — your desk, your car, your door — and Iris launches your morning routine instantly."

**ACTION:** Phone wakes with lock screen visible. NFC scan animation plays. Notification slides in: "Running: Morning Setup." Then rapid-fire action results:

1. Ringer: Silent ✓
2. Slack opened ✓
3. Calendar showing 9 AM standup ✓

**Workflow:** NFC tag scan → `NfcTriggerHandler` → `DeepLink.NfcScan` → `TriggerRegistry.fire()` → `WorkflowRunner`
1. `RingerModeApiExecutor` → vibrate mode
2. `LaunchAppApiExecutor` → opens Slack (via `LaunchAppService` FGS)
3. `LaunchAppApiExecutor` → opens Google Calendar (via `LaunchAppService` FGS)

---

### SCENE 3 — Workflow Builder [0:28–0:45]

**VO:** "Setting it up takes seconds. Add your trigger. Add your actions. Save."

**ACTION:** Cut to Iris Generate screen:

1. User types: "Set my phone to silent, open Slack and Calendar"
2. Blob animates (LISTENING → PROCESSING)
3. Stage pipeline shows: Analyze → Plan → Execute → Done
4. Workflow preview appears: 3 steps, NFC tag trigger
5. User taps Save → workflow card appears in the Workflows tab

**RETO pipeline visible to user:**
- Analyze intent → "3 steps detected"
- Plan actions → "set ringer, launch 2 apps"
- Execute → "Workflow ready"

---

### SCENE 4 — Time Trigger [0:45–1:00]

**VO:** "Schedule it. At 6 PM every weekday, your phone winds down automatically."

**ACTION:** Clock on screen hits 6:00 PM (or show time jump). Three actions fire:

1. Do Not Disturb icon turns on — "DND active" notification
2. Brightness dims on screen
3. Toast: "Evening mode active"

**Workflow:** Time trigger → `AlarmManager.setExactAndAllowWhileIdle()` → `AlarmReceiver` → `TriggerRegistry.fire()`
1. `RingerModeApiExecutor` → Do Not Disturb
2. `BrightnessApiExecutor` → screen brightness to 30%
3. `NotificationApiExecutor` → "Evening mode active" toast

**Camera note:** Keep cuts quick — 2 seconds per action added max. Show the action cards stacking with success indicators.

---

### SCENE 5 — Share Sheet [1:00–1:12]

**VO:** "And when you get something shared — Iris handles it."

**ACTION:** Another phone shares a link via Android share sheet. Demo phone receives it. Iris detects the shared URL, creates a workflow to open it in Chrome, asks user to confirm, then opens it.

**Workflow:** Share sheet intent → `ShareSheetTriggerHandler` → `PendingShare` state → UI prompt
1. User receives shared URL
2. Iris intercepts via `ACTION_SEND` IntentFilter
3. Generate screen shows "Shared: [URL]" with preview
4. One-tap to open in Chrome (Custom Tab)

---

### SCENE 6 — Marketplace [1:12–1:22]

**VO:** "Or browse the Iris marketplace — community workflows other users have shared."

**ACTION:** Switch to Marketplace tab. Scroll through workflow cards: "Morning Routine", "Focus Mode", "Commute Setup". User taps one → preview opens → Save to My Workflows.

**Technical:** `MarketplaceRepository` fetches from Firebase Realtime Database (`/shared_workflows`). Imported workflow parsed via `WorkflowJsonParser.parseFromExport()`.

---

### SCENE 7 — Closing [1:22–1:30]

**VO:** "Iris. Automation that fits your day — not the other way around."

**ACTION:** Phone returns to desk face-down. Iris home screen visible. Workflow "Morning Setup" with NFC tag icon in the corner. Clean idle state — no flashy animation, just the ambient background orb and wordmark.

---

## Workflow Specs for Demo

### Workflow 1: "Morning Setup" — NFC Tag

**Trigger:** NFC tag scan (`TriggerConfig.Nfc`)
**Tag data:** `iris://workflow/morning-setup`
**Actions:**
```
1. RingerModeApiExecutor → mode: VIBRATE
2. LaunchAppApiExecutor → package: com.Slack
3. LaunchAppApiExecutor → package: com.google.android.calendar
```

### Workflow 2: "Evening Wind-Down" — Time

**Trigger:** Scheduled daily at 6:00 PM, Mon–Fri (`TriggerConfig.Time`)
**Actions:**
```
1. RingerModeApiExecutor → mode: DND
2. BrightnessApiExecutor → brightness: 30%
3. NotificationApiExecutor → "Evening mode active"
```

### Workflow 3: "Focus Mode" — Marketplace

**Trigger:** Manual
**Actions:**
```
1. RingerModeApiExecutor → mode: SILENT
2. NotificationApiExecutor → "Focus mode on"
```

---

## Voice-Over Transcript

> "Every morning, the same rituals. Check the calendar. Open Slack. Silence your phone. What if one tap did all of it? Tap an NFC tag — your desk, your car, your door — and Iris launches your morning routine instantly. Setting it up takes seconds. Add your trigger. Add your actions. Save. Schedule it. At 6 PM every weekday, your phone winds down automatically. And when you get something shared — Iris handles it. Or browse the Iris marketplace — community workflows other users have shared. Iris. Automation that fits your day — not the other way around."

---

## Tips for Filming

- **Lighting:** Soft natural light or office ambient — avoid harsh overhead
- **Phone UI:** Use a clean demo account; no personal data visible in notifications
- **Audio:** Record room tone, not VO on set — add VO in post
- **NFC reliability:** Pre-test the tag 5+ times. Use NTAG213 — most reliable for Android foreground dispatch
- **Time trigger:** Can be simulated by manually triggering the workflow from the app
- **Share sheet:** Use a second phone to send a link via Android share sheet
- **Geofence:** Test at actual location before shoot day, or show the config screen instead of live demo