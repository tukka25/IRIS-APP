# IrisApp — Android On-Device AI Workflow Automation

> **Platform:** Android (Kotlin + Jetpack Compose + LiteRT-LM)
> **Goal:** On-device SLM turns natural language into executable cross-app workflows.

---

## Architecture

```
User prompt
    │
    ▼
┌─────────────────────┐
│   LiteRT-LM          │  ← Gemma 4 2B on-device via LiteRT-LM
│   Planner Pipeline   │  ← 4-stage: RequestAnalysis → Capability → ActionPlan → JSON
└────────┬────────────┘
         │ JSON workflow
         ▼
┌─────────────────────┐
│  Validator          │  ← ActionSpec allowlist, param types, trigger compat
│  Parser             │
└────────┬────────────┘
         │ PlannedWorkflow
         ▼
┌─────────────────────┐
│  WorkflowRepository │  ← JSON file storage, survives restart
│  (JsonFileStorage)  │
└────────┬────────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
 Run Now   Trigger
    │         │
    ▼         ▼
WorkflowRunner   NFC / Time / Share Sheet
    │           │
    ▼           ▼
 IntentFactory  AlarmManager / NDEF / ACTION_SEND
    │
    ▼
 Android OS — execute cross-app actions
```

---

## Action Catalog

| Action | Description | Silent |
|---|---|---|
| `browser.open_url` | Open URL in Chrome Custom Tab | No |
| `maps.open_place` | Open a place in Maps | No |
| `share.share_text` | Copy text to clipboard | Yes |
| `share.share_image` | Copy image URI to clipboard | Yes |
| `sms.compose` | Open SMS composer | No |
| `alarm.set_alarm` | Set silent alarm via AlarmManager | Yes |
| `clipboard.copy_text` | Copy text to clipboard | Yes |
| `calendar.create_event` | Create calendar event via CalendarProvider | Yes |

---

## Trigger System

| Trigger | Setup | Execution |
|---|---|---|
| **Manual** | Always available | Tap "Run Now" in workflow detail |
| **Time** | TimeTriggerSetupScreen → schedule via AlarmManager | Notification → Confirm / Dismiss / Run Now |
| **NFC** | NfcSetupScreen → write tag with `iris://workflow/{id}` | Scan tag → confirm → run |
| **Share Sheet** | ShareSheetSetupScreen → enable for workflow | Share content → pick workflow → confirm → run |

**Time trigger persistence:** `BootReceiver` reschedules all time triggers after device reboot. `IrisAppApp.onCreate()` reschedules on every cold start.

---

## Source Tree

```
app/src/main/java/com/iris/
├── app/
│   └── IrisAppApp.kt                    # Cold-start: reschedule time triggers
├── data/
│   ├── local/storage/
│   │   └── JsonFileStorage.kt                 # Generic JSON file CRUD
│   ├── repository/
│   │   ├── WorkflowRepository.kt              # Workflow save/load/delete
│   │   └── ExecutionHistoryRepository.kt      # Append-only execution log
│   └── seed/
│       └── DemoWorkflowSeeder.kt             # Seed demo workflows on first run
├── domain/
│   ├── catalog/
│   │   └── ActionSpecRegistry.kt             # Action specs + IntentFactory dispatch
│   ├── model/
│   │   ├── WorkflowModels.kt                 # PlannedWorkflow, WorkflowStep, TriggerConfig
│   │   └── SharedContent.kt                  # Share sheet content: Text / Image
│   ├── parser/
│   │   └── WorkflowJsonParser.kt             # JSON → typed workflow
│   ├── planner/
│   │   ├── PlannerService.kt                 # Orchestrates planner stages
│   │   ├── PlannerAgents.kt                  # 4-stage prompt agents
│   │   └── PromptBuilder.kt                 # System prompt builder
│   ├── runner/
│   │   ├── WorkflowRunner.kt                 # Step-by-step execution + confirmation
│   │   ├── IntentFactory.kt                  # Intent + CustomTab + BuiltIn dispatch
│   │   └── FallbackParamMapper.kt           # Fallback chains per action
│   ├── safety/
│   │   └── WorkflowValidator.kt              # Schema + allowlist validation
│   └── triggers/
│       └── TriggerCatalog.kt                # TriggerCatalog + TriggerRegistry
├── platform/
│   ├── alarm/
│   │   ├── AlarmApiExecutor.kt               # Silent AlarmManager scheduling
│   │   ├── AlarmReceiver.kt                 # Fires notification on alarm
│   │   ├── BootReceiver.kt                  # Reschedules after reboot
│   │   ├── TimeTriggerReceiver.kt           # Time trigger broadcast receiver
│   │   └── TimeTriggerScheduler.kt          # AlarmManager scheduling + reschedule
│   ├── calendar/
│   │   └── CalendarApiExecutor.kt           # ContentResolver.insert for calendar
│   ├── capability/
│   │   ├── ChromeCustomTabOpener.kt         # CustomTabsIntent in-app browser
│   │   ├── ClipboardApiExecutor.kt          # ClipboardManager silent copy
│   │   ├── IntentDiscoveryEngine.kt         # Dynamic intent discovery
│   │   └── PackageCapabilityScanner.kt      # Installed app capability scanning
│   ├── inference/
│   │   └── InferenceManager.kt              # Singleton model lifecycle
│   ├── nfc/
│   │   ├── DeepLinkRouter.kt                # iris:// routing + foreground NFC
│   │   ├── NfcTriggerHandler.kt             # Background NFC scan receiver
│   │   └── NfcTriggerWriter.kt              # NDEF tag writer
│   └── share/
│       └── ShareSheetTriggerHandler.kt       # Share → workflow matching
└── ui/
    ├── MainActivity.kt                      # Navigation + confirmation dialog
    ├── home/
    │   ├── WorkflowGenerationUiState.kt    # UI state + trigger state
    │   ├── WorkflowGenerationViewModel.kt  # Generation + trigger setup logic
    │   ├── NfcTriggerSetupScreen.kt        # NFC tag writing UI
    │   ├── ShareSheetSetupScreen.kt        # Share sheet trigger UI
    │   └── TimeTriggerSetupScreen.kt       # Time picker + scheduling UI
    ├── nfc/
    │   └── NfcSetupScreen.kt               # NFC tag writing composable
    └── trigger/
        ├── TimeTriggerConfirmationActivity.kt  # Notification action: confirm/dismiss/run
        ├── TimeTriggerNotification.kt          # Notification channel + builder
        └── TimeTriggerPicker.kt                # Time picker UI
```

---

## Build

```bash
# Push model to device (one-time)
adb push local_models/gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.irisapp/files/models/

# Build
./gradlew installDebug

# Or from PowerShell on Windows (no Java in WSL)
.\gradlew installDebug
```

---

## Docs

| File | What |
|---|---|
| `docs/MILESTONE_6_STATUS.md` | Full milestone status vs. Issue #7 checklist |
| `docs/research/silent_calendar_alarm_api.txt` | Silent calendar + alarm Android API research |
| `docs/research/browser_share_silent_actions.md` | Chrome Custom Tabs + clipboard silent research |
| `ARCHITECTURE.md` | Design doc — architecture, packages, contracts |
| `INTENTS.md` | Declarative intent planning — ActionSpec contracts |
| `WORKFLOW_FEATURE.md` | Planner pipeline design |
| `TASKS.md` | Hackathon task breakdown |
