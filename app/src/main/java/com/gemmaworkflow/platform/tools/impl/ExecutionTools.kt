package com.gemmaworkflow.platform.tools.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.gemmaworkflow.domain.runner.IntentFactory
import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolParam
import com.gemmaworkflow.platform.tools.ToolResult

/**
 * Tier 4 — Execution tools. Wraps IntentFactory and Android intents
 * as callable tools the SLM can use during planning.
 */

class SendIntentTool(private val context: Context) : Tool {
    override val name = "send_intent"
    override val description = "Sends an Android intent and reports which app handled it"
    override val parameters = listOf(
        ToolParam("action", "string", description = "Intent action string"),
        ToolParam("data_uri", "string", required = false, description = "Data URI"),
        ToolParam("mime_type", "string", required = false, description = "MIME type"),
        ToolParam("package_name", "string", required = false, description = "Target package")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val action = input["action"] ?: return ToolResult(false, "", "Missing 'action'")
        val intent = Intent(action)
        input["data_uri"]?.let { intent.data = Uri.parse(it) }
        input["mime_type"]?.let { intent.type = it }
        input["package_name"]?.let { intent.setPackage(it) }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION") context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            if (resolved != null) {
                context.startActivity(intent)
                ToolResult(true, "Started: ${resolved.loadLabel(context.packageManager)}")
            } else {
                ToolResult(false, "", "No app found to handle $action")
            }
        }.getOrElse { e -> ToolResult(false, "", e.message ?: "Intent failed") }
    }
}

class OpenUriTool(private val context: Context) : Tool {
    override val name = "open_uri"
    override val description = "Opens a URI (http, geo, tel, spotify, etc.) via the system"
    override val parameters = listOf(
        ToolParam("uri", "string", description = "URI to open")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val uri = input["uri"] ?: return ToolResult(false, "", "Missing 'uri'")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            ToolResult(true, "Opened: $uri")
        }.getOrElse { e -> ToolResult(false, "", e.message ?: "Failed to open URI") }
    }
}

class ShareTextTool(private val context: Context) : Tool {
    override val name = "share_text"
    override val description = "Opens Android share sheet with text"
    override val parameters = listOf(
        ToolParam("text", "string", description = "Text to share"),
        ToolParam("title", "string", required = false, description = "Chooser dialog title")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val text = input["text"] ?: return ToolResult(false, "", "Missing 'text'")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, input["title"] ?: "Share via")
        return runCatching {
            context.startActivity(chooser)
            ToolResult(true, "Share sheet opened")
        }.getOrElse { e -> ToolResult(false, "", e.message ?: "Failed") }
    }
}

class SetAlarmTool(private val context: Context) : Tool {
    override val name = "set_alarm"
    override val description = "Opens clock app to set an alarm"
    override val parameters = listOf(
        ToolParam("hour", "int", description = "Hour (0-23)"),
        ToolParam("minutes", "int", description = "Minutes (0-59)"),
        ToolParam("message", "string", required = false, description = "Alarm label")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val hour = input["hour"]?.toIntOrNull() ?: return ToolResult(false, "", "Missing or invalid 'hour'")
        val minutes = input["minutes"]?.toIntOrNull() ?: return ToolResult(false, "", "Missing or invalid 'minutes'")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            input["message"]?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            ToolResult(true, "Alarm set for $hour:${minutes.toString().padStart(2, '0')}")
        }.getOrElse { e -> ToolResult(false, "", e.message ?: "Failed") }
    }
}

class CreateCalendarEventTool(private val context: Context) : Tool {
    override val name = "create_calendar_event"
    override val description = "Opens calendar to create event with pre-filled details"
    override val parameters = listOf(
        ToolParam("title", "string", description = "Event title"),
        ToolParam("begin_time_millis", "int", description = "Start time in epoch milliseconds"),
        ToolParam("end_time_millis", "int", required = false, description = "End time in epoch ms"),
        ToolParam("location", "string", required = false, description = "Event location")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val title = input["title"] ?: return ToolResult(false, "", "Missing 'title'")
        val beginTime = input["begin_time_millis"]?.toLongOrNull()
            ?: return ToolResult(false, "", "Missing 'begin_time_millis'")

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
            input["end_time_millis"]?.toLongOrNull()?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            input["location"]?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            ToolResult(true, "Calendar opened: $title")
        }.getOrElse { e -> ToolResult(false, "", e.message ?: "Failed") }
    }
}
