package com.gemmaworkflow.platform.tools.reto

import com.gemmaworkflow.platform.tools.ToolResult

/**
 * Parses structured facts from raw tool outputs.
 *
 * Each tool's parser extracts named key-value facts (e.g.,
 * "datetime.iso" → "2026-05-15T18:00:00").
 *
 * These facts are stored in ToolObservation.parsedFacts
 * and used by the RETO compact observation memory.
 */
object ToolFactParserRegistry {

    private val parsers = mutableMapOf<String, (ToolResult) -> Map<String, String>>()

    init {
        register("resolve_datetime") { result ->
            if (!result.success) return@register emptyMap()
            val facts = mutableMapOf<String, String>()
            // Parse structured output: "iso: 2026-05-15T18:00:00+04:00\ndate: 2026-05-15\n..."
            for (line in result.output.lines()) {
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    facts["datetime.${parts[0].trim()}"] = parts[1].trim()
                }
            }
            facts
        }

        register("get_contact") { result ->
            if (!result.success) return@register emptyMap()
            val facts = mutableMapOf<String, String>()
            val firstResult = result.output.lines().firstOrNull { it.contains("phone:") } ?: return@register facts
            // Format: "Maya Chen | phone: +15550101001 | email: maya.chen@example.com"
            val name = firstResult.substringBefore("|").trim()
            val phoneMatch = Regex("""phone:\s*(\S+)""").find(firstResult)
            val emailMatch = Regex("""email:\s*(\S+)""").find(firstResult)
            if (name.isNotBlank()) facts["contact.name"] = name
            phoneMatch?.let { facts["contact.phone"] = it.groupValues[1] }
            emailMatch?.let { facts["contact.email"] = it.groupValues[1] }
            facts
        }

        register("lookup_contact") { result ->
            parsers["get_contact"]?.invoke(result) ?: emptyMap()
        }

        register("get_current_time") { result ->
            if (!result.success) return@register emptyMap()
            val facts = mutableMapOf<String, String>()
            for (line in result.output.lines()) {
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    facts["datetime.${parts[0].trim()}"] = parts[1].trim()
                }
            }
            facts
        }

        register("list_installed_apps") { result ->
            if (!result.success) return@register emptyMap()
            mapOf("device.installed_apps" to result.output.take(500))
        }

        register("resolve_intent") { result ->
            if (!result.success) return@register emptyMap()
            val facts = mutableMapOf<String, String>()
            facts["intent.resolvable"] = (!result.output.contains("No activity found")).toString()
            val handlerMatch = Regex("""handler[:\s]+(\S+)""").find(result.output)
            handlerMatch?.let { facts["intent.handler_package"] = it.groupValues[1] }
            facts
        }

        register("validate_json") { result ->
            mapOf("json.valid" to result.success.toString())
        }

        register("calculator") { result ->
            if (!result.success) return@register emptyMap()
            val num = result.output.lines().lastOrNull()?.trim() ?: result.output.trim()
            mapOf("calculation.result" to num)
        }

        register("search_media") { result ->
            if (!result.success) return@register emptyMap()
            val firstResult = result.output.lines().firstOrNull() ?: ""
            mapOf("media.result" to firstResult.take(200))
        }

        register("search_files") { result ->
            if (!result.success) return@register emptyMap()
            val firstResult = result.output.lines().firstOrNull() ?: ""
            mapOf("file.result" to firstResult.take(200))
        }

        register("search_notes") { result ->
            if (!result.success) return@register emptyMap()
            mapOf("note.result" to result.output.take(200))
        }

        register("search_sms") { result ->
            if (!result.success) return@register emptyMap()
            val firstResult = result.output.lines().firstOrNull() ?: ""
            mapOf("sms.result" to firstResult.take(200))
        }

        register("get_calendar_events") { result ->
            if (!result.success) return@register emptyMap()
            val firstResult = result.output.lines().firstOrNull() ?: ""
            mapOf("calendar.result" to firstResult.take(200))
        }
    }

    fun register(name: String, parser: (ToolResult) -> Map<String, String>) {
        parsers[name] = parser
    }

    fun parse(name: String, result: ToolResult): Map<String, String> {
        return parsers[name]?.invoke(result) ?: emptyMap()
    }
}
