package com.irisapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irisapp.domain.model.*
import com.irisapp.platform.sound.YamnetClassifier
import com.irisapp.ui.components.GlassmorphicCard
import com.irisapp.ui.theme.*

// ── Trigger category ───────────────────────────────────────────────────────────

internal enum class TriggerCategory(val label: String, val icon: ImageVector, val color: Color) {
    MANUAL("Manual", Icons.Default.TouchApp, Color(0xFF5EF2FF)),
    TIME("Time & Schedule", Icons.Default.Schedule, Color(0xFFFFC15E)),
    POWER("Power & Battery", Icons.Default.BatteryChargingFull, Color(0xFF7CF0A8)),
    NETWORK("Network", Icons.Default.Wifi, Color(0xFF9EA9FF)),
    EVENT("Events", Icons.Default.Notifications, Color(0xFFFF6BD6)),
    SENSOR("Sensors & NFC", Icons.Default.Nfc, Color(0xFFB57BFF)),
}

// ── Trigger item with icon and category ───────────────────────────────────────

internal data class TriggerItem(
    val label: String,
    val config: TriggerConfig,
    val icon: ImageVector,
    val category: TriggerCategory
)

internal val TRIGGER_CATEGORIES: Map<TriggerCategory, List<TriggerItem>> = mapOf(
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

internal val ALL_TRIGGERS: List<TriggerItem> = TRIGGER_CATEGORIES.values.flatten()

private val CHARGER_TYPES = listOf("Any", "USB", "AC", "Wireless")

internal class TriggerEditorState(initialTrigger: TriggerConfig?) {
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

@Composable
internal fun TriggerSection(state: TriggerEditorState) {
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
                enter = expandVertically(),
                exit = shrinkVertically()
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

internal fun triggerConfigMatches(a: TriggerConfig, b: TriggerConfig): Boolean {
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
