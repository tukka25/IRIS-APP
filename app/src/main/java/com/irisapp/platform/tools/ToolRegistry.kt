package com.irisapp.platform.tools

/**
 * Singleton registry of all available tools.
 *
 * Tools are registered once at startup. Planner stages receive scoped
 * subsets from ActionSpecRegistry and FindSkill.schemaFor(...).
 */
object ToolRegistry {

    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun all(): List<Tool> = tools.values.toList()

    /**
     * Returns tools filtered by a set of allowed names.
     * Only these tools will be shown to the agent in its prompt.
     */
    fun subset(allowedNames: Set<String>): List<Tool> {
        return allowedNames.mapNotNull { tools[it] }
    }

    /**
     * Minimal one-line-per-tool listing for the SLM prompt.
     * Format: "name: description"
     * Total context: ~50 tokens per tool.
     */
    fun toPromptSummary(toolList: List<Tool>): String = buildString {
        appendLine("Available tools (call with tool_name: {params}):")
        for (tool in toolList) {
            val params = if (tool.parameters.isEmpty()) "(no params)"
            else tool.parameters.joinToString(", ") { "${it.name}:${it.type}" }
            appendLine("  ${tool.name} $params — ${tool.description}")
        }
    }

    /** Execute a tool by name with string params. */
    suspend fun execute(name: String, input: Map<String, String>): ToolResult {
        val tool = tools[name] ?: return ToolResult(
            success = false, output = "", error = "Unknown tool: $name"
        )
        return tool.execute(input)
    }
}
