package com.gemmaworkflow.domain.catalog

/**
 * Static allowlist of supported actions. The model ONLY sees these.
 * Every action the AI can select must be registered here.
 *
 * @param id           Unique action id used in the JSON contract.
 * @param label        Human-readable name for UI.
 * @param description  What this action does — included in the model prompt.
 * @param paramSchema  Required and optional parameters with descriptions.
 * @param example      Concrete usage example for the model prompt.
 * @param requiredPackage  Android package name if this action targets a specific app.
 * @param mimeType     MIME type if applicable (e.g. for share intents).
 * @param triggerCompatible  Which trigger types work with this action.
 * @param needsConfirmation  Whether user must confirm before execution.
 */
data class CatalogAction(
    val id: String,
    val label: String,
    val description: String,
    val paramSchema: Map<String, ParamInfo> = emptyMap(),
    val example: String = "",
    val requiredPackage: String? = null,
    val mimeType: String? = null,
    val triggerCompatible: Set<String> = emptySet(),
    val needsConfirmation: Boolean = false
)

data class ParamInfo(
    val type: String,        // "string", "url", "int"
    val required: Boolean = true,
    val description: String = ""
)

/** Registry of all supported actions. */
object ActionCatalog {

    val all: List<CatalogAction> = listOf(
        CatalogAction(
            id = "browser.open_url",
            label = "Open URL in browser",
            description = "Opens a web URL in the default browser.",
            paramSchema = mapOf(
                "url" to ParamInfo("url", required = true, description = "Full URL to open")
            ),
            example = """{"id":"browser.open_url","params":{"url":"https://example.com"}}""",
            triggerCompatible = setOf("manual", "time", "nfc")
        ),
        CatalogAction(
            id = "maps.open_place",
            label = "Open place in Maps",
            description = "Opens Google Maps to search for a place or address.",
            paramSchema = mapOf(
                "query" to ParamInfo("string", required = true, description = "Place name or address")
            ),
            example = """{"id":"maps.open_place","params":{"query":"coffee shop near me"}}""",
            triggerCompatible = setOf("manual", "nfc")
        ),
        CatalogAction(
            id = "share.share_text",
            label = "Share text via Android Share Sheet",
            description = "Opens the Android share sheet with the given text so the user can choose where to send it.",
            paramSchema = mapOf(
                "text" to ParamInfo("string", required = true, description = "Text content to share")
            ),
            example = """{"id":"share.share_text","params":{"text":"Meeting notes: ..."}}""",
            triggerCompatible = setOf("manual", "time", "share_sheet"),
            needsConfirmation = true
        ),
        CatalogAction(
            id = "share.share_image",
            label = "Share image via Android Share Sheet",
            description = "Opens the Android share sheet with an image URI.",
            paramSchema = mapOf(
                "uri" to ParamInfo("string", required = true, description = "Content URI of the image")
            ),
            example = """{"id":"share.share_image","params":{"uri":"content://..."}}""",
            triggerCompatible = setOf("manual", "share_sheet"),
            needsConfirmation = true
        ),
        CatalogAction(
            id = "spotify.play_search",
            label = "Search and play on Spotify",
            description = "Searches Spotify for a track, album, artist, or playlist.",
            paramSchema = mapOf(
                "query" to ParamInfo("string", required = true, description = "Search query for Spotify")
            ),
            example = """{"id":"spotify.play_search","params":{"query":"focus playlist"}}""",
            requiredPackage = "com.spotify.music",
            triggerCompatible = setOf("manual", "time", "nfc")
        ),
        CatalogAction(
            id = "notes.save_text",
            label = "Save text as note",
            description = "Opens the Android share sheet to save text into any installed notes app.",
            paramSchema = mapOf(
                "title" to ParamInfo("string", required = true, description = "Note title"),
                "content" to ParamInfo("string", required = true, description = "Note body content")
            ),
            example = """{"id":"notes.save_text","params":{"title":"Shopping list","content":"- Milk\\n- Eggs"}}""",
            triggerCompatible = setOf("manual", "time", "share_sheet"),
            needsConfirmation = true
        )
    )

    /** Look up a single action by id. */
    fun find(id: String): CatalogAction? = all.find { it.id == id }

    /** All registered action IDs. */
    val allIds: Set<String> = all.map { it.id }.toSet()

    /**
     * Build a compact, prompt-safe capability summary for the model.
     * Includes only the info the AI needs to select actions.
     */
    fun toPromptSummary(): String = buildString {
        appendLine("Available actions:")
        for (action in all) {
            appendLine("- ${action.id}: ${action.description}")
            appendLine("  Params: ${action.paramSchema.entries.joinToString { (k, v) -> "$k (${v.type}${if (v.required) ", required" else ""})" }}")
            if (action.example.isNotBlank()) {
                appendLine("  Example: ${action.example}")
            }
        }
    }
}
