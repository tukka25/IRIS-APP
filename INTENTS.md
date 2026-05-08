# Declarative Intent Planning

Build a Kotlin orchestrator for on-device SLM workflow generation:

```text
User request -> SLM chooses ActionSpec ids + params -> Kotlin validates -> IntentFactory builds Intent -> Runner executes
```

## Key Rules

- The SLM never builds raw Android intents.
- The SLM never outputs Android action constants, extra keys, URI templates, or package-private APIs.
- `ActionSpecRegistry` is the source of truth for action ids, parameter schemas, Android intent contracts, extras, trigger compatibility, examples, and confirmation policy.
- `PackageManager` validates availability and resolvability only.
- `WorkflowValidator` validates schema, typed params, trigger compatibility, unknown params, URL/URI schemes, availability, and confirmation requirements.
- `IntentFactory` is the only place that converts validated params into Android `Intent` objects.
- `WorkflowRunner` executes only validated ActionSpec-backed intents.

## Runtime Flow

| Step | Owner | Action | Source Of Truth | Output |
|------|-------|--------|-----------------|--------|
| 1 | User | Enters request, e.g. "Send an SMS to Bob when I tap run." | User text | Raw request |
| 2 | SLM | Analyzes goal and trigger hint | Prompt rules | Analysis JSON |
| 3 | Kotlin | Builds available capability list | `ActionSpecRegistry` + `PackageManager` | Available ActionSpecs |
| 4 | SLM | Selects action ids and fills params | Prompt-safe ActionSpec summaries | Draft workflow JSON |
| 5 | Kotlin | Parses and validates JSON | `WorkflowJsonParser` + `WorkflowValidator` | Valid workflow or errors |
| 6 | Kotlin | Builds real Android intent | `IntentFactory` | `Intent` |
| 7 | Kotlin | Executes | `WorkflowRunner` | `ExecutionResult` |

## What PackageManager Can And Cannot Do

`PackageManager` can answer:

- Is an app installed?
- Can any activity resolve this sample intent?
- Which apps can handle this action/data/MIME combination?
- Which labels/packages are available as possible user-visible targets?

`PackageManager` cannot reliably answer:

- Every private API an installed app supports.
- The semantic meaning of every extra key.
- The full parameter schema for arbitrary third-party app actions.
- Whether a private or undocumented intent will keep working.

## Declarative ActionSpec Shape

Each action has a public schema for the model and a private execution contract for Kotlin.

```json
{
  "id": "sms.compose",
  "description": "Open SMS app with a prefilled message",
  "params": {
    "phone": "optional string",
    "message": "required string"
  }
}
```

Kotlin privately owns:

```text
Intent action: android.intent.action.SENDTO
Data template: smsto:{phone}
Extra mapping: message -> sms_body
Flags: FLAG_ACTIVITY_NEW_TASK
```

## ActionSpecs (Implemented)

- `browser.open_url`
- `maps.open_place`
- `share.share_text`
- `share.share_image`
- `sms.compose`
- `alarm.set_alarm`
- `calendar.create_event`
- `clipboard.copy_text`

## Prompt Boundary

The model receives only prompt-safe fields:

```text
id
description
params
trigger compatibility
requires confirmation
examples
```

The model does not receive:

```text
Intent.ACTION_SEND
Intent.EXTRA_TEXT
AlarmClock.EXTRA_HOUR
CalendarContract.EXTRA_EVENT_BEGIN_TIME
package-private implementation details
```

## Design Goal

This avoids hallucinated Android APIs while still letting the SLM decide:

- Which user goal is being requested.
- Which available action ids best match the request.
- Which typed params should be filled.
- Which trigger should be suggested.

Kotlin remains the execution authority.
