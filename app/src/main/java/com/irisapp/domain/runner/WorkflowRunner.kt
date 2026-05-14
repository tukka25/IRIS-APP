package com.irisapp.domain.runner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.irisapp.domain.catalog.ActionSpec
import com.irisapp.domain.catalog.ActionSpecRegistry
import com.irisapp.domain.catalog.ExecutionSpec
import com.irisapp.domain.model.ExecutionResult
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.WorkflowStep
import com.irisapp.platform.alarm.AlarmApiExecutor
import com.irisapp.platform.calendar.CalendarApiExecutor
import com.irisapp.platform.capability.ChromeCustomTabOpener
import com.irisapp.platform.clipboard.ClipboardApiExecutor
import com.irisapp.platform.tools.ToolRegistry
import com.irisapp.platform.media.MediaControlApiExecutor
import com.irisapp.platform.volume.VolumeApiExecutor
import com.irisapp.platform.volume.RingerModeApiExecutor
import com.irisapp.platform.ui.ToastApiExecutor
import com.irisapp.platform.notification.NotificationApiExecutor
import com.irisapp.platform.display.BrightnessApiExecutor
import com.irisapp.platform.http.HttpRequestApiExecutor
import com.irisapp.platform.app.LaunchAppApiExecutor
import com.irisapp.platform.bluetooth.BluetoothApiExecutor
import com.irisapp.platform.wifi.WifiApiExecutor
import com.irisapp.platform.display.RotationApiExecutor
import com.irisapp.platform.intent.GenericIntentApiExecutor
import com.irisapp.platform.hotspot.HotspotApiExecutor
import com.irisapp.platform.cellular.CellularApiExecutor
import com.irisapp.platform.command.CommandApiExecutor
import com.irisapp.platform.sync.SyncApiExecutor
import com.irisapp.platform.airplane.AirplaneModeApiExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thrown by [WorkflowRunner.executeStep] when a step has [WorkflowStep.requiresConfirmation] set.
 * The runner pauses at that step; the UI calls [WorkflowRunner.confirmPendingStep][confirmPendingStep]
 * or [WorkflowRunner.dismissPendingStep][dismissPendingStep] to proceed.
 */
class ConfirmationRequired(
    val step: WorkflowStep,
    val stepIndex: Int
) : Exception("Confirmation required for step: ${step.id}")

/**
 * Thrown by [WorkflowRunner.executeStep] when a step requires runtime permissions
 * that have not been granted. The UI calls [WorkflowRunner.grantPermissionsAndResume]
 * after prompting the user, or [WorkflowRunner.dismissPendingStep] to skip.
 */
class PermissionRequired(
    val step: WorkflowStep,
    val stepIndex: Int,
    val permissions: List<String>
) : Exception("Permission required for step: ${step.id}: $permissions")

/**
 * Holds the step that is waiting for user input plus the context that caused the wait.
 */
private data class PendingStepInfo(
    val step: WorkflowStep,
    val kind: PendingKind
)

private sealed class PendingKind {
    data object Confirmation : PendingKind()
    data class Permission(val missingPermissions: List<String>) : PendingKind()
}

/**
 * Executes a validated PlannedWorkflow step by step.
 */
