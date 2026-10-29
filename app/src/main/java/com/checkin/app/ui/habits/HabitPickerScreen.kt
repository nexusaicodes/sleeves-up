package com.checkin.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adevinta.spark.components.text.Text
import com.checkin.app.R
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.SectionCard
import com.checkin.app.ui.components.SectionDivider
import com.checkin.app.ui.theme.LocalAppTypography

/**
 * Which habit a check-in is for, asked when the home-screen widget's one button is pressed with more
 * than one habit to choose from. A pick goes straight on to the presence gate for that habit.
 *
 * **The Habits screen with everything but choosing taken out.** It lists the same habits in the same
 * order through the same [HabitNameRow], and nothing else: no reminder times, no add, edit or archive,
 * since the user arrived here to check in rather than to manage anything — and a row here means "check
 * in to this", where on the Habits screen it means "show this". Names only, like every list of habits.
 *
 * Drawn at the root where the gate is, by [AppRoot][com.checkin.app.ui.AppRoot], so it is ranked like
 * one: below the welcome tour and the naming step, and with the nav host not composed beneath it.
 * **Not now** and back both drop the request, landing in the app with nothing selected or written.
 */
@Composable
fun HabitPickerScreen(active: List<HabitEntry>, onPick: (habitId: Long) -> Unit, onDismiss: () -> Unit) {
    ConstrainedContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.habit_picker_title),
                        style = LocalAppTypography.current.headlineSmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                item {
                    SectionCard(title = stringResource(R.string.habits_section)) {
                        active.forEachIndexed { index, entry ->
                            if (index > 0) SectionDivider()
                            HabitNameRow(
                                habit = entry.habit,
                                onClick = { onPick(entry.habit.id) },
                                onClickLabel = stringResource(R.string.check_in),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(stringResource(R.string.presence_gate_cancel))
            }
        }
    }
}
