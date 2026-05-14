# IrisApp Hackathon Task Plan

## Working Assumptions

- Team size: 1 to 4 people.
- Timeline: 3 to 4 days.
- Target: physical Android phone for the final demo.
- Stable demo path: prompt -> JSON -> save -> run now -> optional NFC/Tasker trigger.
- Mock planner is mandatory so the app can be demoed even if LiteRT-LM GPU integration is still being tuned.

## Priorities

- P0: required for a credible demo.
- P1: strong demo improvement.
- P2: stretch only after the P0 path is reliable.

## Milestone 0: Day-Zero Setup

- [ ] P0 Install Android Studio.
- [ ] P0 Install Android SDK and NDK through SDK Manager.
- [ ] P0 Create or borrow a physical Android phone.
- [ ] P0 Enable developer mode and USB debugging on the phone.
- [ ] P0 Confirm `adb devices` sees the phone.
- [ ] P0 Install demo target apps on the phone.
- [ ] P0 Choose Android-real demo targets: Spotify, Obsidian or share sheet, Maps, Browser.
- [ ] P1 Install Tasker on the phone if using Tasker-assisted triggers.
- [ ] P1 Obtain NFC tags if using the NFC demo.
- [ ] P1 Clone LiteRT-LM locally and verify its Android example (Google AI Edge Gallery).
- [ ] P1 Download a pre-converted Gemma .litertlm model from HuggingFace litert-community.
- [ ] P1 Write down the exact demo prompt and expected workflow.

Deliverable: phone, tools, target apps, and demo prompt are ready.

## Milestone 1: Android Project Scaffold

- [ ] P0 Create Android project with Kotlin, Compose, and Gradle Kotlin DSL.
- [ ] P0 Set package name, app name, min SDK, and compile SDK.
- [ ] P0 Add Compose Material 3.
- [ ] P0 Add Kotlin coroutines.
- [ ] P0 Add kotlinx.serialization.
- [ ] P0 Add Room dependencies.
- [ ] P0 Add DataStore dependencies.
- [ ] P0 Create package structure from `ARCHITECTURE.md`.
- [ ] P0 Create `MainActivity`.
- [ ] P0 Create app theme.
- [ ] P0 Add simple navigation between Home, Workflow List, Workflow Detail, and Trigger Setup.
- [ ] P0 Run the empty app on a device or emulator.

Deliverable: app launches and navigates between placeholder screens.

## Milestone 2: Data Contracts And Parser

- [ ] P0 Create `PlannedWorkflow`.
- [ ] P0 Create `WorkflowStep`.
- [ ] P0 Create `TriggerConfig`.
- [ ] P0 Create `ExecutionResult`.
- [ ] P0 Create planner JSON schema examples.
- [ ] P0 Implement `WorkflowJsonParser`.
- [ ] P0 Support extracting JSON from raw model text if the model adds extra text.
- [ ] P0 Add parser tests for valid workflow JSON.
- [ ] P0 Add parser tests for invalid JSON.
- [ ] P0 Add parser tests for missing required fields.
- [ ] P0 Implement `ActionCatalog`.
- [ ] P0 Implement `SafeActionRouter`.
- [ ] P0 Reject unknown apps.
- [ ] P0 Reject unknown actions.
- [ ] P0 Reject missing required params.
- [ ] P0 Reject unsafe URLs such as unsupported schemes.

Deliverable: generated JSON can become a validated typed workflow.

## Milestone 3: Mock Planner And Prompt Builder

- [ ] P0 Create `PlannerEngine` interface.
- [ ] P0 Create `PromptBuilder`.
- [ ] P0 Keep prompt compact and Android-specific.
- [ ] P0 Include only supported MVP actions in the prompt catalog.
- [ ] P0 Create `MockPlannerEngine`.
- [ ] P0 Make the mock planner return the known-good demo workflow.
- [ ] P0 Create `PlannerService` that calls prompt builder, engine, parser, and validator.
- [ ] P0 Add UI/backend setting for Mock vs Llama, defaulting to Mock.
- [ ] P1 Add multiple mock examples for different prompts.

Deliverable: entering the demo prompt produces validated workflow JSON without native inference.

## Milestone 4: Compose UI

- [ ] P0 Build Home screen.
- [ ] P0 Add prompt text field.
- [ ] P0 Add Generate button.
- [ ] P0 Add backend selector.
- [ ] P0 Show loading state while generating.
- [ ] P0 Show raw JSON preview.
- [ ] P0 Show parsed workflow preview.
- [ ] P0 Add Save button.
- [ ] P0 Build Workflow List screen.
- [ ] P0 Show saved workflow cards.
- [ ] P0 Build Workflow Detail screen.
- [ ] P0 Show workflow name, trigger, actions, raw JSON, and history.
- [ ] P0 Add Run Now button.
- [ ] P1 Add delete workflow.
- [ ] P1 Add copy JSON button.
- [ ] P1 Add seeded demo prompt button.
- [ ] P1 Add friendly error states for parser, validation, model load, and execution errors.

