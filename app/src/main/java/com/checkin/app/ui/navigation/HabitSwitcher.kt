package com.checkin.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.ui.habits.HabitEditorDialog
import com.checkin.app.ui.habits.HabitsUiState
import com.checkin.app.ui.habits.HabitsViewModel

/**
 * The habit switcher in the top bar: the habit every tab is showing, and a menu to show another.
 * Account-switcher style — one habit at a time, and the menu lists names and nothing else, since a
 * figure beside each would compare them.
 *
 * **Locked while a session runs**, on every tab. There is one open session across all habits, so
 * the app is about the running habit until it is checked out of; the menu then lists that habit
 * alone and says why. The lock is the repository's too ([com.checkin.app.data.repository.HabitRepository.select]
 * refuses), so the menu is the explanation rather than the enforcement.
 */
@Composable
internal fun HabitSwitcher(state: HabitsUiState, viewModel: HabitsViewModel, onManage: () -> Unit) {
    val selected = state.selected ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val selectedName = selected.name ?: stringResource(R.string.habit_unnamed)
    val switcherDescription = stringResource(R.string.habit_switcher_cd, selectedName)
    val running = state.running

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = switcherDescription },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Room for the title beside it: the bar centres the title in what is left.
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                Icon(
                    imageVector = if (running != null) Icons.Default.Lock else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (running != null) {
                DropdownMenuItem(
                    text = { Text(running.name ?: stringResource(R.string.habit_unnamed)) },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { expanded = false },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.habit_switch_locked,
                                running.name ?: stringResource(R.string.habit_unnamed),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    enabled = false,
                    onClick = {},
                )
            } else {
                state.active.forEach { entry ->
                    val habit = entry.habit
                    DropdownMenuItem(
                        text = { Text(habit.name ?: stringResource(R.string.habit_unnamed)) },
                        leadingIcon = {
                            // Every row reserves the check's space, so the names line up.
                            if (habit.id == selected.id) Icon(Icons.Default.Check, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            viewModel.select(habit.id)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.habit_new)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        expanded = false
                        creating = true
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.habits_manage)) },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                onClick = {
                    expanded = false
                    onManage()
                },
            )
        }
    }

    if (creating) {
        HabitEditorDialog(
            title = stringResource(R.string.habit_editor_new_title),
            initialName = "",
            initialHour = NudgeSchedule.DEFAULT_HOUR,
            nameProblem = { viewModel.nameProblem(it, forId = null) },
            onSave = { name, hour ->
                // Created from the switcher means "and show it": the new habit becomes the selection.
                viewModel.save(habitId = null, name = name, hour = hour, selectNew = true)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}
