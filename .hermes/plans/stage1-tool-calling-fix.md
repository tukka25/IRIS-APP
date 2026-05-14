# Plan: Fix Stage 1 (RequestAnalysisAgent) Tool Calling

## RETO Research Summary

RETO paper says: "Code will be made publicly available." The anonymous repo
(`github.com/anonymous-submission2026/RETO_code`) exists but is INCOMPLETE:
- Missing `text_encoder.py` — layer predictor training can't run
- Repair logic is generic JSON validation, not the schema-aware correction claimed
- No configs, no data splits
- README says the same boilerplate "Code will be made publicly available"

**Decision: Implement RETO-inspired layered execution from scratch.**
We don't need the ML layer predictor (overkill for 2-7 tools). We'll use
a deterministic `ToolLayerResolver` that assigns tools to layers based on
their category (contacts → layer 1, time → layer 2, analysis → layer 3).

The core RETO insight we adopt: **constrain tools per execution step so the
SLM can't skip prerequisites.** In a single step with only `get_contact`
available and the prompt says "resolve all names to contacts", the model
MUST call get_contact — it has no other tool option.

## Changes (8 fixes + layered execution)

### File 1: ToolAwareGenerator.kt — Make temperature configurable

**Current:** `private val sampler = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)` — hardcoded
**Target:** Temperature passed via constructor, defaults to 0.2

```
class ToolAwareGenerator(
    ...
    private val temperature: Float = 0.2f  // NEW param
) {
    private val sampler = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
```

Stage 1 will use 0.4, Stage 4 stays at 0.2.

### File 2: AgentToolAssignments.kt — Reduce Stage 1 to 2 tools

**Current:** 7 tools (get_current_time, resolve_datetime, compute_duration,
  get_day_of_week, list_installed_apps, get_contact, get_device_location)

**Target:** 2 tools (get_current_time, resolve_datetime)

Why:
- `list_installed_apps` — already injected in prompt (redundant)
- `compute_duration` — edge case, rarely needed in Stage 1
- `get_day_of_week` — get_current_time already returns day of week
- `get_contact` — REMOVED from tool list, REPLACED by pre-processing entity extraction
- `get_device_location` — rarely needed in Stage 1 analysis

### File 3: New file — EntityPreProcessor.kt

Before the SLM even sees the prompt, extract named entities from the user
request using regex + ContactsContract lookup. This solves the "Maya"
problem deterministically — no model tool call needed.

```
object EntityPreProcessor {
    fun resolveEntities(
        userRequest: String,
        context: Context
    ): ResolvedEntities
}

data class ResolvedEntities(
    val contactLookups: Map<String, ContactResult>,  // "Maya" → {displayName, phone}
    val phoneNumbers: List<String>,                   // raw phone numbers found
    val dateExpressions: List<String>                 // "next Friday" etc.
)
```

Strategy:
1. Regex extract all proper names (capitalized words, not common words)
2. For each name, query ContactsContract to find matches
3. Inject resolved contacts directly into the prompt as KNOWN DATA
4. This means the model NEVER needs to call get_contact — it's pre-resolved

### File 4: PromptBuilder.kt — Complete rewrite of Stage 1 prompt

**Current structure (broken):**
```
You are a request analyzer...
Return JSON only.              ← CONTRADICTION with tool instructions
User request: "..."
[installed apps]
Output schema: {...}
Rules: ...
                                ← THEN tool instructions appended AFTER
Tool use:
- You have access to tools...
[schema for 7 tools]
```

**Target structure (fixed):**
```
You are a request analyzer for IrisApp.
You have access to tools. Use them to get accurate times and dates.

--- AUDIT FIRST ---
STEP 1 — IDENTIFY: Before calling any tools, list what information you need
  that you don't already have.
STEP 2 — GATHER: Call the tools you need, one at a time.
STEP 3 — ANALYZE: With all results, produce the analysis JSON.

--- EXAMPLES ---
Example 1:
  User: "remind me tomorrow at 9am"
  Need: current time, tomorrow at 9am
  → TOOL: get_current_time {}
  → TOOL: resolve_datetime {"expression": "tomorrow at 9am"}
  → {"goal": "Set reminder", "trigger_hint": "time", ...}

Example 2:  
  User: "send message to Maya saying hi tomorrow"
  CONTACTS RESOLVED: Maya = +971501234567
  Need: tomorrow date
  → TOOL: resolve_datetime {"expression": "tomorrow"}
  → {"goal": "Send message to Maya", "trigger_hint": "manual", ...}

Example 3:
  User: "call John next Friday at 6pm"
  CONTACTS RESOLVED: John = +971551112233
  Need: next Friday 6pm
  → TOOL: resolve_datetime {"expression": "next Friday at 6pm"}
  → {"goal": "Call John", "trigger_hint": "manual", ...}

--- YOUR TASK ---
USER REQUEST: "send message to Maya saying hi, and invite him to meeting
on 6 oclock on next friday and then add it to my calender."

CONTACTS RESOLVED: Maya = +971556778792 (from device contacts)

INSTALLED APPS:
[list]

--- TOOLS ---
{schema for get_current_time, resolve_datetime}

Rules for tool calling:
- Call tools BEFORE writing the JSON output.
- If a tool returns ERROR, try rephrasing or use your best estimate.
- Call one tool per response. Wait for TOOL_RESULT before calling another.

--- OUTPUT ---
Now produce the analysis JSON:
{
  "goal": "concise goal statement",
  ...
}
```

