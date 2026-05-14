I'm building an Android automation app called IrisApp that generates
workflows from natural language (e.g. "send message to Maya saying hi at 6pm
next Friday and add to calendar"). It runs Gemma 4 E2B IT (2.4GB) on-device
via LiteRT-LM GPU backend.

I'm using a RETO-inspired orchestration architecture:

RETO (Robust & Efficient Tool Orchestration) splits tool calling into coarse
layers. Instead of giving the SLM all tools at once:
  - A deterministic layer planner scans the request for domain keywords
    (time, contact, media, files, notes, SMS, calendar) and assigns only
    the relevant tools to Layer 0 (fact grounding).
  - The layer executor constrains each SLM call to only the tools in that
    layer, reducing decision complexity.
  - If the model signals LAYER_DONE without calling all layer tools, the
    executor injects a nudge listing uncalled tools.
  - Observations are stored as compact typed facts, not raw transcripts.

Current tools (20 total):
  Temporal: get_current_time, resolve_datetime, compute_duration, get_day_of_week
  Device: list_installed_apps, resolve_intent, get_device_location
  Search: web_search, search_places, get_contact, lookup_contact
  Domain: search_media, search_files, search_notes, search_sms, get_calendar_events
  Execution: send_intent, open_uri, share_text, set_alarm, create_calendar_event
  Reasoning: calculator, validate_json

THE PROBLEM: The SLM (Gemma 4 E2B) often fails to call ALL needed tools in a
layer. For "send message to Maya...next Friday...add to calendar", it calls
resolve_datetime but skips get_contact for "Maya". It sees "Maya" as just a
name, not as an entity requiring resolution.

I've tried:
  1. Entity type guide in the prompt ("NAME → get_contact, PLAYLIST →
     search_media") — works sometimes but doesn't scale to new domains.
  2. Entity pre-processing (regex extract names, query ContactsContract
     before the SLM sees the prompt) — removed because it's not scalable
     (only handles contacts, not playlists, files, notes, etc.).
  3. Audit-first prompt ("STEP 1: identify what you need. STEP 2: call
     tools. STEP 3: analyze") — helps but model still skips tools.
  4. RETO layered execution with minimum-call enforcement — the executor
     nudges the model when it stops early, which helps but adds latency
     from extra model calls.

QUESTIONS:
  1. With RETO's layer-constrained execution, what's the best way to ensure
     an SLM calls ALL matching tools for every identified entity before
     moving to the next layer? Is fine-grained sub-layering (one tool per
     layer) a viable approach or does it introduce too many model calls?

  2. What domains am I missing that would cover common Android automation
     use cases? I have contacts, media, files, notes, SMS, calendar. Should
     I add: reminders, timers, browser bookmarks, settings toggles, WiFi,
     Bluetooth, notifications, clipboard? Which 5 would you prioritize?

  3. Long-term: should I accept that an on-device 2.4GB SLM will always
     need some external scaffolding (deterministic layer planner, minimum-
     call enforcement nudge) vs. fine-tuning a domain-specific entity
     extractor, vs. waiting for better on-device models?

Context: Kotlin 2.3.20, AGP 8.7.3, LiteRT-LM 0.10.0, minSdk 26.
