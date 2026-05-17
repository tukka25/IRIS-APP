package com.irisapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.irisapp.domain.catalog.ActionSpec
import com.irisapp.domain.catalog.ActionSpecRegistry
import com.irisapp.domain.catalog.ExecutionSpec
import com.irisapp.domain.catalog.ParamSpec
import com.irisapp.domain.catalog.ParamType
import com.irisapp.domain.model.BatteryCondition
import com.irisapp.domain.model.GeofenceTransition
import com.irisapp.domain.model.ChargerType
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.domain.model.SetupState
import com.irisapp.domain.model.TriggerConfig
import com.irisapp.domain.model.WorkflowStep
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.CyanVioletGradientDiagonal
import com.irisapp.ui.theme.CyanVioletStart
import com.irisapp.ui.theme.CyanVioletEnd
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.ui.theme.GlassSurface
import com.irisapp.platform.sound.YamnetClassifier
import com.irisapp.ui.theme.SurfaceDark
import com.irisapp.ui.theme.SurfaceVariantDark
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary
import com.irisapp.ui.theme.VioletAccent
import com.irisapp.ui.components.GlassmorphicCard
import com.irisapp.ui.components.GradientButton
import com.irisapp.ui.components.GradientOutlinedButton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ── Trigger category ───────────────────────────────────────────────────────────

private enum class TriggerCategory(val label: String, val icon: ImageVector, val color: Color) {
    MANUAL("Manual", Icons.Default.TouchApp, Color(0xFF5EF2FF)),
    TIME("Time & Schedule", Icons.Default.Schedule, Color(0xFFFFC15E)),
    POWER("Power & Battery", Icons.Default.BatteryChargingFull, Color(0xFF7CF0A8)),
    NETWORK("Network", Icons.Default.Wifi, Color(0xFF9EA9FF)),
    EVENT("Events", Icons.Default.Notifications, Color(0xFFFF6BD6)),
    SENSOR("Sensors & NFC", Icons.Default.Nfc, Color(0xFFB57BFF)),
}

// ── Trigger item with icon and category ───────────────────────────────────────

private data class TriggerItem(
    val label: String,
    val config: TriggerConfig,
    val icon: ImageVector,
    val category: TriggerCategory
)

private val TRIGGER_CATEGORIES: Map<TriggerCategory, List<TriggerItem>> = mapOf(
    TriggerCategory.MANUAL to listOf(
        TriggerItem("Manual", TriggerConfig.Manual, Icons.Default.TouchApp, TriggerCategory.MANUAL)
    ),
    TriggerCategory.TIME to listOf(
        TriggerItem("Time", TriggerConfig.Time(9, 0, emptyList()), Icons.Default.Schedule, TriggerCategory.TIME),
        TriggerItem("Alarm Stopped", TriggerConfig.AlarmStopped("default"), Icons.Default.Alarm, TriggerCategory.TIME),
        TriggerItem("Sleep Proxy", TriggerConfig.SleepProxy(22, 0, 7, 0, true, true), Icons.Default.Nightlight, TriggerCategory.TIME)
    ),
    TriggerCategory.POWER to listOf(
        TriggerItem("Battery", TriggerConfig.Battery(20, BatteryCondition.BELOW), Icons.Default.Power, TriggerCategory.POWER),
        TriggerItem("Charger", TriggerConfig.Charger(ChargerType.ANY), Icons.Default.BatteryChargingFull, TriggerCategory.POWER)
    ),
    TriggerCategory.NETWORK to listOf(
        TriggerItem("WiFi", TriggerConfig.WiFi(null), Icons.Default.Wifi, TriggerCategory.NETWORK),
        TriggerItem("Bluetooth", TriggerConfig.Bluetooth(null), Icons.Default.Bluetooth, TriggerCategory.NETWORK),
        TriggerItem("Airplane Mode", TriggerConfig.AirplaneMode(true), Icons.Default.AirplanemodeActive, TriggerCategory.NETWORK),
        TriggerItem("Do Not Disturb", TriggerConfig.DoNotDisturb(null), Icons.Default.DoNotDisturb, TriggerCategory.NETWORK)
    ),
    TriggerCategory.EVENT to listOf(
        TriggerItem("App Opened", TriggerConfig.AppOpened(emptyList(), true, false), Icons.Default.AppShortcut, TriggerCategory.EVENT),
        TriggerItem("App Closed", TriggerConfig.AppClosed(emptyList(), false, true), Icons.Default.AppShortcut, TriggerCategory.EVENT),
        TriggerItem("SMS Received", TriggerConfig.SmsReceived(null, null), Icons.Default.Sms, TriggerCategory.EVENT),
        TriggerItem("Notification", TriggerConfig.NotificationListenerConfig(emptyList(), null, null, false), Icons.Default.Notifications, TriggerCategory.EVENT),
        TriggerItem("Email Received", TriggerConfig.EmailReceived(null, null), Icons.Default.Email, TriggerCategory.EVENT),
        TriggerItem("Voice", TriggerConfig.Voice, Icons.Default.Mic, TriggerCategory.EVENT),
        TriggerItem("Sound Event", TriggerConfig.SoundEvent(emptyList()), Icons.Default.GraphicEq, TriggerCategory.EVENT)
    ),
    TriggerCategory.SENSOR to listOf(
        TriggerItem("Geofence", TriggerConfig.Geofence(0.0, 0.0, 100f, GeofenceTransition.ENTER_EXIT), Icons.Default.LocationOn, TriggerCategory.SENSOR),
        TriggerItem("NFC Tag", TriggerConfig.Nfc(null), Icons.Default.Nfc, TriggerCategory.SENSOR),
        TriggerItem("Share Sheet", TriggerConfig.ShareSheet(SetupState.NeedsSetup), Icons.Default.Share, TriggerCategory.SENSOR)
    )
)

// Build a flat index for quick lookup
private val ALL_TRIGGERS: List<TriggerItem> = TRIGGER_CATEGORIES.values.flatten()