### File 5: WorkflowGenerationViewModel.kt — Wire up changes

Changes in `generate()`:

1. **Pre-process entities before Stage 1:**
```kotlin
val resolvedEntities = EntityPreProcessor.resolveEntities(prompt, getApplication())
appendDebug("Resolved entities", "contacts=${resolvedEntities.contactLookups.keys}, phones=${resolvedEntities.phoneNumbers}")
```

2. **Pass temperature to ToolAwareGenerator:**
PlannerAgents already creates ToolAwareGenerator internally. Add temperature param:
```kotlin
suspend fun requestAnalysis(
    prompt: String,
    allowedTools: Set<String> = emptySet(),
    temperature: Float = 0.4f,  // NEW — Stage 1 default
    onToolEvent: ...
)
```

3. **Inject resolved entities into prompt:**
```kotlin
val analysisPrompt = PromptBuilder.buildRequestAnalysisPrompt(
    userRequest = prompt,
    installedApps = installedAppsSummary,
    resolvedContacts = resolvedEntities.contactLookups  // NEW
) + "\n\n" + PromptBuilder.buildToolUseInstructions(FindSkill.schemaFor(analysisTools))
```

### File 6: PlannerAgents.kt — Add temperature parameter

Add `temperature: Float = 0.4f` to `requestAnalysis()`, pass it to ToolAwareGenerator.
Keep Stage 2 (deterministic, no model) unchanged.
Keep Stage 3 at default 0.2.
Keep Stage 4 at default 0.2.

### File 7: New file — ToolLayerResolver.kt (RETO-inspired)

Even though Stage 1 has only 2 tools now, build the layered execution
infrastructure for future use (Stage 3 has 13 tools and needs this).

```
object ToolLayerResolver {
    fun resolveLayers(tools: Set<String>, request: String): List<ToolLayer>
}

data class ToolLayer(
    val label: String,           // "Contact Resolution", "Time Resolution"
    val tools: Set<String>,      // tools available in this layer
    val instruction: String      // what to do in this layer
)
```

For Stage 1, the resolver would produce:
```
Layer 1 "Time Resolution": [get_current_time, resolve_datetime]
  → "Resolve all time expressions in the user's request"
```

### RETO-inspired layered execution for ToolAwareGenerator

Add a `generateLayered()` method that executes tools layer by layer:

```
suspend fun generateLayered(layers: List<ToolLayer>, basePrompt: String): String {
    var transcript = basePrompt
    for (layer in layers) {
        // Constrain tools to this layer only
        val layerGenerator = ToolAwareGenerator(
            engine, layer.tools, maxToolCalls = layer.tools.size, ...
        )
        val layerPrompt = transcript + "\n\n${layer.instruction}\n\nTools: ${layer.tools}"
        val result = layerGenerator.generate(layerPrompt)
        transcript += "\n\n[${layer.label} results]\n$result"
    }
    return transcript
}
```

## Execution Order

1. `ToolLayerResolver.kt` — new file (infrastructure, no dependencies)
2. `EntityPreProcessor.kt` — new file (contacts lookup)
3. `ToolAwareGenerator.kt` — add temperature param + generateLayered()
4. `AgentToolAssignments.kt` — reduce Stage 1 to 2 tools
5. `PromptBuilder.kt` — full rewrite of buildRequestAnalysisPrompt() + buildToolUseInstructions()
6. `PlannerAgents.kt` — add temperature param
7. `WorkflowGenerationViewModel.kt` — wire entity pre-processing + temperature + new prompt
8. Build + test
