package com.checkin.app.ui.welcome

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.animationsEnabled
import com.checkin.app.ui.theme.CheckInAppTheme
import kotlinx.coroutines.launch

/**
 * The first-run tour: three pages, shown once, ahead of the `POST_NOTIFICATIONS` request rather than
 * beside it. [onFinished] releases the dialog, which is why the last page describes the reminders it
 * is about to ask for.
 *
 * Skipping and reaching the end are the same outcome deliberately — a skip that withheld the request
 * would leave every notification silently dead, including the one saying they have not checked in.
 */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val pages = WelcomePages.all
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val animated = animationsEnabled()
    val onLastPage = pagerState.currentPage == pages.lastIndex

    // Owned here rather than by the caller, unlike the app's other full-screen surfaces: theirs map
    // to dismiss, which the caller owns, and this maps to page position, which only this knows.
    // Disabled on the first page, so back there leaves the app rather than entering it.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.goTo(pagerState.currentPage - 1, animated) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = SCREEN_PADDING),
    ) {
        // The row keeps its height on the last page, where the button is gone: collapsing it would
        // shift the whole tour up by a button's height on the final swipe.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SKIP_ROW_HEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!onLastPage) {
                TextButton(onClick = onFinished) { Text(stringResource(R.string.welcome_skip)) }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            WelcomePageContent(page = pages[page], icon = { PageIcon(page) })
        }

        PageDots(pageCount = pages.size, current = pagerState.currentPage)

        Spacer(modifier = Modifier.height(DOTS_TO_ACTION_GAP))

        Button(
            onClick = {
                if (onLastPage) {
                    onFinished()
                } else {
                    scope.launch { pagerState.goTo(pagerState.currentPage + 1, animated) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(ACTION_HEIGHT),
            shape = RoundedCornerShape(ACTION_CORNER),
        ) {
            Text(
                text = stringResource(if (onLastPage) R.string.welcome_start else R.string.welcome_next),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(ACTION_BOTTOM_GAP))
    }
}

/** By position rather than on [WelcomePage]: the first is a drawable, the other two are vectors. */
@Composable
private fun PageIcon(page: Int) {
    val tint = MaterialTheme.colorScheme.primary
    val modifier = Modifier.size(ICON_SIZE)
    when (page) {
        0 -> Icon(
            painter = painterResource(R.drawable.ic_stat_checkin),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        // The camera disclosure's own icon, so the screen met at the first check-in reads as a
        // continuation of this one.
        1 -> Icon(Icons.Rounded.Face, contentDescription = null, modifier = modifier, tint = tint)
        else -> Icon(Icons.Default.Notifications, contentDescription = null, modifier = modifier, tint = tint)
    }
}

/**
 * One page, laid out like `GateMessageScreen` so the app's two full-screen messages cannot drift.
 * It scrolls for the same reason that one does: at the largest font scales the content is taller
 * than a phone.
 */
@Composable
private fun WelcomePageContent(page: WelcomePage, icon: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(modifier = Modifier.height(ICON_TO_TITLE_GAP))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TITLE_TO_BODY_GAP))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Silent to a screen reader: the pager already announces "page 2 of 3". */
@Composable
private fun PageDots(pageCount: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = DOT_GAP)
                    .size(DOT_SIZE)
                    .background(
                        color = if (page == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** Jumps rather than slides when the user has turned animations off. */
private suspend fun PagerState.goTo(page: Int, animated: Boolean) {
    if (animated) animateScrollToPage(page) else scrollToPage(page)
}

private val SCREEN_PADDING = 32.dp

/** Reserved whether or not the Skip button is drawn — see the call site. */
private val SKIP_ROW_HEIGHT = 48.dp

private val ICON_SIZE = 56.dp
private val ICON_TO_TITLE_GAP = 24.dp
private val TITLE_TO_BODY_GAP = 16.dp

private val DOT_SIZE = 8.dp
private val DOT_GAP = 4.dp
private val DOTS_TO_ACTION_GAP = 24.dp

/** Echoes the Check-In screen's primary action, which is the control this button hands over to. */
private val ACTION_HEIGHT = 56.dp
private val ACTION_CORNER = 16.dp

/** Lifts the action clear of the gesture bar, above the inset `safeDrawingPadding` already applied. */
private val ACTION_BOTTOM_GAP = 16.dp

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeScreenPreview() {
    CheckInAppTheme {
        WelcomeScreen(onFinished = {})
    }
}
