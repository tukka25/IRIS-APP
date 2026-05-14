package com.irisapp.platform.tools.reto

/**
 * Central registry of RETO metadata for every tool.
 *
 * Maps tool name → ToolMetadata. Used by:
 * - FindSkill.schemaFor(...) to inject parameter examples
 * - Resolver/coverage logic to understand tool safety and produced facts
 */
object ToolMetadataRegistry {

    private val registry = mutableMapOf<String, ToolMetadata>()

    init {
        // ── Tier 1: Temporal (READ_ONLY, FACT_GROUNDING) ──
        register(ToolMetadata(
            name = "get_current_time",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("datetime.iso", "datetime.date", "datetime.time", "datetime.unix_ms", "datetime.day_of_week"),
            examples = listOf(
                ex("Read current device time"),
                ex("Anchor a relative date before resolving it")
            )
        ))
        register(ToolMetadata(
            name = "resolve_datetime",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("datetime.iso", "datetime.date", "datetime.time", "datetime.unix_ms", "datetime.day_of_week"),
            failureSignals = setOf("Could not parse", "Unrecognized expression"),
            repairable = true,
            maxRepairAttempts = 2,
            examples = listOf(
                ex("Resolve a meeting time", "expression" to "next Friday at 6 o'clock", "reference_time_iso" to "2026-05-10T14:30:00+04:00", "timezone" to "Asia/Dubai", "default_period" to "pm"),
                ex("Resolve a relative reminder", "expression" to "in 30 minutes", "reference_time_iso" to "2026-05-10T14:30:00+04:00", "timezone" to "Asia/Dubai")
            )
        ))
        register(ToolMetadata(
            name = "compute_duration",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("duration.seconds", "duration.minutes", "duration.hours"),
            examples = listOf(
                ex("Add two hours", "from" to "2026-05-10T14:30:00+04:00", "operation" to "add_hours", "value" to "2"),
                ex("Compute until another timestamp", "from" to "2026-05-10T14:30:00+04:00", "operation" to "between", "value" to "2026-05-10T18:00:00+04:00")
            )
        ))
        register(ToolMetadata(
            name = "get_day_of_week",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("datetime.day_of_week"),
            examples = listOf(
                ex("Check weekday for a date", "date" to "2026-05-15"),
                ex("Check if event falls on weekend", "date" to "2026-05-16")
            )
        ))

        // ── Tier 2: Device state (READ_ONLY, FACT_GROUNDING / CAPABILITY_CHECK) ──
        register(ToolMetadata(
            name = "list_installed_apps",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.CAPABILITY_CHECK),
            produces = setOf("device.installed_apps"),
            failureSignals = setOf("QUERY_ALL_PACKAGES permission is not granted"),
            examples = listOf(
                ex("List installed launchable apps"),
                ex("Find packages for app.open candidates")
            )
        ))
        register(ToolMetadata(
            name = "resolve_intent",
            mode = ToolMode.DRY_RUN,
            layerHints = setOf(ToolLayerHint.CAPABILITY_CHECK),
            produces = setOf("intent.resolvable", "intent.handler_package"),
            failureSignals = setOf("No activity found to handle"),
            repairable = true,
            maxRepairAttempts = 1,
            examples = listOf(
                ex("Check web URL handler", "action" to "android.intent.action.VIEW", "data_uri" to "https://example.com"),
                ex("Check share text handler", "action" to "android.intent.action.SEND", "mime_type" to "text/plain")
            )
        ))
        register(ToolMetadata(
            name = "get_device_location",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("location.lat", "location.lng", "location.address"),
            failureSignals = setOf("Location permission not granted", "Location services are disabled"),
            examples = listOf(
                ex("Get current location for nearby search"),
                ex("Ground a navigation request from current location")
            )
        ))

