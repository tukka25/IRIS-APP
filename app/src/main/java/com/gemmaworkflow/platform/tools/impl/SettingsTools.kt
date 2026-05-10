package com.gemmaworkflow.platform.tools.impl

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolParam
import com.gemmaworkflow.platform.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings / connectivity panel tools.
 *
 * Opens user-mediated settings panels — does NOT silently toggle.
 * Android 10+ blocks normal apps from enabling/disabling Wi-Fi directly.
 * These tools show the settings screen and let the user confirm.
 */
class OpenSettingsPanelTool(private val context: Context) : Tool {
    override val name = "open_settings_panel"
    override val description = "Opens a settings panel for the user to adjust. Use for 'turn on Wi-Fi' or 'open Bluetooth settings'. Does NOT toggle automatically — user must confirm."
    override val parameters = listOf(
        ToolParam("panel", "string", description = "One of: wifi, bluetooth, notifications, display, battery, location, app_settings, hotspot, main")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val panel = input["panel"]?.lowercase() ?: "main"

        val action = when (panel) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "app_settings" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "hotspot" -> "android.settings.TETHER_SETTINGS"
            else -> Settings.ACTION_SETTINGS
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val intent = Intent(action).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (panel == "app_settings") {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                }
                context.startActivity(intent)
                ToolResult(true, "Opened $panel settings panel")
            }.getOrElse { e ->
                ToolResult(false, "", "Settings open failed: ${e.message}")
            }
        }
    }
}
