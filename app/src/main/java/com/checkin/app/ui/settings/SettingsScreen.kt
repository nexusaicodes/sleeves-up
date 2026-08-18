package com.checkin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checkin.app.ui.about.AboutCard
import com.checkin.app.ui.components.LocalSnackbarHostState
import kotlinx.coroutines.launch

/**
 * Settings holds no settings, and that is by design rather than by omission.
 *
 * There is no daily target to configure, sessions are immutable, and a notification's opt-out is its
 * channel — an in-app switch beside one could only ever agree with it or lie about it, and Android
 * has no API to migrate a stored opt-out back into the system. So what remains is About: the version,
 * the links out, and the licences.
 */
@Composable
fun SettingsScreen(innerPadding: PaddingValues, onOpenLicenses: () -> Unit) {
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
        item { AboutCard(onOpenLicenses = onOpenLicenses, showMessage = showMessage) }
    }
}
