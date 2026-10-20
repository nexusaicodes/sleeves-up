package com.checkin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.about.AboutCard
import com.checkin.app.ui.components.ActionRow
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import kotlinx.coroutines.launch

/**
 * Settings holds a link to the habits, the app language, and About, and the shortness is by design.
 *
 * There is no daily target to configure, sessions are immutable, and a notification's opt-out is its
 * channel — an in-app switch beside one could only ever agree with it or lie about it, and Android
 * has no API to migrate a stored opt-out back into the system. The one thing a user does set, each
 * habit's reminder time, is *timing*, not consent — no channel setting can say when a reminder is
 * due — and it lives on the Habits screen beside the habit it belongs to, so this screen holds no
 * state of its own; [runningHabitName] is passed in only to lock the Habits link while a session
 * runs. The language is the platform's per-app setting: [LanguageRow] reads and writes it
 * through `LocaleManager`, so its short list and Android's own screen cannot disagree.
 */
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    runningHabitName: String?,
    onOpenHabits: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    // Screen-scoped, not item-scoped: the About card that posts these messages is a lazy item, and a
    // scope remembered inside it is cancelled the moment the card scrolls away.
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.habits_section)) {
                // Locked while a session runs, as the switcher is: the app is about the running habit
                // until it is checked out of.
                ActionRow(
                    label = stringResource(R.string.habits_manage),
                    icon = if (runningHabitName ==
                        null
                    ) {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    } else {
                        Icons.Default.Lock
                    },
                    contentDescription = null,
                    value = runningHabitName?.let { stringResource(R.string.habits_manage_locked, it) },
                    enabled = runningHabitName == null,
                    onClick = onOpenHabits,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.language_section)) {
                LanguageRow()
            }
        }
        item {
            AboutCard(
                onOpenPrivacy = onOpenPrivacy,
                onOpenLicenses = onOpenLicenses,
                showMessage = showMessage,
            )
        }
    }
}