        // ── Tier 3: Search & Knowledge (READ_ONLY, FACT_GROUNDING) ──
        register(ToolMetadata(
            name = "web_search",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("search.results"),
            examples = listOf(
                ex("Search web for information", "query" to "weather in Dubai tomorrow"),
                ex("Find public business info", "query" to "nearest Emirates NBD branch Dubai Marina")
            )
        ))
        register(ToolMetadata(
            name = "search_places",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("place.name", "place.lat", "place.lng"),
            examples = listOf(
                ex("Resolve a destination", "query" to "Dubai Mall"),
                ex("Search nearby category", "query" to "coffee shop", "near" to "Dubai Marina")
            )
        ))
        register(ToolMetadata(
            name = "get_contact",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("contact.name", "contact.phone", "contact.email"),
            failureSignals = setOf("READ_CONTACTS permission is not granted", "No contacts matching"),
            repairable = true,
            maxRepairAttempts = 1,
            examples = listOf(
                ex("Resolve message recipient", "name" to "Maya"),
                ex("Resolve call target", "name" to "Mom")
            )
        ))
        register(ToolMetadata(
            name = "lookup_contact",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("contact.name", "contact.phone", "contact.email"),
            failureSignals = setOf("READ_CONTACTS permission is not granted", "No contacts matching"),
            repairable = true,
            maxRepairAttempts = 1,
            examples = listOf(
                ex("Resolve contact by partial name", "name" to "Maya"),
                ex("Resolve email recipient", "name" to "Ali work")
            )
        ))

        // ── Tier 6: Domain Entity Search (READ_ONLY, FACT_GROUNDING) ──
        register(ToolMetadata(
            name = "search_media",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("media.playlists", "media.songs", "media.artists"),
            failureSignals = setOf("READ_MEDIA_AUDIO permission not granted", "No media found"),
            examples = listOf(
                ex("Find a playlist", "query" to "focus", "type" to "playlist"),
                ex("Find a song or artist", "query" to "Daft Punk", "type" to "artist")
            )
        ))
        register(ToolMetadata(
            name = "search_files",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("file.name", "file.path"),
            failureSignals = setOf("No files found"),
            examples = listOf(
                ex("Find a PDF", "query" to "receipt", "kind" to "pdf"),
                ex("Find a spreadsheet", "query" to "budget", "kind" to "spreadsheet")
            )
        ))
        register(ToolMetadata(
            name = "search_notes",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("note.apps", "note.results"),
            failureSignals = setOf("No note-taking apps detected"),
            examples = listOf(
                ex("Find grocery note", "query" to "grocery"),
                ex("Find meeting notes", "query" to "meeting notes")
            )
        ))
        register(ToolMetadata(
            name = "search_sms",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("sms.address", "sms.body", "sms.date"),
            failureSignals = setOf("READ_SMS permission not granted", "No messages found"),
            examples = listOf(
                ex("Find texts from a person", "query" to "Maya", "folder" to "inbox"),
                ex("Find a message by content", "query" to "meeting location")
            )
        ))
        register(ToolMetadata(
            name = "get_calendar_events",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("calendar.event.title", "calendar.event.start", "calendar.event.location"),
            failureSignals = setOf("READ_CALENDAR permission not granted", "No calendar events found"),
            examples = listOf(
                ex("Find upcoming dentist appointment", "query" to "dentist", "lookahead_days" to "30"),
                ex("Find team meeting", "query" to "team meeting", "lookahead_days" to "7")
            )
        ))