/** Returns true for ExecutionSpec variants that can be executed by the app. */
private fun ExecutionSpec.isRunnable(): Boolean = this !is ExecutionSpec.PackageLaunch

private val CHARGER_TYPES = listOf("Any", "USB", "AC", "Wireless")

/**
 * State holder for the Trigger configuration.
 */
private class TriggerEditorState(initialTrigger: TriggerConfig?) {
    var selectedTriggerIndex by mutableIntStateOf(
        initialTrigger?.let { wf ->
            ALL_TRIGGERS.indexOfFirst { triggerConfigMatches(wf, it.config) }
                .coerceAtLeast(0)
        } ?: 0
    )

    // Time trigger state
    var timeHour by mutableIntStateOf((initialTrigger as? TriggerConfig.Time)?.hour ?: 9)
    var timeMinute by mutableIntStateOf((initialTrigger as? TriggerConfig.Time)?.minute ?: 0)
    var repeatDays by mutableStateOf((initialTrigger as? TriggerConfig.Time)?.repeatDays ?: emptyList())
    var repeatMode by mutableIntStateOf(
        when {
            repeatDays.isEmpty() -> 0
            repeatDays == listOf(2, 3, 4, 5, 6) -> 1
            repeatDays == listOf(1, 7) -> 2
            repeatDays == (1..7).toList() -> 3
            else -> 4
        }
    )
    var selectedDays by mutableStateOf(repeatDays.toSet())

    // Battery trigger state
    var batteryLevel by mutableIntStateOf((initialTrigger as? TriggerConfig.Battery)?.levelThreshold ?: 20)
    var batteryCondition by mutableStateOf((initialTrigger as? TriggerConfig.Battery)?.condition ?: BatteryCondition.BELOW)

    // Charger trigger state
    var chargerTypeIndex by mutableIntStateOf(
        when ((initialTrigger as? TriggerConfig.Charger)?.connectionType) {
            ChargerType.USB -> 1
            ChargerType.AC -> 2
            ChargerType.WIRELESS -> 3
            else -> 0
        }
    )

    // WiFi trigger state
    var wifiSsid by mutableStateOf((initialTrigger as? TriggerConfig.WiFi)?.ssid ?: "")

    // Bluetooth trigger state
    var bluetoothAddress by mutableStateOf((initialTrigger as? TriggerConfig.Bluetooth)?.deviceAddress ?: "")

    // AirplaneMode trigger state
    var airplaneEnabled by mutableStateOf((initialTrigger as? TriggerConfig.AirplaneMode)?.enabled ?: true)

    // Geofence trigger state
    var geofenceLatitude by mutableStateOf((initialTrigger as? TriggerConfig.Geofence)?.latitude ?: 0.0)
    var geofenceLongitude by mutableStateOf((initialTrigger as? TriggerConfig.Geofence)?.longitude ?: 0.0)
    var geofenceRadiusMeters by mutableStateOf((initialTrigger as? TriggerConfig.Geofence)?.radiusMeters ?: 100f)
    var geofenceTransitionType by mutableStateOf((initialTrigger as? TriggerConfig.Geofence)?.transitionType ?: GeofenceTransition.ENTER_EXIT)
    var geofenceName by mutableStateOf((initialTrigger as? TriggerConfig.Geofence)?.name ?: "")
    var geofenceDwellDelay by mutableIntStateOf((initialTrigger as? TriggerConfig.Geofence)?.dwellDelaySeconds ?: 0)

    // AlarmStopped trigger state
    var alarmStoppedType by mutableStateOf((initialTrigger as? TriggerConfig.AlarmStopped)?.alarmType ?: "default")

    // AppOpened/AppClosed trigger state
    var appTriggerPatterns by mutableStateOf(
        (initialTrigger as? TriggerConfig.AppOpened)?.appPackagePatterns
            ?: (initialTrigger as? TriggerConfig.AppClosed)?.appPackagePatterns
            ?: emptyList()
    )
    var appTriggerOnOpen by mutableStateOf((initialTrigger as? TriggerConfig.AppOpened)?.triggerOnOpen ?: true)
    var appTriggerOnClose by mutableStateOf((initialTrigger as? TriggerConfig.AppClosed)?.triggerOnClose ?: true)

    // Sound Event trigger state
    var soundEventClasses by mutableStateOf((initialTrigger as? TriggerConfig.SoundEvent)?.soundClasses ?: emptyList())

    // SmsReceived trigger state
    var smsSenderPattern by mutableStateOf((initialTrigger as? TriggerConfig.SmsReceived)?.senderPattern ?: "")
    var smsBodyPattern by mutableStateOf((initialTrigger as? TriggerConfig.SmsReceived)?.bodyPattern ?: "")

    // NotificationListenerConfig trigger state
    var notifAppPatterns by mutableStateOf((initialTrigger as? TriggerConfig.NotificationListenerConfig)?.appPackagePatterns ?: emptyList())
    var notifSenderPattern by mutableStateOf((initialTrigger as? TriggerConfig.NotificationListenerConfig)?.senderPattern ?: "")
    var notifBodyPattern by mutableStateOf((initialTrigger as? TriggerConfig.NotificationListenerConfig)?.bodyPattern ?: "")
    var notifTriggerOnDismiss by mutableStateOf((initialTrigger as? TriggerConfig.NotificationListenerConfig)?.triggerOnDismiss ?: false)

    // EmailReceived trigger state
    var emailSenderPattern by mutableStateOf((initialTrigger as? TriggerConfig.EmailReceived)?.senderPattern ?: "")
    var emailSubjectPattern by mutableStateOf((initialTrigger as? TriggerConfig.EmailReceived)?.subjectPattern ?: "")
    var emailAppPackage by mutableStateOf((initialTrigger as? TriggerConfig.EmailReceived)?.appPackage ?: "com.google.android.gm")

