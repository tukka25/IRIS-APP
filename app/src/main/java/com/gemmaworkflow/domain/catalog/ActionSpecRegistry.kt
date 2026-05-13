package com.gemmaworkflow.domain.catalog

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import com.gemmaworkflow.platform.tools.reto.FactType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Declarative source of truth for every action the model is allowed to pick.
 *
 * Android intent constants, extra keys, URI templates, and flags stay here. The
 * model only receives the prompt-safe summaries produced by this registry.
 */
data class ActionSpec(
    val id: String,
    val label: String,
    val description: String,
    val params: List<ParamSpec>,
    val execution: ExecutionSpec,
    val availability: AvailabilitySpec = AvailabilitySpec.IntentResolvable,
    val triggerCompatible: Set<String>,
    val requiresConfirmation: Boolean = false,
    val requiredPermissions: List<String> = emptyList(),
    val logicalActions: Set<LogicalAction> = emptySet(),
    val source: ActionSource = ActionSource.Standard,
    val appLabels: Set<String> = emptySet(),
    val appKeywords: Set<String> = emptySet(),
    val fallbackActionIds: List<String> = emptyList(),
    val toolBindings: List<ActionToolBinding> = emptyList(),
    val sharedToolGroups: Set<SharedToolGroup> = emptySet(),
    val examples: List<JsonObject> = emptyList(),
    val timeoutSeconds: Int = 0,
    /** When true, this step can run in parallel with the next independent step. */
    val parallelExecutionEnabled: Boolean = false,
    val installedAppListProviderId: String? = null
)

data class ActionToolBinding(
    val toolName: String,
    val purpose: String,
    val paramNames: Set<String> = emptySet()
)

enum class SharedToolGroup(val toolBindings: List<ActionToolBinding>) {
    Temporal(
        listOf(
            ActionToolBinding("resolve_datetime", "Resolve relative date/time phrases into ISO and epoch milliseconds"),
            ActionToolBinding("compute_duration", "Compute durations or convert time spans"),
            ActionToolBinding("get_current_time", "Read the current device date/time")
        )
    ),
    Contacts(
        listOf(
            ActionToolBinding("get_contact", "Resolve a contact name into phone/email facts")
        )
    ),
    Places(
        listOf(
            ActionToolBinding("search_places", "Resolve place names or nearby-place requests")
        )
    ),
    Media(
        listOf(
            ActionToolBinding("search_media", "Find local songs, playlists, artists, or albums")
        )
    ),
    Files(
        listOf(
            ActionToolBinding("search_files", "Find local documents or files")
        )
    ),
    Notes(
        listOf(
            ActionToolBinding("search_notes", "Find note apps or existing notes")
        )
    ),
    Apps(
        listOf(
            ActionToolBinding("list_installed_apps", "Resolve app labels into installed package names")
        )
    ),
    Calculation(
        listOf(
            ActionToolBinding("calculator", "Compute numeric values needed for parameters")
        )
    ),
    Validation(
        listOf(
            ActionToolBinding("validate_json", "Validate generated JSON before save")
        )
    )
}

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val required: Boolean = true,
    val description: String = "",
    val enumValues: List<String> = emptyList(),
    val factType: FactType? = null,
    val resolverTool: String? = factType?.resolverTool
)

enum class LogicalAction(val id: String) {
    SendMessage("send_message"),
    MakeCall("make_call"),
    CreateEvent("create_event"),
    SetReminder("set_reminder"),
    SetAlarm("set_alarm"),
    OpenApp("open_app"),
    Search("search"),
    Share("share"),
    Navigate("navigate"),
    PlayMedia("play_media"),
    OpenFile("open_file"),
    TakeNote("take_note"),
    CheckNotification("check_notification"),
    GetInfo("get_info"),
    Other("other");

    companion object {
        fun fromId(id: String): LogicalAction? =
            entries.find { it.id.equals(id, ignoreCase = true) }
    }
}

enum class ActionSource {
    Standard,
    AppSpecific,
    Internal
}

enum class ParamType(val promptName: String) {
    String("string"),
    Url("url"),
    Uri("uri"),
    Int("int"),
    Long("long"),
    Boolean("boolean"),
    StringArray("string_array"),
    DateTimeMillis("datetime_millis"),
    Enum("enum"),
    AppPicker("app_picker")
}

