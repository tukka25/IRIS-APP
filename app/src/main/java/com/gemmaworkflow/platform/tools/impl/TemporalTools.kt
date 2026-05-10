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
import java.time.temporal.TemporalAdjusters

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
 *
 * Structured input contract:
 * - expression: required natural-language time expression, e.g. "next Friday at 6pm"
 * - reference_time_iso: optional ISO_OFFSET_DATE_TIME, used as "now" for relative phrases
 * - timezone: optional IANA timezone, e.g. "Asia/Dubai"; defaults to the device timezone
 * - default_period: optional "am" | "pm", used only when the phrase has an ambiguous 1-12 hour
 *
 * Supports: "next Friday at 2pm", "6 o'clock on next Friday",
 * "tomorrow morning", "in 2 hours", "May 15 at 18:00", "next week Monday 9am".
 */
object ResolveDatetimeTool : Tool {
    override val name = "resolve_datetime"
    override val description = "Converts relative date/time text to iso/date/time/unix_ms using a reference time"
    override val parameters = listOf(
        ToolParam("expression", "string", description = "Normalize user phrase, e.g. 'next Friday at 6pm', 'tomorrow at 09:00', 'in 30 minutes'"),
        ToolParam("reference_time_iso", "string", required = false, description = "Current time as ISO offset datetime, e.g. 2026-05-09T14:30:00+04:00"),
        ToolParam("timezone", "string", required = false, description = "IANA timezone, e.g. Asia/Dubai"),
        ToolParam("default_period", "string", required = false, description = "Use 'pm' or 'am' only for ambiguous bare hours like '6 o clock'")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val expr = normalizeExpression(input["expression"].orEmpty())
        if (expr.isBlank()) return ToolResult(false, "", "Missing 'expression' param")

        val reference = parseReferenceTime(input["reference_time_iso"])
        val zone = parseZone(input["timezone"], reference?.zone)
        val defaultPeriod = parseDefaultPeriod(input["default_period"])
        val now = reference?.withZoneSameInstant(zone)?.toLocalDateTime() ?: LocalDateTime.now(zone)
        val result = try {
            resolveExpression(expr, now, defaultPeriod)
        } catch (e: Exception) {
            return ToolResult(false, "", "Could not parse: '$expr'. Use formats like 'next Friday at 6pm', '6pm on next Friday', 'tomorrow at 09:00', 'in 30 minutes', or '2026-05-15T18:00'")
        }

        val zoned = ZonedDateTime.of(result, zone)
        return ToolResult(true, buildString {
            appendLine("iso: ${zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
            appendLine("date: ${result.toLocalDate()}")
            appendLine("time: ${result.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}")
            appendLine("unix_ms: ${zoned.toInstant().toEpochMilli()}")
            appendLine("day_of_week: ${result.dayOfWeek.name.lowercase()}")
            if (usesAmbiguousHour(expr) && defaultPeriod != null) {
                appendLine("assumption: ambiguous hour resolved with default_period=$defaultPeriod")
            }
        }.trim())
    }

    private fun resolveExpression(expr: String, now: LocalDateTime, defaultPeriod: String?): LocalDateTime {
        parseDayAndTime(expr, now, defaultPeriod)?.let { return it }

        // "tomorrow morning" / "tomorrow afternoon" / "tomorrow evening"
        val tomorrowPartOfDayRegex = Regex("""tomorrow\s+(morning|afternoon|evening|night)""")
        tomorrowPartOfDayRegex.find(expr)?.let { match ->
            return LocalDateTime.of(now.toLocalDate().plusDays(1), partOfDayTime(match.groupValues[1]))
        }

        // "tomorrow at 9am" / "tomorrow 9"
        val tomorrowRegex = Regex("""tomorrow\s*(at\s*)?${TIME_PATTERN.pattern}""")
        tomorrowRegex.find(expr)?.let { match ->
            val hour24 = parseHour(match.groupValues[2], match.groupValues[4], defaultPeriod)
            val minute = match.groupValues[3].removePrefix(":").toIntOrNull() ?: 0
            return LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(hour24, minute))
        }

        // "today at 6pm"
        val todayRegex = Regex("""today\s*(at\s*)?${TIME_PATTERN.pattern}""")
        todayRegex.find(expr)?.let { match ->
            val hour24 = parseHour(match.groupValues[2], match.groupValues[4], defaultPeriod)
            val minute = match.groupValues[3].removePrefix(":").toIntOrNull() ?: 0
            val candidate = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour24, minute))
            return if (candidate <= now) candidate.plusDays(1) else candidate
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
            val target = now.toLocalDate().plusWeeks(1).with(TemporalAdjusters.nextOrSame(day))
            return LocalDateTime.of(target, LocalTime.of(9, 0))
        }

        // Try ISO parse as fallback: "2026-05-15T18:00"
        try { return LocalDateTime.parse(expr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) } catch (_: Exception) {}
        try { return LocalDate.parse(expr, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() } catch (_: Exception) {}

        throw IllegalArgumentException("Unrecognized expression: $expr")
    }

    private fun parseDayAndTime(expr: String, now: LocalDateTime, defaultPeriod: String?): LocalDateTime? {
        // "next Friday at 6pm", "next friday 18:00", "next friday at 6 o'clock"
        Regex("""(next\s+)?${DAY_PATTERN.pattern}\s*(at\s*)?${TIME_PATTERN.pattern}""")
            .find(expr)?.let { match ->
                val isExplicitNext = match.groupValues[1].isNotBlank()
                val day = parseDay(match.groupValues[2])
                val time = parseTime(match.groupValues[4], match.groupValues[5], match.groupValues[6], defaultPeriod)
                return futureWeekdayDateTime(day, time, now, isExplicitNext)
            }

        // "6pm on next Friday", "6 o'clock next friday"
        Regex("""${TIME_PATTERN.pattern}\s*(on\s*)?(next\s+)?${DAY_PATTERN.pattern}""")
            .find(expr)?.let { match ->
                val time = parseTime(match.groupValues[1], match.groupValues[2], match.groupValues[3], defaultPeriod)
                val isExplicitNext = match.groupValues[5].isNotBlank()
                val day = parseDay(match.groupValues[6])
                return futureWeekdayDateTime(day, time, now, isExplicitNext)
            }

        // "next Friday" with no time defaults to 09:00 so relative date still resolves.
        Regex("""(next\s+)?${DAY_PATTERN.pattern}""")
            .find(expr)?.let { match ->
                val isExplicitNext = match.groupValues[1].isNotBlank()
                val day = parseDay(match.groupValues[2])
                return futureWeekdayDateTime(day, LocalTime.of(9, 0), now, isExplicitNext)
            }

        return null
    }

    private fun parseHour(hourStr: String, ampm: String, defaultPeriod: String? = null): Int {
        val h = hourStr.toInt()
        return when (ampm.lowercase()) {
            "am", "a.m." -> if (h == 12) 0 else h
            "pm", "p.m." -> if (h == 12) 12 else h + 12
            else -> when (defaultPeriod) {
                "pm" -> if (h in 1..11) h + 12 else h
                "am" -> if (h == 12) 0 else h
                else -> h
            }
        }
    }

    private fun parseTime(hour: String, minute: String, ampm: String, defaultPeriod: String?): LocalTime {
        val hour24 = parseHour(hour, ampm, defaultPeriod)
        val minuteValue = minute.removePrefix(":").toIntOrNull() ?: 0
        return LocalTime.of(hour24, minuteValue)
    }

    private fun parseDay(raw: String): DayOfWeek =
        DayOfWeek.valueOf(raw.uppercase())

    private fun futureWeekdayDateTime(
        day: DayOfWeek,
        time: LocalTime,
        now: LocalDateTime,
        explicitNext: Boolean
    ): LocalDateTime {
        val targetDate = if (explicitNext) {
            now.toLocalDate().with(TemporalAdjusters.next(day))
        } else {
            now.toLocalDate().with(TemporalAdjusters.nextOrSame(day))
        }
        val candidate = LocalDateTime.of(targetDate, time)
        return if (!explicitNext && candidate <= now) {
            candidate.plusWeeks(1)
        } else {
            candidate
        }
    }

    private fun partOfDayTime(part: String): LocalTime = when (part) {
        "morning" -> LocalTime.of(9, 0)
        "afternoon" -> LocalTime.of(13, 0)
        "evening" -> LocalTime.of(18, 0)
        "night" -> LocalTime.of(20, 0)
        else -> LocalTime.of(9, 0)
    }

    private fun normalizeExpression(raw: String): String =
        raw.trim()
            .lowercase()
            .replace("o’clock", "o'clock")
            .replace("oclock", "o'clock")
            .replace(Regex("""\s+"""), " ")

    private fun parseReferenceTime(raw: String?): ZonedDateTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            ZonedDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.recoverCatching {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
        }.getOrNull()
    }

    private fun parseZone(raw: String?, fallback: ZoneId?): ZoneId {
        if (!raw.isNullOrBlank()) {
            runCatching { return ZoneId.of(raw) }
        }
        return fallback ?: ZoneId.systemDefault()
    }

    private fun parseDefaultPeriod(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        "pm", "p.m.", "afternoon", "evening", "night" -> "pm"
        "am", "a.m.", "morning" -> "am"
        else -> null
    }

    private fun usesAmbiguousHour(expr: String): Boolean {
        val match = TIME_PATTERN.find(expr) ?: return false
        val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return false
        val meridiem = match.groupValues.getOrNull(3)
        return meridiem.isNullOrBlank() && hour in 1..12
    }

    private val DAY_PATTERN = Regex("""(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
    private val TIME_PATTERN = Regex("""(\d{1,2})(:\d{2})?\s*(am|pm|a\.m\.|p\.m\.)?\s*(?:o'?clock)?""")
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