        // ── Tier 4: Execution (EFFECTFUL — blocked during generation) ──
        register(ToolMetadata(
            name = "send_intent",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Open an HTTPS URL intent", "action" to "android.intent.action.VIEW", "data_uri" to "https://example.com"),
                ex("Open SMS compose intent", "action" to "android.intent.action.SENDTO", "data_uri" to "smsto:+15550101001")
            )
        ))
        register(ToolMetadata(
            name = "open_uri",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Open web URL", "uri" to "https://example.com"),
                ex("Open maps query", "uri" to "geo:0,0?q=Dubai%20Mall")
            )
        ))
        register(ToolMetadata(
            name = "share_text",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Share plain text", "text" to "Meeting notes", "title" to "Share via"),
                ex("Share a reminder", "text" to "Remember to call Maya")
            )
        ))
        register(ToolMetadata(
            name = "set_alarm",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Set morning alarm", "hour" to "7", "minutes" to "30", "message" to "Wake up"),
                ex("Set quick alarm", "hour" to "18", "minutes" to "0")
            )
        ))
        register(ToolMetadata(
            name = "create_calendar_event",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Create meeting event", "title" to "Meeting with Maya", "begin_time_millis" to "1778810400000", "location" to "Office"),
                ex("Create event with end time", "title" to "Workout", "begin_time_millis" to "1778810400000", "end_time_millis" to "1778814000000")
            )
        ))

        // ── Tier 5: Reasoning (VALIDATION / READ_ONLY) ──
        register(ToolMetadata(
            name = "calculator",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("calculation.result"),
            repairable = true,
            maxRepairAttempts = 1,
            examples = listOf(
                ex("Convert minutes to seconds", "expression" to "5*60"),
                ex("Compute one day in seconds", "expression" to "24*60*60")
            )
        ))
        register(ToolMetadata(
            name = "validate_json",
            mode = ToolMode.VALIDATION,
            layerHints = setOf(ToolLayerHint.FINAL_VALIDATION),
            produces = setOf("json.valid"),
            failureSignals = setOf("JSON validation failed"),
            examples = listOf(
                ex("Validate workflow JSON", "json" to "{\"name\":\"Test\",\"actions\":[]}"),
                ex("Check repaired workflow JSON", "json" to "{\"trigger\":{\"type\":\"manual\"},\"actions\":[]}")
            )
        ))

        // ── Tier 7: Reminders, Settings, Notifications, Clipboard, Browser ──
        register(ToolMetadata(
            name = "set_countdown_timer",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Set five minute timer", "minutes" to "5", "message" to "Tea"),
                ex("Set focus timer", "minutes" to "25", "message" to "Focus")
            )
        ))
        register(ToolMetadata(
            name = "create_local_reminder",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Create timed reminder", "title" to "Call Maya", "time_millis" to "1778810400000", "message" to "Ask about meeting"),
                ex("Create simple reminder", "title" to "Buy milk", "time_millis" to "1778810400000")
            )
        ))
        register(ToolMetadata(
            name = "open_settings_panel",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Open Wi-Fi settings", "panel" to "wifi"),
                ex("Open app settings", "panel" to "app_settings")
            )
        ))
        register(ToolMetadata(
            name = "list_recent_notifications",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("notification.list"),
            failureSignals = setOf("Notification access"),
            examples = listOf(
                ex("List recent notifications", "limit" to "5"),
                ex("Filter notifications by app", "app" to "com.whatsapp", "limit" to "3")
            )
        ))
        register(ToolMetadata(
            name = "get_clipboard_text",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("clipboard.text"),
            examples = listOf(
                ex("Read current clipboard"),
                ex("Use clipboard as share text source")
            )
        ))
        register(ToolMetadata(
            name = "set_clipboard_text",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Copy short text", "text" to "Meeting notes copied"),
                ex("Copy generated message", "text" to "Hi Maya, see you at 6.")
            )
        ))
        register(ToolMetadata(
            name = "open_deep_link",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false,
            examples = listOf(
                ex("Open HTTPS deep link", "url" to "https://example.com/path"),
                ex("Open app deep link", "url" to "geo:0,0?q=Dubai%20Mall")
            )
        ))

    }

    private fun ex(description: String, vararg args: Pair<String, String>): ToolExample =
        ToolExample(description = description, args = mapOf(*args))

    fun register(metadata: ToolMetadata) {
        registry[metadata.name] = metadata
    }

    fun get(name: String): ToolMetadata? = registry[name]

    fun all(): Map<String, ToolMetadata> = registry.toMap()

    /** Get metadata for all tools in the given set. */
    fun forTools(toolNames: Set<String>): List<ToolMetadata> =
        toolNames.mapNotNull { registry[it] }

    /** Tools that are safe to use during generation (not EFFECTFUL). */
    fun generationSafe(): List<ToolMetadata> =
        registry.values.filter { it.generationAllowed }

    /** Tools matching a specific layer hint. */
    fun forLayerHint(hint: ToolLayerHint): List<ToolMetadata> =
        registry.values.filter { hint in it.layerHints }
}