class WorkflowRunner(
    private val context: Context,
    private val intentFactory: IntentFactory = IntentFactory(),
    private val calendarApiExecutor: CalendarApiExecutor = CalendarApiExecutor(context),
    private val alarmApiExecutor: AlarmApiExecutor = AlarmApiExecutor(context),
    private val clipboardApiExecutor: ClipboardApiExecutor = ClipboardApiExecutor(context),
    private val chromeCustomTabOpener: ChromeCustomTabOpener = ChromeCustomTabOpener(context),
    // ── BuiltIn Executors ──────────────────────────────────────────────────────
    private val mediaControlApiExecutor: MediaControlApiExecutor = MediaControlApiExecutor(context),
    private val volumeApiExecutor: VolumeApiExecutor = VolumeApiExecutor(context),
    private val ringerModeApiExecutor: RingerModeApiExecutor = RingerModeApiExecutor(context),
    private val toastApiExecutor: ToastApiExecutor = ToastApiExecutor(context),
    private val notificationApiExecutor: NotificationApiExecutor = NotificationApiExecutor(context),
    private val brightnessApiExecutor: BrightnessApiExecutor = BrightnessApiExecutor(context),
    private val httpRequestApiExecutor: HttpRequestApiExecutor = HttpRequestApiExecutor(),
    private val launchAppApiExecutor: LaunchAppApiExecutor = LaunchAppApiExecutor(context),
    private val bluetoothApiExecutor: BluetoothApiExecutor = BluetoothApiExecutor(context),
    private val wifiApiExecutor: WifiApiExecutor = WifiApiExecutor(context),
    private val rotationApiExecutor: RotationApiExecutor = RotationApiExecutor(context),
    private val genericIntentApiExecutor: GenericIntentApiExecutor = GenericIntentApiExecutor(context),
    private val hotspotApiExecutor: HotspotApiExecutor = HotspotApiExecutor(context),
    private val cellularApiExecutor: CellularApiExecutor = CellularApiExecutor(context),
    private val commandApiExecutor: CommandApiExecutor = CommandApiExecutor(context),
    private val syncApiExecutor: SyncApiExecutor = SyncApiExecutor(context),
    private val airplaneModeApiExecutor: AirplaneModeApiExecutor = AirplaneModeApiExecutor(context)
) {
    private var pendingStepInfo: PendingStepInfo? = null
    private var pendingStepIndex: Int = -1
    /** Step indices the user has already confirmed — skip re-confirmation on resume. */
    private val confirmedSteps = mutableSetOf<Int>()
    /** Output from each executed step — used for chaining via $step[N].output. */
    private val stepOutputs = mutableMapOf<Int, String>()

    /**
     * Resolve $step[N].output references in step params before execution.
     * Example: param value "$step[0].output" is replaced with the actual output of step 0.
     */
    private fun resolveParams(params: JsonObject): JsonObject {
        val resolvedEntries = mutableListOf<Pair<String, JsonElement>>()
        for (entry in params.entries) {
            val key = entry.key
            val value = entry.value
            val resolvedValue = if (value is JsonPrimitive && value.contentOrNull != null) {
                JsonPrimitive(resolveOutputRefs(value.content))
            } else {
                value
            }
            resolvedEntries.add(key to resolvedValue)
        }
        return JsonObject(resolvedEntries.toMap())
    }

    /**
     * Replace $step[N].output with the actual output of step N.
     * Returns original string if no reference found or step not yet executed.
     */
    private fun resolveOutputRefs(text: String): String {
        val regex = Regex("""${'$'}step\[(\d+)\]\.output""")
        return regex.replace(text) { match ->
            val index = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            stepOutputs[index] ?: match.value
        }
    }

suspend fun run(
        workflow: PlannedWorkflow,
        startIndex: Int = 0,
        onDebug: (label: String, message: String) -> Unit = { _, _ -> }
    ): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        // If resuming past confirmed steps, mark them as already-executed successes
        for (i in 0 until startIndex) {
            results.add(ExecutionResult(stepId = workflow.actions[i].id, success = true, message = "Confirmed"))
        }

        var i = startIndex
        while (i < workflow.actions.size) {
            val step = workflow.actions[i]
            val spec = ActionSpecRegistry.find(step.id)

            // Check if this step can run in parallel with the next step(s)
            if (spec?.parallelExecutionEnabled == true && i + 1 < workflow.actions.size) {
                // Collect a batch of consecutive parallelizable steps (up to 3)
                val batch = mutableListOf<Pair<Int, WorkflowStep>>()
                var j = i
                while (j < workflow.actions.size && batch.size < 3) {
                    val s = workflow.actions[j]
                    val sp = ActionSpecRegistry.find(s.id)
                    if (sp?.parallelExecutionEnabled == true) {
                        batch.add(j to s)
                        j++
                    } else {
                        break
                    }
                }

                // Execute batch in parallel
                if (batch.size > 1) {
                    onDebug("Parallel batch", "steps ${batch.map { it.first }.joinToString(",")} running in parallel")
                    val batchResults: List<ExecutionResult> = coroutineScope {
                        batch.map { (idx, stp): Pair<Int, WorkflowStep> ->
                            async<ExecutionResult> { executeStep(stp, idx, onDebug) }
                        }.awaitAll<ExecutionResult>()
                    }
                    for (br in batchResults) {
                        results.add(br)
                        val pos = results.size - 1
                        stepOutputs[pos] = br.output
                        onDebug("Tool result", "${br.stepId}: success=${br.success}, output=${br.output.take(80)}")
                        if (!br.success) {
                            // Stop pipeline on first failure in parallel batch
                            return results
                        }
                    }
                    i = batch.last().first + 1
                    continue
                }
            }

            // Sequential execution
            val result = executeStep(step, i, onDebug)
            results.add(result)
            stepOutputs[i] = result.output
            onDebug("Tool result", step.id + ": success=" + result.success + ", output=" + result.output.take(80))
            if (!result.success) break
            i++
        }
        return results
    }

    fun confirmPendingStep(): WorkflowStep? {
        pendingStepIndex.let { confirmedSteps.add(it) }
        val step = pendingStepInfo?.step
        pendingStepInfo = null
        pendingStepIndex = -1
        return step
    }

    /**
     * Called after the user grants the required permissions. Re-checks whether all
     * permissions are now granted; if so, clears pending state and returns the step so
     * the caller can re-execute it. If permissions are still missing, throws again.
     */
    fun grantPermissionsAndResume(context: Context): WorkflowStep? {
        val info = pendingStepInfo ?: return null
        val missingPermissions = (info.kind as? PendingKind.Permission)?.missingPermissions ?: emptyList()

        val stillMissing = missingPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (stillMissing.isNotEmpty()) {
            // Update pending info with remaining missing permissions and re-throw
            pendingStepInfo = info.copy(kind = PendingKind.Permission(stillMissing))
            throw PermissionRequired(info.step, pendingStepIndex, stillMissing)
        }

        // All granted — confirm the step and clear pending state
        pendingStepIndex.let { confirmedSteps.add(it) }
        pendingStepInfo = null
        pendingStepIndex = -1
        return info.step
    }

    fun dismissPendingStep(): WorkflowStep? {
        pendingStepIndex.let { confirmedSteps.add(it) }
        val step = pendingStepInfo?.step
        pendingStepInfo = null
        pendingStepIndex = -1
        return step
    }

    private suspend fun executeStep(
        step: WorkflowStep,
        stepIndex: Int,
        onDebug: (label: String, String) -> Unit
    ): ExecutionResult {
        // Resolve $step[N].output references before execution
        val resolvedParams = resolveParams(step.params)
        val resolvedStep = step.copy(params = resolvedParams)

        val spec = ActionSpecRegistry.find(resolvedStep.id)
            ?: return ExecutionResult(stepId = step.id, success = false, message = "Unknown action")

        // Check and request runtime permissions before executing.
        // grantedPermissions filters spec.requiredPermissions to only those NOT yet granted.
        val grantedPermissions = spec.requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        val missingPermissions = spec.requiredPermissions - grantedPermissions.toSet()
        if (missingPermissions.isNotEmpty()) {
            pendingStepInfo = PendingStepInfo(step, PendingKind.Permission(missingPermissions))
            pendingStepIndex = stepIndex
            throw PermissionRequired(step, stepIndex, missingPermissions)
        }

// Pause and request user confirmation before executing sensitive steps
        if (spec.requiresConfirmation || step.requiresConfirmation) {
            if (stepIndex !in confirmedSteps) {
                pendingStepInfo = PendingStepInfo(step, PendingKind.Confirmation)
                pendingStepIndex = stepIndex
                throw ConfirmationRequired(step, stepIndex)
            }
            // stepIndex is in confirmedSteps — user already confirmed; execute without pausing
        }

        // Wrap execution in a per-step timeout if specified on the ActionSpec
        val timeoutMs = if (spec.timeoutSeconds > 0) spec.timeoutSeconds * 1000L else null
        val exec: suspend () -> ExecutionResult = {
            executeStepBody(resolvedStep, resolvedParams, spec, onDebug)
        }
        val result = if (timeoutMs != null) {
            withTimeoutOrNull(timeoutMs) { exec() }
                ?: ExecutionResult(stepId = step.id, success = false, message = "Step timed out after ${spec.timeoutSeconds}s")
        } else {
            exec()
        }
        return result
    }

    private suspend fun executeStepBody(
        step: WorkflowStep,
        resolvedParams: JsonObject,
        spec: ActionSpec,
        onDebug: (label: String, String) -> Unit
    ): ExecutionResult {
        val resolvedStep = step.copy(params = resolvedParams)

        // Silently execute calendar.create_event
        if (step.id == "calendar.create_event") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = calendarApiExecutor.execute(resolvedParams)
            onDebug("CalendarApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "CalendarApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently execute clipboard.copy_text via ClipboardManager instead of launching the share sheet.
        if (step.id == "clipboard.copy_text") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = clipboardApiExecutor.execute(resolvedParams)
            onDebug("ClipboardApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ClipboardApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently execute alarm.set_alarm via AlarmManager instead of launching the Clock app.
        // On Android 12+ if SCHEDULE_EXACT_ALARM is not granted we redirect to Settings.
        if (step.id == "alarm.set_alarm") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")

            if (alarmApiExecutor.shouldRequestExactAlarmPermission()) {
                // Android 12+: SCHEDULE_EXACT_ALARM gate is closed — redirect to Settings.
                val settingsIntent = alarmApiExecutor.buildExactAlarmSettingsIntent()
                if (settingsIntent != null && context is Activity) {
                    context.startActivity(settingsIntent)
                }
                return ExecutionResult(
                    stepId = step.id,
                    success = false,
                    message = "SCHEDULE_EXACT_ALARM permission required — redirected to Settings"
                )
            }

            val result = alarmApiExecutor.execute(resolvedParams)
            onDebug("AlarmApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "AlarmApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently copy text to clipboard instead of launching the share sheet.
        if (step.id == "share.share_text") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = clipboardApiExecutor.executeShareText(resolvedParams)
            onDebug("ClipboardApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ClipboardApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Silently copy image URI to clipboard instead of launching the share sheet.
        if (step.id == "share.share_image") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
val result = clipboardApiExecutor.executeShareImage(resolvedParams)
            onDebug("ClipboardApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ClipboardApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // ── P1.1: Media Controls ────────────────────────────────────────────
        if (step.id == "media.play_pause") {
            onDebug("Tool call", "${step.id}")
            Log.d(TAG, "Tool call ${step.id}")
            val result = mediaControlApiExecutor.executePlayPause(resolvedParams)
            onDebug("MediaControlApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "MediaControlApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "media.next_track") {
            onDebug("Tool call", "${step.id}")
            Log.d(TAG, "Tool call ${step.id}")
            val result = mediaControlApiExecutor.executeNext(resolvedParams)
            onDebug("MediaControlApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "MediaControlApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "media.previous_track") {
            onDebug("Tool call", "${step.id}")
            Log.d(TAG, "Tool call ${step.id}")
            val result = mediaControlApiExecutor.executePrevious(resolvedParams)
            onDebug("MediaControlApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "MediaControlApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // ── P1.2: Volume Controls ───────────────────────────────────────────
        if (step.id == "volume.set") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = volumeApiExecutor.execute(resolvedParams)
            onDebug("VolumeApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "VolumeApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // ── P1.3: Ringer Mode ─────────────────────────────────────────────
        if (step.id == "ringer_mode.set") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = ringerModeApiExecutor.execute(resolvedParams)
            onDebug("RingerModeApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "RingerModeApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // ── P2: UI / Notification / Display ─────────────────────────────────
        if (step.id == "toast.show") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = toastApiExecutor.execute(resolvedParams)
            onDebug("ToastApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ToastApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "notification.send") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = notificationApiExecutor.execute(resolvedParams)
            onDebug("NotificationApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "NotificationApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "brightness.set") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = brightnessApiExecutor.execute(resolvedParams)
            onDebug("BrightnessApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "BrightnessApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "http_request") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = httpRequestApiExecutor.execute(resolvedParams)
            onDebug("HttpRequestApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "HttpRequestApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "launch_app") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = launchAppApiExecutor.execute(resolvedParams)
            onDebug("LaunchAppApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "LaunchAppApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // ── P3: Bluetooth / WiFi / Display / Intent ────────────────────────
        if (step.id == "bluetooth.toggle") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = bluetoothApiExecutor.execute(resolvedParams)
            onDebug("BluetoothApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "BluetoothApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "wifi.toggle") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = wifiApiExecutor.execute(resolvedParams)
            onDebug("WifiApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "WifiApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "rotation.lock") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = rotationApiExecutor.execute(resolvedParams)
            onDebug("RotationApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "RotationApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "intent.send") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = genericIntentApiExecutor.execute(resolvedParams)
            onDebug("GenericIntentApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "GenericIntentApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // ── P4: System / Root ──────────────────────────────────────────────
        if (step.id == "hotspot.toggle") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = hotspotApiExecutor.execute(resolvedParams)
            onDebug("HotspotApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "HotspotApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "cellular.toggle") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = cellularApiExecutor.execute(resolvedParams)
            onDebug("CellularApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "CellularApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "command.exec") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = commandApiExecutor.execute(resolvedParams)
            onDebug("CommandApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "CommandApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "sync.toggle") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = syncApiExecutor.execute(resolvedParams)
            onDebug("SyncApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "SyncApiExecutor result=${result.success} message=${result.message}")
            return result
        }
        if (step.id == "airplane_mode.toggle") {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = airplaneModeApiExecutor.execute(resolvedParams)
            onDebug("AirplaneModeApiExecutor", "result=${result.success} message=${result.message}")
            Log.d(TAG, "AirplaneModeApiExecutor result=${result.success} message=${result.message}")
            return result
        }

        // Open URL in a Chrome Custom Tab — no app switch, falls back to regular browser.
        val customTabParams = intentFactory.buildCustomTabParams(spec, resolvedStep.params)
        if (customTabParams != null) {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val result = chromeCustomTabOpener.openUrl(customTabParams.url, customTabParams.toolbarColor)
            onDebug("ChromeCustomTabOpener", "result=${result.success} message=${result.message}")
            Log.d(TAG, "ChromeCustomTabOpener result=${result.success} message=${result.message}, url=${customTabParams.url}")
            return result
        }

        when (val execution = spec.execution) {
            is ExecutionSpec.PackageLaunch -> {
                return executePackageLaunch(step, spec, execution, onDebug)
            }
            is ExecutionSpec.InternalTool -> {
                return executeInternalTool(resolvedStep, spec, execution, onDebug)
            }
            is ExecutionSpec.AndroidIntent -> Unit
            is ExecutionSpec.CustomTab,
            ExecutionSpec.BuiltIn -> Unit
        }

        return runCatching {
            onDebug("Tool call", "${step.id} params=$resolvedParams")
            Log.d(TAG, "Tool call ${step.id} params=$resolvedParams")
            val intent = intentFactory.buildExecutableIntent(spec, resolvedStep.params)
            onDebug("Intent built", intent.describe())
            Log.d(TAG, "Intent built action=${intent.action} data=${intent.data} type=${intent.type} package=${intent.`package`}")
            val resolved = resolveActivity(intent)
                ?: return tryFallback(step, spec, onDebug, "No native handler resolved for final intent")
            onDebug(
                "Native resolveActivity",
                "${resolved.activityInfo.loadLabel(context.packageManager)} (${resolved.activityInfo.packageName}/${resolved.activityInfo.name})"
            )
            context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = step.id,
                    success = true,
                    message = "Started ${spec.label}",
                    output = resolvedStep.params["url"]?.jsonPrimitive?.contentOrNull
                        ?: resolvedStep.params["query"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Tool failed ${step.id}", error)
                if (error is ConfirmationRequired || error is PermissionRequired) throw error
                if (error is ActivityNotFoundException || error is IllegalArgumentException) {
                    tryFallback(step, spec, onDebug, error.message ?: "Failed to start ${spec.label}")
                } else {
                    ExecutionResult(
                        stepId = step.id,
                        success = false,
                        message = error.message ?: "Failed to start ${spec.label}"
                    )
                }
            }
        )
    }

    private fun executePackageLaunch(
        step: WorkflowStep,
        spec: ActionSpec,
        execution: ExecutionSpec.PackageLaunch,
        onDebug: (label: String, message: String) -> Unit
    ): ExecutionResult {
        val packageName = step.params[execution.packageParamName]?.asString()
            ?: return ExecutionResult(stepId = step.id, success = false, message = "Missing package name")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return tryFallback(step, spec, onDebug, "No launchable app for package $packageName")

        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            onDebug("Tool call", "${step.id} package=$packageName")
            onDebug("Native getLaunchIntentForPackage", packageName)
            context.startActivity(launchIntent)
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = step.id,
                    success = true,
                    message = "Started ${spec.label}"
                )
            },
            onFailure = {
                tryFallback(step, spec, onDebug, it.message ?: "Failed to launch $packageName")
            }
        )
    }

    private suspend fun executeInternalTool(
        step: WorkflowStep,
        spec: ActionSpec,
        execution: ExecutionSpec.InternalTool,
        onDebug: (label: String, message: String) -> Unit
    ): ExecutionResult {
        val input = step.params.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.contentOrNull ?: value.toString()
                else -> value.toString()
            }
        }
        onDebug("Tool call", "${execution.toolName} params=$input")
        val result = ToolRegistry.execute(execution.toolName, input)

        return if (result.success) {
            ExecutionResult(
                stepId = step.id,
                success = true,
                message = result.output.ifBlank { "Started ${spec.label}" },
                output = result.output
            )
        } else {
            tryFallback(step, spec, onDebug, result.error ?: "Internal tool failed")
        }
    }

    private fun tryFallback(
        sourceStep: WorkflowStep,
        sourceSpec: ActionSpec,
        onDebug: (label: String, message: String) -> Unit,
        reason: String
    ): ExecutionResult {
        onDebug("Fallback lookup", "${sourceSpec.id}: $reason")

        for (fallbackId in sourceSpec.fallbackActionIds) {
            val fallbackSpec = ActionSpecRegistry.find(fallbackId) ?: continue
            val fallbackParams = FallbackParamMapper.mapParams(
                sourceSpec = sourceSpec,
                sourceStep = sourceStep,
                fallbackSpec = fallbackSpec
            )

            if (fallbackParams == null) {
                onDebug("Fallback skipped", "$fallbackId: could not map required params")
                continue
            }

            val fallbackIntentResult = runCatching {
                intentFactory.buildExecutableIntent(fallbackSpec, fallbackParams)
            }
            if (fallbackIntentResult.isFailure) {
                onDebug("Fallback skipped", "$fallbackId: ${fallbackIntentResult.exceptionOrNull()?.message}")
                continue
            }
            val fallbackIntent = fallbackIntentResult.getOrThrow()

            val resolved = resolveActivity(fallbackIntent)
            if (resolved == null) {
                onDebug("Fallback skipped", "$fallbackId: no native handler for ${fallbackIntent.describe()}")
                continue
            }

            return runCatching {
                onDebug("Fallback tool call", "$fallbackId params=$fallbackParams")
                onDebug("Fallback intent built", fallbackIntent.describe())
                onDebug(
                    "Fallback resolveActivity",
                    "${resolved.activityInfo.loadLabel(context.packageManager)} (${resolved.activityInfo.packageName}/${resolved.activityInfo.name})"
                )
                context.startActivity(fallbackIntent)
            }.fold(
                onSuccess = {
                    ExecutionResult(
                        stepId = sourceStep.id,
                        success = true,
                        message = "Started fallback ${fallbackSpec.label}"
                    )
                },
                onFailure = {
                    onDebug("Fallback failed", "$fallbackId: ${it.message}")
                    ExecutionResult(
                        stepId = sourceStep.id,
                        success = false,
                        message = it.message ?: "Fallback failed for ${fallbackSpec.label}"
                    )
                }
            )
        }

        return ExecutionResult(
            stepId = sourceStep.id,
            success = false,
            message = "No fallback available: $reason"
        )
    }

    private fun resolveActivity(intent: Intent): android.content.pm.ResolveInfo? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    private fun Intent.describe(): String {
        return "action=$action data=$data type=$type package=${`package`}"
    }

    private companion object {
        const val TAG = "WorkflowRunner"
    }
}

private fun JsonElement.asString(): String? {
    return runCatching { jsonPrimitive.contentOrNull }.getOrNull()
}
