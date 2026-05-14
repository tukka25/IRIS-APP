package com.irisapp.platform.capability

import android.content.Intent
import android.content.Context
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager
import com.irisapp.domain.catalog.ActionSpec
import com.irisapp.domain.catalog.ActionSpecRegistry
import com.irisapp.domain.catalog.AvailabilitySpec
import com.irisapp.domain.runner.IntentFactory

/**
 * Scans the device to determine which catalog actions are actually resolvable.
 *
 * Android can expose installed/launchable apps and intent handlers, but it does
 * not expose arbitrary app-specific extras or payload schemas. Those remain in
 * ActionSpecRegistry.
 */
class PackageCapabilityScanner(private val context: Context) {
    private val intentFactory = IntentFactory()

    /**
     * Returns the set of action IDs that are resolvable on this device.
     */
    fun resolvableActions(actionIds: Set<String>): Set<String> {
        return ActionSpecRegistry.all
            .filter { it.id in actionIds }
            .filter(::isResolvable)
            .map { it.id }
            .toSet()
    }

    fun isResolvable(actionId: String): Boolean {
        val spec = ActionSpecRegistry.find(actionId) ?: return false
        return isResolvable(spec)
    }

    fun resolvableTargets(actionId: String): List<ResolvedTarget> {
        val spec = ActionSpecRegistry.find(actionId) ?: return emptyList()
        val sampleIntent = intentFactory.buildSampleIntent(spec) ?: return emptyList()
        val activities = queryActivities(sampleIntent)
        return activities.map {
            ResolvedTarget(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(context.packageManager).toString()
            )
        }
    }

    fun installedLaunchableApps(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return queryActivities(launcherIntent)
            .map {
                val packageName = it.activityInfo.packageName
                InstalledApp(
                    packageName = packageName,
                    label = it.loadLabel(context.packageManager).toString(),
                    launchable = context.packageManager.getLaunchIntentForPackage(packageName) != null
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun installedAppsPromptSummary(maxApps: Int = 120): String {
        val apps = installedLaunchableApps()
        return buildString {
            appendLine("Installed launchable apps from Android PackageManager:")
            apps.take(maxApps).forEach { app ->
                appendLine("- ${app.label} | ${app.packageName}")
            }
            if (apps.size > maxApps) {
                appendLine("- ${apps.size - maxApps} more apps omitted; if the requested app is not listed, do not choose an app.")
            }
        }
    }

    fun matchedInstalledApps(applications: List<String>): List<InstalledAppMatch> {
        if (applications.isEmpty()) return emptyList()
        val apps = installedLaunchableApps()
        return applications.map { requested ->
            val normalized = requested.trim().lowercase()
            val matches = apps.filter {
                it.label.lowercase().contains(normalized) ||
                    it.packageName.lowercase().contains(normalized)
            }
            InstalledAppMatch(requestedName = requested, matches = matches)
        }
    }

    fun sampleIntentHandlers(actionId: String): List<IntentHandler> {
        val spec = ActionSpecRegistry.find(actionId) ?: return emptyList()
        val sampleIntent = intentFactory.buildSampleIntent(spec) ?: return emptyList()
        return queryActivities(sampleIntent).map { it.toIntentHandler(sampleIntent) }
    }

    fun resolveFinalIntent(intent: Intent): IntentHandler? {
        val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.toIntentHandler(intent)
    }

    fun nativeDiscoverySummary(
        requestedApplications: List<String>,
        availableActionIds: Set<String>
    ): String = buildString {
        appendLine("Native Android discovery:")

        val appMatches = matchedInstalledApps(requestedApplications)
        if (appMatches.isEmpty()) {
            appendLine("- Requested apps: none explicitly mentioned")
        } else {
            appendLine("- Requested app install checks:")
            for (match in appMatches) {
                if (match.matches.isEmpty()) {
                    appendLine("  - ${match.requestedName}: not found as a launchable installed app")
                } else {
                    val labels = match.matches.take(3).joinToString { "${it.label} (${it.packageName})" }
                    appendLine("  - ${match.requestedName}: $labels")
                }
            }
        }

        appendLine("- Resolvable intent handlers:")
        for (actionId in availableActionIds.sorted()) {
            val handlers = sampleIntentHandlers(actionId)
            val handlerLabels = handlers.take(4).joinToString { "${it.label} (${it.packageName})" }
            appendLine("  - $actionId: ${handlerLabels.ifBlank { "none" }}")
        }
    }

    private fun isResolvable(spec: ActionSpec): Boolean = when (val availability = spec.availability) {
        AvailabilitySpec.Always -> true
        is AvailabilitySpec.RequiredPackage -> isPackageInstalled(availability.packageName)
        AvailabilitySpec.IntentResolvable -> {
            val sampleIntent = intentFactory.buildSampleIntent(spec) ?: return false
            queryActivities(sampleIntent).isNotEmpty()
        }
    }

    private fun queryActivities(intent: android.content.Intent): List<android.content.pm.ResolveInfo> {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        }.isSuccess
    }

    private fun ResolveInfo.toIntentHandler(intent: Intent): IntentHandler {
        return IntentHandler(
            packageName = activityInfo.packageName,
            activityName = activityInfo.name,
            label = loadLabel(context.packageManager).toString(),
            requestedAction = intent.action,
            requestedDataScheme = intent.data?.scheme,
            requestedMimeType = intent.type,
            filterActions = filter?.actionsIterator().toStringList(),
            filterDataSchemes = filter?.schemesIterator().toStringList(),
            filterMimeTypes = filter?.typesIterator().toStringList()
        )
    }

    private fun Iterator<String>?.toStringList(): List<String> {
        return this?.asSequence()?.toList().orEmpty()
    }
}

data class ResolvedTarget(
    val packageName: String,
    val label: String
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val launchable: Boolean
)

data class InstalledAppMatch(
    val requestedName: String,
    val matches: List<InstalledApp>
)

data class IntentHandler(
    val packageName: String,
    val activityName: String,
    val label: String,
    val requestedAction: String?,
    val requestedDataScheme: String?,
    val requestedMimeType: String?,
    val filterActions: List<String>,
    val filterDataSchemes: List<String>,
    val filterMimeTypes: List<String>
)
