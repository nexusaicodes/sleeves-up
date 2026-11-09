package com.checkin.app.ui.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import com.checkin.app.di.AppContainer
import com.checkin.app.platform.PromptSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Whether the widget page is owed, and what it can offer. Built by [rememberWidgetPage]; the root
 * ranks it, and the tour marks it seen when it ends, since the tour carries the page.
 */
@Stable
class WidgetPageState internal constructor(
    private val settings: PromptSettings,
    private val seenState: MutableState<Boolean>,
    private val dueState: MutableState<Boolean>,
    private val canPinState: State<Boolean?>,
    /** Null where the launcher cannot place a widget for the app; the page then says how to by hand. */
    val onAddWidget: (() -> Unit)?,
) {
    val seen: Boolean get() = seenState.value

    /**
     * The page is owed this start and can be drawn: whether the launcher can pin has been answered,
     * so the page never redraws with a different button under the user's thumb.
     */
    val due: Boolean get() = dueState.value && canPinState.value != null

    fun markSeen() {
        settings.markWidgetPageSeen()
        seenState.value = true
        dueState.value = false
    }
}

/**
 * The widget page's state for an install whose first run is [step]. Whether it is owed is decided at
 * each activity start, never mid-use: a check-out that brings the second day would otherwise swap the
 * page in over the screen the user is on. See [FirstRun.widgetPageOwed] for the rule.
 *
 * Every question to the widget service is a binder call, so each is asked off the main thread.
 */
@Composable
fun rememberWidgetPage(container: AppContainer, step: FirstRun.Step, seenWelcome: Boolean): WidgetPageState {
    val settings = container.settings
    val widgets = container.homeWidgets
    val seen = rememberSaveable { mutableStateOf(settings.hasSeenWidgetPage()) }
    val due = remember { mutableStateOf(false) }
    val canPin = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(widgets) {
        canPin.value = withContext(Dispatchers.IO) { runCatching { widgets.canRequestPin() }.getOrDefault(false) }
    }
    val onAddWidget: (() -> Unit)? = remember(canPin.value) {
        if (canPin.value == true) {
            { runCatching { widgets.requestPin() } }
        } else {
            null
        }
    }
    val currentSeenWelcome by rememberUpdatedState(seenWelcome)
    var starts by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(Unit) {
        starts++
        onStopOrDispose { }
    }
    LaunchedEffect(starts, step) {
        if (step != FirstRun.Step.NONE || seen.value) return@LaunchedEffect
        val placed = withContext(Dispatchers.IO) { runCatching { widgets.placed() }.getOrNull() }
        // Unknown counts as placed for this start: a page about a widget the user may already have
        // is the worse miss.
        if (placed != false) {
            // A widget seen placed settles the page for good, so no later start asks again.
            if (placed == true) {
                settings.markWidgetPageSeen()
                seen.value = true
            }
            return@LaunchedEffect
        }
        val days = try {
            container.repository.daysWithSession(container.habitRepository.selectedHabitIdFlow().first())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Not owed this start; the next one asks again.
            0
        }
        due.value = FirstRun.widgetPageOwed(currentSeenWelcome, seen.value, days, widgetPlaced = false)
    }
    return remember(onAddWidget) { WidgetPageState(settings, seen, due, canPin, onAddWidget) }
}
