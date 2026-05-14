package com.irisapp.domain.planner

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RequestAnalysis(
    val goal: String = "",
    @SerialName("trigger_hint")
    val triggerHint: String = "manual",
    @SerialName("schedule_hints")
    val scheduleHints: ScheduleHints? = null,
    val applications: List<ApplicationChoice> = emptyList(),
    @SerialName("candidate_app_categories")
    val candidateAppCategories: List<String> = emptyList(),
    @SerialName("missing_info")
    val missingInfo: List<String> = emptyList()
) {
    val normalizedTriggerHint: String
        get() = triggerHint.takeIf { it in allowedTriggerHints } ?: "manual"

    val applicationSearchTerms: List<String>
        get() = applications.flatMap { app ->
            listOf(app.requestedName, app.selectedAppLabel, app.packageName)
        }.filter { it.isNotBlank() }.distinct()

    companion object {
        private val allowedTriggerHints = setOf(
            "manual",
            "time",
            "nfc",
            "share_sheet",
            "battery",
            "charger",
            "wifi",
            "bluetooth",
            "airplane_mode",
            "dnd",
            "geofence"
        )
    }
}

@Serializable
data class ApplicationChoice(
    @SerialName("requested_name")
    val requestedName: String = "",
    @SerialName("selected_app_label")
    val selectedAppLabel: String = "",
    @SerialName("package_name")
    val packageName: String = "",
    val confidence: String = "low"
)

@Serializable
data class ScheduleHints(
    val hour: Int? = null,
    val minute: Int? = null,
    @SerialName("repeat_days")
    val repeatDays: List<Int> = emptyList()
)

object RequestAnalysisParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawOutput: String): RequestAnalysis {
        return json.decodeFromString<RequestAnalysis>(extractJsonBlock(rawOutput))
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        if (start == -1) throw IllegalArgumentException("No JSON object found in request analysis output")

        var depth = 0
        for (index in start until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }

        throw IllegalArgumentException("Unclosed JSON object in request analysis output")
    }
}
