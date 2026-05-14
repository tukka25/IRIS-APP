# IrisApp Category Tester

Use this as a manual test suite for the RETO planner. Each case is a realistic user query designed to exercise one logical action category from Phase 0.

## How To Run

1. Start the app and load the LiteRT model.
2. Paste one query into the prompt field.
3. Tap Generate.
4. Watch the filtered logs:

```bash
$HOME/Library/Android/sdk/platform-tools/adb logcat | grep -Ei "TaskDecomposer|CapabilityBinder|SlotGroundingPlanner|ResolverRegistry|WorkflowGeneration|WorkflowRunner|Tool call|Tool result|AI output"
```

For each case, check:

- Phase 0 decomposes into the expected `action`.
- Phase 1 selects a reasonable `action_id` from filtered candidates.
- Phase 2 uses only resolver tools that belong to the selected action params.
- Final workflow JSON has no hallucinated action ids or params.
- `missing_setup` appears only for real missing permissions, unavailable apps, unsupported triggers, or unresolved user details.

## Core Category Cases

| # | Category | Query | Expected Phase 0 | Likely ActionSpec | Expected Tools / Notes |
|---|---|---|---|---|---|
| 1 | `send_message` | `Text Maya that I will be there in 10 minutes.` | One task: `send_message`, contact mention `Maya` | `sms.compose`, or `whatsapp.send_text` if WhatsApp is installed and selected | `get_contact` for `phone`; message is literal |
| 2 | `make_call` | `Call Mom now.` | One task: `make_call`, contact mention `Mom` | `phone.dial` | `get_contact` for `phone`; should open dialer, not place a silent call |
| 3 | `create_event` | `Add a calendar event for dentist appointment next Tuesday at 3pm at Dubai Marina Dental.` | One task: `create_event`, time mention `next Tuesday at 3pm`, place mention | `calendar.create_event` | `resolve_datetime` for `begin_time_millis`; optional `search_places` for location |
| 4 | `set_reminder` | `Remind me tomorrow morning to submit the hackathon pitch deck.` | One task: `set_reminder`, time mention `tomorrow morning` | `internal.reminder.create`, fallback `calendar.create_event` | `resolve_datetime` for `time_millis`; title/message literals |
| 5 | `set_alarm` | `Set an alarm for 7:15 tomorrow morning called gym.` | One task: `set_alarm`, time mention `7:15 tomorrow morning` | `alarm.set_alarm` | `resolve_datetime` or direct hour/minutes; label literal |
| 6 | `open_app` | `Open YouTube.` | One task: `open_app`, app mention `YouTube` | `app.open` | `list_installed_apps` if package needs grounding; should not invent package names |
| 7 | `search` | `Search the web for Android common intents documentation.` | One task: `search` | `browser.search` | Query is literal; no grounding tool needed |
| 8 | `share` | `Share the text "I will join the meeting in five minutes" with another app.` | One task: `share` | `share.share_text` | Text is literal; Android sharesheet expected |
| 9 | `navigate` | `Navigate to Dubai Mall.` | One task: `navigate`, place mention `Dubai Mall` | `maps.navigate`, fallback `maps.open_place` | `search_places` optional; destination can remain literal if maps query handles it |
| 10 | `play_media` | `Play my focus playlist on Spotify.` | One task: `play_media`, media mention `focus playlist`, app hint `Spotify` | `spotify.search_and_play` if installed, fallback `media.play_from_search` | `search_media` may ground playlist; should fall back if Spotify unavailable |
| 11 | `open_file` | `Open the budget spreadsheet from my downloads.` | One task: `open_file`, file mention `budget spreadsheet` | `file.open` | `search_files` for `uri`; missing setup if file access unavailable |
| 12 | `take_note` | `Create a note called Groceries with milk, eggs, coffee, and bananas.` | One task: `take_note`, note mention `Groceries` | `note.create`, fallback `share.share_text` | Title/text literals; `search_notes` only if choosing a note app needs grounding |
| 13 | `check_notification` | `Check if I missed any WhatsApp notifications from Maya.` | One task: `check_notification`, app/contact mentions | Usually unsupported or setup required | Should not bind random intent. Should ask for notification listener setup or mark unsupported |
| 14 | `get_info` | `Find out whether it will rain in Dubai tomorrow morning.` | One task: `get_info` | `browser.search`, or internal answer path if added later | Query literal; may use `web_search` if available to that stage |
| 15 | `other` | `Make my phone more productive for the hackathon.` | One task: `other` or clarification-worthy planning request | No direct ActionSpec | Should ask for clarification, not invent a workflow |

## Chained Workflow Cases

These are better for stress testing decomposition, capability binding, and slot grounding together.

| # | Query | Expected Logical Tasks | Expected Notes |
|---|---|---|---|
| C1 | `send message to Maya saying hi, and invite him to meeting on 6 oclock on next friday and then add it to my calendar.` | `send_message` + `create_event` | Baseline you already tested. Should resolve contact and date; no missing setup for resolved datetime |
| C2 | `Call Mom, then text her that I booked dinner for Friday at 8pm.` | `make_call` + `send_message` | Same contact may be reused. `get_contact` should resolve Mom once or consistently for both tasks |
| C3 | `Create a calendar event for team demo next Monday at 10am, then remind me 30 minutes before.` | `create_event` + `set_reminder` | `resolve_datetime` for event time; reminder may use computed offset or a second datetime |
| C4 | `Navigate to Dubai Mall and share my ETA with Maya.` | `navigate` + `send_message` or `share` | Place grounding plus contact grounding. ETA may be missing unless navigation result provides it |
| C5 | `Open Spotify and play my workout playlist, then set a 45 minute timer.` | `open_app` + `play_media` + `set_alarm` | Tests app opening, media, and timer binding. Timer should prefer `alarm.set_timer` |
| C6 | `Open my budget spreadsheet and make a note that I need to update May expenses.` | `open_file` + `take_note` | File grounding plus note creation |
| C7 | `Search for the nearest pharmacy open now and navigate there.` | `search` + `navigate` | Good test for place search. It may need `search_places`; final destination should be grounded or literal |
| C8 | `Remind me tomorrow at 9am to call the dentist, then open the phone app.` | `set_reminder` + `open_app` or `make_call` depending interpretation | Watch whether Phase 0 distinguishes "remind me to call" from "call now" |

## Edge Cases To Watch

| Case | Query | What To Watch |
|---|---|---|
| Ambiguous time | `Schedule coffee with Sara at 6 next Friday.` | The model should preserve ambiguity or resolve using current timezone; avoid fake AM/PM certainty |
| Missing contact | `Text my dentist that I am running late.` | If dentist is not in contacts, `get_contact` should fail and workflow should show missing setup/info |
| Missing app | `Play my focus playlist on Spotify.` | If Spotify is not installed, Phase 1 should not force `spotify.search_and_play`; fallback is expected |
| Unsupported automation | `When I receive a WhatsApp from Maya, summarize it.` | Should likely need setup/unsupported notification listener, not pretend background WhatsApp access exists |
| Vague goal | `Help me study better every day.` | Should ask clarification or create a simple reminder only if the user gives a time/action |

## Pass Criteria

A run is healthy when:

- Phase 0 action category matches the user intent.
- Multi-step requests produce multiple logical tasks.
- Phase 1 candidate actions are small and relevant.
- Selected `action_id`s exist in `ActionSpecRegistry`.
- Tool calls are grounded by param needs, not random exploration.
- Final params match the typed schema for each selected action.
- Execution behavior is user-confirming for SMS, calls, calendar, and shares.


