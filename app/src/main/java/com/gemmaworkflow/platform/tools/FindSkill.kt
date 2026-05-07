package com.gemmaworkflow.platform.tools

/**
 * Minimal skill index for the SLM.
 *
 * This is the "find skill" — it lists every available tool name + one-line
 * description in the most compact format possible. The SLM uses this to
 * discover what tools exist without consuming unnecessary context.
 *
 * Total context cost: ~50 tokens per tool × 12 tools = ~600 tokens.
 */
object FindSkill {

    /**
     * Returns the complete tool index as a minimal string.
     * Each line: "tool_name — one_line_description"
     */
    fun index(): String = buildString {
        appendLine("=== Available Tools ===")
        appendLine()
        appendLine("-- Temporal --")
        appendLine("get_current_time — current date, time, timezone, day")
        appendLine("resolve_datetime — 'next Friday 2pm' → exact timestamp")
        appendLine("compute_duration — add/subtract time or find duration")
        appendLine("get_day_of_week — what day is a given date?")
        appendLine()
        appendLine("-- Device --")
        appendLine("list_installed_apps — all launchable apps on device")
        appendLine("resolve_intent — find apps that handle an action+URI")
        appendLine("get_device_location — coarse lat/lng (no GPS needed)")
        appendLine()
        appendLine("-- Search & Knowledge --")
        appendLine("web_search — search the web, returns top 3 results")
        appendLine("search_places — search places/addresses with lat/lng (free OSM)")
        appendLine("lookup_contact — search device contacts by name")
        appendLine()
        appendLine("-- Execution --")
        appendLine("send_intent — send Android intent, report result")
        appendLine("open_uri — open a URI (http, geo, tel, spotify://)")
        appendLine("share_text — open Android share sheet with text")
        appendLine("set_alarm — open clock to set alarm")
        appendLine("create_calendar_event — open calendar with pre-filled event")
        appendLine()
        appendLine("-- Reasoning --")
        appendLine("calculator — evaluate simple math expression")
        appendLine("validate_json — check workflow JSON against allowlist")
    }

    /**
     * Returns only the tools assigned to a specific agent.
     */
    fun indexFor(allowedTools: Set<String>): String = buildString {
        val registry = ToolRegistry
        appendLine("=== Tools you can use ===")
        for (name in allowedTools.sorted()) {
            val tool = registry.get(name) ?: continue
            val params = if (tool.parameters.isEmpty()) ""
            else " (params: ${tool.parameters.joinToString { "${it.name}:${it.type}" }})"
            appendLine("${tool.name}$params — ${tool.description}")
        }
    }

    /**
     * Full tool schema with parameter details. Use when the SLM
     * needs to actually CALL a tool (not just discover it).
     */
    fun schemaFor(allowedTools: Set<String>): String = buildString {
        val registry = ToolRegistry
        for (name in allowedTools.sorted()) {
            val tool = registry.get(name) ?: continue
            appendLine("Tool: ${tool.name}")
            appendLine("  ${tool.description}")
            if (tool.parameters.isNotEmpty()) {
                appendLine("  Parameters:")
                for (p in tool.parameters) {
                    val req = if (p.required) "required" else "optional"
                    appendLine("    ${p.name} (${p.type}, $req): ${p.description}")
                }
            } else {
                appendLine("  Parameters: none")
            }
            appendLine()
        }
    }
}
