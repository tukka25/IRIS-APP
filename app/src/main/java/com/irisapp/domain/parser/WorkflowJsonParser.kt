package com.irisapp.domain.parser

import com.irisapp.domain.model.BatteryCondition
import com.irisapp.domain.model.ChargerType
import com.irisapp.domain.model.GeofenceTransition
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.domain.model.WorkflowStep
import com.irisapp.domain.model.SetupState
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
            "battery" -> {
                val threshold = obj["level_threshold"]?.jsonPrimitive?.content?.toIntOrNull() ?: 20
                val condition = when (obj["battery_condition"]?.jsonPrimitive?.content) {
                    "above" -> BatteryCondition.ABOVE
                    else -> BatteryCondition.BELOW
                }
                TriggerConfig.Battery(levelThreshold = threshold, condition = condition)
            }
            "charger" -> {
                val chargerType = when (obj["charger_type"]?.jsonPrimitive?.content) {
                    "usb" -> ChargerType.USB
                    "ac" -> ChargerType.AC
                    "wireless" -> ChargerType.WIRELESS
                    else -> ChargerType.ANY
                }
                TriggerConfig.Charger(chargerType)
            }
            "wifi" -> TriggerConfig.WiFi(
                ssid = obj["ssid"]?.jsonPrimitive?.content
            )
            "bluetooth" -> TriggerConfig.Bluetooth(
                deviceAddress = obj["device_address"]?.jsonPrimitive?.content
            )
            "airplane_mode" -> TriggerConfig.AirplaneMode(
                enabled = obj["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            )
            "dnd" -> TriggerConfig.DoNotDisturb(
                interruptionFilter = obj["interruption_filter"]?.jsonPrimitive?.content?.toIntOrNull()
            )
            "geofence" -> {
                TriggerConfig.Geofence(
                    latitude = obj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    longitude = obj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    radiusMeters = obj["radius_meters"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 100f,
                    transitionType = when (obj["transition_type"]?.jsonPrimitive?.content) {
                        "exit" -> GeofenceTransition.EXIT
                        "dwell" -> GeofenceTransition.DWELL
                        "enter_exit" -> GeofenceTransition.ENTER_EXIT
                        else -> GeofenceTransition.ENTER
                    }
                )
            }
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
        repaired = foldSimpleIntegerArithmetic(repaired)
        return stripTrailingCommas(repaired)
    }

    private fun foldSimpleIntegerArithmetic(json: String): String {
        var repaired = json
        val arithmeticPattern = Regex("""(:\s*)(-?\d+)\s*([+-])\s*(\d+)(\s*[,}\]])""")
        while (true) {
            var changed = false
            repaired = arithmeticPattern.replace(repaired) { result ->
                val left = result.groupValues[2].toLongOrNull()
                val right = result.groupValues[4].toLongOrNull()
                if (left == null || right == null) {
                    result.value
                } else {
                    val value = if (result.groupValues[3] == "+") left + right else left - right
                    changed = true
                    "${result.groupValues[1]}$value${result.groupValues[5]}"
                }
            }
            if (!changed) return repaired
        }
    }

    private fun stripTrailingCommas(json: String): String {
        val output = StringBuilder(json.length)
        var i = 0
        var inString = false
        while (i < json.length) {
            val c = json[i]
            if (!inString && c == ',') {
                var j = i + 1
                while (j < json.length && json[j].isWhitespace()) j++
                if (j < json.length && (json[j] == '}' || json[j] == ']')) {
                    i++
                    continue
                }
            }

            output.append(c)
            when {
                c == '\\' && inString && i + 1 < json.length -> {
                    i++
                    output.append(json[i])
                }
                c == '"' -> inString = !inString
            }
            i++
        }
        return output.toString()
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
