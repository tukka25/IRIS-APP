# Workflow Execution Pipeline

**Last updated:** 2026-05-11

This document describes the end-to-end flow of how a workflow is triggered, evaluated, and executed — from a system event (battery change, geofence transition, etc.) through to actions being performed on the device.

---

## 1. Trigger Sources

Every trigger type hooks into a system signal:

| Trigger | Source |
|---------|--------|
| Manual | User taps "Run" in the UI |
| Time | `AlarmManager` → `TimeTriggerReceiver` |
| NFC | `NfcWorkflowTriggerActivity` (launched by system) |
| Share Sheet | `ShareReceiver` activity |
| Tasker | Same as Manual — user initiates in Tasker |
| Battery Level | `BatteryTriggerManager` → `BatteryChangeReceiver` |
| Charger | `ChargerTriggerManager` → `ChargerReceiver` |
| Wi-Fi | `WiFiTriggerManager` → `WiFiChangeReceiver` |
| Bluetooth | `BluetoothTriggerManager` → `BluetoothReceiver` |
| Airplane Mode | `AirplaneModeTriggerManager` → `AirplaneModeReceiver` |
| Do Not Disturb | `DndTriggerManager` → `DndChangeReceiver` |
| **Geofence** | `GeofenceManager` → `GeofenceBroadcastReceiver` (P1, in progress) |
| **SMS** | `SmsTriggerReceiver` / `SmsNotificationListener` (P1, planned) |
| **Own Alarm** | `AlarmTracker` → `AlarmSnoozeReceiver` / `AlarmDismissReceiver` (P1, planned) |

---

## 2. TriggerManager Registration Pattern

Each manager follows a two-phase registration:

### Phase 1: App Startup — `registerAll(context)`

```kotlin
// GemmaWorkflowApp.onCreate()
GeofenceManager.registerAll(this)
```

Loads all saved workflows from `WorkflowRepository`, calls `registerWorkflow()` for each active trigger. This restores all active triggers after a device restart.

### Phase 2: Workflow Save — `registerWorkflow(context, name, config)`

```kotlin
// WorkflowGenerationViewModel.saveWorkflow()
is TriggerConfig.Geofence -> GeofenceManager.registerWorkflow(ctx, name, trigger)
```

Creates the system-specific registration (geofence, broadcast receiver, PendingIntent, etc.) for the workflow. `unregisterWorkflow(name)` removes it on delete.

---

## 3. The Fire Chain

When a trigger fires, it follows this path:

```
System event (geofence entered, SMS received, etc.)
  → BroadcastReceiver / NotificationListenerService / Activity
      → TriggerRegistry.fire(context, workflowName)  OR  TriggerRegistry.fire(context, workflow)
          → WorkflowRepository.get(workflowName)  [if name-based]
              → WorkflowRunner.run(workflow, startIndex=0) { debug log }
                  → ConfirmationRequired? → SHOW NOTIFICATION + STORE pending execution
                  → ExecutionResult per step
              ← List<ExecutionResult>
```

### Detailed steps:

