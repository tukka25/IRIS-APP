package com.gemmaworkflow.widget

import android.content.Context

object WidgetPreferences {
    private const val PREFS_NAME = "workflow_widget_prefs"

    fun setWorkflowName(context: Context, appWidgetId: Int, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("wf_$appWidgetId", name).apply()
    }

    fun getWorkflowName(context: Context, appWidgetId: Int): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("wf_$appWidgetId", null)

    fun clear(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("wf_$appWidgetId").apply()
    }
}
