package com.checkin.app.ui.habits

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adevinta.spark.ExperimentalSparkApi
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Ghost
import com.adevinta.spark.components.buttons.Secondary
import com.adevinta.spark.components.dialog.AlertDialog
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.components.textfields.FormFieldStatus
import com.adevinta.spark.components.textfields.TextField
import com.adevinta.spark.icons.ClockOutline
import com.adevinta.spark.icons.LeboncoinIcons
import com.checkin.app.R
import com.checkin.app.data.HabitRules
import com.checkin.app.data.NameProblem
import com.checkin.app.ui.components.ReminderTimePicker
import com.checkin.app.ui.components.reminderTimeSummary
import com.checkin.app.ui.theme.LocalAppTypography

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
    // The name is not capped at the input: one past the limit shows NameProblem.TOO_LONG as it is
    // typed, the same rule the repository applies, rather than being cut short without a word.
    val shownProblem = problem?.takeIf { showProblem }?.let { problemText(it) }
    val keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
    val keyboardActions = KeyboardActions(onDone = { onDone() })
    Column(
        modifier = modifier,
        // Centred under the naming step's centred title field; start-aligned in a dialog, under its
        // start-aligned field.
        horizontalAlignment = if (placeholder == null) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (placeholder == null) {
            TextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.habit_name_label),
                state = if (shownProblem != null) FormFieldStatus.Error else null,
                stateMessage = shownProblem,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TitleNameField(
                name = name,
                onNameChange = onNameChange,
                placeholder = placeholder,
                problem = shownProblem,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
            )
        }
        // The label is only the time it shows, so the action names what a tap does.
        val changeLabel = stringResource(R.string.reminder_time_change)
        // Sized to its text rather than the field above it: a full-width bar reads as a second
        // primary action beside Continue, where this is a setting with a sensible default.
        Button.Secondary(
            onClick = { picking = true },
            text = reminderTimeSummary(minute),
            modifier = Modifier.semantics { onClick(label = changeLabel, action = null) },
            icon = LeboncoinIcons.ClockOutline,
        )
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

/**
 * The name drawn as a title being written: large, centred, borderless but for its underline, with
 * the placeholder standing in for the label. Spark's text field fixes its input to `body1` and
 * aligns it to the start, so this one is the app's own, on [BasicTextField] in Spark's colours: the
 * underline is `outline` at rest, `main` while focused and `error` with a [problem], which shows
 * beneath it.
 */
@Composable
private fun TitleNameField(
    name: String,
    onNameChange: (String) -> Unit,
    placeholder: String,
    problem: String?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
) {
    val label = stringResource(R.string.habit_name_label)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val titleStyle = LocalAppTypography.current.headlineSmall.copy(
        textAlign = TextAlign.Center,
        color = SparkTheme.colors.onSurface,
    )
    val lineColor = when {
        problem != null -> SparkTheme.colors.error
        focused -> SparkTheme.colors.main
        else -> SparkTheme.colors.outline
    }
    val lineWidth = if (focused || problem != null) FOCUSED_LINE else REST_LINE
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            textStyle = titleStyle,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(SparkTheme.colors.main),
            // The placeholder changes as examples cycle, so the field is named by what it holds.
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (problem != null) error(problem)
                },
            decorationBox = { input ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val y = size.height - lineWidth.toPx() / 2
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), lineWidth.toPx())
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Faint and crossfading, so a cycling example never reads as a name already typed.
                    if (name.isEmpty()) {
                        Crossfade(
                            targetState = placeholder,
                            animationSpec = tween(PLACEHOLDER_FADE_MS),
                            label = "placeholder",
                        ) {
                            Text(
                                text = it,
                                style = titleStyle,
                                color = SparkTheme.colors.onBackgroundVariant.copy(alpha = PLACEHOLDER_ALPHA),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    input()
                }
            },
        )
        if (problem != null) {
            Text(
                text = problem,
                style = SparkTheme.typography.caption,
                color = SparkTheme.colors.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
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
 *
 * [onSave] reports back through its third argument: null once the write has landed, which is the
 * caller's cue to close the dialog, or the problem the write was refused for, which the dialog then
 * shows until the name is edited. Save does nothing while a write is outstanding, so a double tap
 * cannot create the habit twice.
 */
@OptIn(ExperimentalSparkApi::class)
@Composable
fun HabitEditorDialog(
    title: String,
    initialName: String,
    initialMinute: Int,
    nameProblem: (String) -> NameProblem?,
    onSave: (name: String, minute: Int, onResult: (NameProblem?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var minute by rememberSaveable { mutableIntStateOf(initialMinute) }
    var showProblem by rememberSaveable { mutableStateOf(false) }
    var refused by rememberSaveable { mutableStateOf<NameProblem?>(null) }
    var saving by remember { mutableStateOf(false) }
    val problem = refused ?: nameProblem(name)
    val save = {
        showProblem = true
        if (problem == null && !saving) {
            saving = true
            onSave(name, minute) { result ->
                saving = false
                refused = result
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = LocalAppTypography.current.headlineSmall) },
        text = {
            HabitFields(
                name = name,
                onNameChange = {
                    name = it
                    refused = null
                    showProblem = true
                },
                minute = minute,
                onMinuteChange = { minute = it },
                problem = problem,
                showProblem = showProblem,
                onDone = save,
            )
        },
        confirmButton = { Button.Ghost(onClick = save, text = stringResource(R.string.habit_save)) },
        dismissButton = { Button.Ghost(onClick = onDismiss, text = stringResource(R.string.action_cancel)) },
    )
}

private const val PLACEHOLDER_ALPHA = 0.35f
private const val PLACEHOLDER_FADE_MS = 400
private val REST_LINE = 1.dp
private val FOCUSED_LINE = 2.dp
