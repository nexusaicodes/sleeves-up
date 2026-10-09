package com.checkin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.ui.about.AboutCard
import com.checkin.app.ui.components.ActionRow
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.ReminderHourPicker
import com.checkin.app.ui.components.SectionCard
import com.checkin.app.ui.components.reminderTimeSummary
import kotlinx.coroutines.launch

/**
 * Settings holds one setting — the reminder hour — and About, and the shortness is by design.
 *
 * There is no daily target to configure, sessions are immutable, and a notification's opt-out is its
 * channel — an in-app switch beside one could only ever agree with it or lie about it, and Android
 * has no API to migrate a stored opt-out back into the system. The reminder hour is the exception
 * because it is *timing*, not consent: no channel setting can say when a reminder is due, and a
 * user who wants none still turns the channel off.
 */
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }

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
            SectionCard(title = stringResource(R.string.reminders_section)) {
                ActionRow(
                    label = stringResource(R.string.reminder_time_label),
                    value = reminderTimeSummary(uiState.reminderHour),
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    onClick = { picking = true },
                )
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

    if (picking) {
        ReminderHourPicker(
            selectedHour = uiState.reminderHour,
            onPicked = { hour ->
                picking = false
                viewModel.setReminderHour(hour)
            },
            onDismiss = { picking = false },
        )
    }
}
