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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.checkin.app.ui.theme.LocalAppTypography

/**
 * The state behind [HabitFields]: the typed name and chosen minute, whether a problem is shown yet,
 * a refusal from the write, and whether a save is outstanding. Built by [rememberHabitForm], so the
 * dialog and the naming step share one save rule rather than two copies of it.
 */
@Stable
class HabitForm internal constructor(
    private val nameState: MutableState<String>,
    private val minuteState: MutableIntState,
    private val showProblemState: MutableState<Boolean>,
    private val refusedState: MutableState<NameProblem?>,
    private val savingState: MutableState<Boolean>,
    private val nameProblem: State<(String) -> NameProblem?>,
    private val onSave: State<(name: String, minute: Int, onResult: (NameProblem?) -> Unit) -> Unit>,
) {
    val name: String get() = nameState.value

    var minute: Int
        get() = minuteState.intValue
        set(value) {
            minuteState.intValue = value
        }

    /** False until a save is attempted or the name is edited, so an empty field opens without an error. */
    val showProblem: Boolean get() = showProblemState.value

    /** A refusal from the write, shown until the name is edited, else the rule asked of the name as typed. */
    val problem: NameProblem? get() = refusedState.value ?: nameProblem.value(nameState.value)

    fun editName(value: String) {
        nameState.value = value
        refusedState.value = null
        showProblemState.value = true
    }

    /**
     * Saves when the name has no problem and no save is outstanding, so a double tap cannot create the
     * habit twice. The result comes back through `onSave`'s callback: null once the write has landed,
     * or the problem the write refused, which is then shown until the name is edited.
     */
    fun save() {
        showProblemState.value = true
        if (problem != null || savingState.value) return
        savingState.value = true
        onSave.value(nameState.value, minuteState.intValue) { result ->
            savingState.value = false
            refusedState.value = result
        }
    }
}

/**
 * A [HabitForm] whose fields survive a configuration change. [nameProblem] is asked on every read of
 * [HabitForm.problem], and [onSave] performs the write; both are read at the moment they are used.
 */
@Composable
fun rememberHabitForm(
    initialName: String,
    initialMinute: Int,
    nameProblem: (String) -> NameProblem?,
    onSave: (name: String, minute: Int, onResult: (NameProblem?) -> Unit) -> Unit,
): HabitForm {
    val name = rememberSaveable { mutableStateOf(initialName) }
    val minute = rememberSaveable { mutableIntStateOf(initialMinute) }
    val showProblem = rememberSaveable { mutableStateOf(false) }
    val refused = rememberSaveable { mutableStateOf<NameProblem?>(null) }
    val saving = remember { mutableStateOf(false) }
    val currentNameProblem = rememberUpdatedState(nameProblem)
    val currentOnSave = rememberUpdatedState(onSave)
    return remember { HabitForm(name, minute, showProblem, refused, saving, currentNameProblem, currentOnSave) }
}

/** How [HabitFields] draws the name. */
sealed interface HabitFieldsStyle {
    /** The plain labelled field a dialog wants, start-aligned. */
    data object Dialog : HabitFieldsStyle

    /**
     * The name as a title being written — large, centred, borderless but for its underline, with
     * [placeholder] standing in for the label — for a screen that is about nothing else.
     */
    data class Title(val placeholder: String) : HabitFieldsStyle
}

/**
 * A habit's name and reminder time, as one pair of fields. Every place a habit is created or named —
 * the editor dialog and the first-run naming step — draws these, so a name cannot be accepted
 * in one place and refused in another, and the time is always chosen beside the name it belongs to.
 *
 * The form's problem is shown only once [HabitForm.showProblem] is set — after an attempt to save, or
 * an edit — so a field opened empty does not greet the user with an error for something they have not
 * done yet. The keyboard's Done saves.
 */
@Composable
fun HabitFields(form: HabitForm, style: HabitFieldsStyle, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // The name is not capped at the input: one past the limit shows NameProblem.TOO_LONG as it is
    // typed, the same rule the repository applies, rather than being cut short without a word.
    val shownProblem = form.problem?.takeIf { form.showProblem }?.let { problemText(it) }
    val keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
    val keyboardActions = KeyboardActions(onDone = { form.save() })
    Column(
        modifier = modifier,
        // Centred under the naming step's centred title field; start-aligned in a dialog, under its
        // start-aligned field.
        horizontalAlignment = if (style is HabitFieldsStyle.Title) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (style) {
            HabitFieldsStyle.Dialog -> TextField(
                value = form.name,
                onValueChange = form::editName,
                label = stringResource(R.string.habit_name_label),
                state = if (shownProblem != null) FormFieldStatus.Error else null,
                stateMessage = shownProblem,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth(),
            )
            is HabitFieldsStyle.Title -> TitleNameField(
                name = form.name,
                onNameChange = form::editName,
                placeholder = style.placeholder,
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
            text = reminderTimeSummary(form.minute),
            modifier = Modifier.semantics { onClick(label = changeLabel, action = null) },
            icon = LeboncoinIcons.ClockOutline,
        )
    }
    if (picking) {
        ReminderTimePicker(
            selectedMinute = form.minute,
            onPicked = {
                picking = false
                form.minute = it
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
    val form = rememberHabitForm(initialName, initialMinute, nameProblem, onSave)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = LocalAppTypography.current.headlineSmall) },
        text = {
            HabitFields(form = form, style = HabitFieldsStyle.Dialog)
        },
        confirmButton = { Button.Ghost(onClick = form::save, text = stringResource(R.string.habit_save)) },
        dismissButton = { Button.Ghost(onClick = onDismiss, text = stringResource(R.string.action_cancel)) },
    )
}

private const val PLACEHOLDER_ALPHA = 0.35f
private const val PLACEHOLDER_FADE_MS = 400
private val REST_LINE = 1.dp
private val FOCUSED_LINE = 2.dp
