package com.gemmaworkflow.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gemmaworkflow.domain.model.TriggerConfig

/**
 * Days of the week represented as Java Calendar day constants.
 * SUNDAY=1, MONDAY=2, … SATURDAY=7
 */
private val DAYS_OF_WEEK = listOf(
    1 to "Sun",
    2 to "Mon",
    3 to "Tue",
    4 to "Wed",
    5 to "Thu",
    6 to "Fri",
    7 to "Sat"
)

/** Repeat mode for the time trigger. */
private enum class RepeatMode {
    OneTime,
    Daily,
    Weekdays,
    Weekends,
    Custom
}

/**
 * Composable for configuring a [TriggerConfig.Time] trigger.
 *
 * Lets the user pick:
 * - A time (hour + minute)
 * - A repeat mode: one-time, daily, weekdays, weekends, or specific days
 *
 * Calls [onSave] with the configured [TriggerConfig.Time] when the user confirms.
 * Calls [onCancel] to go back without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTriggerSetupScreen(
    initialTrigger: TriggerConfig.Time?,
    onSave: (TriggerConfig.Time) -> Unit,
    onCancel: () -> Unit
) {
    // Initialise from existing trigger if available.
    val initialHour = initialTrigger?.hour ?: 9
    val initialMinute = initialTrigger?.minute ?: 0
    val initialRepeatDays = initialTrigger?.repeatDays ?: emptyList()

    val initialRepeatMode = when {
        initialRepeatDays.isEmpty() -> RepeatMode.OneTime
        initialRepeatDays == listOf(2, 3, 4, 5, 6) -> RepeatMode.Weekdays
        initialRepeatDays == listOf(1, 7) -> RepeatMode.Weekends
        initialRepeatDays == (1..7).toList() -> RepeatMode.Daily
        else -> RepeatMode.Custom
    }

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    var repeatMode by remember { mutableStateOf(initialRepeatMode) }
    var selectedDays by remember {
        mutableStateOf(initialRepeatDays.toSet())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Schedule Time Trigger", style = MaterialTheme.typography.headlineSmall)

        // Time picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Time", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TimePicker(state = timePickerState)
            }
        }

        // Repeat mode selector
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Repeat", style = MaterialTheme.typography.titleMedium)

                Column(modifier = Modifier.selectableGroup()) {
                    RepeatMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .selectable(
                                    selected = repeatMode == mode,
                                    onClick = { repeatMode = mode },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = repeatMode == mode,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (mode) {
                                    RepeatMode.OneTime -> "One time"
                                    RepeatMode.Daily -> "Every day"
                                    RepeatMode.Weekdays -> "Weekdays (Mon–Fri)"
                                    RepeatMode.Weekends -> "Weekends (Sat–Sun)"
                                    RepeatMode.Custom -> "Specific days"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Day checkboxes when Custom is selected
                if (repeatMode == RepeatMode.Custom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select days:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DAYS_OF_WEEK.forEach { (dayConstant, label) ->
                            DayChip(
                                label = label,
                                selected = dayConstant in selectedDays,
                                onClick = {
                                    selectedDays = if (dayConstant in selectedDays) {
                                        selectedDays - dayConstant
                                    } else {
                                        selectedDays + dayConstant
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Preview
        val repeatSummary = when (repeatMode) {
            RepeatMode.OneTime -> "One time"
            RepeatMode.Daily -> "Every day"
            RepeatMode.Weekdays -> "Weekdays (Mon–Fri)"
            RepeatMode.Weekends -> "Weekends (Sat–Sun)"
            RepeatMode.Custom -> if (selectedDays.isEmpty()) {
                "No days selected"
            } else {
                val dayLabels = DAYS_OF_WEEK
                    .filter { (d, _) -> d in selectedDays }
                    .sortedBy { (d, _) -> d }
                    .joinToString { (_, l) -> l }
                "Custom: $dayLabels"
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Summary", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Time: %02d:%02d".format(timePickerState.hour, timePickerState.minute),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Repeat: $repeatSummary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                    val repeatDays = when (repeatMode) {
                        RepeatMode.OneTime -> emptyList()
                        RepeatMode.Daily -> (1..7).toList()
                        RepeatMode.Weekdays -> listOf(2, 3, 4, 5, 6)
                        RepeatMode.Weekends -> listOf(1, 7)
                        RepeatMode.Custom -> selectedDays.sorted()
                    }
                    onSave(
                        TriggerConfig.Time(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                            repeatDays = repeatDays
                        )
                    )
                },
                enabled = repeatMode != RepeatMode.Custom || selectedDays.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}
