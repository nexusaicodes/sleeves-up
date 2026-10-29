package com.checkin.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Ghost
import com.adevinta.spark.components.icons.Icon
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.icons.BoxOutline
import com.adevinta.spark.icons.Check
import com.adevinta.spark.icons.LeboncoinIcons
import com.adevinta.spark.icons.PenOutline
import com.adevinta.spark.icons.Plus
import com.adevinta.spark.icons.ThreeDotsVertical
import com.checkin.app.R
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.ArchiveResult
import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.ui.components.ActionRow
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import com.checkin.app.ui.components.SectionDivider
import com.checkin.app.ui.components.reminderTimeSummary

/**
 * The habits: each one's name and reminder time, a way to add one, and the archived ones with a way
 * back. **It is also where switching happens**: tapping a habit shows it on every tab and calls
 * [onSelected], which returns to wherever the user came from; Edit and Archive are in each row's
 * overflow menu. The selected habit carries a check, the one mark that distinguishes a row.
 *
 * It lists habits and **compares none of them** — no figure sits beside a name, because a count or
 * a total next to each row is a ranking of the user's habits, which this app never makes. Their
 * records are on the other tabs, one habit at a time.
 *
 * Archive is offered on every active habit and refused with a reason where it cannot happen: the
 * habit with the open session (check out first), and the last active one (the app always shows a
 * habit). It needs no confirmation because it destroys nothing — the record is kept, and Restore is
 * one tap away on this same screen.
 */
@Composable
fun HabitsScreen(
    innerPadding: PaddingValues,
    onSelected: () -> Unit,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Resources rather than captured strings: each message takes the event's own name, which no
    // `stringResource` read ahead of the event can know.
    val resources = LocalResources.current
    val snackbarHostState = LocalSnackbarHostState.current

    // The habit being edited, or NEW_HABIT for the "New habit" row; null when no dialog is open.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val unnamed = resources.getString(R.string.habit_unnamed)
            val message = when (event) {
                is HabitsEvent.Archived -> resources.getString(R.string.habit_archived, event.name ?: unnamed)
                is HabitsEvent.ArchiveRefused -> when (event.result) {
                    ArchiveResult.RUNNING -> resources.getString(R.string.habit_archive_running, event.name ?: unnamed)
                    else -> resources.getString(R.string.habit_archive_last)
                }
                is HabitsEvent.RestoreRefused -> resources.getString(
                    R.string.habit_restore_duplicate,
                    event.name ?: unnamed,
                )
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.habits_section)) {
                uiState.active.forEachIndexed { index, entry ->
                    if (index > 0) SectionDivider()
                    HabitRow(
                        habit = entry.habit,
                        detail = reminderTimeSummary(entry.reminderMinute),
                        selected = entry.habit.id == uiState.selected?.id,
                        onSelect = { viewModel.select(entry.habit.id, onSelected) },
                        onEdit = { editing = entry.habit.id },
                        onArchive = { viewModel.archive(entry.habit) },
                    )
                }
                SectionDivider()
                ActionRow(
                    label = stringResource(R.string.habit_new),
                    icon = LeboncoinIcons.Plus,
                    contentDescription = null,
                    onClick = { editing = NEW_HABIT },
                )
            }
        }
        if (uiState.archived.isNotEmpty()) {
            item {
                SectionCard(
                    title = stringResource(R.string.habits_archived_section),
                    subtitle = stringResource(R.string.habits_archived_note),
                ) {
                    uiState.archived.forEachIndexed { index, habit ->
                        if (index > 0) SectionDivider()
                        ArchivedRow(habit = habit, onRestore = { viewModel.restore(habit) })
                    }
                }
            }
        }
    }

    editing?.let { id ->
        val entry = uiState.active.firstOrNull { it.habit.id == id }
        val isNew = id == NEW_HABIT
        // A habit archived from another surface while its dialog was open has nothing left to edit.
        if (isNew || entry != null) {
            HabitEditorDialog(
                title = stringResource(
                    if (isNew) R.string.habit_editor_new_title else R.string.habit_editor_edit_title,
                ),
                initialName = entry?.habit?.name.orEmpty(),
                initialMinute = entry?.reminderMinute ?: NudgeSchedule.DEFAULT_MINUTE,
                nameProblem = { viewModel.nameProblem(it, entry?.habit?.id) },
                onSave = { name, minute, onResult ->
                    viewModel.save(entry?.habit?.id, name, minute) { problem ->
                        if (problem == null) editing = null
                        onResult(problem)
                    }
                },
                onDismiss = { editing = null },
            )
        }
    }
}

/** A row id no habit can have — Room's ids start at 1 — standing for the "New habit" dialog. */
private const val NEW_HABIT = -1L

private val CHECK_SLOT = 36.dp

@Composable
private fun HabitRow(
    habit: Habit,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The check and the text are the select target.
        HabitNameRow(
            habit = habit,
            onClick = onSelect,
            onClickLabel = stringResource(R.string.habit_select),
            modifier = Modifier.weight(1f),
            detail = detail,
        ) {
            // Every row reserves the check's width, so the names line up.
            Box(modifier = Modifier.size(CHECK_SLOT), contentAlignment = Alignment.CenterStart) {
                if (selected) {
                    Icon(
                        sparkIcon = LeboncoinIcons.Check,
                        contentDescription = stringResource(R.string.habit_selected),
                        tint = SparkTheme.colors.main,
                    )
                }
            }
        }
        // Edit and Archive behind one overflow, so the row keeps a single trailing control and the
        // tap target beside it stays the habit itself.
        Box {
            var menuOpen by rememberSaveable { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    sparkIcon = LeboncoinIcons.ThreeDotsVertical,
                    contentDescription = stringResource(
                        R.string.habit_more_actions,
                        habit.name ?: stringResource(R.string.habit_unnamed),
                    ),
                    tint = SparkTheme.colors.onBackgroundVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.habit_edit)) },
                    leadingIcon = { Icon(LeboncoinIcons.PenOutline, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.habit_archive)) },
                    leadingIcon = { Icon(LeboncoinIcons.BoxOutline, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onArchive()
                    },
                )
            }
        }
    }
}

@Composable
private fun ArchivedRow(habit: Habit, onRestore: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = habit.name ?: stringResource(R.string.habit_unnamed),
            style = SparkTheme.typography.body2,
            modifier = Modifier.weight(1f),
        )
        Button.Ghost(onClick = onRestore, text = stringResource(R.string.habit_restore))
    }
}
