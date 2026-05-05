# Workflow Generation With LiteRT-LM Planning Pipeline

## Summary

Build the next feature as an end-to-end **natural language -> generated workflow -> validated preview -> saved Routine -> manual run**, using the existing LiteRT-LM GPU engine. The model should load by default on app start, then the Generate button should call a staged planner pipeline that behaves like multiple agents while reusing one loaded LiteRT-LM engine.

Current repo context:
- Active inference is LiteRT-LM, not llama.cpp.
- `LitertLmEngine` already wraps `Engine`, `Conversation`, `Backend.GPU()`, and generation.
- The app currently has a smoke-test UI with legacy names like `LlamaSmokeViewModel`, but the implementation is LiteRT-LM.
- Local model exists: `local-models/gemma-4-E2B-it.litertlm`.
- Workflow/domain/data packages are still mostly placeholders.

## Key Implementation Changes

- Rename smoke-test concepts to LiteRT/workflow names so future agents do not confuse old llama.cpp work:
  `LlamaSmokeViewModel` -> `WorkflowGenerationViewModel`, `LlamaSmokeUiState` -> `WorkflowGenerationUiState`.

- Add a singleton-style `InferenceManager` that loads the default `.litertlm` model automatically when the app starts or when the Home/Ask screen first appears.
  Use `Backend.GPU()` by default, `cacheDir`, and expose `StateFlow<InferenceState>` with `Loading`, `Ready`, `MissingModel`, `GpuUnavailable`, and `Error`.

- Replace direct prompt generation with a staged planner pipeline:
  `User request -> Agent 1 request analysis -> capability grounding -> Agent 2 action plan -> Agent 3 final JSON -> parser/validator -> preview`.

- Treat “agents” as logical planner stages using the same loaded LiteRT-LM engine, not separate model instances.
  This avoids loading the model multiple times and keeps phone memory under control.

- Use this agent design:
  `RequestAnalysisAgent`: extracts user goal, likely trigger, schedule hints, candidate app categories, and missing setup.
  `CapabilityResolver`: deterministic Kotlin layer that maps requested apps/actions to the app’s supported capability catalog.
  `ActionPlanAgent`: receives only available capabilities and chooses concrete actions, parameters, prerequisites, and trigger.
  `WorkflowJsonAgent`: outputs the final strict JSON contract only.
  `WorkflowValidator`: deterministic parser/router that rejects unsafe or unsupported output and can optionally ask the model for one repair attempt.

- Do not let the AI invent arbitrary Android intents.
  Android private app intents are not reliably discoverable. Build an `ActionCatalog` of supported actions, then use `PackageManager.queryIntentActivities()` and manifest `<queries>` declarations to check what is installed and resolvable.

- Give the AI a compact normalized capability list, not raw Android APIs:
  action id, label, description, params schema, example, required package or MIME type, trigger compatibility, setup requirements.

- First supported action catalog:
  `browser.open_url(url)`, `maps.open_place(query)`, `share.share_text(text)`, `share.share_image(uri)`, `spotify.play_search(query)` if resolvable, `notes.save_text(title, content)` through Android share sheet fallback.

- First trigger catalog:
  `manual`, `time`, `nfc`, `share_sheet`, `tasker_setup_required`.
  The AI may recommend the trigger based on the request, but unsupported or unfinished triggers must save as `NeedsSetup` or `ManualOnly`, never silently pretend to be active.

- Persistence should be local Room, no backend for this feature.
  Add `WorkflowEntity`, `ExecutionHistoryEntity`, and optionally `TriggerRegistrationEntity`.
  Store generated workflow JSON, validated action JSON, trigger config JSON, enabled status, setup state, timestamps, and last run summary.

- Workflow statuses:
  `Draft`, `ManualOnly`, `NeedsSetup`, `Active`, `Off`, `Failed`.
  Turning on a workflow calls `TriggerRegistry.register(workflow)`.
  Turning off calls `TriggerRegistry.unregister(workflow)`.

- Runner flow:
  `WorkflowRunner` executes validated `WorkflowStep`s through dispatchers.
  Use `IntentDispatcher` and `UrlDispatcher` first.
  Record each step result in execution history.
  Require user confirmation before externally visible actions for MVP.

