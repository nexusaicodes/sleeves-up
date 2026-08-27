package com.checkin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.recordedDayShade

/**
 * One day of the record, drawn as the calendar draws it: the recorded-day shade behind the day's
 * own number.
 *
 * It exists because a second surface needs the same mark. The check-out celebration shows the day
 * that just became one of the marked ones, and drawing it there from its own rounded box and its own
 * alpha is how the celebration and the calendar come to render the same fact two different ways —
 * see [recordedDayShade], which is the half that must not drift.
 *
 * **One mark, never a row of them.** Neighbours would make this a display of *consecutive* days,
 * which is a streak under another name: the app counts none, shows none, and says the word nowhere.
 * That this composable takes a single day and has no notion of an adjacent one is the guardrail
 * rather than a limitation — a caller wanting a run of days is the thing to refuse.
 *
 * It states no quantity and grades nothing. A 45-minute day and a nine-hour day are this same mark
 * at this same strength, which is the whole of what the calendar says and the whole of what this
 * says beside a duration it never qualifies.
 */
@Composable
fun DayMark(day: Int, modifier: Modifier = Modifier, size: Dp = DAY_MARK_SIZE) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(DAY_MARK_CORNER))
            .background(recordedDayShade()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The corner a day is drawn with, shared with the calendar's cells so the mark keeps one shape.
 *
 * The calendar clips every cell to this, not only a recorded one: the shape says "a day", and the
 * shade is what says the day holds something.
 */
val DAY_MARK_CORNER = 8.dp

/** Large enough to head a full-screen message, where it stands in for that screen's icon. */
private val DAY_MARK_SIZE = 72.dp

@Preview(showBackground = true)
@Composable
private fun DayMarkPreview() {
    CheckInAppTheme {
        DayMark(day = 27)
    }
}
