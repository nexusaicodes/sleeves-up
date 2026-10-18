package com.checkin.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.ui.theme.LocalTimePatterns
import com.checkin.app.util.TimeFormat

/**
 * A habit's reminder hour as the user reads it: "Around 10:00 AM". One function for every place the
 * hour is shown, so none can come to describe the same setting differently.
 */
@Composable
fun reminderTimeSummary(hour: Int): String =
    stringResource(R.string.reminder_time_value, TimeFormat.hourOfDay(hour, LocalTimePatterns.current))

/**
 * Picks a habit's reminder hour from [NudgeSchedule.hourRange]. Whole hours only: delivery is
 * inexact, so a minute the user chose would be a precision the alarm cannot honour.
 *
 * Shared by every place a habit's hour is chosen — the naming step and the habit editor —
 * because all are the same choice and a second copy is how one would come to offer hours another
 * refuses.
 *
 * A pick commits and closes in one tap, the way a radio list in a dialog reads; there is no confirm
 * button to miss, and dismissing changes nothing.
 */
@Composable
fun ReminderHourPicker(selectedHour: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val hours = NudgeSchedule.hourRange.toList()
    val listState = rememberLazyListState(
        // Opens with the current choice in view rather than at 5 AM, a row above it for context.
        initialFirstVisibleItemIndex = (hours.indexOf(selectedHour) - 1).coerceAtLeast(0),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_dialog_title)) },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .selectableGroup(),
            ) {
                items(hours) { hour ->
                    HourRow(hour = hour, selected = hour == selectedHour, onClick = { onPicked(hour) })
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminder_time_cancel)) }
        },
    )
}

@Composable
private fun HourRow(hour: Int, selected: Boolean, onClick: () -> Unit) {
    val patterns = LocalTimePatterns.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            // 48dp minimum tap target, reached through padding so the row still grows with font scale.
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row carries the click and the role; a second clickable on the button would be a second
        // focus stop announcing the same choice.
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(text = TimeFormat.hourOfDay(hour, patterns), style = MaterialTheme.typography.bodyLarge)
    }
}
