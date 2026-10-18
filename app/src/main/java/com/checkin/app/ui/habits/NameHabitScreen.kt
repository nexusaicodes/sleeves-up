package com.checkin.app.ui.habits

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.FullScreenMessage
import com.checkin.app.ui.components.MESSAGE_BODY_TO_ACTION_GAP
import com.checkin.app.ui.components.MessageIcon

/**
 * The first-run naming step: a habit with no name — the record an install carried onto habits, or
 * the one a skipped tour left unnamed — is named here, with its reminder hour, before the app opens.
 *
 * **No way out but naming it**, and no `BackHandler`: back leaves the app and the step is owed again,
 * exactly as with the welcome tour. The Check In button names the habit, so the app has nothing to
 * put on it until this is answered; and the record is kept whatever happens here, so the only thing
 * the step can cost is the few seconds it takes.
 *
 * [entry] is keyed by the caller so a second unnamed habit — which nothing creates today — would get
 * fresh fields rather than inherit the first one's typing.
 */
@Composable
fun NameHabitScreen(entry: HabitEntry, viewModel: HabitsViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var hour by rememberSaveable { mutableIntStateOf(entry.reminderHour) }
    var showProblem by rememberSaveable { mutableStateOf(false) }
    val problem = viewModel.nameProblem(name, entry.habit.id)
    val save = {
        showProblem = true
        if (problem == null) viewModel.save(entry.habit.id, name, hour)
    }

    ConstrainedContent {
        FullScreenMessage(
            icon = { MessageIcon(Icons.Default.Edit) },
            title = stringResource(R.string.name_habit_title),
            message = stringResource(R.string.name_habit_body),
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding(),
            actions = {
                Spacer(modifier = Modifier.height(MESSAGE_BODY_TO_ACTION_GAP))
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
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(MESSAGE_BODY_TO_ACTION_GAP))
                Button(
                    onClick = save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.name_habit_continue))
                }
            },
        )
    }
}
