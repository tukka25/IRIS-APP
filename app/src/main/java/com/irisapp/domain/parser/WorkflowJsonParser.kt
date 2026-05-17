package com.irisapp.domain.parser

import com.irisapp.domain.model.BatteryCondition
import com.irisapp.domain.model.ChargerType
import com.irisapp.domain.model.GeofenceTransition
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.domain.model.WorkflowStep
import com.irisapp.domain.model.SetupState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
                ssid = obj["ssid"]?.jsonPrimitive?.content,
                bssid = obj["bssid"]?.jsonPrimitive?.content,
                connectionState = obj["connection_state"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            )
            "bluetooth" -> TriggerConfig.Bluetooth(
                deviceAddress = obj["device_address"]?.jsonPrimitive?.content,
                connectionState = obj["connection_state"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
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
                        "enter" -> GeofenceTransition.ENTER
                        "exit" -> GeofenceTransition.EXIT
                        "dwell" -> GeofenceTransition.DWELL
                        "enter_exit" -> GeofenceTransition.ENTER_EXIT
                        else -> GeofenceTransition.ENTER_EXIT
                    },
                    dwellDelaySeconds = obj["dwell_delay_seconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    name = obj["name"]?.jsonPrimitive?.content
                )
            }
            "alarm_stopped" -> TriggerConfig.AlarmStopped(
                alarmType = obj["alarm_type"]?.jsonPrimitive?.content ?: "default"
            )
            "app_opened" -> TriggerConfig.AppOpened(
                appPackagePatterns = obj["app_package_patterns"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                triggerOnOpen = obj["trigger_on_open"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                triggerOnClose = obj["trigger_on_close"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            )
            "app_closed" -> TriggerConfig.AppClosed(
                appPackagePatterns = obj["app_package_patterns"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                triggerOnOpen = obj["trigger_on_open"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                triggerOnClose = obj["trigger_on_close"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            )
            "sms_received" -> TriggerConfig.SmsReceived(
                senderPattern = obj["sender_pattern"]?.jsonPrimitive?.content,
                bodyPattern = obj["body_pattern"]?.jsonPrimitive?.content
            )
            "notification_listener" -> TriggerConfig.NotificationListenerConfig(
                appPackagePatterns = obj["app_package_patterns"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                senderPattern = obj["sender_pattern"]?.jsonPrimitive?.content,
                bodyPattern = obj["body_pattern"]?.jsonPrimitive?.content,
                triggerOnDismiss = obj["trigger_on_dismiss"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            )
            "email_received" -> TriggerConfig.EmailReceived(
                senderPattern = obj["sender_pattern"]?.jsonPrimitive?.content,
                subjectPattern = obj["subject_pattern"]?.jsonPrimitive?.content,
                appPackage = obj["app_package"]?.jsonPrimitive?.content ?: "com.google.android.gm"
            )
            "sleep_proxy" -> TriggerConfig.SleepProxy(
                startTimeHour = obj["start_time_hour"]?.jsonPrimitive?.content?.toIntOrNull() ?: 22,
                startTimeMinute = obj["start_time_minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                endTimeHour = obj["end_time_hour"]?.jsonPrimitive?.content?.toIntOrNull() ?: 7,
                endTimeMinute = obj["end_time_minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                requireChargerDisconnected = obj["require_charger_disconnected"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                requireDndActive = obj["require_dnd_active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            )
            "voice" -> TriggerConfig.Voice
            "sound_event" -> TriggerConfig.SoundEvent(
                soundClasses = obj["sound_classes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            )
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
        val pattern = Regex(""""($UNQUOTED_KEYS)"\s*:\s*([a-zA-Z_][a-zA-Z0-9_\s]*)"""")
        var repaired = json.replace(pattern) { result ->
            "\"${result.groupValues[1]}\":\"${result.groupValues[2].trim()}\""
        }
        // Fix unquoted capitalised string values (common LLM mistake): "key": Meeting Invitation" → "key":"Meeting Invitation"
        val capPattern = Regex(""""($UNQUOTED_KEYS)"\s*:\s*([A-Z][^"\n,\}]+)"""")
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

    /**
     * Serializes a [PlannedWorkflow] to a Map suitable for Firebase Realtime Database storage.
     * Excludes rawModelOutput. [shareId] is the Firebase push key that identifies this export.
     */
    fun serializeForExport(workflow: PlannedWorkflow, shareId: String): Map<String, Any> {
        return buildMap {
            put("shareId", shareId)
            put("name", workflow.name)
            put("summary", workflow.summary)
            put("trigger", serializeTrigger(workflow.trigger))
            put("actions", workflow.actions.map { serializeStep(it) })
            put("exportedAt", System.currentTimeMillis())
        }
    }

    private fun serializeTrigger(trigger: TriggerConfig): Map<String, Any?> = when (trigger) {
        is TriggerConfig.Manual -> buildMap { put("type", "manual") }
        is TriggerConfig.Time -> buildMap {
            put("type", "time")
            put("schedule", buildMap {
                put("hour", trigger.hour)
                put("minute", trigger.minute)
                put("repeat_days", trigger.repeatDays)
            })
        }
        is TriggerConfig.Nfc -> buildMap {
            put("type", "nfc")
            trigger.tagId?.let { put("tag_id", it) }
        }
        is TriggerConfig.ShareSheet -> buildMap {
            put("type", "share_sheet")
            put("setup_state", trigger.setupState.name.lowercase())
        }
        is TriggerConfig.Battery -> buildMap {
            put("type", "battery")
            put("level_threshold", trigger.levelThreshold)
            put("battery_condition", trigger.condition.name.lowercase())
        }
        is TriggerConfig.Charger -> buildMap {
            put("type", "charger")
            put("charger_type", trigger.connectionType.name.lowercase())
        }
        is TriggerConfig.WiFi -> buildMap {
            put("type", "wifi")
            trigger.ssid?.let { put("ssid", it) }
            trigger.bssid?.let { put("bssid", it) }
            trigger.connectionState?.let { put("connection_state", it) }
        }
        is TriggerConfig.Bluetooth -> buildMap {
            put("type", "bluetooth")
            trigger.deviceAddress?.let { put("device_address", it) }
            trigger.connectionState?.let { put("connection_state", it) }
        }
        is TriggerConfig.AirplaneMode -> buildMap {
            put("type", "airplane_mode")
            put("enabled", trigger.enabled)
        }
        is TriggerConfig.DoNotDisturb -> buildMap {
            put("type", "dnd")
            trigger.interruptionFilter?.let { put("interruption_filter", it) }
        }
        is TriggerConfig.AlarmStopped -> buildMap {
            put("type", "alarm_stopped")
            put("alarm_type", trigger.alarmType)
        }
        is TriggerConfig.AppOpened -> buildMap {
            put("type", "app_opened")
            put("app_package_patterns", trigger.appPackagePatterns)
            put("trigger_on_open", trigger.triggerOnOpen)
            put("trigger_on_close", trigger.triggerOnClose)
        }
        is TriggerConfig.AppClosed -> buildMap {
            put("type", "app_closed")
            put("app_package_patterns", trigger.appPackagePatterns)
            put("trigger_on_open", trigger.triggerOnOpen)
            put("trigger_on_close", trigger.triggerOnClose)
        }
        is TriggerConfig.SmsReceived -> buildMap {
            put("type", "sms_received")
            trigger.senderPattern?.let { put("sender_pattern", it) }
            trigger.bodyPattern?.let { put("body_pattern", it) }
        }
        is TriggerConfig.NotificationListenerConfig -> buildMap {
            put("type", "notification_listener")
            put("app_package_patterns", trigger.appPackagePatterns)
            trigger.senderPattern?.let { put("sender_pattern", it) }
            trigger.bodyPattern?.let { put("body_pattern", it) }
            put("trigger_on_dismiss", trigger.triggerOnDismiss)
        }
        is TriggerConfig.EmailReceived -> buildMap {
            put("type", "email_received")
            trigger.senderPattern?.let { put("sender_pattern", it) }
            trigger.subjectPattern?.let { put("subject_pattern", it) }
            put("app_package", trigger.appPackage)
        }
        is TriggerConfig.SleepProxy -> buildMap {
            put("type", "sleep_proxy")
            put("start_time_hour", trigger.startTimeHour)
            put("start_time_minute", trigger.startTimeMinute)
            put("end_time_hour", trigger.endTimeHour)
            put("end_time_minute", trigger.endTimeMinute)
            put("require_charger_disconnected", trigger.requireChargerDisconnected)
            put("require_dnd_active", trigger.requireDndActive)
        }
        is TriggerConfig.Geofence -> buildMap {
            put("type", "geofence")
            put("latitude", trigger.latitude)
            put("longitude", trigger.longitude)
            put("radius_meters", trigger.radiusMeters)
            put("transition_type", trigger.transitionType.name.lowercase())
            put("dwell_delay_seconds", trigger.dwellDelaySeconds)
            trigger.name?.let { put("name", it) }
        }
        is TriggerConfig.Voice -> buildMap {
            put("type", "voice")
        }
        is TriggerConfig.SoundEvent -> buildMap {
            put("type", "sound_event")
            put("sound_classes", trigger.soundClasses)
        }
    }.mapValues { it.value }

    private fun serializeStep(step: WorkflowStep): Map<String, Any> = buildMap {
        put("id", step.id)
        put("params", step.params.entries.associate { it.key to it.value.jsonPrimitive.content })
        put("requires_confirmation", step.requiresConfirmation)
    }

    /**
     * Reconstructs a [PlannedWorkflow] from a Firebase RTDB export map.
     * [extras] can override fields (e.g., a renamed name from the import screen).
     */
    fun parseFromExport(data: Map<String, Any>, extras: Map<String, Any> = emptyMap()): PlannedWorkflow {
        @Suppress("UNCHECKED_CAST")
        val normalized = data.mapValues { entry ->
            when (val v = entry.value) {
                is Map<*, *> -> v.mapKeys { it.key.toString() } as Any
                is List<*> -> v
                else -> v
            }
        }
        val merged = normalized + extras

        val name = (merged["name"] as? String) ?: throw IllegalArgumentException("Missing name")
        val summary = (merged["summary"] as? String) ?: ""

        val triggerObj = (merged["trigger"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.let {
            val jsonMap = it.filterValues { v -> v != null }.mapValues { entry ->
                when (val v = entry.value) {
                    is JsonElement -> v
                    is String -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
            }
            JsonObject(jsonMap)
        }
        val trigger = parseTrigger(triggerObj)

        @Suppress("UNCHECKED_CAST")
        val actions = (merged["actions"] as? List<*>).orEmpty().mapNotNull { item ->
            (item as? Map<*, *>)?.let { map ->
                val m = map.mapKeys { it.key.toString() }
                WorkflowStep(
                    id = (m["id"] as? String) ?: return@mapNotNull null,
                    params = JsonObject((m["params"] as? Map<*, *>)?.entries?.associate { it.key.toString() to JsonPrimitive(it.value?.toString() ?: "") } ?: emptyMap()),
                    requiresConfirmation = (m["requires_confirmation"] as? Boolean) ?: false
                )
            }
        }

        return PlannedWorkflow(
            name = name,
            summary = summary,
            trigger = trigger,
            actions = actions,
            missingSetup = emptyList()
        )
    }
}