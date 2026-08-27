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
import androidx.compose.ui.platform.LocalDensity
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
 * The font scale past which the readout drops to the smaller style. It is the largest type in the
 * app and so the first thing to outgrow a phone's width: at 2.0 a `displayMedium` "12h 34m" measures
 * wider than a 360dp screen's content box, and the step down is what keeps it on one line.
 */
private const val READOUT_SHRINK_SCALE = 1.5f

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
 * [BrandGridGauge], which is where that rule is enforced. The description states the elapsed time
 * and nothing about the mark.
 *
 * **The readout sits below the mark rather than inside it, and it gives up size before it gives up
 * a line.** Enclosing it — in a ring's hole or a lattice's centre — bounds `sp` text by a `dp` box,
 * so a raised font scale clips or wraps it with nothing reporting that. Stacking removes that bound
 * but not the screen's: the clock is the app's largest type and at a large scale it is wider than a
 * narrow phone. So it is `maxLines = 1` and steps down a style past [READOUT_SHRINK_SCALE] — the
 * same sacrifice-the-size-first order `SessionIntervalRow`'s duration column makes, and for the same
 * reason, since a wrapped clock is a wrong number on screen rather than a small one.
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
            // Two reasons to step down, and only the second is an overflow: a short viewport, where
            // the screen scrolls anyway and the smaller style simply buys room; and a font scale at
            // which the larger one no longer fits a narrow phone on one line.
            style = if (markSize < MARK_MIN || LocalDensity.current.fontScale >= READOUT_SHRINK_SCALE) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.displayMedium
            }.tabularFigures(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
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
