package com.checkin.app.ui.checkin

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.BrandGridGauge
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.tabularFigures
import com.checkin.app.util.TimeFormat

/**
 * The mark's side, which is what these constants size — **not** the whole gauge. The readout sits
 * below the mark rather than inside it, so the block is this plus [READOUT_HEIGHT] plus the gap
 * between them, and `CheckInScreen` pays for those two separately in its own fit budget. Splitting
 * the terms is the point: one number standing for a mark and a line of text is a number that stops
 * being true the moment the user raises their font size, since only one of the two grows.
 */
internal val COMPACT_MARK = 110.dp
internal val MARK_MIN = 150.dp
internal val MARK_MAX = 200.dp

/**
 * The gap between the mark and the readout, and the readout's own height at a font scale of 1.
 * Both are terms of `CheckInScreen`'s budget — see [COMPACT_MARK] — and the second is the one that
 * grows with the font scale, so it is charged there as text rather than as fixed chrome.
 */
internal val MARK_TO_READOUT_GAP = 12.dp
internal val READOUT_HEIGHT = 56.dp

/**
 * The **current session's** elapsed time, under the brand mark breathing while the session runs.
 * Zero and still between sessions.
 *
 * It shows the session rather than the day's total, and that is the whole reason it needs no label.
 * A clock starting from zero and counting up is unambiguous by convention; one resuming from the
 * day's accumulated total read as a stopwatch that had been paused — implying a mechanic the app
 * deliberately does not have — and left a user mid-session doing arithmetic to answer "how long
 * have I been sitting here". The day's total is stated directly below, by `TodaySessions`.
 *
 * **The lattice is motion, not measurement, and it says only whether a session is open.** A wave of
 * brightness travels the seven cells while one is; the grid sits at track alpha while none is. No
 * cell's brightness is a figure and no cell's place in the pass means anything — see
 * [BrandGridGauge], which is where that rule is enforced and why the wave replaced a single cell
 * advancing once an hour. The description states the elapsed time and nothing about the mark.
 *
 * This replaced a sweeping arc, and the readout moving out of the ring's hole is the other half of
 * the change: at a raised font scale the clock is wider than the hole it used to sit in, so it had
 * the whole screen's width to spill into and no bound stopping it. Below the mark it simply has the
 * room.
 */
@Composable
internal fun TimerGauge(elapsedMs: Long, running: Boolean, markSize: Dp = MARK_MAX) {
    val description = stringResource(R.string.cd_timer_gauge, TimeFormat.durationShort(elapsedMs))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MARK_TO_READOUT_GAP),
        // One announcement for the pair. Read as separate nodes the mark contributes nothing and
        // the readout arrives without the phrase that says which clock it is.
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        BrandGridGauge(
            running = running,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = TRACK_ALPHA),
            modifier = Modifier.size(markSize),
        )
        Text(
            text = TimeFormat.durationLive(elapsedMs),
            // The compact branch is a short viewport, where the screen scrolls anyway — so this
            // smaller style buys room rather than preventing an overflow.
            style = if (markSize < MARK_MIN) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.displayMedium
            }.tabularFigures(),
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Faint enough that a still grid reads as at rest, dark enough that the mark is still the mark. */
private const val TRACK_ALPHA = 0.15f

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TimerGaugeRunningPreview() {
    CheckInAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TimerGauge(elapsedMs = 5 * 3_600_000L + 45 * 60_000L, running = true)
        }
    }
}

/** Between sessions the grid is still, which is the whole of what it says about state. */
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TimerGaugeAtRestPreview() {
    CheckInAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TimerGauge(elapsedMs = 0L, running = false)
        }
    }
}