Deliverable: user can generate, inspect, save, list, and open workflows.

## Milestone 5: Persistence

- [ ] P0 Create `WorkflowEntity`.
- [ ] P0 Create `ExecutionHistoryEntity`.
- [ ] P0 Create `WorkflowDao`.
- [ ] P0 Create `ExecutionHistoryDao`.
- [ ] P0 Create `AppDatabase`.
- [ ] P0 Create `WorkflowRepository`.
- [ ] P0 Save generated workflow.
- [ ] P0 Load all saved workflows.
- [ ] P0 Load workflow detail by ID.
- [ ] P0 Save execution history.
- [ ] P1 Delete workflow and history.
- [ ] P1 Store selected backend in DataStore.
- [ ] P1 Seed one demo workflow in debug builds.

Deliverable: workflows survive app restart.

## Milestone 6: Workflow Runner

- [ ] P0 Create `WorkflowRunner`.
- [ ] P0 Run workflow steps sequentially.
- [ ] P0 Continue or stop on failure based on a simple policy.
- [ ] P0 Create `IntentDispatcher`.
- [ ] P0 Create `UrlDispatcher`.
- [ ] P0 Implement browser `open_url(url)`.
- [ ] P0 Implement Maps `open_place(query)`.
- [ ] P0 Implement Android share sheet `share_text(text)`.
- [ ] P0 Spike Spotify `play_search(query)` on the demo phone.
- [ ] P0 Spike Obsidian note creation or share path on the demo phone.
- [ ] P0 Add installed-app checks where needed.
- [ ] P0 Write execution results to history.
- [ ] P1 Add per-step delay support if switching apps too quickly causes failures.
- [ ] P1 Add "dry run" preview.

Deliverable: tapping Run Now visibly executes at least two cross-app steps on the phone.

## Milestone 7: NFC Trigger

- [ ] P1 Add `android.permission.NFC`.
- [ ] P1 Add deep link route: `gemmaworkflow://run/{workflowId}`.
- [ ] P1 Add manifest intent filter for the deep link.
- [ ] P1 Create `NfcTriggerWriter`.
- [ ] P1 Write NDEF deep link to an NFC tag.
- [ ] P1 Create Trigger Setup screen for NFC.
- [ ] P1 Test writing tag on physical phone.
- [ ] P1 Test scanning tag opens the workflow.
- [ ] P1 Test scanning tag can start workflow execution after user confirmation.
- [ ] P2 Add foreground NFC read mode for easier debugging.

Deliverable: NFC tag can launch or run a saved workflow.

## Milestone 8: Optional Tasker Integration

- [ ] P1 Decide if Tasker is needed for the hackathon demo.
- [ ] P1 Create Tasker plugin edit activity if needed.
- [ ] P1 Let the user pick a workflow inside the Tasker plugin edit activity.
- [ ] P1 Return a concise Tasker blurb.
- [ ] P1 Return plugin configuration bundle with workflow ID.
- [ ] P1 Create Tasker fire receiver.
- [ ] P1 On fire, load workflow by ID and run it.
- [ ] P1 Test a Tasker profile that fires the plugin.
- [ ] P2 Spike Tasker profile import or prefilled setup only after plugin firing works.

Deliverable: Tasker can trigger a IrisApp workflow.

## Milestone 9: LiteRT-LM GPU Inference

- [ ] P1 Clone or add LiteRT-LM as a sibling repo (scripts/setup_litert_lm.sh).
- [ ] P1 Add LiteRT-LM Gradle dependency (`com.google.ai.edge.litertlm:litertlm-android`).
- [ ] P1 Add GPU native lib declarations to AndroidManifest.xml (OpenCL + Vulkan).
- [ ] P1 Create `LitertLmEngine` wrapping LiteRT-LM Kotlin API (Engine / Conversation).
- [ ] P1 Create `ModelFileLocator` for `.litertlm` model files.
- [ ] P1 Push a `.litertlm` Gemma model to device via adb.
- [ ] P1 Load the model once with `Backend.GPU()` and keep engine instance alive.
- [ ] P1 Generate text on a background dispatcher via coroutines.
- [ ] P1 Add model loading UI state (load status, errors).
- [ ] P1 Add generation timing logs.
- [ ] P1 Tune sampler config (topK, topP, temperature) on the physical phone.
- [ ] P1 Fall back to mock planner if model load or generation fails.
- [ ] P2 Add user-selectable external model path.