```
1. SYSTEM EVENT fires
   e.g. GeofencingEvent.fromIntent(intent) in GeofenceBroadcastReceiver

2. RECEIVER extracts requestId (= workflowName)
   → WorkflowRepository(context).get(workflowName)

3. TRIGGERREGISTRY.fire(context, workflow)
   a. Checks workflow.missingSetup — skips if incomplete
   b. Logs "Firing workflow: ${workflow.name}"
   c. Launches WorkflowRunner in Dispatchers.Default coroutine

4. WORKFLOWRUNNER.run(workflow, startIndex=0)
   For each step (i = startIndex → actions.size):
     a. resolveParams(params)         ← substitutes $step[N].output references
     b. resolve $step[N].output refs  ← fills in previous step outputs
     c. ActionSpecRegistry.find(id)   ← looks up action specification
     d. confirmGate: if spec.requiresConfirmation AND step not in confirmedSteps
          → throw ConfirmationRequired(step, stepIndex)
          → TriggerRegistry catches it → shows notification → stores PendingExecution
          → PIPELINE STOPS HERE (waiting for user)
     e. Execute:
        - calendar.create_event  → CalendarApiExecutor (ContentResolver)
        - clipboard.copy_text   → ClipboardApiExecutor
        - alarm.set_alarm       → AlarmApiExecutor (AlarmManager, checks SCHEDULE_EXACT_ALARM)
        - share.share_text/image → ClipboardApiExecutor (silent copy)
        - url / custom_tab       → ChromeCustomTabOpener (in-app browser tab)
        - package_launch        → packageManager.getLaunchIntentForPackage()
        - android_intent        → intentFactory.buildExecutableIntent() → context.startActivity()
        - internal_tool         → ToolRegistry.execute(toolName, input)
        - built_in              → same as android_intent
     f. Fallback on failure: tryFallback() iterates spec.fallbackActionIds
        → maps params → resolves activity → starts fallback activity
     g. Returns ExecutionResult(stepId, success, message, output)
     h. Stores output in stepOutputs[i] for chaining
     i. If !success → break loop
   Returns List<ExecutionResult>

5. Confirmation required:
   → showConfirmationNotification(context, workflowName, stepId, stepIndex)
   → PendingExecution stored in pendingExecutions map
   → User taps Confirm → TriggerRegistry.confirmAndResume(context, workflowName)
     → runner.run(workflow, startIndex=stepIndex) { debug }
     → catches ConfirmationRequired again if more confirmation gates downstream

6. Confirmation dismissed:
   → TriggerRegistry.dismissConfirmation(context, workflowName)
   → pendingExecutions.remove(workflowName)
   → Pipeline ends, workflow does not complete
```

---

## 4. Key Components

### TriggerRegistry

**File:** `com.gemmaworkflow.platform.trigger.TriggerRegistry` (object/singleton)

| Method | Description |
|--------|-------------|
| `init(context)` | One-time init in `GemmaWorkflowApp.onCreate`. Sets `applicationContext`. Must be called before any manager registers. |
| `fire(context, workflowName)` | Load workflow by name, delegate to `fire(context, workflow)` |
| `fire(context, workflow)` | Launch `WorkflowRunner.run()` in `Dispatchers.Default` coroutine. Catches `ConfirmationRequired` → stores pending + shows notification. |
| `confirmAndResume(context, workflowName)` | Called from `MainActivity` when user confirms. Re-launches runner from stored step index. |
| `dismissConfirmation(context, workflowName)` | Called from `MainActivity` when user dismisses. Removes pending execution. |

### WorkflowRunner

**File:** `com.gemmaworkflow.domain.runner.WorkflowRunner`

| Method | Description |
|--------|-------------|
| `run(workflow, startIndex, onDebug)` | Execute all steps from `startIndex`, collecting `ExecutionResult`s |
| `confirmPendingStep()` | Called from `MainActivity` after user confirms. Clears pending step, returns it. |
| `dismissPendingStep()` | Same for dismiss — step is marked confirmed but not executed. |

### ConfirmationRequired

**File:** `com.gemmaworkflow.domain.runner.WorkflowRunner`

Exception thrown by `executeStep()` when a step requires user confirmation before running. Caught by `TriggerRegistry.fire()` which stores the pending execution and shows a notification.

### ExecutionResult

**File:** `com.gemmaworkflow.domain.model.WorkflowModels.kt`

```kotlin
data class ExecutionResult(
    val stepId: String,
    val success: Boolean,
    val message: String,
    val output: String = ""
)
```

Returned by each step execution. `output` is stored for `$step[N].output` chaining in subsequent steps.

---

## 5. Confirmation Flow (Background Triggers)

```
Background trigger fires
  → WorkflowRunner.executeStep() hits a confirmation gate
  → ConfirmationRequired thrown
  → TriggerRegistry catches it
  → pendingExecutions[workflowName] = PendingExecution(runner, workflow, stepIndex)
  → showConfirmationNotification() — notification posted
  → Pipeline PAUSED

User sees notification: "Confirm: workflowName — Step N: actionLabel"
  → Tap Confirm → MainActivity receives intent, calls confirmAndResume()
  → runner.run() from stored stepIndex
  → If another confirmation gate → same loop again
  → If all clear → steps execute, results returned, pending cleared

  OR
  → Tap Dismiss → dismissConfirmation()
  → pendingExecutions.remove(workflowName)
  → Pipeline ENDS (step not re-executed)
```

