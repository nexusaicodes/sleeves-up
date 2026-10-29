package com.checkin.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Ghost
import com.adevinta.spark.components.text.Text
import com.checkin.app.R
import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.ui.theme.LocalTimePatterns
import com.checkin.app.ui.theme.typographyFor
import com.checkin.app.util.TimeFormat

/**
 * A habit's reminder time as the user reads it: "Around 10:15 AM". One function for every place the
 * time is shown, so none can come to describe the same setting differently.
 */
@Composable
fun reminderTimeSummary(minute: Int): String =
    stringResource(R.string.reminder_time_value, TimeFormat.timeOfDay(minute, LocalTimePatterns.current))

/**
 * Picks a habit's reminder time on a clock, to the minute, within [NudgeSchedule.minuteRange].
 *
 * The minute is kept as chosen rather than rounded. The alarm is inexact, but the platform bounds
 * how inexact: `AlarmManagerService.maxTriggerTime` caps a one-off inexact alarm's window at an hour
 * past its trigger, and it never fires early — so a reminder lands at or after the minute picked,
 * which "Around" covers, and rounding would only discard a choice the user could see themselves make.
 *
 * A time outside the range disables the confirm button and says why, rather than being clamped on
 * confirm: a reminder silently moved from 22:30 to 21:00 is a setting the user did not choose.
 *
 * Shared by every place a habit's time is chosen — the naming step and the habit editor — because all
 * are the same choice and a second copy is how one would come to offer times another refuses.
 *
 * Spark has no time picker, so this stays Material 3 inside a local [MaterialTheme] carrying the
 * app's M3 type: Spark's bridge maps `titleMedium` to a 32sp bold display style and `headlineSmall` to
 * a bold one, which would oversize the picker's digits and the dialog's title. The bridge also folds
 * every `surfaceContainer` role into the page colour, so the dial and the unselected time fields are
 * given the raised paper explicitly; the dialog itself stays on the page, which keeps the out-of-range
 * line in vermilion off a raised surface, where dark mode's would fall short of 4.5:1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePicker(selectedMinute: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = selectedMinute / MINUTES_PER_HOUR,
        initialMinute = selectedMinute % MINUTES_PER_HOUR,
    )
    val picked = state.hour * MINUTES_PER_HOUR + state.minute
    val inRange = picked in NudgeSchedule.minuteRange
    val patterns = LocalTimePatterns.current
    val typography = remember(patterns.locale) { typographyFor(patterns.locale) }
    MaterialTheme(typography = typography) {
        PickerDialog(state, inRange, onConfirm = { onPicked(picked) }, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerDialog(state: TimePickerState, inRange: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val patterns = LocalTimePatterns.current
    val raised = SparkTheme.colors.backgroundVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = raised,
                        timeSelectorUnselectedContainerColor = raised,
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.reminder_time_range,
                        TimeFormat.timeOfDay(NudgeSchedule.minuteRange.first, patterns),
                        TimeFormat.timeOfDay(NudgeSchedule.minuteRange.last, patterns),
                    ),
                    style = SparkTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    color = if (inRange) SparkTheme.colors.onBackgroundVariant else SparkTheme.colors.error,
                )
            }
        },
        confirmButton = {
            Button.Ghost(
                onClick = onConfirm,
                text = stringResource(R.string.reminder_time_confirm),
                enabled = inRange,
            )
        },
        dismissButton = {
            Button.Ghost(onClick = onDismiss, text = stringResource(R.string.action_cancel))
        },
    )
}

private const val MINUTES_PER_HOUR = 60