    // SleepProxy trigger state
    var sleepStartHour by mutableIntStateOf((initialTrigger as? TriggerConfig.SleepProxy)?.startTimeHour ?: 22)
    var sleepStartMinute by mutableIntStateOf((initialTrigger as? TriggerConfig.SleepProxy)?.startTimeMinute ?: 0)
    var sleepEndHour by mutableIntStateOf((initialTrigger as? TriggerConfig.SleepProxy)?.endTimeHour ?: 7)
    var sleepEndMinute by mutableIntStateOf((initialTrigger as? TriggerConfig.SleepProxy)?.endTimeMinute ?: 0)
    var sleepRequireChargerDisconnected by mutableStateOf((initialTrigger as? TriggerConfig.SleepProxy)?.requireChargerDisconnected ?: true)
    var sleepRequireDndActive by mutableStateOf((initialTrigger as? TriggerConfig.SleepProxy)?.requireDndActive ?: true)

    // Nfc trigger state
    var nfcTagId by mutableStateOf((initialTrigger as? TriggerConfig.Nfc)?.tagId ?: "")

    // ShareSheet trigger state
    var shareSheetState by mutableStateOf((initialTrigger as? TriggerConfig.ShareSheet)?.setupState ?: SetupState.NeedsSetup)

    fun build(): TriggerConfig {
        val currentItem = ALL_TRIGGERS.getOrNull(selectedTriggerIndex) ?: return TriggerConfig.Manual
        return when (currentItem.config) {
            is TriggerConfig.Manual -> TriggerConfig.Manual
            is TriggerConfig.Time -> {
                val days = when (repeatMode) {
                    0 -> emptyList()
                    1 -> listOf(2, 3, 4, 5, 6)
                    2 -> listOf(1, 7)
                    3 -> (1..7).toList()
                    else -> selectedDays.sorted()
                }
                TriggerConfig.Time(timeHour, timeMinute, days)
            }
            is TriggerConfig.Battery -> TriggerConfig.Battery(batteryLevel, batteryCondition)
            is TriggerConfig.Charger -> TriggerConfig.Charger(
                when (chargerTypeIndex) {
                    1 -> ChargerType.USB
                    2 -> ChargerType.AC
                    3 -> ChargerType.WIRELESS
                    else -> ChargerType.ANY
                }
            )
            is TriggerConfig.WiFi -> TriggerConfig.WiFi(wifiSsid.ifBlank { null })
            is TriggerConfig.Bluetooth -> TriggerConfig.Bluetooth(bluetoothAddress.ifBlank { null })
            is TriggerConfig.AirplaneMode -> TriggerConfig.AirplaneMode(airplaneEnabled)
            is TriggerConfig.DoNotDisturb -> TriggerConfig.DoNotDisturb(null)
            is TriggerConfig.Geofence -> TriggerConfig.Geofence(
                geofenceLatitude, geofenceLongitude, geofenceRadiusMeters,
                geofenceTransitionType, geofenceDwellDelay,
                geofenceName.ifBlank { null }
            )
            is TriggerConfig.AlarmStopped -> TriggerConfig.AlarmStopped(alarmStoppedType.ifBlank { "default" })
            is TriggerConfig.AppOpened -> TriggerConfig.AppOpened(appTriggerPatterns, appTriggerOnOpen, appTriggerOnClose)
            is TriggerConfig.AppClosed -> TriggerConfig.AppClosed(appTriggerPatterns, appTriggerOnOpen, appTriggerOnClose)
            is TriggerConfig.SmsReceived -> TriggerConfig.SmsReceived(smsSenderPattern.ifBlank { null }, smsBodyPattern.ifBlank { null })
            is TriggerConfig.NotificationListenerConfig -> TriggerConfig.NotificationListenerConfig(
                notifAppPatterns, notifSenderPattern.ifBlank { null }, notifBodyPattern.ifBlank { null }, notifTriggerOnDismiss
            )
            is TriggerConfig.EmailReceived -> TriggerConfig.EmailReceived(emailSenderPattern.ifBlank { null }, emailSubjectPattern.ifBlank { null }, emailAppPackage)
            is TriggerConfig.SleepProxy -> TriggerConfig.SleepProxy(sleepStartHour, sleepStartMinute, sleepEndHour, sleepEndMinute, sleepRequireChargerDisconnected, sleepRequireDndActive)
            is TriggerConfig.Nfc -> TriggerConfig.Nfc(nfcTagId.ifBlank { null })
            is TriggerConfig.ShareSheet -> TriggerConfig.ShareSheet(shareSheetState)
            is TriggerConfig.Voice -> TriggerConfig.Voice
            is TriggerConfig.SoundEvent -> TriggerConfig.SoundEvent(soundEventClasses)
            else -> TriggerConfig.Manual
        }
    }
}

/**
 * State holder for the Manual Workflow Editor.
 */
private class ManualWorkflowEditorState(initialWorkflow: PlannedWorkflow?) {
    var name by mutableStateOf(initialWorkflow?.name ?: "")
    var summary by mutableStateOf(initialWorkflow?.summary ?: "")
    val triggerState = TriggerEditorState(initialWorkflow?.trigger)
    var steps by mutableStateOf(
        initialWorkflow?.actions
            ?: listOf(WorkflowStep(id = "browser.open_url", params = buildJsonObject { put("url", "") }))
    )
    var editingStepIndex by mutableIntStateOf(-1)

    fun buildWorkflow(): PlannedWorkflow {
        return PlannedWorkflow(
            name = name.ifBlank { "Untitled" },
            summary = summary,
            trigger = triggerState.build(),
            actions = steps
        )
    }
}