sealed interface ExecutionSpec {
    data class AndroidIntent(
        val action: String,
        val dataTemplate: String? = null,
        val mimeType: String? = null,
        val extras: List<ExtraSpec> = emptyList(),
        val flags: List<IntentFlag> = listOf(IntentFlag.NewTask),
        val packagePolicy: PackagePolicy = PackagePolicy.None,
        val chooserTitle: String? = null
    ) : ExecutionSpec

    data class PackageLaunch(
        val packageParamName: String = "package_name"
    ) : ExecutionSpec

    data class InternalTool(
        val toolName: String
    ) : ExecutionSpec

    /**
     * Chrome Custom Tabs — opens the URL in an in-app browser tab.
     * Falls back to [fallbackActionIds] when Chrome is not available.
     */
    data class CustomTab(
        val urlTemplate: String,
        val toolbarColor: Int? = null
    ) : ExecutionSpec

    /**
     * Built-in action handled directly by the app's internal executors.
     */
    data object BuiltIn : ExecutionSpec
}

data class ExtraSpec(
    val paramName: String,
    val extraKey: String,
    val type: ParamType
)

enum class IntentFlag {
    NewTask,
    GrantReadUriPermission
}

sealed interface PackagePolicy {
    data object None : PackagePolicy
    data class Exact(val packageName: String) : PackagePolicy
}

sealed interface AvailabilitySpec {
    data object IntentResolvable : AvailabilitySpec
    data object Always : AvailabilitySpec
    data class RequiredPackage(val packageName: String) : AvailabilitySpec
}

object ActionSpecRegistry {

    private val installedAppProviders: Map<String, (Context) -> List<Pair<String, String>>> = mapOf(
        "installed_apps" to { context: Context ->
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val flags = if (android.os.Build.VERSION.SDK_INT >= 33) {
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            } else {
                @Suppress("DEPRECATION") android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            }
            @Suppress("DEPRECATION")
            val activities: List<android.content.pm.ResolveInfo> = pm.queryIntentActivities(intent, flags)
            activities
                .map { ri: android.content.pm.ResolveInfo -> Pair(ri.loadLabel(pm).toString(), ri.activityInfo.packageName) }
                .distinct()
                .sortedBy { it.first.lowercase() }
        }
    )

    /** Returns list of (label, packageName) pairs for the given provider ID. */
    fun getInstalledAppList(providerId: String, context: Context): List<Pair<String, String>> {
        val provider: ((Context) -> List<Pair<String, String>>)? = installedAppProviders[providerId]
        return provider?.invoke(context) ?: emptyList()
    }

