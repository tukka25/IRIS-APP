# UX Analysis — GemmaWorkflow Dark Theme

**Branch:** `new_design` (`249c276` committed)
**Date:** 2026-05-13
**Status:** Phase 1-2 implemented, Phase 3+ pending

---

## Generate Tab (index 0)

| # | Element | Issue | Severity |
|---|---------|-------|----------|
| 1 | **Hero isolation** | HexHeroIcon sits alone with no visual connection to the prompt below — no shared axis, glow continuity, or layered depth. The 3D isometric home inside the hex has no relationship to the "GemmaWorkflow" wordmark. | Medium |
| 2 | **"GemmaWorkflow" wordmark** | Static large text — no animation, no gradient, no color pulse. Doesn't match the animated hero above it. | Low |
| 3 | **Saved workflows strip** | Glass boxes with `GlassSurface` fill are visually indistinguishable from plain background. No hover/press feedback differentiating them from surrounding empty space. | Medium |
| 4 | **Generate action button** | `GradientButton` on "Generate" is well-styled, but the primary action lacks a keyboard shortcut hint or voice input option visible on the screen. | Low |
| 5 | **Prompt field** | OutlinedTextField has no character counter, no example prompt pre-filled, and the supporting text is easy to miss on first read. | Low |
| 6 | **Model status card** | `ModelStatusCard` sits visually disconnected from the prompt — no shared container or visual grouping. A user could miss it entirely when writing a prompt. | Medium |

### Generate Tab Priority
1. ModelStatusCard grouping with prompt
2. Saved workflow card differentiation
3. Hero → wordmark animation alignment
4. Prompt field character counter + voice input affordance

---

## Workflows Tab (index 1)

| # | Element | Issue | Severity |
|---|---------|-------|----------|
| 1 | **Scene chip strip — no selection state** | Chips are not togglable/selected; `onChipClick` is a stub. Currently 4 chips but no visual indicator of which is "active". The strip is scrollable but shows no scroll indicators. | High |
| 2 | **Scene strip — no filter behavior** | Selecting a chip should filter the workflow list below it. Nothing happens when you tap. | Medium |
| 3 | **"+ New" button position** | Placed inline with the "Saved Workflows" title in the header row. On small screens this row could wrap or compress awkwardly. | Low |
| 4 | **Empty state** | "No workflows yet" + "Create one with AI or build manually" is plain text on glass background — no illustration, no CTA arrow pointing to the "+ New" button. | Medium |
| 5 | **WorkflowListCard — edit affordance** | Cards have edit button (`onEdit`) but no visual affordance for the edit action. Unclear if the whole card is tap-to-select or if there is a separate edit target. | Medium |
| 6 | **Card layout** | `workflow.name` + `workflow.summary` + action count — the card hierarchy is unclear. No scene tag, no relative time ("2h ago"), no device count icon. | Low |

### Workflows Tab Priority
1. Scene chip strip selection state (interaction dead)
2. Workflow empty state CTA
3. Card layout — add scene tag, relative time, device count
4. "+ New" button repositioning

---

## Debug Tab (index 2)

| # | Element | Issue | Severity |
|---|---------|-------|----------|
| 1 | **Card colors clash** | `Card` (Material default teal) on dark background is jarring — the Debug tab is the only tab still using raw `Card` without glassmorphic treatment. | High |
| 2 | **Token usage** | Displayed as raw numbers with no context — no daily limit, no percentage bar, no color coding for approaching limit. | Medium |
| 3 | **Unicode icons** | `"\u2713"`, `"\u25B6"`, `"\u25CB"` for stage status are fragile and accessibility-hostile. No screen-reader labels. | Low |
| 4 | **Monospace font** | `FontFamily.Monospace` for message content is correct for debug, but the grouping by `substringBefore(":")` label can produce ugly unformatted group names when labels are non-standard. | Low |

### Debug Tab Priority
1. `Card` → `GlassmorphicCard`
2. Token usage — add progress bar + color coding
3. Replace Unicode icons with semantic icons

---

## ManualWorkflowEditorScreen

Full-screen overlay for creating/editing workflows. **Zero dark theme treatment** — every component uses raw Material defaults on the `#06080d` dark background.

