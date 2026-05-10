package com.gemmaworkflow.platform.tools.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolParam
import com.gemmaworkflow.platform.tools.ToolResult

/**
 * Tier 2 — Device state tools. Uses Android PackageManager, LocationManager.
 */

/** Lists all launchable apps on the device. */
class ListInstalledAppsTool(private val context: Context) : Tool {
    override val name = "list_installed_apps"
    override val description = "Lists installed launchable apps with package names"
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val apps = activities.map { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            "${label}|${pkg}"
        }.distinct().sorted().take(50)

        return ToolResult(true, apps.joinToString("\n").ifBlank { "No launchable apps found" })
    }
}

/** Checks which apps can handle a given intent. */
class ResolveIntentTool(private val context: Context) : Tool {
    override val name = "resolve_intent"
    override val description = "Finds apps that can handle a given action + data URI"
    override val parameters = listOf(
        ToolParam("action", "string", description = "Intent action like android.intent.action.VIEW"),
        ToolParam("data_uri", "string", required = false, description = "Optional URI like https://example.com"),
        ToolParam("mime_type", "string", required = false, description = "Optional MIME type")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val action = input["action"] ?: return ToolResult(false, "", "Missing 'action'")
        val intent = Intent(action)
        input["data_uri"]?.let { intent.data = android.net.Uri.parse(it) }
        input["mime_type"]?.let { intent.type = it }

        val pm = context.packageManager
        val activities = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        val result = activities.map { "${it.loadLabel(pm)}|${it.activityInfo.packageName}" }
        return ToolResult(true, result.joinToString("\n").ifBlank { "No apps found" })
    }
}

/** Returns coarse device location (no GPS permission needed). */
class GetDeviceLocationTool(private val context: Context) : Tool {
    override val name = "get_device_location"
    override val description = "Returns coarse device location (city-level, no GPS needed)"
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) return ToolResult(false, "", "Location service unavailable")

        // Try coarse (network) provider — no GPS permission needed
        @Suppress("MissingPermission")
        val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

        return if (loc != null) {
            ToolResult(true, "lat: ${loc.latitude}, lng: ${loc.longitude}, accuracy: ${loc.accuracy}m")
        } else {
            ToolResult(true, "lat: unknown, lng: unknown (no recent location fix)")
        }
    }
}