/**
 * Full-screen editor for building or editing a workflow manually — without AI generation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualWorkflowEditorScreen(
    initialWorkflow: PlannedWorkflow?,
    onSave: (PlannedWorkflow) -> Unit,
    onCancel: () -> Unit
) {
    val state = remember { ManualWorkflowEditorState(initialWorkflow) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        EditorTopBar(state, onSave, onCancel)

        Text(
            text = if (initialWorkflow != null) "Edit Workflow" else "New Workflow",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        // ── Basic fields ────────────────────────────────────────────────────────
        BasicInfoFields(state)

        HorizontalDivider()

        // ── Trigger Section ─────────────────────────────────────────────────────
        TriggerSection(state.triggerState)

        HorizontalDivider()

        // ── Actions Section ─────────────────────────────────────────────────────
        ActionsSection(state)

        // ── Bottom Buttons ──────────────────────────────────────────────────────
        EditorBottomButtons(state, onSave, onCancel)

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ── Action editor dialog ─────────────────────────────────────────────
    if (state.editingStepIndex >= 0) {
        ActionEditDialog(
            step = state.steps[state.editingStepIndex],
            onSave = { updated ->
                state.steps = state.steps.toMutableList().apply { set(state.editingStepIndex, updated) }
                state.editingStepIndex = -1
            },
            onDismiss = { state.editingStepIndex = -1 }
        )
    }
}

@Composable
private fun EditorTopBar(
    state: ManualWorkflowEditorState,
    onSave: (PlannedWorkflow) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        val brush = Brush.linearGradient(
                            colors = listOf(CyanVioletStart, CyanVioletEnd),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(
                                brush = brush,
                                blendMode = BlendMode.SrcIn
                            )
                        }
                    }
            )
        }
        GradientButton(
            text = "Save",
            onClick = { onSave(state.buildWorkflow()) },
            modifier = Modifier.height(36.dp),
            enabled = state.name.isNotBlank() && state.steps.isNotEmpty() && state.steps.all { isStepValid(it) },
            fillWidth = false
        )
    }
}

@Composable
private fun BasicInfoFields(state: ManualWorkflowEditorState) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { state.name = it },
        label = { Text("Workflow name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = GlassBorder,
            focusedLabelColor = CyanAccent,
            unfocusedLabelColor = TextSecondary,
            cursorColor = CyanAccent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )

    OutlinedTextField(
        value = state.summary,
        onValueChange = { state.summary = it },
        label = { Text("Summary (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = GlassBorder,
            focusedLabelColor = CyanAccent,
            unfocusedLabelColor = TextSecondary,
            cursorColor = CyanAccent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

@Composable
private fun TriggerSection(state: TriggerEditorState) {
    Text("Trigger", style = MaterialTheme.typography.titleMedium)

    TriggerTypeSelector(state)

    TriggerConfigSection(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerTypeSelector(state: TriggerEditorState) {
    var triggerSearchQuery by remember { mutableStateOf("") }
    val filteredCategories = remember(triggerSearchQuery) {
        if (triggerSearchQuery.isBlank()) {
            TRIGGER_CATEGORIES
        } else {
            TRIGGER_CATEGORIES.mapValues { (cat, items) ->
                items.filter { item ->
                    item.label.contains(triggerSearchQuery, ignoreCase = true) ||
                            cat.label.contains(triggerSearchQuery, ignoreCase = true)
                }
            }.filter { (_, items) -> items.isNotEmpty() }
        }
    }

    OutlinedTextField(
        value = triggerSearchQuery,
        onValueChange = { triggerSearchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search triggers...", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
            if (triggerSearchQuery.isNotEmpty()) {
                IconButton(onClick = { triggerSearchQuery = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = GlassBorder,
            focusedLabelColor = CyanAccent,
            unfocusedLabelColor = TextSecondary,
            cursorColor = CyanAccent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )

    var selectedCategory by remember { mutableStateOf(TriggerCategory.MANUAL) }

    LaunchedEffect(state.selectedTriggerIndex) {
        if (state.selectedTriggerIndex in ALL_TRIGGERS.indices) {
            selectedCategory = ALL_TRIGGERS[state.selectedTriggerIndex].category
        }
    }

    Column {
        filteredCategories.forEach { (category, items) ->
            val isExpanded = selectedCategory == category
            val hasSelectedItem = items.any { item -> ALL_TRIGGERS.indexOf(item) == state.selectedTriggerIndex }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isExpanded) category.color.copy(alpha = 0.12f)
                        else if (hasSelectedItem) category.color.copy(alpha = 0.07f)
                        else GlassSurface
                    )
                    .clickable {
                        selectedCategory = if (isExpanded) selectedCategory else category
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = if (isExpanded || hasSelectedItem) category.color else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = if (isExpanded || hasSelectedItem) category.color else TextSecondary,
                        fontWeight = if (isExpanded) FontWeight(600) else FontWeight(400)
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                if (hasSelectedItem && !isExpanded) {
                    val selectedLabel = ALL_TRIGGERS.getOrNull(state.selectedTriggerIndex)?.label ?: ""
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = androidx.compose.animation.core.spring()),
                exit = shrinkVertically(animationSpec = androidx.compose.animation.core.spring())
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
                    items.forEach { item ->
                        val flatIndex = ALL_TRIGGERS.indexOf(item)
                        val isSelected = state.selectedTriggerIndex == flatIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) item.category.color.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    state.selectedTriggerIndex = flatIndex
                                    selectedCategory = item.category
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    state.selectedTriggerIndex = flatIndex
                                    selectedCategory = item.category
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = item.category.color,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isSelected) item.category.color else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TriggerConfigSection(state: TriggerEditorState) {
    val currentItem = ALL_TRIGGERS.getOrNull(state.selectedTriggerIndex)
        ?: return ManualTriggerInfo()

    when (currentItem.config) {
        is TriggerConfig.Manual -> ManualTriggerInfo()
        is TriggerConfig.Time -> TimeTriggerConfig(state)
        is TriggerConfig.Battery -> BatteryTriggerConfig(state)
        is TriggerConfig.Charger -> ChargerTriggerConfig(state)
        is TriggerConfig.WiFi -> WifiTriggerConfig(state)
        is TriggerConfig.Bluetooth -> BluetoothTriggerConfig(state)
        is TriggerConfig.AirplaneMode -> AirplaneModeTriggerConfig(state)
        is TriggerConfig.DoNotDisturb -> DndTriggerInfo()
        is TriggerConfig.Geofence -> GeofenceTriggerConfig(state)
        is TriggerConfig.AlarmStopped -> AlarmStoppedTriggerConfig(state)
        is TriggerConfig.AppOpened, is TriggerConfig.AppClosed -> AppTriggerConfig(state, currentItem.config)
        is TriggerConfig.SmsReceived -> SmsTriggerConfig(state)
        is TriggerConfig.NotificationListenerConfig -> NotificationTriggerConfig(state)
        is TriggerConfig.EmailReceived -> EmailTriggerConfig(state)
        is TriggerConfig.SleepProxy -> SleepProxyTriggerConfig(state)
        is TriggerConfig.Nfc -> NfcTriggerConfig(state)
        is TriggerConfig.ShareSheet -> ShareSheetTriggerInfo(state)
        is TriggerConfig.Voice -> VoiceTriggerInfo()
        is TriggerConfig.SoundEvent -> SoundEventTriggerConfig(state)
        else -> ManualTriggerInfo()
    }
}

@Composable
private fun ManualTriggerInfo() {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "This workflow runs manually from the widget or list.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeTriggerConfig(state: TriggerEditorState) {
    val timePickerState = rememberTimePickerState(
        initialHour = state.timeHour,
        initialMinute = state.timeMinute,
        is24Hour = true
    )

    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        state.timeHour = timePickerState.hour
        state.timeMinute = timePickerState.minute
    }

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimePicker(state = timePickerState)
            Spacer(modifier = Modifier.height(8.dp))
            val modes = listOf("One-time", "Weekdays", "Weekends", "Daily", "Custom")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                modes.forEachIndexed { i, label ->
                    FilterChip(
                        selected = state.repeatMode == i,
                        onClick = { state.repeatMode = i },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (state.repeatMode == 4) {
                Spacer(modifier = Modifier.height(8.dp))
                val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                val dayConstants = listOf(1, 2, 3, 4, 5, 6, 7)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayConstants.forEachIndexed { idx, day ->
                        FilterChip(
                            selected = day in state.selectedDays,
                            onClick = {
                                state.selectedDays = if (day in state.selectedDays) {
                                    state.selectedDays - day
                                } else {
                                    state.selectedDays + day
                                }
                            },
                            label = { Text(dayLabels[idx], style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Battery level: ${state.batteryLevel}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.batteryLevel.toFloat(),
                onValueChange = { state.batteryLevel = it.toInt() },
                valueRange = 5f..100f,
                steps = 18
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BatteryCondition.BELOW to "Below", BatteryCondition.ABOVE to "Above").forEach { (cond, label) ->
                    FilterChip(
                        selected = state.batteryCondition == cond,
                        onClick = { state.batteryCondition = cond },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargerTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection type:", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CHARGER_TYPES.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = state.chargerTypeIndex == idx,
                        onClick = { state.chargerTypeIndex = idx },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiTriggerConfig(state: TriggerEditorState) {
    OutlinedTextField(
        value = state.wifiSsid,
        onValueChange = { state.wifiSsid = it },
        label = { Text("SSID (leave blank for any)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun BluetoothTriggerConfig(state: TriggerEditorState) {
    OutlinedTextField(
        value = state.bluetoothAddress,
        onValueChange = { state.bluetoothAddress = it },
        label = { Text("Device address (leave blank for any)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun AirplaneModeTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Trigger when airplane mode is ON")
            Switch(checked = state.airplaneEnabled, onCheckedChange = { state.airplaneEnabled = it })
        }
    }
}

@Composable
private fun DndTriggerInfo() {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Do Not Disturb trigger", style = MaterialTheme.typography.bodyMedium)
            Text("Fires on any DND interruption filter change.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun GeofenceTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Geofence trigger", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = state.geofenceName,
                onValueChange = { state.geofenceName = it },
                label = { Text("Location name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (state.geofenceLatitude == 0.0) "" else state.geofenceLatitude.toString(),
                    onValueChange = { state.geofenceLatitude = it.toDoubleOrNull() ?: 0.0 },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = if (state.geofenceLongitude == 0.0) "" else state.geofenceLongitude.toString(),
                    onValueChange = { state.geofenceLongitude = it.toDoubleOrNull() ?: 0.0 },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OsmMapPicker(
                latitude = state.geofenceLatitude,
                longitude = state.geofenceLongitude,
                radiusMeters = state.geofenceRadiusMeters,
                onLocationSelected = { lat, lng ->
                    state.geofenceLatitude = lat
                    state.geofenceLongitude = lng
                }
            )

            Text("Radius: ${state.geofenceRadiusMeters.toInt()} m", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.geofenceRadiusMeters,
                onValueChange = { state.geofenceRadiusMeters = it },
                valueRange = 50f..2000f,
            )
            Text("Trigger when:", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    GeofenceTransition.ENTER to "Arriving",
                    GeofenceTransition.EXIT to "Leaving",
                    GeofenceTransition.DWELL to "Staying",
                    GeofenceTransition.ENTER_EXIT to "Either"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = state.geofenceTransitionType == type,
                        onClick = { state.geofenceTransitionType = type },
                        label = { Text(label) }
                    )
                }
            }
            if (state.geofenceTransitionType == GeofenceTransition.DWELL) {
                Text("Dwell delay: ${state.geofenceDwellDelay}s", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.geofenceDwellDelay.toFloat(),
                    onValueChange = { state.geofenceDwellDelay = it.toInt() },
                    valueRange = 0f..300f,
                    steps = 29,
                )
            }
        }
    }
}

@Composable
private fun AlarmStoppedTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alarm Stopped", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires when a IrisApp alarm is dismissed or cancelled by the user.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            OutlinedTextField(
                value = state.alarmStoppedType,
                onValueChange = { state.alarmStoppedType = it },
                label = { Text("Alarm type filter (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. default, reminder, timer") }
            )
        }
    }
}

@Composable
private fun AppTriggerConfig(state: TriggerEditorState, config: TriggerConfig) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (config is TriggerConfig.AppOpened) "App Opened" else "App Closed",
                style = MaterialTheme.typography.bodyMedium
            )
            AppPatternEditor(
                patterns = state.appTriggerPatterns,
                onPatternsChange = { state.appTriggerPatterns = it }
            )
            Text("Trigger on:", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.appTriggerOnOpen,
                    onClick = { state.appTriggerOnOpen = !state.appTriggerOnOpen },
                    label = { Text("Open") }
                )
                FilterChip(
                    selected = state.appTriggerOnClose,
                    onClick = { state.appTriggerOnClose = !state.appTriggerOnClose },
                    label = { Text("Close") }
                )
            }
        }
    }
}

@Composable
private fun SmsTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SMS Received", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires when an SMS matching the patterns is received. Requires notification access.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            OutlinedTextField(
                value = state.smsSenderPattern,
                onValueChange = { state.smsSenderPattern = it },
                label = { Text("Sender pattern (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. +1 or ABC") }
            )
            OutlinedTextField(
                value = state.smsBodyPattern,
                onValueChange = { state.smsBodyPattern = it },
                label = { Text("Body pattern (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. OTP or verification") }
            )
        }
    }
}

@Composable
private fun NotificationTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App Notification", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires on notifications from specific apps. Requires notification access.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            OutlinedTextField(
                value = state.notifAppPatterns.joinToString(", "),
                onValueChange = {
                    state.notifAppPatterns = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                },
                label = { Text("App package patterns (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. com.whatsapp, org.telegram") }
            )
            OutlinedTextField(
                value = state.notifSenderPattern,
                onValueChange = { state.notifSenderPattern = it },
                label = { Text("Sender pattern (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.notifBodyPattern,
                onValueChange = { state.notifBodyPattern = it },
                label = { Text("Body pattern (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Also on dismiss")
                Switch(
                    checked = state.notifTriggerOnDismiss,
                    onCheckedChange = { state.notifTriggerOnDismiss = it }
                )
            }
        }
    }
}

@Composable
private fun EmailTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Email Received", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires on email notifications. Requires notification access.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            OutlinedTextField(
                value = state.emailSenderPattern,
                onValueChange = { state.emailSenderPattern = it },
                label = { Text("From / sender pattern (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. @company.com") }
            )
            OutlinedTextField(
                value = state.emailSubjectPattern,
                onValueChange = { state.emailSubjectPattern = it },
                label = { Text("Subject pattern (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. Invoice or Urgent") }
            )
            OutlinedTextField(
                value = state.emailAppPackage,
                onValueChange = { state.emailAppPackage = it },
                label = { Text("Email app package") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun SleepProxyTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sleep Proxy", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires when bedtime conditions are met (DND active, charger disconnected).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = String.format("%02d:%02d", state.sleepStartHour, state.sleepStartMinute),
                    onValueChange = {
                        val parts = it.split(":")
                        state.sleepStartHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: state.sleepStartHour
                        state.sleepStartMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: state.sleepStartMinute
                    },
                    label = { Text("Start") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = String.format("%02d:%02d", state.sleepEndHour, state.sleepEndMinute),
                    onValueChange = {
                        val parts = it.split(":")
                        state.sleepEndHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: state.sleepEndHour
                        state.sleepEndMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: state.sleepEndMinute
                    },
                    label = { Text("End") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Require charger disconnected")
                Switch(
                    checked = state.sleepRequireChargerDisconnected,
                    onCheckedChange = { state.sleepRequireChargerDisconnected = it }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Require DND active")
                Switch(
                    checked = state.sleepRequireDndActive,
                    onCheckedChange = { state.sleepRequireDndActive = it }
                )
            }
        }
    }
}

@Composable
private fun NfcTriggerConfig(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NFC Tag", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires when a specific NFC tag is scanned. Requires NFC enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            OutlinedTextField(
                value = state.nfcTagId,
                onValueChange = { state.nfcTagId = it },
                label = { Text("Tag ID (leave blank for any)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun ShareSheetTriggerInfo(state: TriggerEditorState) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Share Sheet", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires when content is shared to IrisApp from any app.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            val stateLabel = when (state.shareSheetState) {
                SetupState.Ready -> "Ready"
                SetupState.NeedsSetup -> "Needs setup"
                SetupState.Unsupported -> "Unsupported"
            }
            Text("Status: $stateLabel", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VoiceTriggerInfo() {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voice Intent", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Fires when the user speaks a trigger phrase and the Gemini model identifies a matching workflow.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Text(
                "Use the mic button on the main screen to train and trigger voice workflows.",
                style = MaterialTheme.typography.bodySmall,
                color = CyanAccent
            )
        }
    }
}

@Composable
private fun SoundEventTriggerConfig(state: TriggerEditorState) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allSounds = remember { YamnetClassifier.AUDIOSET_CLASSES.toList() }
    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) allSounds
        else allSounds.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sound Event", style = MaterialTheme.typography.bodyMedium)
                    if (state.soundEventClasses.isEmpty()) {
                        Text(
                            "No sound classes configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB74D)
                        )
                    } else {
                        Text(
                            "${state.soundEventClasses.size} sound${if (state.soundEventClasses.size != 1) "s" else ""} configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search sounds…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true
                )
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    items(filtered.take(50)) { sound ->
                        val checked = sound in state.soundEventClasses
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.soundEventClasses = if (checked) {
                                        state.soundEventClasses - sound
                                    } else {
                                        state.soundEventClasses + sound
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected ->
                                    state.soundEventClasses = if (selected) {
                                        state.soundEventClasses + sound
                                    } else {
                                        state.soundEventClasses - sound
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sound,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (filtered.size > 50) {
                        item {
                            Text(
                                "Showing first 50 results",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
                if (state.soundEventClasses.isNotEmpty()) {
                    TextButton(
                        onClick = { state.soundEventClasses = emptyList() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Clear all")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsSection(state: ManualWorkflowEditorState) {
    Text("Actions", style = MaterialTheme.typography.titleMedium)

    Text(
        text = "${state.steps.size} step${if (state.steps.size != 1) "s" else ""}",
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary
    )

    state.steps.forEachIndexed { index, step ->
        val spec = ActionSpecRegistry.find(step.id)
        val stepValid = isStepValid(step)
        ActionStepCard(
            step = step,
            stepNumber = index + 1,
            label = spec?.label ?: step.id,
            isValid = spec != null && stepValid,
            onEdit = { state.editingStepIndex = index },
            onDelete = {
                if (state.steps.size > 1) {
                    state.steps = state.steps.toMutableList().apply { removeAt(index) }
                    if (state.editingStepIndex == index) state.editingStepIndex = -1
                }
            }
        )
    }

    GradientOutlinedButton(
        text = "+ Add Action",
        onClick = {
            state.steps = state.steps + WorkflowStep(id = "browser.open_url", params = buildJsonObject { put("url", "") })
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EditorBottomButtons(
    state: ManualWorkflowEditorState,
    onSave: (PlannedWorkflow) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GradientButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        GradientButton(
            text = "Save",
            onClick = { onSave(state.buildWorkflow()) },
            modifier = Modifier.weight(1f),
            enabled = state.name.isNotBlank() && state.steps.isNotEmpty() && state.steps.all { isStepValid(it) }
        )
    }
}

@Composable
private fun ActionStepCard(
    step: WorkflowStep,
    stepNumber: Int,
    label: String,
    isValid: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "$stepNumber.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                if (step.params.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            step.params.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionEditDialog(
    step: WorkflowStep,
    onSave: (WorkflowStep) -> Unit,
    onDismiss: () -> Unit
) {
    val allActions = remember { ActionSpecRegistry.all.filter { it.execution.isRunnable() }.sortedBy { it.label } }
    var selectedActionId by remember { mutableStateOf(step.id) }
    var params by remember {
        mutableStateOf(
            step.params.entries.associate { it.key to (it.value.toString().removeSurrounding("\"")) }
                .toMutableMap()
        )
    }
    val validationErrors = remember(selectedActionId, params) {
        val preview = WorkflowStep(id = selectedActionId, params = buildJsonObject {
            params.forEach { (k, v) -> put(k, v) }
        })
        validateStep(preview)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (validationErrors.isEmpty()) "Edit Action" else "Edit Action — ${validationErrors.size} issue${if (validationErrors.size != 1) "s" else ""}")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Action picker
                var expanded by remember { mutableStateOf(false) }
                val selectedSpec = ActionSpecRegistry.find(selectedActionId)

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedSpec?.label ?: selectedActionId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        allActions.forEach { spec ->
                            DropdownMenuItem(
                                text = { Text(spec.label) },
                                onClick = {
                                    selectedActionId = spec.id
                                    params = spec.params.filter { p -> p.required }
                                        .associate { it.name to "" }
                                        .toMutableMap()
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Params
                val spec = ActionSpecRegistry.find(selectedActionId)
                val context = LocalContext.current
                if (spec != null) {
                    spec.params.forEach { param ->
                        val paramName = param.name
                        val paramErrors = validationErrors.filter { it.paramName == paramName }

                        when (param.type) {
                            ParamType.AppPicker -> {
                                var appExpanded by remember { mutableStateOf(false) }
                                var searchQuery by remember { mutableStateOf(params[paramName] ?: "") }
                                val providerId = spec.installedAppListProviderId ?: "installed_apps"
                                val allApps = remember { ActionSpecRegistry.getInstalledAppList(providerId, context) }
                                val filteredApps = remember(searchQuery) {
                                    if (searchQuery.isBlank()) allApps
                                    else allApps.filter { (label, pkg) ->
                                        label.contains(searchQuery, ignoreCase = true) ||
                                                pkg.contains(searchQuery, ignoreCase = true)
                                    }
                                }
                                val selectedLabel = allApps.find { it.second == params[paramName] }?.first
                                    ?: params[paramName] ?: "Select an app"

                                ExposedDropdownMenuBox(expanded = appExpanded, onExpandedChange = { appExpanded = it }) {
                                    OutlinedTextField(
                                        value = selectedLabel,
                                        onValueChange = { searchQuery = it },
                                        label = { Text(paramName) },
                                        placeholder = { Text("Search installed apps...") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        singleLine = true,
                                        isError = paramErrors.isNotEmpty(),
                                        supportingText = {
                                            if (paramErrors.isNotEmpty()) {
                                                Text(paramErrors.joinToString { it.message }, color = MaterialTheme.colorScheme.error)
                                            } else {
                                                Text("Type to filter — tap a result to select", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    )
                                    val appsToShow = filteredApps.take(50)
                                    if (appsToShow.isNotEmpty()) {
                                        ExposedDropdownMenu(
                                            expanded = appExpanded,
                                            onDismissRequest = { appExpanded = false }
                                        ) {
                                            appsToShow.forEach { (label, pkg) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                                                        }
                                                    },
                                                    onClick = {
                                                        params = params.toMutableMap().apply { put(paramName, pkg) }
                                                        searchQuery = label
                                                        appExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                OutlinedTextField(
                                    value = params[paramName] ?: "",
                                    onValueChange = { newValue ->
                                        params = params.toMutableMap().apply { put(paramName, newValue) }
                                    },
                                    label = {
                                        Text(
                                            paramName + if (!param.required) " (optional)" else ""
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    isError = paramErrors.isNotEmpty(),
                                    supportingText = {
                                        if (paramErrors.isNotEmpty()) {
                                            Text(paramErrors.joinToString { it.message }, color = MaterialTheme.colorScheme.error)
                                        } else if (param.description.isNotBlank()) {
                                            Text(param.description, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                // Show ID-level errors (unknown action)
                val idErrors = validationErrors.filter { it.paramName == "id" }
                idErrors.forEach { error ->
                    Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            val canSave = validationErrors.isEmpty()
            GradientButton(
                text = "Save",
                onClick = {
                    val json = buildJsonObject {
                        params.forEach { (k, v) -> put(k, v) }
                    }
                    onSave(WorkflowStep(id = selectedActionId, params = json))
                },
                enabled = canSave,
                fillWidth = false
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun triggerConfigMatches(a: TriggerConfig, b: TriggerConfig): Boolean {
    return when {
        a is TriggerConfig.Manual && b is TriggerConfig.Manual -> true
        a is TriggerConfig.Time && b is TriggerConfig.Time -> true
        a is TriggerConfig.Nfc && b is TriggerConfig.Nfc -> true
        a is TriggerConfig.ShareSheet && b is TriggerConfig.ShareSheet -> true
        a is TriggerConfig.Battery && b is TriggerConfig.Battery -> true
        a is TriggerConfig.Charger && b is TriggerConfig.Charger -> true
        a is TriggerConfig.WiFi && b is TriggerConfig.WiFi -> true
        a is TriggerConfig.Bluetooth && b is TriggerConfig.Bluetooth -> true
        a is TriggerConfig.AirplaneMode && b is TriggerConfig.AirplaneMode -> true
        a is TriggerConfig.DoNotDisturb && b is TriggerConfig.DoNotDisturb -> true
        a is TriggerConfig.Geofence && b is TriggerConfig.Geofence -> true
        a is TriggerConfig.AlarmStopped && b is TriggerConfig.AlarmStopped -> true
        a is TriggerConfig.AppOpened && b is TriggerConfig.AppOpened -> true
        a is TriggerConfig.AppClosed && b is TriggerConfig.AppClosed -> true
        a is TriggerConfig.SmsReceived && b is TriggerConfig.SmsReceived -> true
        a is TriggerConfig.NotificationListenerConfig && b is TriggerConfig.NotificationListenerConfig -> true
        a is TriggerConfig.EmailReceived && b is TriggerConfig.EmailReceived -> true
        a is TriggerConfig.SleepProxy && b is TriggerConfig.SleepProxy -> true
        a is TriggerConfig.Voice && b is TriggerConfig.Voice -> true
        a is TriggerConfig.SoundEvent && b is TriggerConfig.SoundEvent -> true
        else -> false
    }
}

// ── Action validation ────────────────────────────────────────────────────────

private data class ActionValidationError(val paramName: String, val message: String)

/** Validates a WorkflowStep against its ActionSpec. Returns empty list if valid. */
private fun validateStep(step: WorkflowStep): List<ActionValidationError> {
    val spec = ActionSpecRegistry.find(step.id)
    if (spec == null) return listOf(ActionValidationError("id", "Unknown action: ${step.id}"))

    val errors = mutableListOf<ActionValidationError>()
    val params = step.params.entries.associate { it.key to it.value.toString().removeSurrounding("\"") }

    // Check required params are present and non-empty
    spec.params.filter { it.required }.forEach { param ->
        val value = params[param.name]
        if (value.isNullOrBlank()) {
            errors.add(ActionValidationError(param.name, "${param.name} is required"))
        }
    }

    // Validate param types
    spec.params.forEach { param ->
        val value = params[param.name] ?: return@forEach
        if (value.isBlank()) return@forEach

        when (param.type) {
            com.irisapp.domain.catalog.ParamType.Url -> {
                if (!value.startsWith("http://") && !value.startsWith("https://") && !value.startsWith("content://")) {
                    errors.add(ActionValidationError(param.name, "Must be a valid URL (http:// or https://)"))
                }
            }
            com.irisapp.domain.catalog.ParamType.Int -> {
                if (value.toIntOrNull() == null) {
                    errors.add(ActionValidationError(param.name, "Must be a whole number"))
                }
            }
            com.irisapp.domain.catalog.ParamType.Long,
            com.irisapp.domain.catalog.ParamType.DateTimeMillis -> {
                if (value.toLongOrNull() == null) {
                    errors.add(ActionValidationError(param.name, "Must be a number"))
                }
            }
            else -> { /* string/uri/enum — no type check */ }
        }
    }

    return errors
}

