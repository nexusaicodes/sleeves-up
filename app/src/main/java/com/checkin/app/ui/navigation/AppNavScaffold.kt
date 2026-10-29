package com.checkin.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.adevinta.spark.components.icons.Icon
import com.adevinta.spark.components.text.Text
import com.checkin.app.R
import com.checkin.app.ui.AppUpdateOffer
import com.checkin.app.ui.ForfeitUpdateCheck
import com.checkin.app.ui.UpdateCheckTurn
import com.checkin.app.ui.checkin.CheckInViewModel
import com.checkin.app.ui.components.AppIcons
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.habits.HabitsViewModel
import com.checkin.app.ui.presence.PresenceGate
import com.checkin.app.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/**
 * Top-level chrome: a start-aligned title bar and the bottom nav around the nav host. The title names the
 * active section, so a screen never has to draw its own heading; screens receive the combined inset
 * through the Scaffold's padding. The bar's actions hold the [HabitSwitcher] on every tab.
 * With [offerUpdates] it composes [AppUpdateOffer] inside the snackbar host it posts to; otherwise, and
 * while its presence gate is up, it forfeits the [updateTurn].
 */
@Composable
internal fun AppNavScaffold(navController: NavHostController, offerUpdates: Boolean, updateTurn: UpdateCheckTurn) {
    // Hoisted here (shared with the Check-In tab) so its presence gate can render full-screen above
    // the chrome — the camera preview and the gate's own controls must not sit under the bottom nav.
    val checkInViewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory)
    val checkInState by checkInViewModel.uiState.collectAsStateWithLifecycle()
    // Activity-scoped, so the switcher reads the same instance AppRoot's first-run steps do.
    val habitsViewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory)
    val habitsState by habitsViewModel.uiState.collectAsStateWithLifecycle()

    if (checkInState.showPresenceGate) {
        // Full-screen modal gate: the Scaffold is not composed underneath (gate XOR chrome), so the
        // nav bar can't overlap the gate's dismiss and device-unlock controls. Back dismisses it.
        BackHandler { checkInViewModel.dismissPresenceGate() }
        PresenceGate(
            onAuthSuccess = { checkInViewModel.onAuthSuccess() },
            onDismiss = { checkInViewModel.dismissPresenceGate() },
        )
        ForfeitUpdateCheck(updateTurn)
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val runningName = habitsState.running?.let { it.name ?: stringResource(R.string.habit_unnamed) }
    val lockedMessage = runningName?.let { stringResource(R.string.habit_switch_locked, it) }.orEmpty()

    // One back-stack subscription drives both bars: the title and the selected nav item can never
    // disagree about which section is showing.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = titledScreens.firstOrNull { it.route == currentRoute } ?: Screen.CheckIn
    // A detail screen keeps its parent tab lit rather than leaving the bar with nothing selected.
    val selectedTab = when (currentScreen) {
        is Screen.Detail -> currentScreen.parent
        is Screen.Tab -> currentScreen
    }

    // Only a detail screen gets a back arrow.
    val onBack: (() -> Unit)? = if (currentScreen is Screen.Detail) {
        { navController.popBackStack() }
    } else {
        null
    }

    Scaffold(
        topBar = {
            AppTopBar(currentScreen = currentScreen, onBack = onBack) {
                // On every tab, because every tab shows the selected habit's record. Not on a detail
                // screen: those are about the app, not about a habit, and Habits manages them all.
                if (currentScreen is Screen.Tab) {
                    HabitSwitcher(
                        state = habitsState,
                        onOpen = { navController.navigate(Screen.Habits.route) { launchSingleTop = true } },
                        onLocked = { scope.launch { snackbarHostState.showSnackbar(lockedMessage) } },
                    )
                }
            }
        },
        bottomBar = { BottomNavigationBar(navController, currentScreen, selectedTab) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavigationGraph(navController, innerPadding, checkInViewModel, runningName)
            if (offerUpdates) {
                AppUpdateOffer(turn = updateTurn, sessionOpen = habitsState.running != null)
            } else {
                ForfeitUpdateCheck(updateTurn)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(currentScreen: Screen, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    // Start-aligned, so the title and the habit switcher at the end each keep their own side rather
    // than the title being centred in whatever width the switcher leaves.
    TopAppBar(
        title = {
            Text(
                stringResource(currentScreen.titleRes),
                style = LocalAppTypography.current.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = actions,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        sparkIcon = AppIcons.Back,
                        contentDescription = stringResource(R.string.nav_back),
                    )
                }
            }
        },
    )
}
