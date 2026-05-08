package com.gemmaworkflow.platform.tools.reto

/**
 * Central registry of RETO metadata for every tool.
 *
 * Maps tool name → ToolMetadata. Used by:
 * - RetoLayerPlanner (to determine layers)
 * - ToolSchemaGate (to block effectful tools during generation)
 * - RetoRepairAgent (to determine repairability)
 * - ObservationStore (to parse structured facts)
 */
object ToolMetadataRegistry {

    private val registry = mutableMapOf<String, ToolMetadata>()

    init {
        // ── Tier 1: Temporal (READ_ONLY, FACT_GROUNDING) ──
        register(ToolMetadata(
            name = "get_current_time",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("datetime.iso", "datetime.date", "datetime.time", "datetime.unix_ms", "datetime.day_of_week")
        ))
        register(ToolMetadata(
            name = "resolve_datetime",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("datetime.iso", "datetime.date", "datetime.time", "datetime.unix_ms", "datetime.day_of_week"),
            failureSignals = setOf("Could not parse", "Unrecognized expression"),
            repairable = true,
            maxRepairAttempts = 2
        ))
        register(ToolMetadata(
            name = "compute_duration",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("duration.seconds", "duration.minutes", "duration.hours")
        ))
        register(ToolMetadata(
            name = "get_day_of_week",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("datetime.day_of_week")
        ))

        // ── Tier 2: Device state (READ_ONLY, FACT_GROUNDING / CAPABILITY_CHECK) ──
        register(ToolMetadata(
            name = "list_installed_apps",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.CAPABILITY_CHECK),
            produces = setOf("device.installed_apps"),
            failureSignals = setOf("QUERY_ALL_PACKAGES permission is not granted")
        ))
        register(ToolMetadata(
            name = "resolve_intent",
            mode = ToolMode.DRY_RUN,
            layerHints = setOf(ToolLayerHint.CAPABILITY_CHECK),
            produces = setOf("intent.resolvable", "intent.handler_package"),
            failureSignals = setOf("No activity found to handle"),
            repairable = true,
            maxRepairAttempts = 1
        ))
        register(ToolMetadata(
            name = "get_device_location",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("location.lat", "location.lng", "location.address"),
            failureSignals = setOf("Location permission not granted", "Location services are disabled")
        ))

        // ── Tier 3: Search & Knowledge (READ_ONLY, FACT_GROUNDING) ──
        register(ToolMetadata(
            name = "web_search",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("search.results")
        ))
        register(ToolMetadata(
            name = "search_places",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("place.name", "place.lat", "place.lng")
        ))
        register(ToolMetadata(
            name = "get_contact",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("contact.name", "contact.phone", "contact.email"),
            failureSignals = setOf("READ_CONTACTS permission is not granted", "No contacts matching"),
            repairable = true,
            maxRepairAttempts = 1
        ))
        register(ToolMetadata(
            name = "lookup_contact",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING, ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("contact.name", "contact.phone", "contact.email"),
            failureSignals = setOf("READ_CONTACTS permission is not granted", "No contacts matching"),
            repairable = true,
            maxRepairAttempts = 1
        ))

        // ── Tier 6: Domain Entity Search (READ_ONLY, FACT_GROUNDING) ──
        register(ToolMetadata(
            name = "search_media",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("media.playlists", "media.songs", "media.artists"),
            failureSignals = setOf("READ_MEDIA_AUDIO permission not granted", "No media found")
        ))
        register(ToolMetadata(
            name = "search_files",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("file.name", "file.path"),
            failureSignals = setOf("No files found")
        ))
        register(ToolMetadata(
            name = "search_notes",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("note.apps", "note.results"),
            failureSignals = setOf("No note-taking apps detected")
        ))
        register(ToolMetadata(
            name = "search_sms",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("sms.address", "sms.body", "sms.date"),
            failureSignals = setOf("READ_SMS permission not granted", "No messages found")
        ))
        register(ToolMetadata(
            name = "get_calendar_events",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.FACT_GROUNDING),
            produces = setOf("calendar.event.title", "calendar.event.start", "calendar.event.location"),
            failureSignals = setOf("READ_CALENDAR permission not granted", "No calendar events found")
        ))

        // ── Tier 4: Execution (EFFECTFUL — blocked during generation) ──
        register(ToolMetadata(
            name = "send_intent",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false
        ))
        register(ToolMetadata(
            name = "open_uri",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false
        ))
        register(ToolMetadata(
            name = "share_text",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false
        ))
        register(ToolMetadata(
            name = "set_alarm",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false
        ))
        register(ToolMetadata(
            name = "create_calendar_event",
            mode = ToolMode.EFFECTFUL,
            layerHints = setOf(ToolLayerHint.EXECUTION),
            produces = setOf("execution.result"),
            generationAllowed = false
        ))

        // ── Tier 5: Reasoning (VALIDATION / READ_ONLY) ──
        register(ToolMetadata(
            name = "calculator",
            mode = ToolMode.READ_ONLY,
            layerHints = setOf(ToolLayerHint.PARAMETER_RESOLUTION),
            produces = setOf("calculation.result"),
            repairable = true,
            maxRepairAttempts = 1
        ))
        register(ToolMetadata(
            name = "validate_json",
            mode = ToolMode.VALIDATION,
            layerHints = setOf(ToolLayerHint.FINAL_VALIDATION),
            produces = setOf("json.valid"),
            failureSignals = setOf("JSON validation failed")
        ))
    }

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
