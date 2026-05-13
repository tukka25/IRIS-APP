package com.gemmaworkflow.ui.home

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
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
import com.gemmaworkflow.domain.catalog.ActionSpec
import com.gemmaworkflow.domain.catalog.ActionSpecRegistry
import com.gemmaworkflow.domain.catalog.ExecutionSpec
import com.gemmaworkflow.domain.catalog.ParamSpec
import com.gemmaworkflow.domain.catalog.ParamType
import com.gemmaworkflow.domain.model.BatteryCondition
import com.gemmaworkflow.domain.model.GeofenceTransition
import com.gemmaworkflow.domain.model.ChargerType
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.SetupState
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.model.WorkflowStep
import com.gemmaworkflow.ui.theme.BackgroundDark
import com.gemmaworkflow.ui.theme.CyanAccent
import com.gemmaworkflow.ui.theme.GlassBorder
import com.gemmaworkflow.ui.theme.GlassSurface
import com.gemmaworkflow.ui.theme.SurfaceDark
import com.gemmaworkflow.ui.theme.SurfaceVariantDark
import com.gemmaworkflow.ui.theme.TextPrimary
import com.gemmaworkflow.ui.theme.TextSecondary
import com.gemmaworkflow.ui.theme.VioletAccent
import com.gemmaworkflow.ui.components.GlassmorphicCard
import com.gemmaworkflow.ui.components.GradientButton
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
        TriggerItem("Email Received", TriggerConfig.EmailReceived(null, null), Icons.Default.Email, TriggerCategory.EVENT)
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

private val TRIGGER_TYPES = listOf(
    "Manual" to TriggerConfig.Manual,
    "Time" to TriggerConfig.Time(9, 0, emptyList()),
    "Battery" to TriggerConfig.Battery(20, BatteryCondition.BELOW),
    "Charger" to TriggerConfig.Charger(ChargerType.ANY),
    "WiFi" to TriggerConfig.WiFi(null),
    "Bluetooth" to TriggerConfig.Bluetooth(null),
    "Airplane Mode" to TriggerConfig.AirplaneMode(true),
    "Do Not Disturb" to TriggerConfig.DoNotDisturb(null),
    "Geofence" to TriggerConfig.Geofence(0.0, 0.0, 100f, GeofenceTransition.ENTER_EXIT),
    "Alarm Stopped" to TriggerConfig.AlarmStopped("default"),
    "App Opened" to TriggerConfig.AppOpened(emptyList(), true, false),
    "App Closed" to TriggerConfig.AppClosed(emptyList(), false, true),
    "SMS Received" to TriggerConfig.SmsReceived(null, null),
    "Notification" to TriggerConfig.NotificationListenerConfig(emptyList(), null, null, false),
    "Email Received" to TriggerConfig.EmailReceived(null, null),
    "Sleep Proxy" to TriggerConfig.SleepProxy(22, 0, 7, 0, true, true),
    "NFC Tag" to TriggerConfig.Nfc(null),
    "Share Sheet" to TriggerConfig.ShareSheet(SetupState.NeedsSetup),
)

private val CHARGER_TYPES = listOf("Any", "USB", "AC", "Wireless")

