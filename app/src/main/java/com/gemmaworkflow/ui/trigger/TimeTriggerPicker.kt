package com.gemmaworkflow.ui.trigger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.gemmaworkflow.domain.model.TriggerConfig

private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/**
 * Human-readable time label for a Time trigger.
 */
fun formatTimeLabel(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, period)
}

/**
 * Human-readable repeat label, e.g. "Mon, Wed, Fri" or "Daily" or "Once".
 */
fun formatRepeatLabel(repeatDays: List<Int>): String = when {
    repeatDays.isEmpty() -> "Once"
    repeatDays.size == 7 -> "Daily"
    repeatDays.sorted() == listOf(java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY) -> "Weekdays"
    repeatDays.sorted() == listOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY) -> "Weekends"
    else -> repeatDays.sorted().joinToString(", ") { dayIndex ->
        DAY_LABELS.getOrElse(dayIndex - 1) { "" }
    }
}

/**
 * Full trigger summary string for display.
 */
fun formatTriggerSummary(trigger: TriggerConfig.Time): String {
    return "${formatTimeLabel(trigger.hour, trigger.minute)} \u2022 ${formatRepeatLabel(trigger.repeatDays)}"
}

/**
 * A chip that shows the current time trigger and opens the picker dialog on tap.
 */
@Composable
fun TimeTriggerChip(
    trigger: TriggerConfig.Time,
    onTriggerChanged: (TriggerConfig.Time) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(formatTriggerSummary(trigger))
    }

    if (showDialog) {
        TimeTriggerPickerDialog(
            initialTrigger = trigger,
            onDismiss = { showDialog = false },
            onConfirm = { newTrigger ->
                onTriggerChanged(newTrigger)
                showDialog = false
            }
        )
    }
}

/**
 * Dialog containing the full time + repeat-days picker.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimeTriggerPickerDialog(
    initialTrigger: TriggerConfig.Time,
    onDismiss: () -> Unit,
    onConfirm: (TriggerConfig.Time) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialTrigger.hour) }
    var minute by remember { mutableIntStateOf(initialTrigger.minute) }
    var selectedDays by remember { mutableStateOf(initialTrigger.repeatDays.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Time Trigger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Time picker — hour/minute derived from timeState, not mutated separately.
                val timeState = rememberTimePickerState(
                    initialHour = hour,
                    initialMinute = minute,
                    is24Hour = false
                )

                // Sync timeState back to outer scope hour/minute on user interaction.
                LaunchedEffect(timeState.hour, timeState.minute) {
                    hour = timeState.hour
                    minute = timeState.minute
                }

                TimePicker(state = timeState)

                HorizontalDivider()

                Text("Repeat", style = MaterialTheme.typography.labelMedium)

                // Day-of-week chips.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val dayConstant = index + 1 // Calendar.SUNDAY = 1
                        val isSelected = dayConstant in selectedDays
                        DayChip(
                            label = label,
                            selected = isSelected,
                            onClick = {
                                selectedDays = if (isSelected) {
                                    selectedDays - dayConstant
                                } else {
                                    selectedDays + dayConstant
                                }
                            }
                        )
                    }
                }

                // Quick-select buttons.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { selectedDays = emptySet() }
                    ) { Text("Once") }
                    TextButton(
                        onClick = {
                            selectedDays = setOf(
                                java.util.Calendar.MONDAY,
                                java.util.Calendar.TUESDAY,
                                java.util.Calendar.WEDNESDAY,
                                java.util.Calendar.THURSDAY,
                                java.util.Calendar.FRIDAY
                            )
                        }
                    ) { Text("Weekdays") }
                    TextButton(
                        onClick = {
                            selectedDays = setOf(
                                java.util.Calendar.SATURDAY,
                                java.util.Calendar.SUNDAY
                            )
                        }
                    ) { Text("Weekends") }
                    TextButton(
                        onClick = {
                            selectedDays = (1..7).toSet()
                        }
                    ) { Text("Daily") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val repeatDays = if (selectedDays.isEmpty()) {
                        emptyList()
                    } else {
                        selectedDays.toList().sorted()
                    }
                    onConfirm(TriggerConfig.Time(hour, minute, repeatDays))
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