---

## 6. Sequential Step Pipeline — Each Step Waits for the Previous

Steps execute **one at a time, in order**. A step does not begin until the previous step has fully completed (success or failure). The output of each step is captured and made available to subsequent steps via the `$step[N].output` reference.

### Execution Flow (Step-by-Step)

```
for stepIndex = 0 .. workflow.actions.size - 1:
    ┌─────────────────────────────────────────────────────────┐
    │  STEP stepIndex BEGINS — previous step must be done     │
    │                                                         │
    │  1. RESOLVE PARAMETERS                                  │
    │     → replace $step[N].output refs with actual outputs   │
    │       from previously-executed steps                     │
    │                                                         │
    │  2. LOOK UP ACTION SPEC                                  │
    │     → ActionSpecRegistry.find(step.id)                  │
    │     → if not found: return failure, stop pipeline        │
    │                                                         │
    │  3. CONFIRMATION GATE                                   │
    │     → if spec.requiresConfirmation AND not yet confirmed │
    │         → throw ConfirmationRequired                      │
    │         → TriggerRegistry catches it                      │
    │         → POST CONFIRMATION NOTIFICATION                 │
    │         → PIPELINE PAUSED — waiting for user             │
    │         → (resume continues from this step after user   │
    │            confirms)                                     │
    │                                                         │
    │  4. EXECUTE STEP                                         │
    │     → calendar.create_event  → CalendarApiExecutor      │
    │     → clipboard.copy_text   → ClipboardApiExecutor      │
    │     → alarm.set_alarm       → AlarmApiExecutor           │
    │     → share.*              → ClipboardApiExecutor        │
    │     → url (custom_tab)     → ChromeCustomTabOpener       │
    │     → package_launch       → getLaunchIntentForPackage() │
    │     → android_intent       → startActivity()             │
    │     → internal_tool        → ToolRegistry.execute()       │
    │                                                         │
    │  5. CAPTURE OUTPUT                                       │
    │     → result = ExecutionResult(stepId, success,          │
    │         message, output)                                  │
    │     → stepOutputs[stepIndex] = result.output             │
    │                                                         │
    │  6. CHECK RESULT                                         │
    │     → if !result.success: PIPELINE STOPS (break)         │
    │     → else: PIPELINE CONTINUES to next step              │
    └─────────────────────────────────────────────────────────┘

PIPELINE COMPLETE ← all steps succeeded, or first failure stopped it
```

### Output Chaining — Passing Data Between Steps

The `$step[N].output` syntax lets step N read the output of step N-1 (or any earlier step):

```
Step 0 (index 0):
  action = "weather.get_weather"
  params = { "city": "Tokyo" }
  output = "Tokyo: 22°C, sunny"

Step 1 (index 1):
  action = "notification.send"
  params = { "message": "Weather update: $step[0].output" }
  → "Weather update: Tokyo: 22°C, sunny"
  output = "Notification sent"

Step 2 (index 2):
  action = "alarm.set_alarm"
  params = {
    "label": "Weather alert",
    "time": "$step[0].output"   ← still references step 0
  }
  → time param resolved to "Tokyo: 22°C, sunny" (raw output used here as example)
```

Each step waits for the previous step to finish before it even starts resolving parameters. The `stepOutputs` map is populated sequentially:

```
run() called
  → step 0 executeStep() completes → stepOutputs[0] = result.output
  → step 1 executeStep() begins    → reads stepOutputs[0] during param resolution
  → step 1 completes → stepOutputs[1] = result.output
  → step 2 executeStep() begins    → reads stepOutputs[0] AND stepOutputs[1]
  → ...and so on
```

### Resuming After Confirmation

When a confirmation notification is shown, the pipeline pauses at that step. The pending `WorkflowRunner` instance and step index are stored in `TriggerRegistry.pendingExecutions`. When the user confirms:

