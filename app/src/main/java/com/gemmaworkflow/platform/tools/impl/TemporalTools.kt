package com.gemmaworkflow.platform.tools.impl

import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolParam
import com.gemmaworkflow.platform.tools.ToolResult
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Tier 1 — Temporal tools. Zero external dependencies, pure java.time.
 * These are the highest-impact tools: every time-based request needs them.
 */

/** Returns current date, time, timezone, and day of week. */
object GetCurrentTimeTool : Tool {
    override val name = "get_current_time"
    override val description = "Returns current date, time, timezone, and day of week"
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val now = ZonedDateTime.now()
        val output = buildString {
            appendLine("iso: ${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
            appendLine("date: ${now.toLocalDate()}")
            appendLine("time: ${now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}")
            appendLine("timezone: ${now.zone.id}")
            appendLine("day_of_week: ${now.dayOfWeek.name.lowercase()}")
            appendLine("unix_ms: ${now.toInstant().toEpochMilli()}")
        }
        return ToolResult(success = true, output = output.trim())
    }
}

/**
 * Resolves human datetime expressions into precise timestamps.
 * Supports: "next Friday at 2pm", "tomorrow morning", "in 2 hours",
 * "May 15 at 18:00", "next week Monday 9am".
 */
object ResolveDatetimeTool : Tool {
    override val name = "resolve_datetime"
    override val description = "Converts human time expressions to exact timestamps"
    override val parameters = listOf(
        ToolParam("expression", "string", description = "e.g. 'next Friday at 2pm', 'tomorrow 9am', 'in 30 minutes'")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val expr = input["expression"]?.trim()?.lowercase() ?: ""
        if (expr.isBlank()) return ToolResult(false, "", "Missing 'expression' param")

        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val result = try {
            resolveExpression(expr, now)
        } catch (e: Exception) {
            return ToolResult(false, "", "Could not parse: '$expr'. Try 'next Friday at 2pm' or '2026-05-15T18:00'")
        }

        val zoned = ZonedDateTime.of(result, zone)
        return ToolResult(true, buildString {
            appendLine("iso: ${zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
            appendLine("date: ${result.toLocalDate()}")
            appendLine("time: ${result.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}")
            appendLine("unix_ms: ${zoned.toInstant().toEpochMilli()}")
            appendLine("day_of_week: ${result.dayOfWeek.name.lowercase()}")
        }.trim())
    }

    private fun resolveExpression(expr: String, now: LocalDateTime): LocalDateTime {
        // "next Friday at 2pm"
        val nextDayRegex = Regex("""next\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s*(at\s*)?(\d{1,2})(:\d{2})?\s*(am|pm)?""")
        nextDayRegex.find(expr)?.let { match ->
            val day = DayOfWeek.valueOf(match.groupValues[1].uppercase())
            val hour24 = parseHour(match.groupValues[3], match.groupValues[5])
            val minute = match.groupValues[4].removePrefix(":").toIntOrNull() ?: 0
            val targetTime = LocalTime.of(hour24, minute)
            var targetDate = now.toLocalDate().plusDays(1)
            while (targetDate.dayOfWeek != day) targetDate = targetDate.plusDays(1)
            return LocalDateTime.of(targetDate, targetTime)
        }

        // "tomorrow at 9am" / "tomorrow 9"
        val tomorrowRegex = Regex("""tomorrow\s*(at\s*)?(\d{1,2})(:\d{2})?\s*(am|pm)?""")
        tomorrowRegex.find(expr)?.let { match ->
            val hour24 = parseHour(match.groupValues[2], match.groupValues[4])
            val minute = match.groupValues[3].removePrefix(":").toIntOrNull() ?: 0
            return LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(hour24, minute))
        }

        // "today at 6pm"
        val todayRegex = Regex("""today\s*(at\s*)?(\d{1,2})(:\d{2})?\s*(am|pm)?""")
        todayRegex.find(expr)?.let { match ->
            val hour24 = parseHour(match.groupValues[2], match.groupValues[4])
            val minute = match.groupValues[3].removePrefix(":").toIntOrNull() ?: 0
            return LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour24, minute))
        }

        // "in 30 minutes" / "in 2 hours"
        val inRegex = Regex("""in\s+(\d+)\s*(minute|hour|day|week)s?""")
        inRegex.find(expr)?.let { match ->
            val amount = match.groupValues[1].toLong()
            return when (match.groupValues[2]) {
                "minute" -> now.plusMinutes(amount)
                "hour" -> now.plusHours(amount)
                "day" -> now.plusDays(amount)
                "week" -> now.plusWeeks(amount)
                else -> now
            }
        }

        // "next week Monday"
        val nextWeekRegex = Regex("""next\s+week\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
        nextWeekRegex.find(expr)?.let { match ->
            val day = DayOfWeek.valueOf(match.groupValues[1].uppercase())
            var target = now.toLocalDate().plusWeeks(1).with(java.time.temporal.TemporalAdjusters.nextOrSame(day))
            return LocalDateTime.of(target, LocalTime.of(9, 0))
        }

        // Try ISO parse as fallback: "2026-05-15T18:00"
        try { return LocalDateTime.parse(expr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) } catch (_: Exception) {}
        try { return LocalDate.parse(expr, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() } catch (_: Exception) {}

        throw IllegalArgumentException("Unrecognized expression: $expr")
    }

    private fun parseHour(hourStr: String, ampm: String): Int {
        val h = hourStr.toInt()
        return when (ampm.lowercase()) {
            "am" -> if (h == 12) 0 else h
            "pm" -> if (h == 12) 12 else h + 12
            else -> h
        }
    }
}

/** Computes duration between two times or adds/subtracts from a time. */
object ComputeDurationTool : Tool {
    override val name = "compute_duration"
    override val description = "Add/subtract time or compute duration between two timestamps"
    override val parameters = listOf(
        ToolParam("from", "string", description = "Start timestamp (ISO or unix_ms)"),
        ToolParam("operation", "string", description = "'add_minutes', 'add_hours', 'add_days', or 'between'"),
        ToolParam("value", "int", required = false, description = "Amount to add, or end timestamp for 'between'")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val from = parseTimestamp(input["from"] ?: "")
        val op = input["operation"] ?: ""
        val value = input["value"]

        return when (op) {
            "add_minutes" -> {
                val mins = value?.toLongOrNull() ?: return ToolResult(false, "", "Need 'value' as int")
                val result = from.plusMinutes(mins)
                ToolResult(true, formatTimestamp(result))
            }
            "add_hours" -> {
                val hrs = value?.toLongOrNull() ?: return ToolResult(false, "", "Need 'value' as int")
                val result = from.plusHours(hrs)
                ToolResult(true, formatTimestamp(result))
            }
            "add_days" -> {
                val days = value?.toLongOrNull() ?: return ToolResult(false, "", "Need 'value' as int")
                val result = from.plusDays(days)
                ToolResult(true, formatTimestamp(result))
            }
            "between" -> {
                val to = parseTimestamp(value ?: "")
                val duration = Duration.between(from, to)
                ToolResult(true, "minutes: ${duration.toMinutes()}, hours: ${duration.toHours()}, days: ${duration.toDays()}")
            }
            else -> ToolResult(false, "", "Unknown operation: $op. Try add_minutes, add_hours, add_days, or between")
        }
    }

    private fun parseTimestamp(raw: String): LocalDateTime {
        return try {
            if (raw.contains("T")) LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            else LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(raw.toLong()), ZoneId.systemDefault())
        } catch (_: Exception) {
            LocalDateTime.now()
        }
    }

    private fun formatTimestamp(dt: LocalDateTime): String {
        val zoned = ZonedDateTime.of(dt, ZoneId.systemDefault())
        return "iso: ${zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}\nunix_ms: ${zoned.toInstant().toEpochMilli()}\nday_of_week: ${dt.dayOfWeek.name.lowercase()}"
    }
}

/** Quick day-of-week lookup for a given date. */
object GetDayOfWeekTool : Tool {
    override val name = "get_day_of_week"
    override val description = "What day of the week is a given date?"
    override val parameters = listOf(
        ToolParam("date", "string", description = "Date in YYYY-MM-DD format")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val dateStr = input["date"] ?: ""
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            ToolResult(true, "${date.dayOfWeek.name.lowercase()}")
        } catch (e: DateTimeParseException) {
            ToolResult(false, "", "Invalid date format. Use YYYY-MM-DD")
        }
    }
}
