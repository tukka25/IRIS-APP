package com.gemmaworkflow.platform.tools.impl

import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolParam
import com.gemmaworkflow.platform.tools.ToolResult

/**
 * Tier 5 — Reasoning tools. Pure computation, no external dependencies.
 */

/** Simple calculator for basic arithmetic. */
object CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Evaluates a simple arithmetic expression"
    override val parameters = listOf(
        ToolParam("expression", "string", description = "Math expression like '2+2', '60*24', '3600/60'")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val expr = input["expression"]?.trim() ?: return ToolResult(false, "", "Missing 'expression'")
        val safe = expr.replace(" ", "")
        if (!safe.matches(Regex("""^[\d+\-*/.()]+$"""))) {
            return ToolResult(false, "", "Only numbers and + - * / ( ) allowed")
        }
        return try {
            val result = evalSimple(safe)
            ToolResult(true, "$result")
        } catch (e: Exception) {
            ToolResult(false, "", "Could not evaluate: ${e.message}")
        }
    }

    private fun evalSimple(expr: String): Long {
        // Split into tokens: numbers and operators
        var s = expr
        // Handle * and /
        while (true) {
            val m = Regex("""(\d+)([*/])(\d+)""").find(s) ?: break
            val a = m.groupValues[1].toLong()
            val op = m.groupValues[2]
            val b = m.groupValues[3].toLong()
            val result = if (op == "*") a * b else a / b
            s = s.replaceFirst(m.value, result.toString())
        }
        // Handle + and -
        while (true) {
            val m = Regex("""(-?\d+)([+\-])(\d+)""").find(s) ?: break
            val a = m.groupValues[1].toLong()
            val op = m.groupValues[2]
            val b = m.groupValues[3].toLong()
            val result = if (op == "+") a + b else a - b
            s = s.replaceFirst(m.value, result.toString())
        }
        return s.toLong()
    }
}

/** Validates JSON against expected schema (reuses WorkflowValidator). */
object ValidateJsonTool : Tool {
    override val name = "validate_json"
    override val description = "Validates a workflow JSON against the action allowlist"
    override val parameters = listOf(
        ToolParam("json", "string", description = "Workflow JSON to validate")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val json = input["json"] ?: return ToolResult(false, "", "Missing 'json'")
        return try {
            val workflow = com.gemmaworkflow.domain.parser.WorkflowJsonParser.parse(json)
            val errors = com.gemmaworkflow.domain.safety.WorkflowValidator.validate(workflow)
            if (errors.isEmpty()) {
                ToolResult(true, "Valid workflow: ${workflow.name} with ${workflow.actions.size} actions")
            } else {
                ToolResult(false, errors.joinToString("\n"), "Validation failed")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Invalid JSON: ${e.message}")
        }
    }
}
