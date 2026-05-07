package com.gemmaworkflow.domain.runner

import android.net.Uri
import com.gemmaworkflow.domain.catalog.ActionSpec
import com.gemmaworkflow.domain.model.WorkflowStep
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object FallbackParamMapper {

    fun mapParams(
        sourceSpec: ActionSpec,
        sourceStep: WorkflowStep,
        fallbackSpec: ActionSpec
    ): JsonObject? {
        val direct = mutableMapOf<String, JsonElement>()
        for (param in fallbackSpec.params) {
            sourceStep.params[param.name]?.let { direct[param.name] = it }
        }

        val derived = when (fallbackSpec.id) {
            "browser.open_url" -> deriveBrowserOpenUrl(sourceSpec, sourceStep)
            "share.share_text" -> deriveShareText(sourceSpec, sourceStep)
            else -> JsonObject(direct)
        }

        val merged = JsonObject(direct + derived)
        return if (fallbackSpec.params.filter { it.required }.all { it.name in merged }) {
            merged
        } else {
            null
        }
    }

    private fun deriveBrowserOpenUrl(
        sourceSpec: ActionSpec,
        sourceStep: WorkflowStep
    ): JsonObject {
        if (sourceSpec.id == "maps.open_place") {
            val query = sourceStep.params["query"]?.asString().orEmpty()
            if (query.isNotBlank()) {
                return buildJsonObject {
                    put("url", "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
                }
            }
        }

        val url = sourceStep.params["url"]?.asString()
        return if (!url.isNullOrBlank()) buildJsonObject { put("url", url) } else JsonObject(emptyMap())
    }

    private fun deriveShareText(
        sourceSpec: ActionSpec,
        sourceStep: WorkflowStep
    ): JsonObject {
        val explicit = listOf("text", "message", "description", "title", "query", "url", "uri")
            .firstNotNullOfOrNull { sourceStep.params[it]?.asString()?.takeIf(String::isNotBlank) }

        if (!explicit.isNullOrBlank()) {
            return buildJsonObject { put("text", explicit) }
        }

        val summary = sourceStep.params.entries.joinToString(separator = "\n") { (key, value) ->
            "$key: ${value.asString() ?: value}"
        }

        val label = sourceSpec.label.takeIf { it.isNotBlank() } ?: sourceSpec.id
        return buildJsonObject {
            put("text", "$label\n$summary")
        }
    }

    private fun JsonElement.asString(): String? {
        return runCatching { jsonPrimitive.contentOrNull }.getOrNull()
    }
}
