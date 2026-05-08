package com.gemmaworkflow.platform.capability

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Discovers available app intents by combining a curated JSON catalog
 * with runtime PackageManager intent filter discovery.
 *
 * Android does NOT expose intent extras programmatically. This engine
 * merges what the system CAN tell us (which apps respond to which intents)
 * with what we KNOW (curated extras from docs/reverse-engineering).
 */
object IntentDiscoveryEngine {

    private const val TAG = "IntentDiscovery"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var catalog: IntentCatalog? = null

    /** Load the bundled intent_catalog.json from assets. */
    fun loadCatalog(context: Context): IntentCatalog {
        if (catalog != null) return catalog!!

        return try {
            val raw = context.assets.open("intent_catalog.json")
                .bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<IntentCatalog>(raw)
            catalog = parsed
            Log.i(TAG, "Loaded intent catalog: ${parsed.apps.size} apps, ${parsed.standardIntents.size} standard intents")
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load intent catalog", e)
            IntentCatalog(version = 0, description = "", apps = emptyList(), standardIntents = emptyList())
        }
    }

    /**
     * Builds a SLM-ready summary of all available intents on this device.
     *
     * For each app in the catalog that IS installed, include its full intent
     * catalog with exact extras. For standard intents, include them always
     * (they're system-level).
     */
    fun buildSlmPromptSummary(context: Context, maxActions: Int = 80): String {
        val catalog = loadCatalog(context)
        val pm = context.packageManager

        return buildString {
            appendLine("=== Available Android Actions ===")
            appendLine("You may select these by their exact action ID. Parameters are exactly as listed.")
            appendLine()

            // Standard intents (always available)
            appendLine("--- Universal Actions (always available) ---")
            for (si in catalog.standardIntents) {
                appendLine()
                appendLine("Action ID: ${si.id}")
                appendLine("  Description: ${si.description}")
                appendLine("  Android Action: ${si.action}")
                if (si.mimeType != null) appendLine("  MIME Type: ${si.mimeType}")
                if (si.params.isNotEmpty()) {
                    appendLine("  Parameters:")
                    si.params.forEach { p ->
                        val req = if (p.required) "required" else "optional"
                        appendLine("    - ${p.name} (${p.type}, $req): ${p.description}")
                    }
                }
                if (si.example != null) appendLine("  Example: ${si.example}")
            }

            // App-specific intents (only if installed/resolvable)
            for (app in catalog.apps) {
                val installed = isPackageInstalled(pm, app.packageName)
                if (!installed) continue

                appendLine()
                appendLine("--- ${app.label} (installed: ${app.packageName}) ---")
                appendLine("Triggers to use this app: ${app.triggers.joinToString()}")

                for (intent in app.intents) {
                    appendLine()
                    appendLine("  Action ID: ${intent.id}")
                    appendLine("    Android Action: ${intent.action}")
                    var resolvable = isIntentResolvable(pm, intent, app.packageName)
                    if (!resolvable && intent.uriTemplate != null) {
                        resolvable = true  // URI intents are usually resolvable
                    }
                    appendLine("    Resolvable on this device: $resolvable")
                    appendLine("    ${intent.description}")
                    if (intent.uriTemplate != null) appendLine("    URI template: ${intent.uriTemplate}")
                    if (intent.dataUri != null) appendLine("    Data URI: ${intent.dataUri}")
                    if (intent.params.isNotEmpty()) {
                        appendLine("    Parameters:")
                        intent.params.forEach { p ->
                            val req = if (p.required) "required" else "optional"
                            val keyInfo = if (p.key != null) " (intent extra key: ${p.key})" else ""
                            appendLine("      - ${p.name} (${p.type}, $req): ${p.description}$keyInfo")
                        }
                    }
                    if (intent.extraParams.isNotEmpty()) {
                        appendLine("    Extra parameters:")
                        intent.extraParams.forEach { ep ->
                            appendLine("      - ${ep.name} (${ep.type}, key: ${ep.key}): ${ep.description}")
                        }
                    }
                    if (intent.example != null) appendLine("    Example: ${intent.example}")
                    if (intent.requiresPermission != null) {
                        appendLine("    WARNING: Requires permission: ${intent.requiresPermission}")
                    }
                }
            }

            // Also include raw intent filter discovery for apps NOT in the catalog
            appendLine()
            appendLine("--- Other installed apps (raw intent filters) ---")
            appendLine("These apps are installed but their intent extras are unknown.")
            appendLine("Use standard actions (VIEW, SEND, DIAL, etc.) with them.")
        }
    }

