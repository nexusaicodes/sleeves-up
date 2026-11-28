package com.checkin.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.text.Text
import com.checkin.app.R
import com.checkin.app.data.local.Habit

/**
 * A habit's name as a tap target: the one way a list of habits draws a habit, shared by the Habits
 * screen and the picker the widget opens, so the two cannot come to render the same habit differently.
 *
 * **A name and nothing that measures it.** [detail] is the habit's reminder time on the Habits screen
 * and absent in the picker; a count, a total or a mark of today's state beside a name would rank the
 * user's habits against each other, which no list of them ever does. [leading] is the Habits screen's
 * selection check. The 48dp floor is reached through the vertical padding, single line or two.
 */
@Composable
internal fun HabitNameRow(
    habit: Habit,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column {
            Text(
                text = habit.name ?: stringResource(R.string.habit_unnamed),
                style = SparkTheme.typography.body2,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = SparkTheme.typography.caption,
                    color = SparkTheme.colors.onBackgroundVariant,
                )
            }
        }
    }
}
