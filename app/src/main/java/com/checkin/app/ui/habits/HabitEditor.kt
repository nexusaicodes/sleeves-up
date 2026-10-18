package com.checkin.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.HabitRules
import com.checkin.app.data.NameProblem
import com.checkin.app.ui.components.ReminderHourPicker
import com.checkin.app.ui.components.reminderTimeSummary

/**
 * A habit's name and reminder hour, as one pair of fields. Every place a habit is created or named —
 * the editor dialog, the tour, the first-run naming step — draws these, so a name cannot be accepted
 * in one place and refused in another, and the hour is always chosen beside the name it belongs to.
 *
 * [problem] is shown only once [showProblem] is set — after an attempt to save, or an edit — so a
 * field opened empty does not greet the user with an error for something they have not done yet.
 */
@Composable
fun HabitFields(
    name: String,
    onNameChange: (String) -> Unit,
    hour: Int,
    onHourChange: (Int) -> Unit,
    problem: NameProblem?,
    showProblem: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            // Capped at the input rather than only refused at save, so the counter the user is
            // typing against and the rule the repository applies are the same number.
            onValueChange = { onNameChange(it.take(HabitRules.MAX_NAME_LENGTH)) },
            label = { Text(stringResource(R.string.habit_name_label)) },
            singleLine = true,
            isError = showProblem && problem != null,
            supportingText = (problem?.takeIf { showProblem })?.let { { Text(problemText(it)) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
        // The label is only the time it shows, so the action names what a tap does.
        val changeLabel = stringResource(R.string.reminder_time_change)
        FilledTonalButton(
            onClick = { picking = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { onClick(label = changeLabel, action = null) },
        ) {
            Text(reminderTimeSummary(hour))
        }
    }
    if (picking) {
        ReminderHourPicker(
            selectedHour = hour,
            onPicked = {
                picking = false
                onHourChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun problemText(problem: NameProblem): String = when (problem) {
    NameProblem.BLANK -> stringResource(R.string.habit_name_blank)
    NameProblem.TOO_LONG ->
        pluralStringResource(R.plurals.habit_name_too_long, HabitRules.MAX_NAME_LENGTH, HabitRules.MAX_NAME_LENGTH)
    NameProblem.DUPLICATE -> stringResource(R.string.habit_name_duplicate)
}

/**
 * Creates or edits a habit in a dialog. [nameProblem] is asked on every edit and before saving; a
 * save it refuses keeps the dialog open with the reason shown.
 */
@Composable
fun HabitEditorDialog(
    title: String,
    initialName: String,
    initialHour: Int,
    nameProblem: (String) -> NameProblem?,
    onSave: (name: String, hour: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var hour by rememberSaveable { mutableIntStateOf(initialHour) }
    var showProblem by rememberSaveable { mutableStateOf(false) }
    val problem = nameProblem(name)
    val save = {
        showProblem = true
        if (problem == null) onSave(name, hour)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            HabitFields(
                name = name,
                onNameChange = {
                    name = it
                    showProblem = true
                },
                hour = hour,
                onHourChange = { hour = it },
                problem = problem,
                showProblem = showProblem,
                onDone = save,
            )
        },
        confirmButton = { TextButton(onClick = save) { Text(stringResource(R.string.habit_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) } },
    )
}
