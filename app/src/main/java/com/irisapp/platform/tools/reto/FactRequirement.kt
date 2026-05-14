package com.irisapp.platform.tools.reto

/**
 * Maps a fact type to the resolver tool that can satisfy it.
 * Used by the Kotlin executor to deterministically resolve
 * all blocking requirements without asking the SLM.
 */
enum class FactType(
    val label: String,
    val resolverTool: String,
    val resolverParam: String = "query"  // param name to pass the mention to
) {
    CONTACT_PHONE("contact.phone", "get_contact", "name"),
    CONTACT_EMAIL("contact.email", "get_contact", "name"),
    DATETIME_ISO("datetime.iso", "resolve_datetime", "expression"),
    DATETIME_UNIX_MS("datetime.unix_ms", "resolve_datetime", "expression"),
    PLACE_COORDINATES("place.coordinates", "search_places", "query"),
    PLACE_ADDRESS("place.address", "search_places", "query"),
    MEDIA_URI("media.uri", "search_media", "query"),
    FILE_URI("file.uri", "search_files", "query"),
    NOTE_ID("note.id", "search_notes", "query"),
    SMS_THREAD("sms.thread", "search_sms", "query"),
    CALENDAR_EVENTS("calendar.events", "get_calendar_events", "query"),
    INSTALLED_APP("app.package", "list_installed_apps"),
    INTENT_HANDLER("intent.handler", "resolve_intent", "action"),
    WEB_SEARCH_RESULT("web.result", "web_search", "query"),
    CALCULATION_RESULT("calc.result", "calculator", "expression"),
    CURRENT_TIME("datetime.current", "get_current_time"),
    DEVICE_LOCATION("location.current", "get_device_location");

    companion object {
        fun fromLabel(label: String): FactType? =
            entries.find { it.label.equals(label, ignoreCase = true) }
    }
}

/**
 * A single fact that must be resolved before an action can be planned.
 * Produced by the SLM in Phase 0, resolved by Kotlin in Phase 1.
 */
data class FactRequirement(
    val id: String,                    // unique ID like "r1", "r2"
    val sourceAction: String,          // which action needs this, e.g. "sms.compose"
    val slot: String,                  // which parameter slot, e.g. "recipient"
    val mention: String,               // the text from the user request, e.g. "Maya"
    val factType: FactType,            // what kind of fact is needed
    val blocking: Boolean = true,      // true = workflow can't proceed without this
    val resolverTool: String? = null,  // optional SLM-selected resolver, validated against factType
    val toolArgs: Map<String, String> = emptyMap(),
    var status: RequirementStatus = RequirementStatus.PENDING,
    var resolvedValue: String? = null,
    var failureReason: String? = null
)

enum class RequirementStatus { PENDING, RESOLVED, AMBIGUOUS, FAILED, SKIPPED }

/**
 * The complete ledger of facts required by planned actions.
 * Built by the SLM in Phase 0, processed by the Kotlin executor in Phase 1.
 */
data class RequirementLedger(
    val actionCandidates: List<String>,
    val requirements: List<FactRequirement>,
    val literalSlots: List<GroundedSlotValue> = emptyList()
) {
    val blockingRequirements: List<FactRequirement>
        get() = requirements.filter { it.blocking }

    val resolvedRequirements: List<FactRequirement>
        get() = requirements.filter { it.status == RequirementStatus.RESOLVED }

    val unresolvedBlocking: List<FactRequirement>
        get() = requirements.filter { it.blocking && it.status != RequirementStatus.RESOLVED }

    fun resolve(requirementId: String, value: String) {
        requirements.find { it.id == requirementId }?.let {
            it.status = RequirementStatus.RESOLVED
            it.resolvedValue = value
            it.failureReason = null
        }
    }

    fun fail(requirementId: String, reason: String? = null) {
        requirements.find { it.id == requirementId }?.let {
            it.status = RequirementStatus.FAILED
            it.failureReason = reason
        }
    }

    fun skip(requirementId: String) {
        requirements.find { it.id == requirementId }?.let {
            it.status = RequirementStatus.SKIPPED
        }
    }

    /** Build a compact summary of resolved facts for the SLM prompt. */
    fun compactSummary(): String = buildString {
        val resolved = resolvedRequirements
        val groundedParams = buildList {
            literalSlots.forEach { slot ->
                add("${slot.sourceAction}.${slot.slot} = ${slot.value}")
            }
            resolved.mapNotNullTo(this) { req ->
                req.resolvedParamValue()?.let { value -> "${req.sourceAction}.${req.slot} = $value" }
            }
        }.distinct()

        if (groundedParams.isNotEmpty()) {
            appendLine("Grounded action params:")
            groundedParams.forEach { param ->
                appendLine("  $param")
            }
        }
        if (resolved.isEmpty()) {
            if (groundedParams.isEmpty()) {
                appendLine("No facts resolved yet.")
            }
        } else {
            appendLine("Resolved facts:")
            resolved.forEach { req ->
                appendLine("  ${req.factType.label} from \"${req.mention}\" →")
                appendLine(req.resolvedValue.orEmpty().trim().prependIndent("    "))
            }
        }
        val failed = requirements.filter { it.status == RequirementStatus.FAILED }
        if (failed.isNotEmpty()) {
            appendLine("Failed to resolve:")
            failed.forEach { req ->
                appendLine("  ${req.factType.label} from \"${req.mention}\" → FAILED${req.failureReason?.let { ": $it" }.orEmpty()}")
            }
        }
    }

    companion object {
        val EMPTY = RequirementLedger(emptyList(), emptyList())
    }
}

private fun FactRequirement.resolvedParamValue(): String? {
    val raw = resolvedValue.orEmpty()
    if (raw.isBlank()) return null

    return when (factType) {
        FactType.DATETIME_UNIX_MS -> Regex("""(?im)^\s*(?:unix_ms|millis|epoch_millis)\s*:\s*(\d+)\s*$""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)

        FactType.DATETIME_ISO -> Regex("""(?im)^\s*iso\s*:\s*(\S+)\s*$""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)

        FactType.CONTACT_PHONE -> extractPipeValue(raw, "phone")
            ?.let(::firstListValue)
            ?.takeUnless { it.equals("none", ignoreCase = true) }

        FactType.CONTACT_EMAIL -> extractPipeValue(raw, "email")
            ?.let(::firstListValue)
            ?.takeUnless { it.equals("none", ignoreCase = true) }

        else -> null
    }
}

private fun extractPipeValue(raw: String, key: String): String? {
    val line = raw.lines().firstOrNull { it.contains("$key:", ignoreCase = true) } ?: return null
    return Regex("""(?i)\b$key\s*:\s*([^|]+)""")
        .find(line)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
}

private fun firstListValue(raw: String): String {
    return raw
        .trim()
        .removePrefix("[")
        .removeSuffix("]")
        .split(",")
        .firstOrNull()
        ?.trim()
        .orEmpty()
}

data class GroundedSlotValue(
    val sourceAction: String,
    val slot: String,
    val value: String,
    val reason: String = ""
)
