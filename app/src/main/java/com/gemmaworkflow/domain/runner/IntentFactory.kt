package com.gemmaworkflow.domain.runner

import android.content.Intent
import android.net.Uri
import com.gemmaworkflow.domain.catalog.ActionSpec
import com.gemmaworkflow.domain.catalog.ExecutionSpec
import com.gemmaworkflow.domain.catalog.ExtraSpec
import com.gemmaworkflow.domain.catalog.IntentFlag
import com.gemmaworkflow.domain.catalog.PackagePolicy
import com.gemmaworkflow.domain.catalog.ParamType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class IntentFactory {

    /**
     * Returns the resolved URL string for a CustomTab execution spec, or null if
     * the spec is not a CustomTab.
     */
    fun buildCustomTabParams(spec: ActionSpec, params: JsonObject): CustomTabParams? {
        val execution = spec.execution as? ExecutionSpec.CustomTab ?: return null
        val url = resolveTemplate(spec, execution.urlTemplate, params)
        return CustomTabParams(url = url, toolbarColor = execution.toolbarColor)
    }

    data class CustomTabParams(val url: String, val toolbarColor: Int?)

    fun buildSampleIntent(spec: ActionSpec): Intent? {
        val exampleParams = spec.examples.firstOrNull()?.get("params") as? JsonObject ?: JsonObject(emptyMap())
        return buildBaseIntent(spec, exampleParams, includeChooser = false)
    }

    fun buildExecutableIntent(spec: ActionSpec, params: JsonObject): Intent {
        return buildBaseIntent(spec, params, includeChooser = true)
            ?: error("Unsupported execution spec for ${spec.id}")
    }

    private fun buildBaseIntent(
        spec: ActionSpec,
        params: JsonObject,
        includeChooser: Boolean
    ): Intent? {
        return when (val execution = spec.execution) {
            is ExecutionSpec.AndroidIntent -> buildAndroidIntent(execution, spec, params, includeChooser)
            is ExecutionSpec.PackageLaunch,
            is ExecutionSpec.InternalTool,
            is ExecutionSpec.CustomTab -> null // handled separately in WorkflowRunner
            ExecutionSpec.BuiltIn -> null // handled separately in WorkflowRunner
        }
    }

    private fun buildAndroidIntent(
        execution: ExecutionSpec.AndroidIntent,
        spec: ActionSpec,
        params: JsonObject,
        includeChooser: Boolean
    ): Intent {
        val base = Intent(execution.action)

        val data = execution.dataTemplate?.let { resolveTemplate(spec, it, params) }?.let(Uri::parse)
        when {
            data != null && execution.mimeType != null -> base.setDataAndType(data, execution.mimeType)
            data != null -> base.data = data
            execution.mimeType != null -> base.type = execution.mimeType
        }

        for (extra in execution.extras) {
            putExtra(base, extra, params[extra.paramName])
        }

        when (val packagePolicy = execution.packagePolicy) {
            PackagePolicy.None -> Unit
            is PackagePolicy.Exact -> base.setPackage(packagePolicy.packageName)
        }

        applyFlags(base, execution.flags)

        return if (includeChooser && execution.chooserTitle != null) {
            Intent.createChooser(base, execution.chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            base
        }
    }

    private fun resolveTemplate(spec: ActionSpec, template: String, params: JsonObject): String {
        return TEMPLATE_PARAM_REGEX.replace(template) { match ->
            val name = match.groupValues[1]
            val paramSpec = spec.params.firstOrNull { it.name == name }
            val rawValue = params[name]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (paramSpec?.type == ParamType.Url || paramSpec?.type == ParamType.Uri) {
                rawValue
            } else {
                Uri.encode(rawValue)
            }
        }
    }

    private fun putExtra(intent: Intent, extra: ExtraSpec, value: JsonElement?) {
        if (value == null || value is JsonNull) return

        when (extra.type) {
            ParamType.String,
            ParamType.Url,
            ParamType.Enum,
            ParamType.AppPicker -> intent.putExtra(extra.extraKey, value.jsonPrimitive.content)
            ParamType.Uri -> intent.putExtra(extra.extraKey, Uri.parse(value.jsonPrimitive.content))
            ParamType.Int -> value.jsonPrimitive.intOrNull?.let { intent.putExtra(extra.extraKey, it) }
            ParamType.Long,
            ParamType.DateTimeMillis -> value.jsonPrimitive.longOrNull?.let { intent.putExtra(extra.extraKey, it) }
            ParamType.Boolean -> value.jsonPrimitive.booleanOrNull?.let { intent.putExtra(extra.extraKey, it) }
            ParamType.StringArray -> {
                val values = (value as? JsonArray)?.map { it.jsonPrimitive.content } ?: return
                intent.putStringArrayListExtra(extra.extraKey, ArrayList(values))
            }
        }
    }

    private fun applyFlags(intent: Intent, flags: List<IntentFlag>) {
        for (flag in flags) {
            when (flag) {
                IntentFlag.NewTask -> intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                IntentFlag.GrantReadUriPermission -> intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private companion object {
        val TEMPLATE_PARAM_REGEX = Regex("\\{([A-Za-z0-9_]+)\\}")
    }
}
