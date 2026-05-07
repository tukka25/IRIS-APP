package com.gemmaworkflow.domain.parser

import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.model.WorkflowStep
import com.gemmaworkflow.domain.model.SetupState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts JSON from raw model output (which may contain extra text) and
 * parses it into a [PlannedWorkflow].
 */
object WorkflowJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawOutput: String): PlannedWorkflow {
        val extractedJson = extractJsonBlock(rawOutput)
        val repairedJson = repairMalformedJson(extractedJson)
        val root = json.parseToJsonElement(repairedJson).jsonObject

        val name = root["name"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing required field: name")

        val summary = root["summary"]?.jsonPrimitive?.content ?: ""

        val trigger = parseTrigger(root["trigger"]?.jsonObject)

        val actions = root["actions"]?.jsonArray?.map { actionElement ->
            val obj = actionElement.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing action id")

            val params = obj["params"]?.jsonObject ?: JsonObject(emptyMap())

            val requiresConfirmation = obj["requires_confirmation"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

            WorkflowStep(id = id, params = params, requiresConfirmation = requiresConfirmation)
        } ?: emptyList()

        val missingSetup = root["missing_setup"]?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: emptyList()

        return PlannedWorkflow(
            name = name,
            summary = summary,
            trigger = trigger,
            actions = actions,
            missingSetup = missingSetup,
            rawModelOutput = rawOutput
        )
    }

    private fun parseTrigger(obj: JsonObject?): TriggerConfig {
        if (obj == null) return TriggerConfig.Manual

        val type = obj["type"]?.jsonPrimitive?.content ?: return TriggerConfig.Manual
        val setupState = parseSetupState(obj["setup_state"]?.jsonPrimitive?.content)

        return when (type) {
            "time" -> {
                val schedule = obj["schedule"]?.jsonObject
                TriggerConfig.Time(
                    hour = schedule?.get("hour")?.jsonPrimitive?.content?.toIntOrNull() ?: 9,
                    minute = schedule?.get("minute")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    repeatDays = schedule?.get("repeat_days")?.jsonArray?.map {
                        it.jsonPrimitive.content.toInt()
                    } ?: emptyList()
                )
            }
            "nfc" -> TriggerConfig.Nfc(
                tagId = obj["tag_id"]?.jsonPrimitive?.content
            )
            "share_sheet" -> TriggerConfig.ShareSheet(setupState = setupState)
            "tasker_setup_required" -> TriggerConfig.TaskerRequired(setupState = setupState)
            else -> TriggerConfig.Manual
        }
    }

    private fun parseSetupState(raw: String?): SetupState = when (raw?.lowercase()) {
        "ready" -> SetupState.Ready
        "needs_setup" -> SetupState.NeedsSetup
        else -> SetupState.Unsupported
    }

    private val UNQUOTED_KEYS = listOf(
        "key", "id", "type", "reason", "name", "summary", "label", "message",
        "title", "description", "phone", "url", "text", "timezone",
        "year", "month", "day", "hour", "minute", "second", "repeat_days"
    ).joinToString("|")

    private fun repairMalformedJson(json: String): String {
        // Fix unquoted string values: "key": Value → "key":"Value"
        val pattern = Regex("""\"($UNQUOTED_KEYS)"\s*:\s*([a-zA-Z_][a-zA-Z0-9_\s]*)""")
        var repaired = json.replace(pattern) { result ->
            "\"${result.groupValues[1]}\":\"${result.groupValues[2].trim()}\""
        }
        // Fix unquoted capitalised string values (common LLM mistake): "key": Meeting Invitation" → "key":"Meeting Invitation"
        val capPattern = Regex("""\"($UNQUOTED_KEYS)"\s*:\s*([A-Z][^\"\n,\}]+)""")
        repaired = capPattern.replace(repaired) { result ->
            "\"${result.groupValues[1]}\":\"${result.groupValues[2].trim().trimEnd(',').trimEnd('"').trimEnd(',')}\""
        }
        return repaired
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        if (start == -1) throw IllegalArgumentException("No JSON object found in output")

        var i = start
        var depth = 0
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (!inString) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                    '"' -> inString = true
                }
            } else {
                when {
                    c == '\\' && i + 1 < text.length -> i++
                    c == '"' -> inString = false
                }
            }
            i++
        }
        throw IllegalArgumentException("Unclosed JSON object in output")
    }
}
