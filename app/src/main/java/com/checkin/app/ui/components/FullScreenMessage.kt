package com.checkin.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.ButtonSize
import com.adevinta.spark.components.buttons.Primary
import com.adevinta.spark.components.icons.Icon
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.icons.SparkIcon
import com.checkin.app.ui.theme.LocalAppTypography

/**
 * The app's one full-screen message: an icon, a title, a paragraph, and whatever the caller puts
 * below them.
 *
 * The presence gate's stops, the welcome tour's pages and the naming step are this arrangement with
 * different words, drawn from here so that a change to the spacing, the insets or the behaviour at a
 * large font scale cannot land on one and miss the others. The scroll wrapper is such a behaviour: at
 * the largest accessibility font sizes this content is taller than a phone, and centred text with no
 * scroll puts the actions off screen.
 */
@Composable
fun FullScreenMessage(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MESSAGE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(modifier = Modifier.height(ICON_TO_TITLE_GAP))
        Text(
            text = title,
            style = LocalAppTypography.current.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TITLE_TO_BODY_GAP))
        Text(
            text = message,
            style = SparkTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = SparkTheme.colors.onBackgroundVariant,
        )
        actions()
    }
}

/** The mark above a [FullScreenMessage], sized and tinted once for every caller. */
@Composable
fun MessageIcon(icon: SparkIcon) {
    Icon(
        sparkIcon = icon,
        contentDescription = null,
        modifier = Modifier.size(MESSAGE_ICON_SIZE),
        tint = SparkTheme.colors.main,
    )
}

/** The drawable form of [MessageIcon], for a mark the app ships as a drawable of its own. */
@Composable
fun MessageIcon(painter: Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.size(MESSAGE_ICON_SIZE),
        tint = SparkTheme.colors.main,
    )
}

/**
 * The one button pinned below a full-screen surface — the tour's Next / Get started and the naming
 * step's Continue — so stepping from one surface to the next leaves the action where the thumb is.
 */
@Composable
fun FullScreenAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Large is Spark's 56.dp minimum height: a comfortable full-width action, independent of the
    // Check-In screen's 64.dp button, which is sized by that screen's fit budget.
    Button.Primary(
        onClick = onClick,
        modifier = modifier
            .padding(start = MESSAGE_PADDING, end = MESSAGE_PADDING, bottom = ACTION_BOTTOM_GAP)
            .fillMaxWidth(),
        size = ButtonSize.Large,
    ) {
        Text(text = text, style = LocalAppTypography.current.titleMedium)
    }
}

/** Wide enough to keep a centred paragraph off the screen edges at any font scale. */
val MESSAGE_PADDING = 32.dp

/**
 * The space between the paragraph and whatever a caller puts in `actions`. The slot adds none of its
 * own, so each caller spaces from this one value rather than choosing a gap of its own.
 */
val MESSAGE_BODY_TO_ACTION_GAP = 24.dp

private val MESSAGE_ICON_SIZE = 56.dp
private val ICON_TO_TITLE_GAP = 24.dp
private val TITLE_TO_BODY_GAP = 16.dp

/** Lifts the action clear of the gesture bar, above the inset `safeDrawingPadding` already applied. */
private val ACTION_BOTTOM_GAP = 16.dp
