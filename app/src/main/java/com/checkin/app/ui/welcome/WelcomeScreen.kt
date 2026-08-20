package com.checkin.app.ui.welcome

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.FullScreenMessage
import com.checkin.app.ui.components.MESSAGE_PADDING
import com.checkin.app.ui.components.MessageIcon
import com.checkin.app.ui.components.animationsEnabled
import com.checkin.app.ui.theme.CheckInAppTheme
import kotlinx.coroutines.launch

/**
 * The first-run tour: three pages, shown once, ahead of the `POST_NOTIFICATIONS` request rather than
 * beside it. [onFinished] releases the dialog, which is why the last page describes the reminders it
 * is about to ask for.
 *
 * [onSkipped] deliberately does not release it. Skipping and finishing are the same in every other
 * respect — both retire the tour for good — but a skip means nothing has explained the app, so
 * firing the dialog behind it would reproduce in one tap the defect the tour was built to close.
 * Notifications are not lost: the presence gate asks at the first check-in, beside the camera.
 */
@Composable
fun WelcomeScreen(onFinished: () -> Unit, onSkipped: () -> Unit) {
    val pages = WelcomePages.all
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val animated = animationsEnabled()

    // `settledPage`, never `currentPage`: the latter flips as soon as a scroll passes halfway, so on
    // page 2 the button would relabel to "Get started" under a still-descending finger and the
    // second tap of an ordinary double-tap would finish the tour — skipping the one page that
    // explains the dialog it releases. It also keeps the label and the Skip button from flickering
    // through a swipe.
    val onLastPage = pagerState.settledPage == pages.lastIndex

    // Owned here rather than by the caller, unlike the app's other full-screen surfaces: theirs map
    // to dismiss, which the caller owns, and this maps to page position, which only this knows.
    // Disabled on the first page, so back there leaves the app rather than entering it.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.goTo(pagerState.currentPage - 1, animated) }
    }

    ConstrainedContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            // The row keeps its height on the last page, where the button is gone: collapsing it
            // would shift the whole tour up by a button's height on the final swipe. A minimum
            // rather than a fixed height, so the label still fits at the largest font scales.
            Box(
                modifier = Modifier
                    .padding(horizontal = MESSAGE_PADDING)
                    .fillMaxWidth()
                    .heightIn(min = SKIP_ROW_MIN_HEIGHT),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (!onLastPage) {
                    TextButton(onClick = onSkipped) { Text(stringResource(R.string.welcome_skip)) }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                // The swipe is the tour's main interaction, so "remove animations" has to reach it
                // too — gating only the Next button would leave a tap jumping and a drag sliding.
                // Branched rather than always passing a spec, so the animated case keeps the
                // platform's own snap spring rather than a tween chosen here.
                flingBehavior = if (animated) {
                    PagerDefaults.flingBehavior(state = pagerState)
                } else {
                    PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = tween(durationMillis = 0),
                    )
                },
            ) { page ->
                // No horizontal padding above this: each page brings MESSAGE_PADDING of its own, so
                // the shared message layout owns its insets exactly as it does behind the gate.
                WelcomePageContent(pages[page])
            }

            PageDots(pageCount = pages.size, current = pagerState.currentPage)

            Spacer(modifier = Modifier.height(DOTS_TO_ACTION_GAP))

            Button(
                onClick = {
                    if (onLastPage) {
                        onFinished()
                    } else {
                        scope.launch { pagerState.goTo(pagerState.settledPage + 1, animated) }
                    }
                },
                modifier = Modifier
                    .padding(horizontal = MESSAGE_PADDING)
                    .fillMaxWidth()
                    .heightIn(min = ACTION_MIN_HEIGHT),
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
}

/** Drawn through the shared full-screen message, so the tour and the gate cannot drift apart. */
@Composable
private fun WelcomePageContent(page: WelcomePage) {
    FullScreenMessage(
        icon = { PageIcon(page.icon) },
        title = stringResource(page.titleRes),
        message = stringResource(page.bodyRes),
    )
}

/** An expression, so adding a [WelcomeIcon] fails to compile rather than falling through to a bell. */
@Composable
private fun PageIcon(icon: WelcomeIcon) = when (icon) {
    WelcomeIcon.CHECK_IN_MARK -> MessageIcon(painterResource(R.drawable.ic_stat_checkin))
    // The camera disclosure's own icon, so the screen met at the first check-in reads as a
    // continuation of this one.
    WelcomeIcon.FACE -> MessageIcon(Icons.Rounded.Face)
    WelcomeIcon.REMINDER -> MessageIcon(Icons.Default.Notifications)
}

/**
 * The row speaks the position and its dots say nothing, because `HorizontalPager` publishes a scroll
 * range and no page count — TalkBack never announces "page 2 of 3" on its own, and three unlabelled
 * nodes are worse than one that states where the reader is.
 */
@Composable
private fun PageDots(pageCount: Int, current: Int) {
    val position = stringResource(R.string.welcome_page_position, current + 1, pageCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = position },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = DOT_GAP)
                    .size(DOT_SIZE)
                    .background(
                        // `outline`, not `outlineVariant`: the latter is the surface's own container
                        // in the dark scheme and under 2:1 against the background in both, so the
                        // companions of the active dot would not be there at all.
                        color = if (page == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
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

/** Reserved whether or not the Skip button is drawn — see the call site. */
private val SKIP_ROW_MIN_HEIGHT = 48.dp

private val DOT_SIZE = 8.dp
private val DOT_GAP = 4.dp
private val DOTS_TO_ACTION_GAP = 24.dp

/** Echoes the Check-In screen's primary action, which is the control this button hands over to. */
private val ACTION_MIN_HEIGHT = 56.dp
private val ACTION_CORNER = 16.dp

/** Lifts the action clear of the gesture bar, above the inset `safeDrawingPadding` already applied. */
private val ACTION_BOTTOM_GAP = 16.dp

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeScreenPreview() {
    CheckInAppTheme {
        WelcomeScreen(onFinished = {}, onSkipped = {})
    }
}