| # | Element | Issue | Severity |
|---|---------|-------|----------|
| 1 | **Zero dark theme** | Every component — `Card`, `OutlinedTextField`, `RadioButton`, `TimePicker`, `Slider`, `Switch`, `FilterChip` — uses raw Material defaults. `Text("Edit Workflow")` at line 310 renders black-on-black. | Critical |
| 2 | **No back button** | Header is just text — no arrow, no "Cancel" text button visible at the top level. User must infer `onCancel` is available via the back gesture. | High |
| 3 | **Trigger list (19 items) — no search/filter** | `TRIGGER_TYPES` is a long scrollable `Column` of `RadioButton` rows. Finding "NFC Tag" or "Share Sheet" requires scrolling the entire list. | High |
| 4 | **`ActionStepCard` uses raw `Card`** | Line 949 — same teal-material Card as Debug tab. Visually clashes with dark background. | High |
| 5 | **No step count indicator** | Editor manages `steps: List<WorkflowStep>` but nowhere shows "X steps" — user doesn't know if they're adding 1 or 10 steps. | Medium |
| 6 | **No trigger icons** | 19 triggers with only text labels — no icons, no color coding by category (Time, Location, Event, Sensor). | Medium |
| 7 | **No empty state for actions** | When steps list is at minimum (hardcoded `browser.open_url` default), no empty state illustration encourages adding more steps. | Medium |
| 8 | **`ActionEditDialog` — monospace params** | Line 972 shows raw JSON `step.params.toString()` in monospace. Confusing for a manual editor where users expect labelled fields. | Low |
| 9 | **No help/tooltip per trigger type** | No `?` icon or tooltip explains what "Sleep Proxy" or "Share Sheet" actually do. | Low |
| 10 | **No validation summary before save** | Saving with errors only highlights in `ActionEditDialog` — no top-level "3 actions need attention" banner. | Low |
| 11 | **Pre-filled default action** | Hardcoded `browser.open_url` with empty URL as the first step — user may not realize they need to configure it. No prompt to configure. | Low |

### ManualWorkflowEditor Priority
1. Add dark theme colors to all components (`Text`, `OutlinedTextField`, `Card`, etc.)
2. Add back button + "Cancel" text button to header
3. Add search/filter to trigger list
4. `ActionStepCard` → `GlassmorphicCard`
5. Add step count indicator
6. Add icons per trigger category

---

## Combined Priority (All Screens)

| # | Screen | Issue | Severity |
|---|---------|-------|----------|
| 1 | **ManualWorkflowEditor** | Zero dark theme — every component | ~~Critical~~ Medium (fixed) |
| 2 | **ManualWorkflowEditor** | No back button / cancel affordance | ~~High~~ ✅ Done |
| 3 | **ManualWorkflowEditor** | Trigger list (19 items) — no search | ~~High~~ ✅ Done (search added) |
| 4 | **ManualWorkflowEditor** | `ActionStepCard` → raw `Card` | ~~High~~ ✅ Done |
| 5 | **Debug** | `Card` → raw teal Material card | ~~High~~ ✅ Done |
| 6 | **Workflows** | Scene chip strip selection state dead | ~~High~~ ✅ Done |
| 7 | **ManualWorkflowEditor** | No step count shown | ~~Medium~~ ✅ Done |
| 8 | **Generate** | Saved workflow strip — no press feedback | ~~Medium~~ ✅ Done |
| 9 | **ManualWorkflowEditor** | No icons for trigger categories | Medium |
| 10 | **Workflows** | Empty state — no CTA arrow to "+ New" | Medium |
| 11 | **Generate** | ModelStatusCard disconnected from prompt | Medium |
| 12 | **Generate** | Hero/wordmark no visual connection | Medium |
| 13 | **Debug** | Token usage — raw numbers, no progress bar | Medium |
| 14 | **Workflows** | Card layout — no scene tag, no relative time | Low |
| 15 | **Generate** | Prompt field — no char counter, no voice input | Low |
| 16 | **ManualWorkflowEditor** | No help tooltips per trigger | Low |
| 17 | **ManualWorkflowEditor** | No validation summary before save | Low |

---

## Implementation Status

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | Theme: dark palette, typography, shapes | ✅ Done |
| Phase 2 | Components: HexHeroIcon, GlassmorphicCard, GradientButton, SceneChip | ✅ Done |
| Phase 3 | MainActivity dark integration (all 3 tabs) | ✅ Done |
| Phase 4 | Debug tab — replace `Card` with `GlassmorphicCard` | ✅ Done |
| Phase 5 | Workflows tab — scene chip selection state + filter behavior | ✅ Done |
| Phase 6 | ManualWorkflowEditorScreen — full dark theme pass | ✅ Done |
| Phase 7 | ManualWorkflowEditor — back button, trigger search, step count | ✅ Done |
| Phase 8 | Generate tab — hero/wordmark unity, saved card press feedback | ✅ Done |