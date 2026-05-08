package com.gemmaworkflow.platform.tools.reto

import android.util.Log

/**
 * Deterministic RETO layer planner for GemmaWorkflow MVP.
 *
 * Examines the user request and available tools, then produces
 * a RetoLayerSketch with ordered execution layers.
 *
 * Rules (from design doc):
 * - If request contains date/time phrase → resolve_datetime in Layer 0
 * - If request includes named person/contact or message/invite/call/email
 *   → get_contact in Layer 0
 * - If request references app/category and apps weren't pre-injected
 *   → list_installed_apps in Layer 0
 * - If final actions require Android intent availability
 *   → resolve_intent in Layer 1
 * - Always include validate_json in final validation layer
 * - Exclude ToolMode.EFFECTFUL from generation layers
 */
object RetoLayerPlanner {

    private const val TAG = "RetoLayerPlanner"

    /**
     * Generate a layered execution plan for a user request.
     *
     * @param request The user's natural language request
     * @param installedAppsSummary Already-injected installed apps (so we skip list_installed_apps tool)
     * @param availableTools All tool names available in ToolRegistry
     * @param needsIntentCheck Whether ActionPlan stage needs intent resolution
     */
    fun plan(
        request: String,
        installedAppsSummary: String = "",
        availableTools: Set<String> = emptySet(),
        needsIntentCheck: Boolean = true
    ): RetoLayerSketch {
        val layers = mutableListOf<RetoLayer>()
        val lowerRequest = request.lowercase()
        val entities = mutableMapOf<Int, MutableList<DetectedEntity>>()

        // ── Layer 0: Fact Grounding ──
        val factTools = mutableSetOf<String>()
        val layer0Entities = mutableListOf<DetectedEntity>()

        // Date/time detection
        val timeMatches = extractTimeExpressions(request)
        if (timeMatches.isNotEmpty() && "resolve_datetime" in availableTools) {
            factTools.add("resolve_datetime")
            timeMatches.take(2).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "time expression", "resolve_datetime"))
            }
        }
        if (timeMatches.isNotEmpty() && "get_current_time" in availableTools) {
            factTools.add("get_current_time")
        }

        // Contact detection
        val contactMatches = extractContactNames(request)
        if (contactMatches.isNotEmpty() && "get_contact" in availableTools) {
            factTools.add("get_contact")
            contactMatches.take(2).forEach { name ->
                layer0Entities.add(DetectedEntity(name, "contact name", "get_contact"))
            }
        }

        // App detection — only if not pre-injected
        val appMatches = extractAppReferences(request)
        if (appMatches.isNotEmpty() && installedAppsSummary.isBlank() && "list_installed_apps" in availableTools) {
            factTools.add("list_installed_apps")
            appMatches.take(1).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "app reference", "list_installed_apps"))
            }
        }

        // Location detection
        val locMatches = extractLocationReferences(request)
        if (locMatches.isNotEmpty() && "get_device_location" in availableTools) {
            factTools.add("get_device_location")
            locMatches.take(1).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "location", "get_device_location"))
            }
        }

        // Domain entity detection
        val mediaMatches = extractMediaReferences(request)
        if (mediaMatches.isNotEmpty() && "search_media" in availableTools) {
            factTools.add("search_media")
            mediaMatches.take(1).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "media/playlist", "search_media"))
            }
        }
        val fileMatches = extractFileReferences(request)
        if (fileMatches.isNotEmpty() && "search_files" in availableTools) {
            factTools.add("search_files")
            fileMatches.take(1).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "file/document", "search_files"))
            }
        }
        val noteMatches = extractNoteReferences(request)
        if (noteMatches.isNotEmpty() && "search_notes" in availableTools) {
            factTools.add("search_notes")
            noteMatches.take(1).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "note/list", "search_notes"))
            }
        }
        val smsMatches = extractSmsReferences(request)
        if (smsMatches.isNotEmpty() && "search_sms" in availableTools) {
            factTools.add("search_sms")
            smsMatches.take(1).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "SMS/message", "search_sms"))
            }
        }
        val calMatches = extractCalendarReferences(request)
        if (calMatches.isNotEmpty() && "get_calendar_events" in availableTools) {
            factTools.add("get_calendar_events")
            calMatches.take(1).forEach { text ->
                layer0Entities.add(DetectedEntity(text, "calendar event", "get_calendar_events"))
            }
        }

        // Add calculation tool if math expression detected
        if (hasMathExpression(lowerRequest) && "calculator" in availableTools) {
            factTools.add("calculator")
        }

        if (factTools.isNotEmpty()) {
            val objective = buildString {
                val parts = mutableListOf<String>()
                if (timeMatches.isNotEmpty()) parts.add("time")
                if (contactMatches.isNotEmpty()) parts.add("contact")
                if (mediaMatches.isNotEmpty()) parts.add("media")
                if (fileMatches.isNotEmpty()) parts.add("files")
                if (noteMatches.isNotEmpty()) parts.add("notes")
                if (smsMatches.isNotEmpty()) parts.add("messages")
                if (calMatches.isNotEmpty()) parts.add("calendar")
                if (appMatches.isNotEmpty() && installedAppsSummary.isBlank()) parts.add("app availability")
                if (locMatches.isNotEmpty()) parts.add("location")
                if (hasMathExpression(lowerRequest)) parts.add("calculation")
                append("Resolve factual inputs: ${parts.joinToString(" and ")}.")
            }
            layers.add(RetoLayer(
                index = 0,
                objective = objective,
                allowedTools = factTools,
                outputContract = "facts needed for workflow parameters"
            ))
            if (layer0Entities.isNotEmpty()) entities[0] = layer0Entities
            Log.d(TAG, "Layer 0: ${factTools.size} tools, ${layer0Entities.size} entities — $objective")
        }

        // ── Layer 1: Capability Check ──
        val capabilityTools = mutableSetOf<String>()
        if (needsIntentCheck && "resolve_intent" in availableTools) {
            capabilityTools.add("resolve_intent")
        }

        if (capabilityTools.isNotEmpty() && factTools.isNotEmpty()) {
            layers.add(RetoLayer(
                index = layers.size,
                objective = "Check Android capability availability for required actions.",
                allowedTools = capabilityTools,
                requiredObservations = setOf("contact.phone", "datetime.iso"),
                outputContract = "available handlers for required intents"
            ))
            Log.d(TAG, "Layer 1: capability check — ${capabilityTools.joinToString()}")
        }

        // ── Layer N: Final Validation ──
        if ("validate_json" in availableTools) {
            layers.add(RetoLayer(
                index = layers.size,
                objective = "Validate the final workflow JSON output.",
                allowedTools = setOf("validate_json"),
                outputContract = "valid JSON or validation error"
            ))
            Log.d(TAG, "Layer ${layers.size - 1}: final validation")
        }

        Log.i(TAG, "Planned ${layers.size} layers for request: ${request.take(80)}")
        return RetoLayerSketch(request = request, layers = layers, detectedEntities = entities)
    }

    // ── Extraction helpers (return matched text, not just boolean) ──

    private fun extractTimeExpressions(text: String): List<String> {
        val patterns = listOf(
            Regex("""next\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s*(at\s*\d{1,2}(:\d{2})?\s*(am|pm|o'clock)?)?""", RegexOption.IGNORE_CASE),
            Regex("""tomorrow\s*(at\s*\d{1,2}(:\d{2})?\s*(am|pm|o'clock)?)?""", RegexOption.IGNORE_CASE),
            Regex("""today\s*(at\s*\d{1,2}(:\d{2})?\s*(am|pm|o'clock)?)?""", RegexOption.IGNORE_CASE),
            Regex("""in\s+\d+\s+(minute|hour|day|week)s?""", RegexOption.IGNORE_CASE),
            Regex("""at\s+\d{1,2}(:\d{2})?\s*(am|pm|o'clock)?""", RegexOption.IGNORE_CASE),
            Regex("""\d{1,2}(:\d{2})?\s*(am|pm)""", RegexOption.IGNORE_CASE),
            Regex("""\d+\s*o'clock""", RegexOption.IGNORE_CASE),
            Regex("""next\s+(week|month|year)""", RegexOption.IGNORE_CASE),
            Regex("""every\s+(day|morning|evening|night|week|month)""", RegexOption.IGNORE_CASE),
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value.trim() }.toList() }
            .distinct().take(3)
    }

    private fun extractContactNames(text: String): List<String> {
        // Extract capitalized words that look like proper names, filtering common words
        val commonWords = setOf(
            "I", "Me", "My", "You", "Your", "He", "She", "It", "We", "They",
            "The", "A", "An", "This", "That", "These", "Those",
            "Is", "Are", "Was", "Were", "Be", "Been", "Being",
            "Have", "Has", "Had", "Do", "Does", "Did", "Will", "Would",
            "Can", "Could", "Should", "May", "Might", "Must",
            "To", "From", "In", "On", "At", "By", "For", "With",
            "And", "Or", "But", "If", "So", "Then", "Now", "Not", "No", "Yes",
            "Just", "Only", "Also", "Very", "Really", "About",
            "Hi", "Hello", "Hey", "Saying",
            "Friday", "Monday", "Tuesday", "Wednesday", "Thursday", "Saturday", "Sunday",
            "January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December",
            "GemmaWorkflow", "Android", "Google", "Spotify", "WhatsApp", "Telegram",
            "Calendar", "Calender", "Message", "Meeting", "Remind", "Reminder"
        )
        val namePattern = Regex("""\b([A-Z][a-z]{1,20})\b""")
        return namePattern.findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in commonWords }
            .distinct().take(2).toList()
    }

    private fun extractAppReferences(text: String): List<String> {
        val appWords = listOf("whatsapp", "telegram", "signal", "messenger", "gmail", "outlook",
            "maps", "waze", "spotify", "youtube", "chrome", "safari", "camera", "gallery",
            "photos", "notes", "keep", "alarm", "clock", "calculator")
        return appWords.filter { it in text.lowercase() }.take(2)
    }

    private fun extractLocationReferences(text: String): List<String> {
        val patterns = listOf(
            Regex("""(near me|nearby|at home|at work|at the gym|at school|at the store)""", RegexOption.IGNORE_CASE),
            Regex("""(when i arrive|when i leave|around here)""", RegexOption.IGNORE_CASE)
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value }.toList() }.take(1)
    }

    private fun extractMediaReferences(text: String): List<String> {
        val patterns = listOf(
            Regex("""(my\s+)?(\w+\s+)?(playlist|song|track|album|mix|podcast)""", RegexOption.IGNORE_CASE),
            Regex("""play\s+(my\s+)?(\w+\s+)?(playlist|song|music|mix)""", RegexOption.IGNORE_CASE)
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value.trim() }.toList() }.distinct().take(1)
    }

    private fun extractFileReferences(text: String): List<String> {
        val patterns = listOf(
            Regex("""(my\s+)?(\w+\s+)?(spreadsheet|pdf|document|file|resume|report|csv|excel|sheet)""", RegexOption.IGNORE_CASE),
            Regex("""open\s+(my\s+)?(\w+\s+)?(file|document|spreadsheet|pdf)""", RegexOption.IGNORE_CASE)
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value.trim() }.toList() }.distinct().take(1)
    }

    private fun extractNoteReferences(text: String): List<String> {
        val patterns = listOf(
            Regex("""(my\s+)?(\w+\s+)?(note|list|checklist|todo|grocery|shopping list)""", RegexOption.IGNORE_CASE),
            Regex("""find\s+(my\s+)?(\w+\s+)?(note|list|checklist)""", RegexOption.IGNORE_CASE)
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value.trim() }.toList() }.distinct().take(1)
    }

    private fun extractSmsReferences(text: String): List<String> {
        val patterns = listOf(
            Regex("""(message|text|sms)\s+(from|about|to)\s+\w+""", RegexOption.IGNORE_CASE),
            Regex("""find\s+(my\s+)?(message|text|sms|conversation)""", RegexOption.IGNORE_CASE)
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value.trim() }.toList() }.distinct().take(1)
    }

    private fun extractCalendarReferences(text: String): List<String> {
        val patterns = listOf(
            Regex("""(my\s+)?(\w+\s+)?(appointment|meeting|event|dentist|doctor)""", RegexOption.IGNORE_CASE),
            Regex("""(add|create|put|schedule)\s+.*(calendar|meeting|appointment|event)""", RegexOption.IGNORE_CASE),
            Regex("""(lunch|dinner|coffee|call)\s+with\s+\w+""", RegexOption.IGNORE_CASE)
        )
        return patterns.flatMap { it.findAll(text).map { m -> m.value.trim() }.toList() }.distinct().take(1)
    }

    // ── Boolean detection helpers (keep for math and general use) ──

    private fun hasTimeExpression(text: String): Boolean {
        val patterns = listOf(
            "tomorrow", "today", "yesterday", "tonight",
            "next week", "next month", "next year",
            "next monday", "next tuesday", "next wednesday", "next thursday",
            "next friday", "next saturday", "next sunday",
            "this week", "this month",
            "in \\d+ (minute|hour|day|week|month)",
            "at \\d+", "at noon", "at midnight",
            "every (day|morning|evening|night|week|month|hour)",
            "\\d+ o'clock", "\\d+oclock",
            "\\d+ ?[ap]m", "\\d+:\\d+",
            "daily", "weekly", "monthly"
        )
        return patterns.any { Regex(it).containsMatchIn(text) }
    }

    private fun hasContactReference(text: String): Boolean {
        val indicators = listOf(
            "message", "call", "text", "sms", "email", "invite",
            "send.*to", "tell", "notify", "remind",
            "contact", "phone number"
        )
        // Check for proper names (capitalized words) OR contact-related verbs
        val hasProperName = Regex("\\b[A-Z][a-z]+\\b").containsMatchIn(text)
        val hasContactVerb = indicators.any { Regex(it).containsMatchIn(text) }
        return hasProperName || hasContactVerb
    }

    private fun hasAppReference(text: String): Boolean {
        val appWords = listOf(
            "app", "application", "whatsapp", "telegram", "signal",
            "messenger", "gmail", "outlook", "calendar", "google calendar",
            "maps", "waze", "spotify", "youtube", "chrome", "safari",
            "camera", "gallery", "photos", "notes", "keep", "todo",
            "alarm", "clock", "timer", "calculator", "browser",
            "open", "launch", "start.*app", "using"
        )
        return appWords.any { it in text }
    }

    private fun hasLocationReference(text: String): Boolean {
        val locationWords = listOf(
            "near me", "nearby", "around", "location",
            "where", "at home", "at work", "at the office",
            "at the gym", "at school", "at the store",
            "when i arrive", "when i leave"
        )
        return locationWords.any { it in text }
    }

    private fun hasMathExpression(text: String): Boolean {
        val mathPatterns = listOf(
            Regex("\\d+\\s*[+\\-*/]\\s*\\d+"),
            Regex("\\b(calculate|compute|sum|total|average|percent|%)\\b")
        )
        return mathPatterns.any { it.containsMatchIn(text) }
    }

    private fun hasMediaReference(text: String): Boolean {
        val mediaWords = listOf(
            "playlist", "song", "track", "artist", "album", "music",
            "play", "shuffle", "podcast", "radio", "audio",
            "spotify", "youtube music", "apple music"
        )
        return mediaWords.any { it in text }
    }

    private fun hasFileReference(text: String): Boolean {
        val fileWords = listOf(
            "file", "document", "spreadsheet", "pdf", "csv",
            "excel", "sheet", "doc", "txt", "text file",
            "download", "resume", "report"
        )
        return fileWords.any { it in text }
    }

    private fun hasNoteReference(text: String): Boolean {
        val noteWords = listOf(
            "note", "notepad", "memo", "list", "checklist",
            "todo", "to-do", "to do", "grocery", "shopping list",
            "keep", "evernote", "onenote", "notion"
        )
        return noteWords.any { it in text }
    }

    private fun hasSmsReference(text: String): Boolean {
        val smsWords = listOf(
            "message", "text", "sms", "mms",
            "chat", "conversation", "thread"
        )
        return smsWords.any { it in text }
    }

    private fun hasCalendarReference(text: String): Boolean {
        val calendarWords = listOf(
            "appointment", "meeting", "event", "calendar",
            "schedule", "dentist", "doctor", "lunch with",
            "dinner with", "coffee with", "call with"
        )
        return calendarWords.any { it in text }
    }
}