    /**
     * Find the catalog entry for a specific action ID.
     * Searches both app intents and standard intents.
     */
    fun findIntentById(context: Context, actionId: String): DiscoveredIntent? {
        val catalog = loadCatalog(context)

        // Search standard intents
        catalog.standardIntents.find { it.id == actionId }?.let {
            return it.toDiscoveredIntent()
        }

        // Search app intents
        for (app in catalog.apps) {
            app.intents.find { it.id == actionId }?.let {
                return it.toDiscoveredIntent(app.packageName)
            }
        }

        return null
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }.isSuccess
    }

    private fun isIntentResolvable(
        pm: PackageManager,
        catalogIntent: CatalogIntent,
        appPackage: String
    ): Boolean {
        val intent = Intent(catalogIntent.action)
        catalogIntent.dataUri?.let { intent.data = android.net.Uri.parse(it) }
        catalogIntent.mimeType?.let { intent.type = it }
        if (catalogIntent.action != "android.intent.action.VIEW") {
            intent.setPackage(appPackage)
        }
        return queryActivities(pm, intent).isNotEmpty()
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<*> {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    /** Result type for a discovered intent. */
    data class DiscoveredIntent(
        val id: String,
        val action: String,
        val description: String,
        val packageName: String?,
        val params: List<CatalogParam>,
        val extraParams: List<CatalogExtraParam> = emptyList(),
        val uriTemplate: String? = null,
        val dataUri: String? = null,
        val mimeType: String? = null,
        val example: JsonObject? = null
    )

    private fun CatalogIntent.toDiscoveredIntent(pkg: String? = null) = IntentDiscoveryEngine.DiscoveredIntent(
        id = id, action = action, description = description,
        packageName = pkg, params = params, extraParams = extraParams,
        uriTemplate = uriTemplate, dataUri = dataUri, mimeType = mimeType, example = example
    )

    private fun StandardIntent.toDiscoveredIntent() = IntentDiscoveryEngine.DiscoveredIntent(
        id = id, action = action, description = description,
        packageName = null, params = params, extraParams = emptyList(),
        mimeType = mimeType, example = example
    )
}

// -- JSON model classes matching intent_catalog.json --

@kotlinx.serialization.Serializable
data class IntentCatalog(
    val version: Int,
    val description: String,
    val apps: List<CatalogApp>,
    @kotlinx.serialization.SerialName("standard_intents")
    val standardIntents: List<StandardIntent>
)

@kotlinx.serialization.Serializable
data class CatalogApp(
    @kotlinx.serialization.SerialName("package")
    val packageName: String,
    val label: String,
    val triggers: List<String>,
    val intents: List<CatalogIntent>
)

@kotlinx.serialization.Serializable
data class CatalogIntent(
    val id: String,
    val action: String,
    val description: String,
    val params: List<CatalogParam> = emptyList(),
    @kotlinx.serialization.SerialName("extra_params")
    val extraParams: List<CatalogExtraParam> = emptyList(),
    @kotlinx.serialization.SerialName("uri_template")
    val uriTemplate: String? = null,
    @kotlinx.serialization.SerialName("data_uri")
    val dataUri: String? = null,
    @kotlinx.serialization.SerialName("mime_type")
    val mimeType: String? = null,
    @kotlinx.serialization.SerialName("uri_scheme_fallback")
    val uriSchemeFallback: String? = null,
    @kotlinx.serialization.SerialName("requires_permission")
    val requiresPermission: String? = null,
    val example: JsonObject? = null
)

@kotlinx.serialization.Serializable
data class CatalogParam(
    val name: String,
    val key: String? = null,
    val type: String,
    val required: Boolean = true,
    val description: String = "",
    val values: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class CatalogExtraParam(
    val name: String,
    val key: String,
    val type: String,
    val description: String = ""
)

@kotlinx.serialization.Serializable
data class StandardIntent(
    val id: String,
    val action: String,
    @kotlinx.serialization.SerialName("mime_type")
    val mimeType: String? = null,
    val description: String,
    val params: List<CatalogParam> = emptyList(),
    val example: JsonObject? = null
)