Deliverable: LiteRT-LM GPU mode can produce JSON for the demo prompt on the phone.

## Milestone 10: Safety And Guardrails

- [ ] P0 Require validation before saving or running generated workflows.
- [ ] P0 Show user-visible workflow preview before execution.
- [ ] P0 Prevent unsupported URL schemes.
- [ ] P0 Prevent unknown packages.
- [ ] P0 Prevent script execution in MVP.
- [ ] P0 Avoid storing secrets in workflow JSON.
- [ ] P1 Add confirmation before running externally visible actions.
- [ ] P1 Add clear error messages for rejected model output.

Deliverable: generated output cannot directly execute unsupported actions.

## Milestone 11: Demo Polish

- [ ] P0 Create final demo prompt.
- [ ] P0 Create final saved demo workflow.
- [ ] P0 Test full path in mock mode.
- [ ] P1 Test full path in llama mode.
- [ ] P1 Record backup video of the successful demo.
- [ ] P1 Add readable app title and icon.
- [ ] P1 Add execution success/failure indicators.
- [ ] P1 Add compact JSON preview collapse/expand.
- [ ] P1 Add empty states for workflow list.
- [ ] P1 Rehearse demo narration.
- [ ] P1 Prepare fallback plan: use mock mode if inference fails.

Deliverable: demo can be run live or recovered quickly.

## Suggested 4-Day Schedule

### Day 1: App Foundation

- [ ] Finish Milestone 1.
- [ ] Finish Milestone 2.
- [ ] Finish Milestone 3.
- [ ] Start Milestone 4.

End-of-day demo: prompt creates validated workflow JSON in mock mode.

### Day 2: Product Flow

- [ ] Finish Milestone 4.
- [ ] Finish Milestone 5.
- [ ] Start Milestone 6 with Browser, Maps, and ShareSheet.

End-of-day demo: generate, save, list, open, and run a visible workflow.

### Day 3: Cross-App Execution And Trigger

- [ ] Finish P0 tasks in Milestone 6.
- [ ] Spike Spotify and Obsidian.
- [ ] Build NFC trigger from Milestone 7 or Tasker plugin from Milestone 8.
- Start LiteRT-LM integration if the demo flow is already stable.

End-of-day demo: saved workflow runs on the phone and has one trigger path.

### Day 4: Model And Polish

- [ ] Continue Milestone 9.
- [ ] Complete Milestone 10.
- [ ] Complete Milestone 11.
- [ ] Record backup video.
- [ ] Rehearse live flow.

Final demo: natural language to local or mock-planner workflow, save, run, trigger, and explain on-device inference.

## Role Split

### Android/UI Owner

- Compose screens.
- Navigation.
- UI state and ViewModels.
- Visual polish.

### Domain/Data Owner

- Data models.
- Parser.
- Action catalog.
- Safe action router.
- Room repository.

### Automation Owner

- Workflow runner.
- Intent and URL dispatchers.
- NFC trigger.
- Tasker plugin if used.

### Native/Inference Owner
| Native/Inference Owner |
|- LiteRT-LM Gradle integration.
|- LitertLmEngine wrapper.
|- Model file loading / pre-converted .litertlm models.
|- GPU backend configuration and performance tuning. |

For a solo build, follow the milestone order and keep Tasker plus LiteRT-LM behind the mock-first path.

## Critical Risks And Fallbacks

| Risk | Impact | Fallback |
|------|--------|----------|
| LiteRT-LM build fails / GPU unavailable | No live local model | Use mock planner; fall back to CPU backend. |
| Inference is too slow | Demo stalls | Preload model, cap token output, use mock mode for live demo. |
| Model returns bad JSON | Workflow cannot save | Use grammar, parser repair, and mock fallback. |
| Spotify intent is unreliable | Demo action fails | Use Browser, Maps, or ShareSheet as reliable visible actions. |
| Obsidian note creation is unreliable | Note step fails | Use Android share sheet to send note text. |
| NFC write fails | Trigger demo fails | Use Run Now or Tasker profile. |
| Tasker profile automation is limited | Trigger setup takes too long | Use Tasker plugin or NFC deep link instead. |

## Final Definition Of Done

- [ ] App installs on physical Android phone.
- [ ] User can enter a prompt and generate a workflow.
- [ ] User can inspect planner JSON.
- [ ] User can save workflow locally.
- [ ] User can reopen saved workflow.
- [ ] User can run workflow manually.
- [ ] At least two visible Android actions execute.
- [ ] Execution history records results.
- [ ] One trigger path works or is clearly demoed.
- [ ] Mock fallback is available.
- [ ] LiteRT-LM path is either working or isolated so it cannot break the demo.

