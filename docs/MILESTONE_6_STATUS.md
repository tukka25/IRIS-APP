# Milestone 6 Status — Workflow Lifecycle: Store, Run, Trigger, Execute

**Issue:** [#7](https://github.com/tukka25/gemma4good_android/issues/7)
**Branch:** `7-milestone-6-workflow-lifecycle-store-run-trigger-execute`
**Commits ahead of origin/main:** 11

---

## Summary

Milestone 6 is substantially complete. The full pipeline from natural language → workflow generation → persistence → trigger-wired execution is implemented and committed. Three items remain open: preview-before-run UX, dry-run mode, and execution history UI.

---

## Generation Layer ✅ Complete

| Item | Status | Evidence |
|---|---|---|
| 4-stage planner (RequestAnalysis → Capability → ActionPlan → JSON) | ✅ | `PlannerService.kt`, `PlannerAgents.kt` |
| ActionSpecRegistry with typed actions | ✅ | `ActionSpecRegistry.kt` — 8 actions |
| IntentFactory builds Android Intents from specs | ✅ | `IntentFactory.kt` |
| FallbackParamMapper for fallback chains | ✅ | `FallbackParamMapper.kt` |
| WorkflowRunner per-step execution | ✅ | `WorkflowRunner.kt` |

---

## Persistence & Storage ✅ Complete

| Item | Status | Evidence |
|---|---|---|
| Save workflow JSON to device | ✅ | `JsonFileStorage.kt`, `WorkflowRepository.kt` |
| Workflow survives restart | ✅ | JSON files in app internal storage |
| Execution history persists | ✅ | `ExecutionHistoryRepository.kt` (append-only, last 100) |
| Load saved workflows on start | ✅ | `IrisAppApp.kt` + `DemoWorkflowSeeder.kt` |

---

## Workflow Execution ✅ Complete

| Item | Status | Evidence |
|---|---|---|
| IntentFactory → intent dispatch | ✅ | `WorkflowRunner.executeAndroidIntent()` |
| Fallback chains wired | ✅ | `resolveActivity` check + fallback map |
| Per-step execution with resolveActivity | ✅ | `d39ea8d` commit |
| Execution result recording | ✅ | `ExecutionResult` per step, `ExecutionLogEntry` per run |
| Error handling → fallback → user | ✅ | `catch` → fallback → `ExecutionResult.Failure` |

---

## Silent Action Execution ✅ Complete

| Action | Method | Status |
|---|---|---|
| `calendar.create_event` | `ContentResolver.insert(CalendarContract.Events.CONTENT_URI, values)` | ✅ Silent |
| `alarm.set_alarm` | `AlarmManager.setExactAndAllowWhileIdle()` + notification | ✅ Silent |
| `clipboard.copy_text` | `ClipboardManager.setPrimaryClip(ClipData.newPlainText())` | ✅ Silent |
| `share.share_text` | Redirected to `ClipboardApiExecutor` | ✅ Silent |
| `share.share_image` | Redirected to `ClipboardApiExecutor` | ✅ Silent |
| `browser.open_url` | Chrome Custom Tabs (`androidx.browser:browser`) | ✅ In-app |
| `sms.compose` | `Intent.ACTION_SENDTO` → SMS app | ⚠️ UI required (Android restriction) |
| `maps.open_place` | `Intent.ACTION_VIEW` with geo: URI | ⚠️ UI required |

---

## Trigger System 🟡 Complete

| Trigger | Status | Files |
|---|---|---|
| Manual run (tap button) | ✅ | Works — confirmed by user |
| NFC write tag + scan + run | ✅ | `NfcTriggerWriter`, `NfcTriggerHandler`, `DeepLinkRouter`, `NfcSetupScreen` |
| Time trigger via AlarmManager | ✅ | `TimeTriggerScheduler`, `TimeTriggerReceiver`, `BootReceiver`, `TimeTriggerNotification`, `TimeTriggerConfirmationActivity` |
| Share sheet (receive shared content) | ✅ | `ShareSheetTriggerHandler`, `ShareSheetSetupScreen` |
| TriggerRegistry register/unregister | ✅ | `TriggerCatalog.kt` |
| Setup state tracking | ✅ | `NfcTriggerSetupScreen`, `TimeTriggerSetupScreen`, `ShareSheetSetupScreen` |

**Note:** `BootReceiver` reschedules all Time-triggered workflows on device reboot. `IrisAppApp.onCreate()` reschedules on every cold start.

---

## Background Execution 🟡 Partial

| Item | Status | Note |
|---|---|---|
| AlarmManager exact-time triggers | ✅ | `AlarmApiExecutor`, `TimeTriggerScheduler` |
| WorkManager for recurring/deferred | ❌ | Not implemented — one-shot alarms only |
| Foreground service | ✅ Skipped | Not needed for MVP |

---

## Safety & UX 🟡 Partial

| Item | Status | Note |
|---|---|---|
| Confirmation dialog for requiresConfirmation steps | ✅ | `ConfirmationRequired` exception → `AlertDialog` in `MainActivity` |
| Preview-before-run | ⚠️ Partial | `workflowPreview` + `WorkflowDetailScreen` show the workflow; step-by-step intent breakdown not yet shown |
| Dry-run mode | ❌ | Not implemented |
| Execution history UI | ❌ | `ExecutionHistoryRepository` logs data; no browse UI |

---

## Remaining Work

| Priority | Item | Description |
|---|---|---|
| P1 | Preview-before-run | Show step-by-step breakdown of what each action will do (intent, params, target app) before execution. Confirm or cancel. |
| P1 | Dry-run mode | Run `WorkflowRunner` in intent-only mode — build and log intents without `startActivity()` or API calls. |
| P2 | Execution history screen | Browse past runs from `ExecutionHistoryRepository`. Show success/failure per step, timestamps, workflow name. |
| P2 | WorkManager recurring | Extend `TimeTriggerScheduler` to support recurring workflows (daily, weekly) via `WorkManager` or AlarmManager reschedule loop. |

---

## Branch Commit History

```
d70ab70 feat(triggers): NFC, time, and share sheet trigger system
3c6b87d feat: silent action execution — clipboard, calendar, alarm, Chrome Custom Tabs
dff8a91 alarm: silent scheduling via AlarmManager + notification on fire
8d123fc feat(domain/model): add ExecutionLogEntry for execution history tracking
504d3a3 fix(parser): handle unquoted capitalised string values in JSON repair
3bb1565 feat(data): JSON file-based persistence and demo workflow seeding
d39ea8d feat(domain/runner): per-step execution with resolveActivity and fallback chains
c6b49d4 feat: add saved workflows list UI and fix JSON parser resilience
1a58427 fix: add delay(16) after each inference call to prevent ANR dialog
53621e7 feat: expand intent catalog to 18 apps from real research
576f952 feat: curated intent catalog + IntentDiscoveryEngine for dynamic app intent discovery
```

---

## File Inventory (Milestone 6 additions)

```
app/build.gradle.kts                              +3 lines  (androidx.browser)
app/src/main/AndroidManifest.xml                 +102 lines (permissions, intent-filters, receivers)
app/src/main/java/com/iris/
  app/IrisAppApp.kt                         +62 lines  reschedule time triggers on start
  data/local/storage/JsonFileStorage.kt            NEW        JSON file CRUD
  data/repository/ExecutionHistoryRepository.kt    NEW        append-only execution log
  data/repository/WorkflowRepository.kt            modified   wired to JsonFileStorage
  data/seed/DemoWorkflowSeeder.kt                  NEW        seed demo workflows
  domain/catalog/ActionSpecRegistry.kt             modified   +CustomTab, +BuiltIn, clipboard, calendar
  domain/model/SharedContent.kt                    NEW        sealed Text/Image for share intent
  domain/model/WorkflowModels.kt                   modified   TriggerConfig sealed class
  domain/parser/WorkflowJsonParser.kt              modified   parser fixes
  domain/planner/PlannerAgents.kt                 modified   4-stage planner
  domain/planner/PlannerService.kt                 modified   planner orchestration
  domain/runner/IntentFactory.kt                  modified   fallback chain wiring
  domain/runner/WorkflowRunner.kt                 modified   ConfirmationRequired, confirmedSteps
  domain/triggers/TriggerCatalog.kt                modified   TriggerRegistry with Time scheduling
  domain/safety/WorkflowValidator.kt              modified   validation
  platform/alarm/AlarmApiExecutor.kt               modified   silent AlarmManager
  platform/alarm/AlarmReceiver.kt                  NEW        fires notification on alarm
  platform/alarm/BootReceiver.kt                  NEW        reschedule on reboot
  platform/alarm/TimeTriggerReceiver.kt            NEW        time trigger broadcast
  platform/alarm/TimeTriggerScheduler.kt           NEW        AlarmManager scheduling
  platform/calendar/CalendarApiExecutor.kt         NEW        ContentResolver.insert
  platform/capability/ChromeCustomTabOpener.kt     NEW        CustomTabsIntent
  platform/capability/ClipboardApiExecutor.kt     NEW        ClipboardManager
  platform/capability/IntentDiscoveryEngine.kt     NEW        dynamic intent discovery
  platform/capability/PackageCapabilityScanner.kt NEW        installed app scanning
  platform/nfc/DeepLinkRouter.kt                   NEW        foreground NFC + deep-link
  platform/nfc/NfcTriggerHandler.kt                NEW        background NFC receiver
  platform/nfc/NfcTriggerWriter.kt                 NEW        NDEF tag writer
  platform/share/ShareSheetTriggerHandler.kt       NEW        share → workflow routing
  ui/MainActivity.kt                              modified   trigger setup screens, confirmation dialog
  ui/home/NfcTriggerSetupScreen.kt                NEW
  ui/home/ShareSheetSetupScreen.kt               NEW
  ui/home/TimeTriggerSetupScreen.kt               NEW
  ui/home/WorkflowGenerationUiState.kt             modified   trigger state, NFC state, pending share
  ui/home/WorkflowGenerationViewModel.kt          modified   trigger setup methods, confirmPending
  ui/nfc/NfcSetupScreen.kt                        NEW
  ui/trigger/TimeTriggerConfirmationActivity.kt    NEW
  ui/trigger/TimeTriggerNotification.kt            NEW
  ui/trigger/TimeTriggerPicker.kt                  NEW
```
