package com.irisapp.platform.tools

import android.content.Context
import com.irisapp.platform.tools.impl.CalculatorTool
import com.irisapp.platform.tools.impl.ComputeDurationTool
import com.irisapp.platform.tools.impl.CreateCalendarEventTool
import com.irisapp.platform.tools.impl.CreateLocalReminderTool
import com.irisapp.platform.tools.impl.GetCalendarEventsTool
import com.irisapp.platform.tools.impl.GetClipboardTextTool
import com.irisapp.platform.tools.impl.GetCurrentTimeTool
import com.irisapp.platform.tools.impl.GetDayOfWeekTool
import com.irisapp.platform.tools.impl.GetDeviceLocationTool
import com.irisapp.platform.tools.impl.ListInstalledAppsTool
import com.irisapp.platform.tools.impl.ListRecentNotificationsTool
import com.irisapp.platform.tools.impl.LookupContactTool
import com.irisapp.platform.tools.impl.OpenDeepLinkTool
import com.irisapp.platform.tools.impl.OpenSettingsPanelTool
import com.irisapp.platform.tools.impl.OpenUriTool
import com.irisapp.platform.tools.impl.ResolveDatetimeTool
import com.irisapp.platform.tools.impl.ResolveIntentTool
import com.irisapp.platform.tools.impl.SearchFilesTool
import com.irisapp.platform.tools.impl.SearchMediaTool
import com.irisapp.platform.tools.impl.SearchNotesTool
import com.irisapp.platform.tools.impl.SearchPlacesTool
import com.irisapp.platform.tools.impl.SearchSmsTool
import com.irisapp.platform.tools.impl.SendIntentTool
import com.irisapp.platform.tools.impl.SetAlarmTool
import com.irisapp.platform.tools.impl.SetClipboardTextTool
import com.irisapp.platform.tools.impl.SetCountdownTimerTool
import com.irisapp.platform.tools.impl.ShareTextTool
import com.irisapp.platform.tools.impl.ValidateJsonTool
import com.irisapp.platform.tools.impl.WebSearchTool

/**
 * Registers all tools once at app startup.
 * Call from Application.onCreate() or InferenceManager init.
 */
object ToolInitializer {

    private var registered = false

    fun initialize(context: Context) {
        if (registered) return
        registered = true

        // Tier 1 — Temporal (zero-dependency)
        ToolRegistry.register(GetCurrentTimeTool)
        ToolRegistry.register(ResolveDatetimeTool)
        ToolRegistry.register(ComputeDurationTool)
        ToolRegistry.register(GetDayOfWeekTool)

        // Tier 2 — Device state
        ToolRegistry.register(ListInstalledAppsTool(context))
        ToolRegistry.register(ResolveIntentTool(context))
        ToolRegistry.register(GetDeviceLocationTool(context))

        // Tier 3 — Search & Knowledge
        ToolRegistry.register(WebSearchTool)
        ToolRegistry.register(SearchPlacesTool)
        ToolRegistry.register(LookupContactTool(context, name = "get_contact"))
        ToolRegistry.register(LookupContactTool(context))

        // Tier 6 — Domain Entity Search
        ToolRegistry.register(SearchMediaTool(context))
        ToolRegistry.register(SearchFilesTool(context))
        ToolRegistry.register(SearchNotesTool(context))
        ToolRegistry.register(SearchSmsTool(context))
        ToolRegistry.register(GetCalendarEventsTool(context))

        // Tier 4 — Execution
        ToolRegistry.register(SendIntentTool(context))
        ToolRegistry.register(OpenUriTool(context))
        ToolRegistry.register(ShareTextTool(context))
        ToolRegistry.register(SetAlarmTool(context))
        ToolRegistry.register(CreateCalendarEventTool(context))

        // Tier 5 — Reasoning
        ToolRegistry.register(CalculatorTool)
        ToolRegistry.register(ValidateJsonTool)

        // Tier 7 — Reminders, Settings, Notifications, Clipboard, Browser
        ToolRegistry.register(SetCountdownTimerTool(context))
        ToolRegistry.register(CreateLocalReminderTool(context))
        ToolRegistry.register(OpenSettingsPanelTool(context))
        ToolRegistry.register(ListRecentNotificationsTool(context))
        ToolRegistry.register(GetClipboardTextTool(context))
        ToolRegistry.register(SetClipboardTextTool(context))
        ToolRegistry.register(OpenDeepLinkTool(context))

        android.util.Log.i("ToolInitializer", "Registered ${ToolRegistry.all().size} tools")
    }
}