/** True if the step has no validation errors. */
private fun isStepValid(step: WorkflowStep): Boolean = validateStep(step).isEmpty()

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppPatternEditor(
    patterns: List<String>,
    onPatternsChange: (List<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val allApps = remember { ActionSpecRegistry.getInstalledAppList("installed_apps", context) }
    val filteredApps = remember(searchQuery) {
        if (searchQuery.isBlank()) allApps.take(50)
        else allApps.filter { (label, pkg) ->
            label.contains(searchQuery, ignoreCase = true) || pkg.contains(searchQuery, ignoreCase = true)
        }.take(50)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Show selected patterns as chips
        if (patterns.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                patterns.forEach { pattern ->
                    InputChip(
                        label = { Text(pattern, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = false,
                        onClick = { onPatternsChange(patterns - pattern) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }

        // Search + add from installed apps
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Add app pattern") },
                placeholder = { Text("Search installed apps...") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true
            )
            if (filteredApps.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    filteredApps.forEach { (label, pkg) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pkg, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                                }
                            },
                            onClick = {
                                if (!patterns.contains(pkg)) {
                                    onPatternsChange(patterns + pkg)
                                }
                                searchQuery = ""
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Manual text input for additional patterns
        var manualInput by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = { Text("Or enter package pattern") },
                placeholder = { Text("e.g. com.instagram") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(
                onClick = {
                    val trimmed = manualInput.trim()
                    if (trimmed.isNotEmpty() && !patterns.contains(trimmed)) {
                        onPatternsChange(patterns + trimmed)
                    }
                    manualInput = ""
                },
                enabled = manualInput.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add pattern")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputChip(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        trailingIcon = trailingIcon
    )
}
