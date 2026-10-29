package com.checkin.app.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.tokens.highlight
import com.checkin.app.R
import com.checkin.app.ui.components.BrandGrid
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.LocalAppTypography
import com.checkin.app.ui.theme.LocalTimePatterns
import com.checkin.app.ui.theme.tabularFigures
import com.checkin.app.util.MILLIS_PER_HOUR
import com.checkin.app.util.MILLIS_PER_MINUTE
import com.checkin.app.util.TimeFormat

/**
 * The moment after a check-out: what was just recorded, held until the user closes it.
 *
 * **It celebrates showing up, never how long for.** The copy, the emphasis and the icon are
 * identical at twenty minutes and at four hours — a message that warmed as the number grew would be
 * a daily target returning as a congratulation threshold, which is the one thing this screen must
 * not become. The duration is stated because it is a quantity worth seeing, exactly as
 * it is everywhere else in the app, and it is never graded.
 *
 * **Nothing retires it on a timer.** A tap anywhere and the back gesture are the only ways out, so
 * the figures stay put for as long as they are being read — a countdown racing the reader is what
 * the explicit dismiss exists instead of.
 *
 * Rendered above the nav host rather than inside the Check-In screen because a check-out can be
 * written from the notification while any tab is open — see [CheckOutSignal].
 */
@Composable
fun CheckOutCelebration(completed: CheckOutSignal.Completed, onDismiss: () -> Unit) {
    val patterns = LocalTimePatterns.current
    val titleText = stringResource(R.string.checkout_celebration_title)
    val sessionText = TimeFormat.durationShort(completed.sessionMs, patterns)
    val dayText = stringResource(
        R.string.todays_sessions_summary,
        pluralStringResource(
            R.plurals.sessions_count,
            completed.daySessionCount,
            completed.daySessionCount,
        ),
        TimeFormat.durationShort(completed.dayTotalMs, patterns),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SparkTheme.colors.background)
            // No ripple and no role: the whole surface is a dismiss target rather than a control,
            // and an indication spreading from the tap point would read as a button that isn't there.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            // One announcement for the whole surface. Read as separate nodes it arrives as four
            // disconnected fragments, and the dismiss hint is the least useful of them.
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(completed.habitName, titleText, sessionText, dayText)
                    .joinToString(". ")
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // The record gaining an entry rather than a congratulation appearing (see `BrandGrid`'s
            // settle). It plays once and rests, and nothing about it changes with the duration.
            BrandGrid(size = MARK_SIZE, settle = true)
            // Which record gained the entry — a name, worded identically for every habit and never
            // set beside another habit's figures.
            completed.habitName?.let { name ->
                Text(
                    text = name,
                    style = SparkTheme.typography.body2.highlight,
                    color = SparkTheme.colors.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = titleText,
                style = LocalAppTypography.current.headlineSmall,
                color = SparkTheme.colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = sessionText,
                style = LocalAppTypography.current.displaySmall.tabularFigures(),
                fontWeight = FontWeight.Bold,
                color = SparkTheme.colors.main,
                textAlign = TextAlign.Center,
            )
            Text(
                text = dayText,
                style = SparkTheme.typography.body2,
                color = SparkTheme.colors.onBackgroundVariant,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = stringResource(R.string.checkout_celebration_dismiss),
            style = SparkTheme.typography.caption.highlight,
            color = SparkTheme.colors.onBackgroundVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

private val MARK_SIZE = 72.dp

@Preview(showBackground = true)
@Composable
private fun CheckOutCelebrationPreview() {
    CheckInAppTheme {
        CheckOutCelebration(
            completed = CheckOutSignal.Completed(
                sessionMs = 2 * MILLIS_PER_HOUR + 14 * MILLIS_PER_MINUTE,
                dayTotalMs = 6 * MILLIS_PER_HOUR + 12 * MILLIS_PER_MINUTE,
                daySessionCount = 2,
                habitName = "Writing",
            ),
            onDismiss = {},
        )
    }
}

/** A short session gets exactly the same treatment, which is the point. */
@Preview(showBackground = true)
@Composable
private fun CheckOutCelebrationShortPreview() {
    CheckInAppTheme {
        CheckOutCelebration(
            completed = CheckOutSignal.Completed(
                sessionMs = 20 * MILLIS_PER_MINUTE,
                dayTotalMs = 20 * MILLIS_PER_MINUTE,
                daySessionCount = 1,
                habitName = "Gym",
            ),
            onDismiss = {},
        )
    }
}
