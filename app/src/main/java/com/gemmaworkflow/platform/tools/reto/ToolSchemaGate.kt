package com.gemmaworkflow.platform.tools.reto

import android.util.Log
import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolRegistry

/**
 * Validates tool calls before execution.
 *
 * Checks:
 * - Tool exists in ToolRegistry
 * - Tool is allowed in current layer
 * - Tool is allowed during generation (not EFFECTFUL)
 * - All required params present
 * - No unknown params
 * - Param values are parseable to declared type
 */
object ToolSchemaGate {

    private const val TAG = "RetoSchemaGate"

    fun validate(
        toolName: String,
        params: Map<String, String>,
        allowedTools: Set<String>,
        allowEffectful: Boolean = false
    ): SchemaGateResult {
        // 1. Tool exists
        val tool: Tool = ToolRegistry.get(toolName)
            ?: return SchemaGateResult.Invalid("Unknown tool: '$toolName'", repairable = false)

        // 2. Tool is allowed in this layer
        if (allowedTools.isNotEmpty() && toolName !in allowedTools) {
            return SchemaGateResult.Invalid(
                "Tool '$toolName' is not allowed in this layer. Allowed: ${allowedTools.joinToString()}",
                repairable = false
            )
        }

        // 3. Tool is safe during generation
        val metadata = ToolMetadataRegistry.get(toolName)
        if (!allowEffectful && metadata != null && !metadata.generationAllowed) {
            return SchemaGateResult.Invalid(
                "Tool '$toolName' is effectful and blocked during workflow generation",
                repairable = false
            )
        }

        // 4. Check required params
        val requiredParams = tool.parameters.filter { it.required }
        val missingRequired = requiredParams.filter { it.name !in params || params[it.name].isNullOrBlank() }
        if (missingRequired.isNotEmpty()) {
            val missingNames = missingRequired.joinToString(", ") { "'${it.name}' (${it.type})" }
            return SchemaGateResult.Invalid(
                "Missing required parameters for '$toolName': $missingNames",
                repairable = true
            )
        }

        // 5. Check unknown params
        val knownParamNames = tool.parameters.map { it.name }.toSet()
        val unknownParams = params.keys.filter { it !in knownParamNames }
        if (unknownParams.isNotEmpty()) {
            return SchemaGateResult.Invalid(
                "Unknown parameters for '$toolName': ${unknownParams.joinToString(", ") { "'$it'" }}. Known: ${knownParamNames.joinToString()}",
                repairable = true
            )
        }

        // 6. Type coercion check (best-effort, don't reject — warn only)
        for (p in tool.parameters) {
            val value = params[p.name] ?: continue
            when (p.type) {
                "int" -> if (value.toIntOrNull() == null) {
                    Log.w(TAG, "Param '${p.name}' expected int, got '$value' — will attempt coercion")
                }
                "float" -> if (value.toFloatOrNull() == null) {
                    Log.w(TAG, "Param '${p.name}' expected float, got '$value' — will attempt coercion")
                }
                "boolean" -> {
                    val lower = value.lowercase()
                    if (lower !in setOf("true", "false")) {
                        Log.w(TAG, "Param '${p.name}' expected boolean, got '$value' — will attempt coercion")
                    }
                }
            }
        }

        return SchemaGateResult.Valid
    }
}
