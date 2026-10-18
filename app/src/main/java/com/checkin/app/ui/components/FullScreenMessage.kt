package com.checkin.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The app's one full-screen message: an icon, a title, a paragraph, and whatever the caller puts
 * below them.
 *
 * The presence gate's stops and the welcome tour's pages are this arrangement with different words,
 * and they are drawn from here rather than each carrying a copy so that a change to the spacing, the
 * insets or the behaviour at a large font scale cannot land on one and miss the other. The scroll
 * wrapper is exactly such a change: at the largest accessibility font sizes this content is taller
 * than a phone, and centred text with no scroll puts the actions off screen.
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
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TITLE_TO_BODY_GAP))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actions()
    }
}

/** The mark above a [FullScreenMessage], sized and tinted once for every caller. */
@Composable
fun MessageIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.size(MESSAGE_ICON_SIZE),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** The drawable form of [MessageIcon], for a mark the app ships rather than a Material vector. */
@Composable
fun MessageIcon(painter: Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.size(MESSAGE_ICON_SIZE),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The one button pinned below a full-screen surface — the tour's Next / Get started and the naming
 * step's Continue — so stepping from one surface to the next leaves the action where the thumb is.
 */
@Composable
fun FullScreenAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(start = MESSAGE_PADDING, end = MESSAGE_PADDING, bottom = ACTION_BOTTOM_GAP)
            .fillMaxWidth()
            .heightIn(min = ACTION_MIN_HEIGHT),
        shape = RoundedCornerShape(ACTION_CORNER),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
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

/** A comfortable full-width action. Independent of the Check-In screen's 64.dp primary button,
 *  which is sized by that screen's fit budget rather than by how a button should look. */
private val ACTION_MIN_HEIGHT = 56.dp
private val ACTION_CORNER = 16.dp

/** Lifts the action clear of the gesture bar, above the inset `safeDrawingPadding` already applied. */
private val ACTION_BOTTOM_GAP = 16.dp