```
confirmAndResume(context, workflowName)
  → pending = pendingExecutions.remove(workflowName)
  → runner.run(workflow, startIndex = pending.startIndex)
      → starts from stored stepIndex (the confirmation gate)
      → re-executes that step (user already confirmed)
      → continues to stepIndex+1, stepIndex+2, ...
      → output chaining still works — stepOutputs from prior steps preserved
```

The runner instance is the **same object** that was paused — `stepOutputs` map, `confirmedSteps` set, and pending step state are all retained. This is why the chaining works correctly after resuming.

---

## 7. Fallback Strategy

When a primary action fails (no handler, ActivityNotFoundException, IllegalArgumentException):

```
tryFallback(sourceStep, sourceSpec, reason):
  for fallbackId in sourceSpec.fallbackActionIds:
    fallbackSpec = ActionSpecRegistry.find(fallbackId) ?: continue
    fallbackParams = FallbackParamMapper.mapParams(sourceSpec, sourceStep, fallbackSpec)
    fallbackIntent = buildExecutableIntent(fallbackSpec, fallbackParams)
    resolved = resolveActivity(fallbackIntent)  ?: continue
    context.startActivity(fallbackIntent)
    return ExecutionResult(success=true, "Started fallback ${fallbackSpec.label}")
  return ExecutionResult(success=false, "No fallback available: $reason")
```

Fallbacks are defined in `ActionSpecRegistry` per action. Example: Google Chrome → Firefox fallback.

---

## 8. Silent Actions (No App Switch)

Certain actions execute silently in the background without launching an activity:

| Action | Executor | Method |
|--------|----------|--------|
| `calendar.create_event` | `CalendarApiExecutor` | `ContentResolver.insert(calendars, values)` |
| `clipboard.copy_text` | `ClipboardApiExecutor` | `ClipboardManager.setPrimaryClip()` |
| `alarm.set_alarm` | `AlarmApiExecutor` | `AlarmManager.setAlarmClock()` |
| `share.share_text` | `ClipboardApiExecutor` | `ClipboardManager.setPrimaryClip()` |
| `share.share_image` | `ClipboardApiExecutor` | `ClipboardManager.setPrimaryClip()` |
| `*` with `custom_tab_params` | `ChromeCustomTabOpener` | `CustomTabsIntent.Builder.build().launchUrl()` |

---

## 9. Notification Channels & IDs

| Channel ID | Name | Purpose |
|-----------|------|---------|
| `workflow_confirm` | Workflow Confirmation | Prompts user to confirm background workflow steps |

Confirmation notification IDs are `1000 + workflowName.hashCode()` to allow per-workflow replacement.

---

## 10. Deep Links (Confirmation → App)

```
showConfirmationNotification():
  Intent(context, MainActivity::class.java)
    .putExtra(EXTRA_WORKFLOW_NAME, workflowName)
    .putExtra(EXTRA_STEP_INDEX, stepIndex)
    .putExtra(EXTRA_ACTION, ACTION_CONFIRM)  OR  ACTION_DISMISS
    .flags = FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP

MainActivity.onNewIntent():
  when intent.getStringExtra(EXTRA_ACTION):
    ACTION_CONFIRM → TriggerRegistry.confirmAndResume(this, workflowName)
    ACTION_DISMISS → TriggerRegistry.dismissConfirmation(this, workflowName)
```

---

## 11. Startup Restore

When the app starts, `GemmaWorkflowApp.onCreate()` calls `registerAll()` on each manager. This loads all workflows from `WorkflowRepository` and re-registers their system-level triggers. Without this step, triggers would not survive a device restart.

```
App.onCreate()
  → TriggerRegistry.init(this)
  → BatteryTriggerManager.registerAll(this)
  → ChargerTriggerManager.registerAll(this)
  → WiFiTriggerManager.registerAll(this)
  → BluetoothTriggerManager.registerAll(this)
  → AirplaneModeTriggerManager.registerAll(this)
  → DndTriggerManager.registerAll(this)
  → GeofenceManager.registerAll(this)        // P1
  → SmsTriggerManager.registerAll(this)        // P1
  → AlarmTracker.init(this)                    // P1
```

Each `registerAll()` reads all workflows with the matching trigger type and calls `registerWorkflow()` to restore the registration.