package com.gemmaworkflow.platform.tools

/**
 * A callable tool that the SLM can invoke during planning.
 * Each tool is a single function with typed params and a string result.
 *
 * Context cost target: ~50-100 tokens per tool in the prompt.
 */
interface Tool {
    /** Unique name used by the SLM to call this tool, e.g. "get_current_time". */
    val name: String

    /** Model-facing alias shown in prompts instead of internal name. E.g. "find_contact_by_name" for "get_contact". */
    val modelAlias: String? get() = null

    /** One-line description shown in the tool listing. Keep under 100 chars. */
    val description: String

    /** Parameter schema. Keep param descriptions tight. */
    val parameters: List<ToolParam>

    /** Execute the tool. Returns structured result. */
    suspend fun execute(input: Map<String, String>): ToolResult
}

data class ToolParam(
    val name: String,
    val type: String,        // "string", "int", "float", "boolean"
    val required: Boolean = true,
    val description: String = ""
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)
