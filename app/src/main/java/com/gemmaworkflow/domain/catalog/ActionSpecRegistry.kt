package com.gemmaworkflow.domain.catalog

import android.content.Intent
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
    val fallbackActionIds: List<String> = emptyList(),
    val examples: List<JsonObject> = emptyList()
)

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val required: Boolean = true,
    val description: String = "",
    val enumValues: List<String> = emptyList()
)

enum class ParamType(val promptName: String) {
    String("string"),
    Url("url"),
    Uri("uri"),
    Int("int"),
    Long("long"),
    Boolean("boolean"),
    StringArray("string_array"),
    DateTimeMillis("datetime_millis"),
    Enum("enum")
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
            examples = listOf(
                buildJsonObject {
                    put("id", "browser.open_url")
                    put("params", buildJsonObject { put("url", "https://example.com") })
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
            fallbackActionIds = listOf("browser.open_url"),
            examples = listOf(
                buildJsonObject {
                    put("id", "maps.open_place")
                    put("params", buildJsonObject { put("query", "coffee shop near me") })
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
            fallbackActionIds = listOf("share.share_text"),
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
                ParamSpec("phone", ParamType.String, required = false, description = "Optional phone number"),
                ParamSpec("message", ParamType.String, description = "Message body")
            ),
            execution = ExecutionSpec.AndroidIntent(
                action = Intent.ACTION_SENDTO,
                dataTemplate = "smsto:{phone}",
                extras = listOf(ExtraSpec("message", "sms_body", ParamType.String))
            ),
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = true,
            fallbackActionIds = listOf("share.share_text"),
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
            id = "clipboard.copy_text",
            label = "Copy text to clipboard",
            description = "Silently copy text to the system clipboard without showing any chooser or share sheet.",
            params = listOf(
                ParamSpec("text", ParamType.String, description = "Text content to copy to clipboard")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
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
                ParamSpec("begin_time_millis", ParamType.DateTimeMillis, description = "Start time in epoch milliseconds"),
                ParamSpec("end_time_millis", ParamType.DateTimeMillis, required = false, description = "End time in epoch milliseconds"),
                ParamSpec("location", ParamType.String, required = false, description = "Event location"),
                ParamSpec("description", ParamType.String, required = false, description = "Event notes")
            ),
            execution = ExecutionSpec.BuiltIn,
            availability = AvailabilitySpec.Always,
            triggerCompatible = setOf("manual", "time", "nfc"),
            requiresConfirmation = false,
            fallbackActionIds = listOf("share.share_text"),
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
        )
    )

    val allIds: Set<String> = all.map { it.id }.toSet()

    fun find(id: String): ActionSpec? = all.find { it.id == id }

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