/**
 * Full-screen editor for building or editing a workflow manually — without AI generation.
 *
 * Supports all trigger types and an arbitrary list of actions picked from the
 * [ActionSpecRegistry]. Parameters are edited inline with text fields.
 *
 * @param initialWorkflow  Non-null when editing an existing workflow; null when creating new.
 * @param onSave           Called with the final [PlannedWorkflow] when the user saves.
 * @param onCancel         Go back without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualWorkflowEditorScreen(
    initialWorkflow: PlannedWorkflow?,
    onSave: (PlannedWorkflow) -> Unit,
    onCancel: () -> Unit
) {
    // ── Basic fields ────────────────────────────────────────────────────────
    var name by remember { mutableStateOf(initialWorkflow?.name ?: "") }
    var summary by remember { mutableStateOf(initialWorkflow?.summary ?: "") }

    // ── Trigger ─────────────────────────────────────────────────────────────
    var selectedTriggerIndex by remember {
        mutableIntStateOf(
            initialWorkflow?.let { wf ->
                ALL_TRIGGERS.indexOfFirst { triggerConfigMatches(wf.trigger, it.config) }
                    .coerceAtLeast(0)
            } ?: 0
        )
    }

    // Time trigger state
    var timeHour by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Time)?.hour ?: 9)
    }
    var timeMinute by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Time)?.minute ?: 0)
    }
    var repeatDays by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Time)?.repeatDays ?: emptyList())
    }
    var repeatMode by remember {
        mutableStateOf(
            when {
                repeatDays.isEmpty() -> 0       // One-time
                repeatDays == listOf(2,3,4,5,6) -> 1  // Weekdays
                repeatDays == listOf(1,7) -> 2    // Weekends
                repeatDays == (1..7).toList() -> 3    // Daily
                else -> 4                         // Custom
            }
        )
    }
    var selectedDays by remember { mutableStateOf(repeatDays.toSet()) }

    val timePickerState = rememberTimePickerState(
        initialHour = timeHour,
        initialMinute = timeMinute,
        is24Hour = true
    )

    // Battery trigger state
    var batteryLevel by remember {
        mutableIntStateOf((initialWorkflow?.trigger as? TriggerConfig.Battery)?.levelThreshold ?: 20)
    }
    var batteryCondition by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Battery)?.condition ?: BatteryCondition.BELOW)
    }

    // Charger trigger state
    var chargerTypeIndex by remember {
        mutableIntStateOf(
            when ((initialWorkflow?.trigger as? TriggerConfig.Charger)?.connectionType) {
                ChargerType.USB -> 1
                ChargerType.AC -> 2
                ChargerType.WIRELESS -> 3
                else -> 0
            }
        )
    }

    // WiFi trigger state
    var wifiSsid by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.WiFi)?.ssid ?: "")
    }

    // Bluetooth trigger state
    var bluetoothAddress by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Bluetooth)?.deviceAddress ?: "")
    }

    // AirplaneMode trigger state
    var airplaneEnabled by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.AirplaneMode)?.enabled ?: true)
    }

    // Geofence trigger state
    var geofenceLatitude by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Geofence)?.latitude ?: 0.0)
    }
    var geofenceLongitude by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Geofence)?.longitude ?: 0.0)
    }
    var geofenceRadiusMeters by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Geofence)?.radiusMeters ?: 100f)
    }
    var geofenceTransitionType by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Geofence)?.transitionType ?: GeofenceTransition.ENTER_EXIT)
    }
    var geofenceName by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Geofence)?.name ?: "")
    }
    var geofenceDwellDelay by remember {
        mutableIntStateOf((initialWorkflow?.trigger as? TriggerConfig.Geofence)?.dwellDelaySeconds ?: 0)
    }

    // AlarmStopped trigger state
    var alarmStoppedType by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.AlarmStopped)?.alarmType ?: "default")
    }

    // AppOpened/AppClosed trigger state
    var appTriggerPatterns by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.AppOpened)?.appPackagePatterns
            ?: (initialWorkflow?.trigger as? TriggerConfig.AppClosed)?.appPackagePatterns
            ?: emptyList())
    }
    var appTriggerOnOpen by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.AppOpened)?.triggerOnOpen
            ?: (initialWorkflow?.trigger as? TriggerConfig.AppClosed)?.triggerOnOpen
            ?: true)
    }
    var appTriggerOnClose by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.AppOpened)?.triggerOnClose
            ?: (initialWorkflow?.trigger as? TriggerConfig.AppClosed)?.triggerOnClose
            ?: false)
    }

    // SmsReceived trigger state
    var smsSenderPattern by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.SmsReceived)?.senderPattern ?: "")
    }
    var smsBodyPattern by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.SmsReceived)?.bodyPattern ?: "")
    }

    // NotificationListenerConfig trigger state
    var notifAppPatterns by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.NotificationListenerConfig)?.appPackagePatterns ?: emptyList())
    }
    var notifSenderPattern by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.NotificationListenerConfig)?.senderPattern ?: "")
    }
    var notifBodyPattern by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.NotificationListenerConfig)?.bodyPattern ?: "")
    }
    var notifTriggerOnDismiss by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.NotificationListenerConfig)?.triggerOnDismiss ?: false)
    }

    // EmailReceived trigger state
    var emailSenderPattern by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.EmailReceived)?.senderPattern ?: "")
    }
    var emailSubjectPattern by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.EmailReceived)?.subjectPattern ?: "")
    }
    var emailAppPackage by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.EmailReceived)?.appPackage ?: "com.google.android.gm")
    }

    // SleepProxy trigger state
    var sleepStartHour by remember {
        mutableIntStateOf((initialWorkflow?.trigger as? TriggerConfig.SleepProxy)?.startTimeHour ?: 22)
    }
    var sleepStartMinute by remember {
        mutableIntStateOf((initialWorkflow?.trigger as? TriggerConfig.SleepProxy)?.startTimeMinute ?: 0)
    }
    var sleepEndHour by remember {
        mutableIntStateOf((initialWorkflow?.trigger as? TriggerConfig.SleepProxy)?.endTimeHour ?: 7)
    }
    var sleepEndMinute by remember {
        mutableIntStateOf((initialWorkflow?.trigger as? TriggerConfig.SleepProxy)?.endTimeMinute ?: 0)
    }
    var sleepRequireChargerDisconnected by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.SleepProxy)?.requireChargerDisconnected ?: true)
    }
    var sleepRequireDndActive by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.SleepProxy)?.requireDndActive ?: true)
    }

    // Nfc trigger state
    var nfcTagId by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.Nfc)?.tagId ?: "")
    }

    // ShareSheet trigger state
    var shareSheetState by remember {
        mutableStateOf((initialWorkflow?.trigger as? TriggerConfig.ShareSheet)?.setupState ?: SetupState.NeedsSetup)
    }

    // ── Actions ─────────────────────────────────────────────────────────────
    var steps by remember {
        mutableStateOf(
            initialWorkflow?.actions
                ?: listOf(WorkflowStep(id = "browser.open_url", params = buildJsonObject { put("url", "") }))
        )
    }
    var editingStepIndex by remember { mutableStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onCancel() }
            )
            GradientButton(
                text = "Save",
                onClick = {
                    val trigger = buildTrigger(
                        triggerType = Pair(
                            "",
                            ALL_TRIGGERS.getOrNull(selectedTriggerIndex)?.config ?: TriggerConfig.Manual
                        ),
                        timeHour = timeHour,
                        timeMinute = timeMinute,
                        repeatMode = repeatMode,
                        selectedDays = selectedDays,
                        batteryLevel = batteryLevel,
                        batteryCondition = batteryCondition,
                        chargerTypeIndex = chargerTypeIndex,
                        wifiSsid = wifiSsid,
                        bluetoothAddress = bluetoothAddress,
                        airplaneEnabled = airplaneEnabled,
                        geofenceLatitude = geofenceLatitude,
                        geofenceLongitude = geofenceLongitude,
                        geofenceRadiusMeters = geofenceRadiusMeters,
                        geofenceTransitionType = geofenceTransitionType,
                        geofenceName = geofenceName,
                        geofenceDwellDelay = geofenceDwellDelay,
                        alarmStoppedType = alarmStoppedType,
                        appTriggerPatterns = appTriggerPatterns,
                        appTriggerOnOpen = appTriggerOnOpen,
                        appTriggerOnClose = appTriggerOnClose,
                        smsSenderPattern = smsSenderPattern,
                        smsBodyPattern = smsBodyPattern,
                        notifAppPatterns = notifAppPatterns,
                        notifSenderPattern = notifSenderPattern,
                        notifBodyPattern = notifBodyPattern,
                        notifTriggerOnDismiss = notifTriggerOnDismiss,
                        emailSenderPattern = emailSenderPattern,
                        emailSubjectPattern = emailSubjectPattern,
                        emailAppPackage = emailAppPackage,
                        sleepStartHour = sleepStartHour,
                        sleepStartMinute = sleepStartMinute,
                        sleepEndHour = sleepEndHour,
                        sleepEndMinute = sleepEndMinute,
                        sleepRequireChargerDisconnected = sleepRequireChargerDisconnected,
                        sleepRequireDndActive = sleepRequireDndActive,
                        nfcTagId = nfcTagId,
                        shareSheetState = shareSheetState
                    )
                    val workflow = PlannedWorkflow(
                        name = name.ifBlank { "Untitled" },
                        summary = summary,
                        trigger = trigger,
                        actions = steps
                    )
                    onSave(workflow)
                },
                modifier = Modifier.height(36.dp)
            )
        }

        Text(
            text = if (initialWorkflow != null) "Edit Workflow" else "New Workflow",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        // ── Name ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
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

        // ── Summary ──────────────────────────────────────────────────────
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
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

        HorizontalDivider()

        // ── Trigger type selector — accordion with categories ─────────────────────────
        Text("Trigger", style = MaterialTheme.typography.titleMedium)

        // Search field for triggers
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

        // Find which category + item index the current selection belongs to
        var selectedCategory by remember { mutableStateOf(TriggerCategory.MANUAL) }
        var selectedItemIndex by remember { mutableIntStateOf(0) }

        // Sync selection to categories on first load
        LaunchedEffect(selectedTriggerIndex) {
            if (selectedTriggerIndex in ALL_TRIGGERS.indices) {
                val item = ALL_TRIGGERS[selectedTriggerIndex]
                selectedCategory = item.category
                selectedItemIndex = ALL_TRIGGERS.indexOf(item)
            }
        }

        Column {
            filteredCategories.forEach { (category, items) ->
                val isExpanded = selectedCategory == category
                val hasSelectedItem = items.any { item -> ALL_TRIGGERS.indexOf(item) == selectedTriggerIndex }

                // Category header
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
                        val selectedLabel = ALL_TRIGGERS.getOrNull(selectedTriggerIndex)?.label ?: ""
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .then(
                                if (isExpanded) Modifier else Modifier
                            )
                    )
                }

                // Items in this category — animated expand/collapse
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = androidx.compose.animation.core.spring()),
                    exit = shrinkVertically(animationSpec = androidx.compose.animation.core.spring())
                ) {
                    Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
                        items.forEach { item ->
                            val flatIndex = ALL_TRIGGERS.indexOf(item)
                            val isSelected = selectedTriggerIndex == flatIndex

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) item.category.color.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedTriggerIndex = flatIndex
                                        selectedCategory = item.category
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTriggerIndex = flatIndex
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

        // Trigger-specific config
        val currentTriggerConfig = ALL_TRIGGERS.getOrNull(selectedTriggerIndex)?.config
            ?: TriggerConfig.Manual

        when (currentTriggerConfig) {
            is TriggerConfig.Time -> {
                Card(modifier = Modifier.fillMaxWidth()) {
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
                                    selected = repeatMode == i,
                                    onClick = { repeatMode = i },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        if (repeatMode == 4) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                            val dayConstants = listOf(1, 2, 3, 4, 5, 6, 7)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                dayConstants.forEachIndexed { idx, day ->
                                    FilterChip(
                                        selected = day in selectedDays,
                                        onClick = {
                                            selectedDays = if (day in selectedDays) {
                                                selectedDays - day
                                            } else {
                                                selectedDays + day
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
            is TriggerConfig.Battery -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Battery level: $batteryLevel%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = batteryLevel.toFloat(),
                            onValueChange = { batteryLevel = it.toInt() },
                            valueRange = 5f..100f,
                            steps = 18
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(BatteryCondition.BELOW to "Below", BatteryCondition.ABOVE to "Above").forEach { (cond, label) ->
                                FilterChip(
                                    selected = batteryCondition == cond,
                                    onClick = { batteryCondition = cond },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
            is TriggerConfig.Charger -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Connection type:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CHARGER_TYPES.forEachIndexed { idx, label ->
                                FilterChip(
                                    selected = chargerTypeIndex == idx,
                                    onClick = { chargerTypeIndex = idx },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
            is TriggerConfig.WiFi -> {
                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = { Text("SSID (leave blank for any)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            is TriggerConfig.Bluetooth -> {
                OutlinedTextField(
                    value = bluetoothAddress,
                    onValueChange = { bluetoothAddress = it },
                    label = { Text("Device address (leave blank for any)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            is TriggerConfig.AirplaneMode -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trigger when airplane mode is ON")
                        Switch(checked = airplaneEnabled, onCheckedChange = { airplaneEnabled = it })
                    }
                }
            }
            is TriggerConfig.DoNotDisturb -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Do Not Disturb trigger", style = MaterialTheme.typography.bodyMedium)
                        Text("Fires on any DND interruption filter change.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            is TriggerConfig.Geofence -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Geofence trigger", style = MaterialTheme.typography.bodyMedium)

                        // Name field
                        OutlinedTextField(
                            value = geofenceName,
                            onValueChange = { geofenceName = it },
                            label = { Text("Location name (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Lat/Lon text fields
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = if (geofenceLatitude == 0.0) "" else geofenceLatitude.toString(),
                                onValueChange = { geofenceLatitude = it.toDoubleOrNull() ?: 0.0 },
                                label = { Text("Latitude") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = if (geofenceLongitude == 0.0) "" else geofenceLongitude.toString(),
                                onValueChange = { geofenceLongitude = it.toDoubleOrNull() ?: 0.0 },
                                label = { Text("Longitude") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        // Map picker (tap to set)
                        OsmMapPicker(
                            latitude = geofenceLatitude,
                            longitude = geofenceLongitude,
                            radiusMeters = geofenceRadiusMeters,
                            onLocationSelected = { lat, lng ->
                                geofenceLatitude = lat
                                geofenceLongitude = lng
                            }
                        )

                        Text("Radius: ${geofenceRadiusMeters.toInt()} m", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = geofenceRadiusMeters,
                            onValueChange = { geofenceRadiusMeters = it },
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
                                    selected = geofenceTransitionType == type,
                                    onClick = { geofenceTransitionType = type },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (geofenceTransitionType == GeofenceTransition.DWELL) {
                            Text("Dwell delay: ${geofenceDwellDelay}s", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = geofenceDwellDelay.toFloat(),
                                onValueChange = { geofenceDwellDelay = it.toInt() },
                                valueRange = 0f..300f,
                                steps = 29,
                            )
                        }
                    }
                }
            }
            is TriggerConfig.AlarmStopped -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Alarm Stopped", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fires when a GemmaWorkflow alarm is dismissed or cancelled by the user.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = alarmStoppedType,
                            onValueChange = { alarmStoppedType = it },
                            label = { Text("Alarm type filter (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. default, reminder, timer") }
                        )
                    }
                }
            }
            is TriggerConfig.AppOpened, is TriggerConfig.AppClosed -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (currentTriggerConfig is TriggerConfig.AppOpened) "App Opened" else "App Closed",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = appTriggerPatterns.joinToString(", "),
                            onValueChange = {
                                appTriggerPatterns = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                            },
                            label = { Text("App package patterns (comma-separated)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. com.instagram.android") }
                        )
                        Text("Trigger on:", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = appTriggerOnOpen,
                                onClick = { appTriggerOnOpen = !appTriggerOnOpen },
                                label = { Text("Open") }
                            )
                            FilterChip(
                                selected = appTriggerOnClose,
                                onClick = { appTriggerOnClose = !appTriggerOnClose },
                                label = { Text("Close") }
                            )
                        }
                    }
                }
            }
            is TriggerConfig.SmsReceived -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SMS Received", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fires when an SMS matching the patterns is received. Requires notification access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = smsSenderPattern,
                            onValueChange = { smsSenderPattern = it },
                            label = { Text("Sender pattern (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. +1 or ABC") }
                        )
                        OutlinedTextField(
                            value = smsBodyPattern,
                            onValueChange = { smsBodyPattern = it },
                            label = { Text("Body pattern (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. OTP or verification") }
                        )
                    }
                }
            }
            is TriggerConfig.NotificationListenerConfig -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("App Notification", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fires on notifications from specific apps. Requires notification access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = notifAppPatterns.joinToString(", "),
                            onValueChange = {
                                notifAppPatterns = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                            },
                            label = { Text("App package patterns (comma-separated)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. com.whatsapp, org.telegram") }
                        )
                        OutlinedTextField(
                            value = notifSenderPattern,
                            onValueChange = { notifSenderPattern = it },
                            label = { Text("Sender pattern (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = notifBodyPattern,
                            onValueChange = { notifBodyPattern = it },
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
                                checked = notifTriggerOnDismiss,
                                onCheckedChange = { notifTriggerOnDismiss = it }
                            )
                        }
                    }
                }
            }
            is TriggerConfig.EmailReceived -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Email Received", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fires on email notifications. Requires notification access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = emailSenderPattern,
                            onValueChange = { emailSenderPattern = it },
                            label = { Text("From / sender pattern (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. @company.com") }
                        )
                        OutlinedTextField(
                            value = emailSubjectPattern,
                            onValueChange = { emailSubjectPattern = it },
                            label = { Text("Subject pattern (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. Invoice or Urgent") }
                        )
                        OutlinedTextField(
                            value = emailAppPackage,
                            onValueChange = { emailAppPackage = it },
                            label = { Text("Email app package") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
            is TriggerConfig.SleepProxy -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sleep Proxy", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fires when bedtime conditions are met (DND active, charger disconnected).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = String.format("%02d:%02d", sleepStartHour, sleepStartMinute),
                                onValueChange = {
                                    val parts = it.split(":")
                                    sleepStartHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: sleepStartHour
                                    sleepStartMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: sleepStartMinute
                                },
                                label = { Text("Start") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = String.format("%02d:%02d", sleepEndHour, sleepEndMinute),
                                onValueChange = {
                                    val parts = it.split(":")
                                    sleepEndHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: sleepEndHour
                                    sleepEndMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: sleepEndMinute
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
                                checked = sleepRequireChargerDisconnected,
                                onCheckedChange = { sleepRequireChargerDisconnected = it }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Require DND active")
                            Switch(
                                checked = sleepRequireDndActive,
                                onCheckedChange = { sleepRequireDndActive = it }
                            )
                        }
                    }
                }
            }
            is TriggerConfig.Nfc -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NFC Tag", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fires when a specific NFC tag is scanned. Requires NFC enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = nfcTagId,
                            onValueChange = { nfcTagId = it },
                            label = { Text("Tag ID (leave blank for any)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
            is TriggerConfig.ShareSheet -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Share Sheet", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Fires when content is shared to GemmaWorkflow from any app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        val stateLabel = when (shareSheetState) {
                            SetupState.Ready -> "Ready"
                            SetupState.NeedsSetup -> "Needs setup"
                            SetupState.Unsupported -> "Unsupported"
                        }
                        Text("Status: $stateLabel", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> {}
        }

        HorizontalDivider()

        // ── Actions ───────────────────────────────────────────────────────
        Text("Actions", style = MaterialTheme.typography.titleMedium)

        Text(
            text = "${steps.size} step${if (steps.size != 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        steps.forEachIndexed { index, step ->
            val spec = ActionSpecRegistry.find(step.id)
            val stepValid = isStepValid(step)
            ActionStepCard(
                step = step,
                stepNumber = index + 1,
                label = spec?.label ?: step.id,
                isValid = spec != null && stepValid,
                onEdit = { editingStepIndex = index },
                onDelete = {
                    if (steps.size > 1) {
                        steps = steps.toMutableList().apply { removeAt(index) }
                        if (editingStepIndex == index) editingStepIndex = -1
                    }
                }
            )
        }

        OutlinedButton(
            onClick = {
                steps = steps + WorkflowStep(id = "browser.open_url", params = buildJsonObject { put("url", "") })
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Action")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Save / Cancel ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val trigger = buildTrigger(
                        triggerType = "Trigger" to currentTriggerConfig,
                        timeHour = timePickerState.hour,
                        timeMinute = timePickerState.minute,
                        repeatMode = repeatMode,
                        selectedDays = selectedDays,
                        batteryLevel = batteryLevel,
                        batteryCondition = batteryCondition,
                        chargerTypeIndex = chargerTypeIndex,
                        wifiSsid = wifiSsid.ifBlank { null },
                        bluetoothAddress = bluetoothAddress.ifBlank { null },
                        airplaneEnabled = airplaneEnabled,
                        geofenceLatitude = geofenceLatitude,
                        geofenceLongitude = geofenceLongitude,
                        geofenceRadiusMeters = geofenceRadiusMeters,
                        geofenceTransitionType = geofenceTransitionType,
                        geofenceName = geofenceName,
                        geofenceDwellDelay = geofenceDwellDelay,
                        alarmStoppedType = alarmStoppedType,
                        appTriggerPatterns = appTriggerPatterns,
                        appTriggerOnOpen = appTriggerOnOpen,
                        appTriggerOnClose = appTriggerOnClose,
                        smsSenderPattern = smsSenderPattern,
                        smsBodyPattern = smsBodyPattern,
                        notifAppPatterns = notifAppPatterns,
                        notifSenderPattern = notifSenderPattern,
                        notifBodyPattern = notifBodyPattern,
                        notifTriggerOnDismiss = notifTriggerOnDismiss,
                        emailSenderPattern = emailSenderPattern,
                        emailSubjectPattern = emailSubjectPattern,
                        emailAppPackage = emailAppPackage,
                        sleepStartHour = sleepStartHour,
                        sleepStartMinute = sleepStartMinute,
                        sleepEndHour = sleepEndHour,
                        sleepEndMinute = sleepEndMinute,
                        sleepRequireChargerDisconnected = sleepRequireChargerDisconnected,
                        sleepRequireDndActive = sleepRequireDndActive,
                        nfcTagId = nfcTagId,
                        shareSheetState = shareSheetState
                    )
                    onSave(
                        PlannedWorkflow(
                            name = name.ifBlank { "Untitled" },
                            summary = summary,
                            trigger = trigger,
                            actions = steps
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank() && steps.isNotEmpty()
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ── Action editor dialog ─────────────────────────────────────────────
    if (editingStepIndex >= 0) {
        ActionEditDialog(
            step = steps[editingStepIndex],
            onSave = { updated ->
                steps = steps.toMutableList().apply { set(editingStepIndex, updated) }
                editingStepIndex = -1
            },
            onDismiss = { editingStepIndex = -1 }
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
                            color = MaterialTheme.colorScheme.outline
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
                                                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
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
            Button(
                onClick = {
                    val json = buildJsonObject {
                        params.forEach { (k, v) -> put(k, v) }
                    }
                    onSave(WorkflowStep(id = selectedActionId, params = json))
                },
                enabled = canSave
            ) {
                Text("Save")
            }
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
            com.gemmaworkflow.domain.catalog.ParamType.Url -> {
                if (!value.startsWith("http://") && !value.startsWith("https://") && !value.startsWith("content://")) {
                    errors.add(ActionValidationError(param.name, "Must be a valid URL (http:// or https://)"))
                }
            }
            com.gemmaworkflow.domain.catalog.ParamType.Int -> {
                if (value.toIntOrNull() == null) {
                    errors.add(ActionValidationError(param.name, "Must be a whole number"))
                }
            }
            com.gemmaworkflow.domain.catalog.ParamType.Long,
            com.gemmaworkflow.domain.catalog.ParamType.DateTimeMillis -> {
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

private fun buildTrigger(
    triggerType: Pair<String, TriggerConfig>,
    timeHour: Int,
    timeMinute: Int,
    repeatMode: Int,
    selectedDays: Set<Int>,
    batteryLevel: Int,
    batteryCondition: BatteryCondition,
    chargerTypeIndex: Int,
    wifiSsid: String?,
    bluetoothAddress: String?,
    airplaneEnabled: Boolean,
    geofenceLatitude: Double,
    geofenceLongitude: Double,
    geofenceRadiusMeters: Float,
    geofenceTransitionType: GeofenceTransition,
    geofenceName: String,
    geofenceDwellDelay: Int,
    alarmStoppedType: String,
    appTriggerPatterns: List<String>,
    appTriggerOnOpen: Boolean,
    appTriggerOnClose: Boolean,
    smsSenderPattern: String,
    smsBodyPattern: String,
    notifAppPatterns: List<String>,
    notifSenderPattern: String,
    notifBodyPattern: String,
    notifTriggerOnDismiss: Boolean,
    emailSenderPattern: String,
    emailSubjectPattern: String,
    emailAppPackage: String,
    sleepStartHour: Int,
    sleepStartMinute: Int,
    sleepEndHour: Int,
    sleepEndMinute: Int,
    sleepRequireChargerDisconnected: Boolean,
    sleepRequireDndActive: Boolean,
    nfcTagId: String,
    shareSheetState: SetupState
): TriggerConfig {
    return when (triggerType.second) {
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
        is TriggerConfig.WiFi -> TriggerConfig.WiFi(wifiSsid)
        is TriggerConfig.Bluetooth -> TriggerConfig.Bluetooth(bluetoothAddress)
        is TriggerConfig.AirplaneMode -> TriggerConfig.AirplaneMode(airplaneEnabled)
        is TriggerConfig.DoNotDisturb -> TriggerConfig.DoNotDisturb(null)
        is TriggerConfig.Geofence -> TriggerConfig.Geofence(
            geofenceLatitude, geofenceLongitude, geofenceRadiusMeters,
            geofenceTransitionType, geofenceDwellDelay,
            geofenceName.ifBlank { null }
        )
        is TriggerConfig.AlarmStopped -> TriggerConfig.AlarmStopped(
            alarmStoppedType.ifBlank { "default" }
        )
        is TriggerConfig.AppOpened -> TriggerConfig.AppOpened(
            appTriggerPatterns, appTriggerOnOpen, appTriggerOnClose
        )
        is TriggerConfig.AppClosed -> TriggerConfig.AppClosed(
            appTriggerPatterns, appTriggerOnOpen, appTriggerOnClose
        )
        is TriggerConfig.SmsReceived -> TriggerConfig.SmsReceived(
            smsSenderPattern.ifBlank { null },
            smsBodyPattern.ifBlank { null }
        )
        is TriggerConfig.NotificationListenerConfig -> TriggerConfig.NotificationListenerConfig(
            notifAppPatterns,
            notifSenderPattern.ifBlank { null },
            notifBodyPattern.ifBlank { null },
            notifTriggerOnDismiss
        )
        is TriggerConfig.EmailReceived -> TriggerConfig.EmailReceived(
            emailSenderPattern.ifBlank { null },
            emailSubjectPattern.ifBlank { null },
            emailAppPackage
        )
        is TriggerConfig.SleepProxy -> TriggerConfig.SleepProxy(
            sleepStartHour, sleepStartMinute,
            sleepEndHour, sleepEndMinute,
            sleepRequireChargerDisconnected, sleepRequireDndActive
        )
        is TriggerConfig.Nfc -> TriggerConfig.Nfc(nfcTagId.ifBlank { null })
        is TriggerConfig.ShareSheet -> TriggerConfig.ShareSheet(shareSheetState)
        else -> TriggerConfig.Manual
    }
}