## Suggested JSON Contract

The final model output should be JSON only:

```json
{
  "name": "Receipt Saver",
  "summary": "Saves receipt photos and prepares an expense note.",
  "trigger": {
    "type": "share_sheet",
    "setup_state": "needs_setup",
    "schedule": null
  },
  "actions": [
    {
      "id": "share.share_text",
      "params": {
        "text": "Receipt summary: {{extracted_text}}"
      },
      "requires_confirmation": true
    }
  ],
  "missing_setup": [
    "Choose where receipt summaries should be saved"
  ]
}
```

Parser rules:
- Reject unknown trigger types.
- Reject unknown action ids.
- Reject missing required params.
- Reject unsupported URL schemes.
- Reject package names or custom intent strings not present in `ActionCatalog`.
- Keep raw model output for debugging, but save only validated workflows as runnable.

## Task Breakdown For Agent Execution

1. Inference foundation:
   Rename LiteRT smoke classes, add `InferenceManager`, auto-load the default model, expose loading state, and keep the engine alive across generations.

2. Domain contracts:
   Add `PlannedWorkflow`, `WorkflowStep`, `TriggerConfig`, `WorkflowStatus`, `SetupState`, `ExecutionResult`, and kotlinx.serialization setup.

3. Capability catalog:
   Add static MVP `ActionCatalog`, Android `PackageCapabilityScanner`, manifest `<queries>`, and a compact prompt-safe capability summary.

4. Planner pipeline:
   Add `PlannerEngine`, `PromptBuilder`, `RequestAnalysisAgent`, `ActionPlanAgent`, `WorkflowJsonAgent`, `WorkflowJsonParser`, `WorkflowValidator`, and `PlannerService`.

5. UI flow:
   Update Generate button to call `PlannerService.plan(userRequest)`, show agent-stage progress, show raw/parsed preview, and allow Save / Run Now.

6. Persistence:
   Add Room dependencies, database, DAO, repository, workflow save/load/update, execution history save/load.

7. Runner:
   Add `WorkflowRunner`, `IntentDispatcher`, `UrlDispatcher`, and first runnable actions: browser URL, maps place, Android share text, Android share image if available.

8. Trigger handling:
   Add `TriggerCatalog` and `TriggerRegistry`.
   MVP must always support manual run.
   AI-selected triggers that are not implemented yet should be saved as `NeedsSetup`.
   Add time or NFC as the first real automatic trigger after manual run works.

9. Safety:
   Add preview-before-run, confirmation requirement, URL scheme allowlist, app/action allowlist, and friendly error messages for invalid model output.

10. Demo polish:
   Seed one known-good prompt, one mock workflow fallback, and one saved demo workflow so the feature remains demoable if LiteRT output is malformed.

## Test Plan

- Unit test parser with valid JSON, extra text around JSON, malformed JSON, unknown action, unknown trigger, and missing params.
- Unit test `ActionCatalog` and `WorkflowValidator` against supported and unsupported actions.
- Unit test `PromptBuilder` to ensure only allowed capabilities are exposed to the model.
- Unit test `WorkflowRunner` with fake dispatchers to confirm ordered execution and failure capture.
- Room test for save, load, update status, delete, and history insert.
- Device test: app launches, model auto-loads on GPU, Generate returns a workflow preview, Save persists it, Run Now executes at least one visible Android action.
- Trigger test: AI can recommend a trigger, but unsupported triggers show `NeedsSetup`; manual run still works.

## Assumptions And Defaults

- No llama.cpp code should be reintroduced.
- No backend/server is needed for this feature; use local Room.
- LiteRT-LM model loading should happen once by default, not on every Generate tap.
- Multiple agents are logical stages over one LiteRT-LM engine, not multiple loaded models.
- Android app intent discovery must be allowlist-first because arbitrary installed app private intents are not a safe or reliable API surface.
- The first complete feature target is the MVP demo: generate, validate, save, preview, and manually run.
- The AI may choose the trigger, but the app decides whether that trigger is runnable, manual-only, or needs setup.
- Official docs consulted: LiteRT Android/GPU docs, Android Intent/PackageManager/package visibility docs, Android WorkManager/AlarmManager/NFC docs.
