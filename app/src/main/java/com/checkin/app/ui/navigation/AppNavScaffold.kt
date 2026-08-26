package com.checkin.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.checkin.app.R
import com.checkin.app.ui.checkin.CheckInViewModel
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.presence.PresenceGate

/**
 * Top-level chrome: a centered title bar and the bottom nav around the nav host. The title names the
 * active section, so a screen never has to draw its own heading; screens receive the combined inset
 * through the Scaffold's padding.
 */
@Composable
fun AppNavScaffold(navController: NavHostController) {
    // Hoisted here (shared with the Check-In tab) so its presence gate can render full-screen above
    // the chrome — the camera preview and the gate's own controls must not sit under the bottom nav.
    val checkInViewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory)
    val checkInState by checkInViewModel.uiState.collectAsStateWithLifecycle()

    if (checkInState.showPresenceGate) {
        // Full-screen modal gate: the Scaffold is not composed underneath (gate XOR chrome), so the
        // nav bar can't overlap the gate's dismiss and device-unlock controls. Back dismisses it.
        BackHandler { checkInViewModel.dismissPresenceGate() }
        PresenceGate(
            onAuthSuccess = { checkInViewModel.onAuthSuccess() },
            onDismiss = { checkInViewModel.dismissPresenceGate() },
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }

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

    // Only a detail screen gets a back arrow; the tabs keep the bare centred title.
    val onBack: (() -> Unit)? = if (currentScreen is Screen.Detail) {
        { navController.popBackStack() }
    } else {
        null
    }

    Scaffold(
        topBar = { AppTopBar(currentScreen = currentScreen, onBack = onBack) },
        bottomBar = { BottomNavigationBar(navController, currentScreen, selectedTab) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavigationGraph(navController, innerPadding, checkInViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(currentScreen: Screen, onBack: (() -> Unit)?) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(currentScreen.titleRes)) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                    )
                }
            }
        },
    )
}
