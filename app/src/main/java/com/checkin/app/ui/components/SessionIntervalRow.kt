package com.checkin.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.theme.tabularFigures
import com.checkin.app.util.TimeFormat

/**
 * One session as a ledger row — the clock range it covered, and what that came to.
 *
 * **Two columns and no label**, because a third would only paraphrase one of these. A word for where
 * in the day it fell ("Morning") restates the start time sitting beside it, and an ordinal restates
 * both the row order and the session count in the header directly above.
 *
 * **Nothing here ticks.** The open interval shows `ongoing` in place of an end time and a pulse in
 * place of a duration, because a second live clock beside the gauge is a number the user has to
 * reconcile with the one above it. The gauge is where elapsed time is read; this list is the shape
 * of the day.
 *
 * Shared by the Check-In screen's today list and the History tab's selected day, which are the same
 * ledger over different days — a second copy is how the two would come to render a session
 * differently depending on which tab you were looking at it from.
 */
@Composable
internal fun SessionIntervalRow(session: CheckInSession) {
    val running = session.stoppedAt == null
    val color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val range = "${TimeFormat.clock(session.startedAt)} - " +
        if (running) {
            stringResource(R.string.session_in_progress)
        } else {
            session.stoppedAt?.let { TimeFormat.clock(it) }.orEmpty()
        }
    // Spoken form of the row. An open one needs no join: the pulse carries no text, and `range`
    // already ends in "ongoing".
    val duration = session.duration?.takeUnless { running }?.let { TimeFormat.durationShort(it) }
    val rowDescription = duration?.let { stringResource(R.string.cd_session_row, range, it) } ?: range

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            // One announcement per row: read as separate nodes it arrives as disconnected fragments.
            .clearAndSetSemantics { contentDescription = rowDescription },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = range,
            // Tabular so the clock times stack into a column instead of shifting per digit width.
            style = MaterialTheme.typography.bodySmall.tabularFigures(),
            color = color,
            maxLines = 1,
            // The range is the column that absorbs the squeeze, so it states when it has been cut:
            // at a large font scale it can outgrow what is left beside the duration, and a clipped
            // clock time would end mid-character with nothing to say so.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.width(durationColumnWidth()),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (running) {
                OngoingPulse(color = color)
            } else {
                Text(
                    text = duration.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.tabularFigures(),
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The open session's mark: three dots breathing in sequence, in the duration column's place.
 *
 * Drawn rather than an emoji glyph, which would vary by device font, ignore the theme colour and sit
 * on its own baseline. It holds still when the system animation scale is off — see
 * [animationsEnabled], which is where that reasoning lives now that a second surface honours it.
 */
@Composable
private fun OngoingPulse(color: Color) {
    val animated = animationsEnabled()
    val transition = rememberInfiniteTransition(label = "ongoing")

    Row(horizontalArrangement = Arrangement.spacedBy(PULSE_DOT_GAP)) {
        repeat(PULSE_DOTS) { dot ->
            val alpha by transition.animateFloat(
                initialValue = PULSE_MIN_ALPHA,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(PULSE_CYCLE_MS, delayMillis = dot * PULSE_STAGGER_MS),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "ongoing-dot-$dot",
            )
            Box(
                modifier = Modifier
                    .size(PULSE_DOT_SIZE)
                    .alpha(if (animated) alpha else 1f)
                    .background(color, CircleShape),
            )
        }
    }
}

/**
 * The amount column: wide enough for "12h 59m", so a settled duration and the pulse share one slot
 * and the figures stack instead of ragging with the range beside them.
 *
 * **It scales with the font, and that is not polish.** The width is `dp` while the text is `sp`, so
 * a fixed column stops fitting the moment the user raises their font size — and the duration is
 * `maxLines = 1` with no ellipsis, so it does not report the cut: at 1.5x a 2h 15m session rendered
 * as a flat "2h", which is a wrong number on screen rather than a clipped one. The range column
 * absorbs the squeeze instead, which it is built for — it ellipsizes and says so.
 *
 * The cap stops the column eating the range at the largest scales; past it the range ellipsizes,
 * which is the correct order of sacrifice — a shortened clock range still reads as one, a shortened
 * duration reads as a different duration.
 */
private val DURATION_COLUMN = 56.dp
private val DURATION_COLUMN_MAX = 88.dp

@Composable
private fun durationColumnWidth(): Dp =
    (DURATION_COLUMN * LocalDensity.current.fontScale).coerceIn(DURATION_COLUMN, DURATION_COLUMN_MAX)

private const val PULSE_DOTS = 3
private const val PULSE_CYCLE_MS = 600
private const val PULSE_STAGGER_MS = 200
private const val PULSE_MIN_ALPHA = 0.25f
private val PULSE_DOT_SIZE = 5.dp
private val PULSE_DOT_GAP = 3.dp
