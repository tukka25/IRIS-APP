package com.irisapp.platform.tools

/**
 * Maps internal tool names to model-facing aliases.
 *
 * Research shows SLMs are more accurate with semantically clear,
 * action-oriented tool names. This registry provides prompt-only
 * aliases — the internal name is still used for registration
 * and execution.
 */
object ToolAliasRegistry {

    private val aliases = mutableMapOf<String, String>()

    init {
        register("get_contact", "find_contact_by_name")
        register("lookup_contact", "find_contact_by_name")
        register("resolve_datetime", "parse_relative_datetime")
        register("get_calendar_events", "find_calendar_events")
        register("search_media", "find_music_or_video")
        register("search_files", "find_file_by_name")
        register("search_notes", "find_note_or_list")
        register("search_sms", "find_text_message")
        register("resolve_intent", "find_android_intent_for_action")
        register("search_places", "find_place_or_address")
        register("web_search", "search_the_web")
        register("get_device_location", "get_current_location")
        register("list_installed_apps", "list_installed_apps") // already clear
        register("get_current_time", "get_current_time")       // already clear
        register("compute_duration", "compute_time_duration")
        register("get_day_of_week", "get_day_of_week")         // already clear
        register("calculator", "calculate_math")
        register("validate_json", "validate_workflow_json")
        register("send_intent", "execute_android_intent")      // not shown to generation agents
        register("open_uri", "open_uri")                       // not shown to generation agents
        register("share_text", "share_text")                   // not shown to generation agents
        register("set_alarm", "set_alarm_clock")
        register("create_calendar_event", "create_calendar_event")
        register("set_countdown_timer", "set_countdown_timer")
        register("create_local_reminder", "create_local_reminder")
        register("open_settings_panel", "open_settings_panel")
        register("list_recent_notifications", "list_recent_notifications")
        register("get_clipboard_text", "get_clipboard_text_if_foreground")
        register("set_clipboard_text", "set_clipboard_text")
        register("open_deep_link", "open_deep_link")
    }

    fun register(internalName: String, modelAlias: String) {
        aliases[internalName] = modelAlias
    }

    /** Get the model-facing alias for an internal tool name. Falls back to internal name. */
    fun aliasFor(internalName: String): String = aliases[internalName] ?: internalName

    /** Get the internal name from an alias (for reverse lookup during parsing). */
    fun internalFor(alias: String): String? {
        val entry = aliases.entries.firstOrNull { it.value == alias }
        return entry?.key
    }
}
