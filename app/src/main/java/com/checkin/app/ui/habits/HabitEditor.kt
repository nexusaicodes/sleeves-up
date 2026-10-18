package com.checkin.app.ui.habits

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.HabitRules
import com.checkin.app.data.NameProblem
import com.checkin.app.ui.components.ReminderTimePicker
import com.checkin.app.ui.components.reminderTimeSummary

/**
 * A habit's name and reminder time, as one pair of fields. Every place a habit is created or named —
 * the editor dialog and the first-run naming step — draws these, so a name cannot be accepted
 * in one place and refused in another, and the time is always chosen beside the name it belongs to.
 *
 * [problem] is shown only once [showProblem] is set — after an attempt to save, or an edit — so a
 * field opened empty does not greet the user with an error for something they have not done yet.
 *
 * A [placeholder] draws the name as a title being written rather than a form field — large,
 * centred, borderless but for its underline, with the placeholder standing in for the label — for a
 * screen that is about nothing else. Without one it is the plain labelled field a dialog wants.
 */
@Composable
fun HabitFields(
    name: String,
    onNameChange: (String) -> Unit,
    minute: Int,
    onMinuteChange: (Int) -> Unit,
    problem: NameProblem?,
    showProblem: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // Capped at the input rather than only refused at save, so the counter the user is typing
    // against and the rule the repository applies are the same number.
    val onValueChange = { value: String -> onNameChange(value.take(HabitRules.MAX_NAME_LENGTH)) }
    val isError = showProblem && problem != null
    val supportingText: (@Composable () -> Unit)? = (problem?.takeIf { showProblem })?.let { { Text(problemText(it)) } }
    val keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
    val keyboardActions = KeyboardActions(onDone = { onDone() })
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (placeholder == null) {
            OutlinedTextField(
                value = name,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.habit_name_label)) },
                singleLine = true,
                isError = isError,
                supportingText = supportingText,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val label = stringResource(R.string.habit_name_label)
            val titleStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)
            TextField(
                value = name,
                onValueChange = onValueChange,
                textStyle = titleStyle,
                // Faint and crossfading, so a cycling example never reads as a name already typed.
                placeholder = {
                    Crossfade(
                        targetState = placeholder,
                        animationSpec = tween(PLACEHOLDER_FADE_MS),
                        label = "placeholder",
                    ) {
                        Text(
                            text = it,
                            style = titleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                singleLine = true,
                isError = isError,
                supportingText = supportingText,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                ),
                // The placeholder changes as examples cycle, so the field is named by what it holds.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            )
        }
        // The label is only the time it shows, so the action names what a tap does.
        val changeLabel = stringResource(R.string.reminder_time_change)
        // Sized to its text rather than the field above it: a full-width bar reads as a second
        // primary action beside Continue, where this is a setting with a sensible default.
        FilledTonalButton(
            onClick = { picking = true },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.semantics { onClick(label = changeLabel, action = null) },
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(reminderTimeSummary(minute))
        }
    }
    if (picking) {
        ReminderTimePicker(
            selectedMinute = minute,
            onPicked = {
                picking = false
                onMinuteChange(it)
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
    initialMinute: Int,
    nameProblem: (String) -> NameProblem?,
    onSave: (name: String, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var minute by rememberSaveable { mutableIntStateOf(initialMinute) }
    var showProblem by rememberSaveable { mutableStateOf(false) }
    val problem = nameProblem(name)
    val save = {
        showProblem = true
        if (problem == null) onSave(name, minute)
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
                minute = minute,
                onMinuteChange = { minute = it },
                problem = problem,
                showProblem = showProblem,
                onDone = save,
            )
        },
        confirmButton = { TextButton(onClick = save) { Text(stringResource(R.string.habit_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) } },
    )
}

private const val PLACEHOLDER_ALPHA = 0.35f
private const val PLACEHOLDER_FADE_MS = 400
