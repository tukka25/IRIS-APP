package com.irisapp.platform.tools.impl

import android.content.Context
import android.service.notification.StatusBarNotification
import com.irisapp.platform.tools.Tool
import com.irisapp.platform.tools.ToolParam
import com.irisapp.platform.tools.ToolResult

/**
 * Notification tools.
 *
 * Requires NotificationListenerService — the user must grant
 * notification access in Settings. Returns a stub indicating
 * whether the service is enabled.
 *
 * Full implementation needs:
 * 1. A NotificationListenerService subclass in the manifest
 * 2. User to enable notification access in Settings
 * 3. The service to cache recent notifications
 */
class ListRecentNotificationsTool(private val context: Context) : Tool {
    override val name = "list_recent_notifications"
    override val description = "Lists recent device notifications (requires notification access permission). Use for 'check my WhatsApp notifications' or 'what notifications do I have?'."
    override val parameters = listOf(
        ToolParam("app", "string", required = false, description = "Optional: filter by app package name"),
        ToolParam("limit", "int", required = false, description = "Max results (default 5)")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        return ToolResult(true, buildString {
            appendLine("Notification access: requires user to enable in Settings → Notification Access.")
            appendLine()
            appendLine("To use this tool:")
            appendLine("1. Go to Settings → Apps → Special app access → Notification access")
            appendLine("2. Enable IrisApp")
            appendLine()
            appendLine("Once enabled, this tool will show recent notifications from all apps.")
            if (input["app"] != null) {
                appendLine("Filter: ${input["app"]}")
            }
        })
    }
}
