package com.gemmaworkflow.platform.tools

import com.gemmaworkflow.platform.tools.reto.ToolMetadataRegistry

/**
 * Minimal skill index for the SLM.
 *
 * Three modes:
 * - index(): full catalog (discovery)
 * - indexFor(allowed): only tools that agent can use
 * - schemaFor(allowed): full parameter details for the agent
 *
 * All three read from ToolRegistry dynamically — add a tool, it appears.
 */
object FindSkill {

    /**
     * Full catalog — every registered tool, one line each.
     * Use for: "what tools exist?"
     */
    fun index(): String = buildString {
        val all = ToolRegistry.all()
        appendLine("=== Available Tools (${all.size}) ===")
        appendLine()
        // Group by category for readability
        val temporal = all.filter { it.name in setOf("get_current_time", "resolve_datetime", "compute_duration", "get_day_of_week") }
        val device = all.filter { it.name in setOf("list_installed_apps", "resolve_intent", "get_device_location") }
        val search = all.filter { it.name in setOf("web_search", "search_places", "get_contact", "lookup_contact") }
        val execution = all.filter { it.name in setOf("send_intent", "open_uri", "share_text", "set_alarm", "create_calendar_event") }
        val reasoning = all.filter { it.name in setOf("calculator", "validate_json") }

        if (temporal.isNotEmpty()) { appendLine("-- Temporal --"); temporal.forEach { appendLine("${it.name} — ${it.description}") }; appendLine() }
        if (device.isNotEmpty()) { appendLine("-- Device --"); device.forEach { appendLine("${it.name} — ${it.description}") }; appendLine() }
        if (search.isNotEmpty()) { appendLine("-- Search --"); search.forEach { appendLine("${it.name} — ${it.description}") }; appendLine() }
        if (execution.isNotEmpty()) { appendLine("-- Execution --"); execution.forEach { appendLine("${it.name} — ${it.description}") }; appendLine() }
        if (reasoning.isNotEmpty()) { appendLine("-- Reasoning --"); reasoning.forEach { appendLine("${it.name} — ${it.description}") } }
    }

    /**
     * Filtered index — only the tools this agent is allowed to use.
     * The RETO slot-grounding stage normally passes action-scoped tool names
     * from ActionSpecRegistry.toolNamesForAction(...).
     */
    fun indexFor(allowedTools: Set<String>): String = buildString {
        val registry = ToolRegistry
        val tools = allowedTools.mapNotNull { registry.get(it) }.sortedBy { it.name }
        appendLine("=== Tools you can use (${tools.size}) ===")
        for (tool in tools) {
            val params = if (tool.parameters.isEmpty()) ""
            else " (${tool.parameters.joinToString { "${it.name}:${it.type}" }})"
            appendLine("${tool.name}$params — ${tool.description}")
        }
    }

    /**
     * Full schema — parameter details for each allowed tool.
     * Use when the SLM needs to actually CALL a tool.
     */
    fun schemaFor(allowedTools: Set<String>): String = buildString {
        val registry = ToolRegistry
        val tools = allowedTools.mapNotNull { registry.get(it) }.sortedBy { it.name }
        for (tool in tools) {
            val displayName = ToolAliasRegistry.aliasFor(tool.name)
            val callName = if (displayName != tool.name) " (call as: ${tool.name})" else ""
            appendLine("Tool: $displayName$callName")
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
            val examples = ToolMetadataRegistry.get(tool.name)?.examples.orEmpty().take(2)
            if (examples.isNotEmpty()) {
                appendLine("  Examples:")
                examples.forEach { example ->
                    appendLine("    - ${example.description}: TOOL: ${tool.name} ${example.args.toJsonLike()}")
                }
            }
            appendLine()
        }
    }

    private fun Map<String, String>.toJsonLike(): String {
        if (isEmpty()) return "{}"
        return entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${key.escapeJsonLike()}\":\"${value.escapeJsonLike()}\""
        }
    }

    private fun String.escapeJsonLike(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