    val all: List<ActionSpec> = listOf(
        ActionSpec(
            id = "browser.open_url",
            label = "Open URL",
            description = "Open a web URL in a Chrome Custom Tab inside the app (no app switch). Falls back to external browser if Chrome is unavailable.",
            params = listOf(
                ParamSpec("url", ParamType.Url, description = "Full http or https URL to open")
            ),
            execution = ExecutionSpec.CustomTab(
                urlTemplate = "{url}"
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            logicalActions = setOf(LogicalAction.Search, LogicalAction.GetInfo),
            appKeywords = setOf("browser", "chrome", "web", "url", "link"),
            sharedToolGroups = setOf(SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "browser.open_url")
                    put("params", buildJsonObject { put("url", "https://example.com") })
                }
            )
        ),
        ActionSpec(
            id = "browser.search",
            label = "Search web",
            description = "Open the user's web search provider with a query.",
            params = listOf(
                ParamSpec("query", ParamType.String, description = "Search query")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_WEB_SEARCH,
                extras = listOf(ExtraSpec("query", SearchManager.QUERY, ParamType.String))
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            logicalActions = setOf(LogicalAction.Search, LogicalAction.GetInfo),
            appKeywords = setOf("browser", "chrome", "search", "google", "web"),
            sharedToolGroups = setOf(SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "browser.search")
                    put("params", buildJsonObject { put("query", "weather tomorrow") })
                }
            )
        ),
        ActionSpec(
            id = "maps.open_place",
            label = "Open place",
            description = "Open a place, address, or search query in a maps app.",
            params = listOf(
                ParamSpec("query", ParamType.String, description = "Place name, address, or search query")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_VIEW,
                dataTemplate = "geo:0,0?q={query}"
            ),
            triggerCompatible = setOf("manual", "nfc"),
            logicalActions = setOf(LogicalAction.Navigate, LogicalAction.Search),
            appKeywords = setOf("maps", "navigation", "place", "address", "nearby"),
            fallbackActionIds = listOf("browser.open_url"),
            sharedToolGroups = setOf(SharedToolGroup.Places, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "maps.open_place")
                    put("params", buildJsonObject { put("query", "coffee shop near me") })
                }
            )
        ),
        ActionSpec(
            id = "maps.navigate",
            label = "Navigate",
            description = "Open turn-by-turn navigation to a destination in a maps app.",
            params = listOf(
                ParamSpec("destination", ParamType.String, description = "Destination place name or address")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_VIEW,
                dataTemplate = "google.navigation:q={destination}"
            ),
            triggerCompatible = setOf("manual", "nfc"),
            logicalActions = setOf(LogicalAction.Navigate),
            appKeywords = setOf("maps", "navigation", "directions", "drive"),
            fallbackActionIds = listOf("maps.open_place", "browser.open_url"),
            sharedToolGroups = setOf(SharedToolGroup.Places, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "maps.navigate")
                    put("params", buildJsonObject { put("destination", "Dubai Mall") })
                }
            )
        ),
        ActionSpec(
            id = "share.share_text",
            label = "Copy text to clipboard",
            description = "Silently copy text to the system clipboard.",
            params = listOf(
                ParamSpec("text", ParamType.String, description = "Text content to copy to clipboard")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "share_sheet", "nfc"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.Share, LogicalAction.SendMessage),
            appKeywords = setOf("share", "message", "text"),
            sharedToolGroups = setOf(SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "share.share_text")
                    put("params", buildJsonObject { put("text", "Meeting notes: ...") })
                }
            )
        ),
        ActionSpec(
            id = "share.share_image",
            label = "Copy image to clipboard",
            description = "Silently copy an image URI to the clipboard.",
            params = listOf(
                ParamSpec("uri", ParamType.Uri, description = "Content URI of the image to copy")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "share_sheet"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.Share),
            appKeywords = setOf("share", "image", "photo"),
            fallbackActionIds = listOf("share.share_text"),
            sharedToolGroups = setOf(SharedToolGroup.Files, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "share.share_image")
                    put("params", buildJsonObject { put("uri", "content://media/external/images/media/1") })
                }
            )
        ),
        ActionSpec(
            id = "sms.compose",
            label = "Compose SMS",
            description = "Open the SMS app with an optional recipient and a prefilled message.",
            params = listOf(
                ParamSpec(
                    "phone",
                    ParamType.String,
                    required = false,
                    description = "Optional phone number",
                    factType = FactType.CONTACT_PHONE
                ),
                ParamSpec("message", ParamType.String, description = "Message body")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_SENDTO,
                dataTemplate = "smsto:{phone}",
                extras = listOf(ExtraSpec("message", "sms_body", ParamType.String))
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            requiredPermissions = emptyList(),
            logicalActions = setOf(LogicalAction.SendMessage),
            appKeywords = setOf("sms", "message", "messages", "text"),
            fallbackActionIds = listOf("share.share_text"),
            sharedToolGroups = setOf(SharedToolGroup.Contacts, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "sms.compose")
                    put("params", buildJsonObject {
                        put("phone", "+15551234567")
                        put("message", "I am on my way.")
                    })
                }
            )
        ),
        ActionSpec(
            id = "whatsapp.send_text",
            label = "Send WhatsApp text",
            description = "Open WhatsApp with a phone number and optional prefilled text.",
            params = listOf(
                ParamSpec(
                    "phone",
                    ParamType.String,
                    description = "Phone number in international format without plus sign",
                    factType = FactType.CONTACT_PHONE
                ),
                ParamSpec("text", ParamType.String, required = false, description = "Optional text to prefill")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_VIEW,
                dataTemplate = "https://wa.me/{phone}?text={text}",
                packagePolicy = PackagePolicy.Exact("com.whatsapp")
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            requiredPermissions = emptyList(),
            logicalActions = setOf(LogicalAction.SendMessage),
            source = ActionSource.AppSpecific,
            appLabels = setOf("WhatsApp"),
            appKeywords = setOf("whatsapp", "message", "chat"),
            fallbackActionIds = listOf("sms.compose", "share.share_text"),
            sharedToolGroups = setOf(SharedToolGroup.Contacts, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "whatsapp.send_text")
                    put("params", buildJsonObject {
                        put("phone", "15551234567")
                        put("text", "Hi, just checking in.")
                    })
                }
            )
        ),
        ActionSpec(
            id = "phone.dial",
            label = "Dial phone",
            description = "Open the phone dialer with a prefilled phone number. Does not auto-call.",
            params = listOf(
                ParamSpec(
                    "phone",
                    ParamType.String,
                    description = "Phone number to dial",
                    factType = FactType.CONTACT_PHONE
                )
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_DIAL,
                dataTemplate = "tel:{phone}"
            ),
            triggerCompatible = setOf("manual", "nfc"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.MakeCall),
            appKeywords = setOf("phone", "dial", "call"),
            sharedToolGroups = setOf(SharedToolGroup.Contacts, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "phone.dial")
                    put("params", buildJsonObject { put("phone", "+15551234567") })
                }
            )
        ),
        ActionSpec(
            id = "alarm.set_alarm",
            label = "Set silent alarm",
            description = "Set a silent alarm via AlarmManager — no alarm app UI is opened.",
            params = listOf(
                ParamSpec("hour", ParamType.Int, description = "Hour in 24-hour time, 0-23"),
                ParamSpec("minutes", ParamType.Int, description = "Minutes, 0-59"),
                ParamSpec("message", ParamType.String, required = false, description = "Optional alarm label")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.SetAlarm),
            appKeywords = setOf("alarm", "clock", "wake"),
            sharedToolGroups = setOf(SharedToolGroup.Temporal, SharedToolGroup.Calculation, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "alarm.set_alarm")
                    put("params", buildJsonObject {
                        put("hour", 7)
                        put("minutes", 30)
                        put("message", "Morning workout")
                    })
                }
            )
        ),
        ActionSpec(
            id = "alarm.set_timer",
            label = "Set timer",
            description = "Open the clock app to create or confirm a countdown timer.",
            params = listOf(
                ParamSpec("seconds", ParamType.Int, description = "Timer duration in seconds"),
                ParamSpec("message", ParamType.String, required = false, description = "Optional timer label")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = AlarmClock.ACTION_SET_TIMER,
                extras = listOf(
                    ExtraSpec("seconds", AlarmClock.EXTRA_LENGTH, ParamType.Int),
                    ExtraSpec("message", AlarmClock.EXTRA_MESSAGE, ParamType.String)
                )
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.SetAlarm, LogicalAction.SetReminder),
            appKeywords = setOf("timer", "countdown", "clock"),
            sharedToolGroups = setOf(SharedToolGroup.Temporal, SharedToolGroup.Calculation, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "alarm.set_timer")
                    put("params", buildJsonObject {
                        put("seconds", 300)
                        put("message", "Tea")
                    })
                }
            )
        ),
        ActionSpec(
            id = "clipboard.copy_text",
            label = "Copy text to clipboard",
            description = "Silently copy text to the system clipboard without showing any chooser or share sheet.",
            params = listOf(
                ParamSpec("text", ParamType.String, description = "Text content to copy to clipboard")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            logicalActions = setOf(LogicalAction.Share, LogicalAction.TakeNote),
            appKeywords = setOf("clipboard", "copy", "text"),
            sharedToolGroups = setOf(SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "clipboard.copy_text")
                    put("params", buildJsonObject { put("text", "https://example.com") })
                }
            )
        ),
        ActionSpec(
            id = "calendar.create_event",
            label = "Create calendar event",
            description = "Silently create a calendar event via CalendarProvider. Requires WRITE_CALENDAR permission.",
            params = listOf(
                ParamSpec("title", ParamType.String, description = "Event title"),
                ParamSpec(
                    "begin_time_millis",
                    ParamType.DateTimeMillis,
                    description = "Start time in epoch milliseconds",
                    factType = FactType.DATETIME_UNIX_MS
                ),
                ParamSpec(
                    "end_time_millis",
                    ParamType.DateTimeMillis,
                    required = false,
                    description = "End time in epoch milliseconds",
                    factType = FactType.DATETIME_UNIX_MS
                ),
                ParamSpec("location", ParamType.String, required = false, description = "Event location"),
                ParamSpec("description", ParamType.String, required = false, description = "Event notes")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            logicalActions = setOf(LogicalAction.CreateEvent, LogicalAction.SetReminder),
            appKeywords = setOf("calendar", "event", "meeting", "schedule"),
            fallbackActionIds = listOf("share.share_text"),
            sharedToolGroups = setOf(SharedToolGroup.Temporal, SharedToolGroup.Places, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "calendar.create_event")
                    put("params", buildJsonObject {
                        put("title", "Gym session")
                        put("begin_time_millis", 1770000000000L)
                        put("end_time_millis", 1770003600000L)
                        put("location", "Local gym")
                    })
                }
            )
        ),
        ActionSpec(
            id = "internal.reminder.create",
            label = "Create local reminder",
            description = "Schedule a local GemmaWorkflow reminder notification.",
            params = listOf(
                ParamSpec("title", ParamType.String, description = "Reminder title"),
                ParamSpec(
                    "time_millis",
                    ParamType.DateTimeMillis,
                    description = "Reminder time in epoch milliseconds",
                    factType = FactType.DATETIME_UNIX_MS
                ),
                ParamSpec("message", ParamType.String, required = false, description = "Optional reminder body")
            ),
            execution = ExecutionSpec.InternalTool("create_local_reminder"),
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            logicalActions = setOf(LogicalAction.SetReminder),
            source = ActionSource.Internal,
            appKeywords = setOf("reminder", "notify", "notification"),
            fallbackActionIds = listOf("calendar.create_event"),
            sharedToolGroups = setOf(SharedToolGroup.Temporal, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "internal.reminder.create")
                    put("params", buildJsonObject {
                        put("title", "Call Maya")
                        put("time_millis", 1770000000000L)
                        put("message", "Call Maya")
                    })
                }
            )
        ),
        ActionSpec(
            id = "app.open",
            label = "Open app",
            description = "Launch an installed app by package name.",
            params = listOf(
                ParamSpec(
                    "package_name",
                    ParamType.String,
                    description = "Installed app package name",
                    factType = FactType.INSTALLED_APP
                )
            ),
            execution = ExecutionSpec.PackageLaunch("package_name"),
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            logicalActions = setOf(LogicalAction.OpenApp),
            source = ActionSource.Internal,
            appKeywords = setOf("open app", "launch app"),
            sharedToolGroups = setOf(SharedToolGroup.Apps, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "app.open")
                    put("params", buildJsonObject { put("package_name", "com.android.chrome") })
                }
            )
        ),

        // ── P1.1: Media Controls ────────────────────────────────────────────
        ActionSpec(
            id = "media.play_pause",
            label = "Play/Pause media",
            description = "Toggles play/pause on the active media session (music, video, podcast). No permissions required.",
            params = emptyList(),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = emptyList(),
            logicalActions = setOf(LogicalAction.PlayMedia)
        ),
        ActionSpec(
            id = "media.next_track",
            label = "Next track",
            description = "Skips to the next track in the current media session. No permissions required.",
            params = emptyList(),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = emptyList(),
            logicalActions = setOf(LogicalAction.PlayMedia)
        ),
        ActionSpec(
            id = "media.previous_track",
            label = "Previous track",
            description = "Skips to the previous track in the current media session. No permissions required.",
            params = emptyList(),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = emptyList(),
            logicalActions = setOf(LogicalAction.PlayMedia)
        ),

        // ── P1.2: Volume Controls ───────────────────────────────────────────
        ActionSpec(
            id = "volume.set",
            label = "Set volume",
            description = "Silently sets the volume level for a given audio stream (ring/media/alarm/notification). Requires MODIFY_AUDIO_SETTINGS which is granted automatically on all Android builds.",
            params = listOf(
                ParamSpec("level", ParamType.Int, description = "Volume level 0–100"),
                ParamSpec("stream", ParamType.String, required = false, description = "Audio stream: ring/media/alarm/notification", enumValues = listOf("ring", "media", "alarm", "notification")),
                ParamSpec("mute", ParamType.Boolean, required = false, description = "Mute the stream")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.MODIFY_AUDIO_SETTINGS"),
            logicalActions = setOf()
        ),

        // ── P1.3: Ringer Mode ───────────────────────────────────────────────
        ActionSpec(
            id = "ringer_mode.set",
            label = "Set ringer mode",
            description = "Silently sets the global ringer mode (normal/silent/vibrate) or Do Not Disturb (dnd_all/dnd_priority/dnd_none). MODIFY_AUDIO_SETTINGS is automatically granted.",
            params = listOf(
                ParamSpec("mode", ParamType.String, description = "Mode: normal, silent, vibrate, dnd_all, dnd_priority, dnd_none", enumValues = listOf("normal", "silent", "vibrate", "dnd_all", "dnd_priority", "dnd_none"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.MODIFY_AUDIO_SETTINGS"),
            logicalActions = setOf()
        ),

        // ── P2: UI / Notification / Display ─────────────────────────────────
        ActionSpec(
            id = "toast.show",
            label = "Show toast",
            description = "Displays a transient on-screen text message. No permissions required.",
            params = listOf(
                ParamSpec("message", ParamType.String, description = "Toast message text"),
                ParamSpec("duration", ParamType.String, required = false, description = "Duration: short or long", enumValues = listOf("short", "long"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = emptyList(),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "notification.send",
            label = "Send notification",
            description = "Sends a custom notification with title and body text. On Android 13+ requires POST_NOTIFICATIONS runtime permission.",
            params = listOf(
                ParamSpec("title", ParamType.String, description = "Notification title"),
                ParamSpec("body", ParamType.String, description = "Notification body text"),
                ParamSpec("channel", ParamType.String, required = false, description = "Channel ID", enumValues = listOf("notification_default")),
                ParamSpec("priority", ParamType.String, required = false, description = "Priority: low/normal/high", enumValues = listOf("low", "normal", "high"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "brightness.set",
            label = "Set screen brightness",
            description = "Sets the screen brightness level (0–100) or enables auto-brightness. Requires WRITE_SETTINGS permission which must be granted by the user via Settings.",
            params = listOf(
                ParamSpec("level", ParamType.Int, required = false, description = "Brightness 0–100"),
                ParamSpec("auto", ParamType.Boolean, required = false, description = "Enable auto-brightness")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.WRITE_SETTINGS"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "http_request",
            label = "Send HTTP request",
            description = "Sends an HTTP GET/POST/PUT/DELETE/PATCH request to a URL. INTERNET permission already declared. Requires confirmation due to arbitrary network calls.",
            params = listOf(
                ParamSpec("url", ParamType.Url, description = "Full URL"),
                ParamSpec("method", ParamType.String, required = false, description = "HTTP method", enumValues = listOf("GET", "POST", "PUT", "DELETE", "PATCH")),
                ParamSpec("headers", ParamType.String, required = false, description = "JSON object of headers"),
                ParamSpec("body", ParamType.String, required = false, description = "Request body"),
                ParamSpec("content_type", ParamType.String, required = false, description = "Content-Type header")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            requiredPermissions = emptyList(),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "launch_app",
            label = "Launch app",
            description = "Launches any installed app by package name. Tap the field to browse installed apps.",
            params = listOf(
                ParamSpec("package_name", ParamType.AppPicker, description = "Tap to select an installed app"),
                ParamSpec("class_name", ParamType.String, required = false, description = "Specific Activity within the app")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.QUERY_ALL_PACKAGES"),
            logicalActions = setOf(LogicalAction.OpenApp),
            installedAppListProviderId = "installed_apps",
            timeoutSeconds = 10
        ),

        // ── P3: Bluetooth / WiFi / Display / Intent ───────────────────────
        ActionSpec(
            id = "bluetooth.toggle",
            label = "Toggle Bluetooth",
            description = "Toggles Bluetooth on or off. On Android 12+ opens a system dialog. Falls back to opening Bluetooth Settings.",
            params = listOf(
                ParamSpec("state", ParamType.String, description = "on or off", enumValues = listOf("on", "off", "toggle"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.BLUETOOTH_CONNECT"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "wifi.toggle",
            label = "Toggle Wi-Fi",
            description = "Toggles Wi-Fi on or off. Silently works on Android 9 and below. Opens system Settings on Android 10+.",
            params = listOf(
                ParamSpec("state", ParamType.String, description = "on or off", enumValues = listOf("on", "off", "toggle"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.ACCESS_WIFI_STATE", "android.permission.CHANGE_WIFI_STATE"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "rotation.lock",
            label = "Lock screen rotation",
            description = "Locks screen rotation to portrait, landscape, or restores auto-rotate. Requires WRITE_SETTINGS permission which must be granted via Settings first.",
            params = listOf(
                ParamSpec("mode", ParamType.String, description = "Rotation mode", enumValues = listOf("portrait", "landscape", "auto"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.WRITE_SETTINGS"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "intent.send",
            label = "Send intent",
            description = "Sends a raw Android Intent (broadcast, activity start, or service start). SECURITY-SENSITIVE — requires confirmation.",
            params = listOf(
                ParamSpec("action", ParamType.String, description = "Intent action string"),
                ParamSpec("data", ParamType.String, required = false, description = "Data URI"),
                ParamSpec("type", ParamType.String, required = false, description = "MIME type"),
                ParamSpec("extras", ParamType.String, required = false, description = "JSON object of extras"),
                ParamSpec("target", ParamType.String, required = false, description = "broadcast/activity/service", enumValues = listOf("broadcast", "activity", "service"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual"),
            requiresConfirmation = true,
            requiredPermissions = emptyList(),
            logicalActions = setOf()
        ),

        // ── P4: System / Root ───────────────────────────────────────────────
        ActionSpec(
            id = "hotspot.toggle",
            label = "Toggle mobile hotspot",
            description = "Toggles the mobile hotspot (Wi-Fi AP) on or off. Only works silently on devices with system or root access. Otherwise opens Settings.",
            params = listOf(
                ParamSpec("state", ParamType.String, description = "on or off", enumValues = listOf("on", "off"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.WRITE_SETTINGS"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "cellular.toggle",
            label = "Toggle mobile data",
            description = "Toggles mobile data on or off. Only works silently on devices with system or root access. Otherwise opens Settings.",
            params = listOf(
                ParamSpec("state", ParamType.String, description = "on or off", enumValues = listOf("on", "off"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.WRITE_SECURE_SETTINGS"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "command.exec",
            label = "Execute shell command",
            description = "Executes an arbitrary shell command in user space. SECURITY-SENSITIVE — arbitrary command execution is dangerous. Requires confirmation every time.",
            params = listOf(
                ParamSpec("command", ParamType.String, description = "Shell command to execute"),
                ParamSpec("timeout_ms", ParamType.Int, required = false, description = "Timeout in milliseconds (default 5000, max 30000)")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual"),
            requiresConfirmation = true,
            requiredPermissions = emptyList(),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "sync.toggle",
            label = "Toggle account sync",
            description = "Enables or disables automatic sync for a Google account and authority (e.g. calendar sync).",
            params = listOf(
                ParamSpec("account_type", ParamType.String, description = "Account type, e.g. com.google"),
                ParamSpec("authority", ParamType.String, description = "Sync authority, e.g. com.google.android.gms.calendar"),
                ParamSpec("enable", ParamType.Boolean, description = "Enable or disable sync")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.WRITE_SYNC_SETTINGS"),
            logicalActions = setOf()
        ),
        ActionSpec(
            id = "airplane_mode.toggle",
            label = "Toggle airplane mode",
            description = "Toggles Airplane Mode on or off. Only works silently on devices with system or root access. Otherwise opens Settings.",
            params = listOf(
                ParamSpec("state", ParamType.String, description = "on or off", enumValues = listOf("on", "off"))
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual"),
            requiresConfirmation = false,
            requiredPermissions = listOf("android.permission.WRITE_SECURE_SETTINGS"),
            logicalActions = setOf()
        ),

        ActionSpec(
            id = "media.play_from_search",
            label = "Play media from search",
            description = "Ask a media app to play music, playlist, album, artist, or podcast from a search query.",
            params = listOf(
                ParamSpec("query", ParamType.String, description = "Media search query"),
                ParamSpec(
                    "media_focus",
                    ParamType.Enum,
                    required = false,
                    description = "Optional media focus",
                    enumValues = listOf("playlist", "artist", "album", "audio", "podcast")
                )
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH,
                extras = listOf(
                    ExtraSpec("query", SearchManager.QUERY, ParamType.String),
                    ExtraSpec("media_focus", MediaStore.EXTRA_MEDIA_FOCUS, ParamType.Enum)
                )
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.PlayMedia, LogicalAction.Search),
            appKeywords = setOf("music", "media", "play", "playlist"),
            sharedToolGroups = setOf(SharedToolGroup.Media, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "media.play_from_search")
                    put("params", buildJsonObject {
                        put("query", "focus playlist")
                        put("media_focus", "playlist")
                    })
                }
            )
        ),
        ActionSpec(
            id = "spotify.search_and_play",
            label = "Play Spotify search",
            description = "Ask Spotify to search and play the best media result.",
            params = listOf(
                ParamSpec("query", ParamType.String, description = "Spotify search query")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH,
                extras = listOf(ExtraSpec("query", SearchManager.QUERY, ParamType.String)),
                packagePolicy = PackagePolicy.Exact("com.spotify.music")
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.PlayMedia),
            source = ActionSource.AppSpecific,
            appLabels = setOf("Spotify"),
            appKeywords = setOf("spotify", "music", "playlist", "podcast"),
            fallbackActionIds = listOf("media.play_from_search"),
            sharedToolGroups = setOf(SharedToolGroup.Media, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "spotify.search_and_play")
                    put("params", buildJsonObject { put("query", "focus playlist") })
                }
            )
        ),
        ActionSpec(
            id = "file.open",
            label = "Open file",
            description = "Open a resolved file or content URI with an installed viewer.",
            params = listOf(
                ParamSpec(
                    "uri",
                    ParamType.Uri,
                    description = "Content or file URI to open",
                    factType = FactType.FILE_URI
                )
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_VIEW,
                dataTemplate = "{uri}",
                flags = listOf(IntentFlag.NewTask, IntentFlag.GrantReadUriPermission)
            ),
            triggerCompatible = setOf("manual", "nfc"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.OpenFile),
            appKeywords = setOf("file", "document", "pdf", "open"),
            sharedToolGroups = setOf(SharedToolGroup.Files, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "file.open")
                    put("params", buildJsonObject { put("uri", "content://media/external/file/1") })
                }
            )
        ),
        ActionSpec(
            id = "note.create",
            label = "Create note",
            description = "Open a note app to create a note when a compatible handler exists.",
            params = listOf(
                ParamSpec("title", ParamType.String, required = false, description = "Optional note title"),
                ParamSpec("text", ParamType.String, description = "Note body")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = "com.google.android.gms.actions.CREATE_NOTE",
                extras = listOf(
                    ExtraSpec("title", "com.google.android.gms.actions.extra.NAME", ParamType.String),
                    ExtraSpec("text", "com.google.android.gms.actions.extra.TEXT", ParamType.String)
                )
            ),
            triggerCompatible = setOf("manual", "time", "nfc", "share_sheet"),
            requiresConfirmation = true,
            logicalActions = setOf(LogicalAction.TakeNote),
            appKeywords = setOf("note", "notes", "keep"),
            fallbackActionIds = listOf("share.share_text"),
            sharedToolGroups = setOf(SharedToolGroup.Notes, SharedToolGroup.Validation),
            examples = listOf(
                buildJsonObject {
                    put("id", "note.create")
                    put("params", buildJsonObject {
                        put("title", "Quick note")
                        put("text", "Remember to buy milk")
                    })
                }
            )
        )
    )

    val allIds: Set<String> = all.map { it.id }.toSet()

    fun find(id: String): ActionSpec? = all.find { it.id == id }

    fun toolBindingsForAction(action: ActionSpec): List<ActionToolBinding> {
        val paramResolverBindings = action.params.mapNotNull { param ->
            val tool = param.resolverTool ?: return@mapNotNull null
            val factLabel = param.factType?.label ?: "parameter fact"
            ActionToolBinding(
                toolName = tool,
                purpose = "Resolve ${action.id}.${param.name} from $factLabel",
                paramNames = setOf(param.name)
            )
        }

        val sharedBindings = action.sharedToolGroups.flatMap { it.toolBindings }

        return (paramResolverBindings + action.toolBindings + sharedBindings)
            .distinctBy { binding ->
                listOf(
                    binding.toolName,
                    binding.paramNames.sorted().joinToString(",")
                ).joinToString(":")
            }
    }

    fun toolNamesForAction(action: ActionSpec): Set<String> =
        toolBindingsForAction(action).map { it.toolName }.toSet()

    fun resolverToolNamesForParam(action: ActionSpec, param: ParamSpec): Set<String> =
        toolBindingsForAction(action)
            .filter { param.name in it.paramNames }
            .map { it.toolName }
            .toSet()

    fun findByLogicalAction(
        action: String,
        availableActionIds: Set<String> = allIds
    ): List<ActionSpec> {
        val logicalAction = LogicalAction.fromId(action) ?: return emptyList()
        if (logicalAction == LogicalAction.Other || logicalAction == LogicalAction.CheckNotification) {
            return emptyList()
        }
        return all.filter { spec ->
            spec.id in availableActionIds && logicalAction in spec.logicalActions
        }
    }

    fun toPromptSummary(actions: List<ActionSpec> = all): String = buildString {
        appendLine("Available actions (ONLY pick from this list):")
        for (action in actions) {
            appendLine("- ${action.id}: ${action.description}")
            appendLine("  Params: ${action.params.joinToString { it.toPromptString() }}")
            appendLine("  Triggers: ${action.triggerCompatible.joinToString()}")
            if (action.requiresConfirmation) {
                appendLine("  Requires confirmation: true")
            }
            action.examples.firstOrNull()?.let { appendLine("  Example: $it") }
        }
    }

    private fun ParamSpec.toPromptString(): String {
        val requiredLabel = if (required) "required" else "optional"
        val enumLabel = if (enumValues.isNotEmpty()) " one of ${enumValues.joinToString(prefix = "[", postfix = "]")}" else ""
        return "$name (${type.promptName}, $requiredLabel$enumLabel)"
    }
}
