package com.gemmaworkflow.platform.tools

import android.content.Context
import com.gemmaworkflow.platform.tools.impl.CalculatorTool
import com.gemmaworkflow.platform.tools.impl.ComputeDurationTool
import com.gemmaworkflow.platform.tools.impl.CreateCalendarEventTool
import com.gemmaworkflow.platform.tools.impl.GetCurrentTimeTool
import com.gemmaworkflow.platform.tools.impl.GetDayOfWeekTool
import com.gemmaworkflow.platform.tools.impl.GetDeviceLocationTool
import com.gemmaworkflow.platform.tools.impl.ListInstalledAppsTool
import com.gemmaworkflow.platform.tools.impl.OpenUriTool
import com.gemmaworkflow.platform.tools.impl.ResolveDatetimeTool
import com.gemmaworkflow.platform.tools.impl.ResolveIntentTool
import com.gemmaworkflow.platform.tools.impl.SearchPlacesTool
import com.gemmaworkflow.platform.tools.impl.SendIntentTool
import com.gemmaworkflow.platform.tools.impl.SetAlarmTool
import com.gemmaworkflow.platform.tools.impl.ShareTextTool
import com.gemmaworkflow.platform.tools.impl.ValidateJsonTool
import com.gemmaworkflow.platform.tools.impl.WebSearchTool
import com.gemmaworkflow.platform.tools.impl.LookupContactTool
import com.gemmaworkflow.platform.tools.impl.SearchMediaTool
import com.gemmaworkflow.platform.tools.impl.SearchFilesTool
import com.gemmaworkflow.platform.tools.impl.SearchNotesTool
import com.gemmaworkflow.platform.tools.impl.SearchSmsTool
import com.gemmaworkflow.platform.tools.impl.GetCalendarEventsTool

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

        // Tier 6 — Domain Entity Search (entity type classification via tool selection)
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

        android.util.Log.i("ToolInitializer", "Registered ${ToolRegistry.all().size} tools")
    }
}
