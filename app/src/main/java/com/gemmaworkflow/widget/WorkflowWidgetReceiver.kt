package com.gemmaworkflow.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class WorkflowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WorkflowWidgetGlance()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPreferences.clear(context, it) }
    }
}
