package com.checkin.app.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.checkin.app.ui.about.LicensesScreen
import com.checkin.app.ui.about.PrivacyScreen
import com.checkin.app.ui.checkin.CheckInScreen
import com.checkin.app.ui.checkin.CheckInViewModel
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.habits.HabitsScreen
import com.checkin.app.ui.history.HistoryScreen
import com.checkin.app.ui.reports.ReportsScreen
import com.checkin.app.ui.settings.SettingsScreen

@Composable
internal fun NavigationGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
    checkInViewModel: CheckInViewModel,
    /** The habit with the open session, named; null when none is open. Managing habits waits for it. */
    runningHabitName: String?,
) {
    NavHost(
        navController,
        startDestination = Screen.CheckIn.route,
        enterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 200, easing = LinearEasing))
        },
    ) {
        composable(Screen.CheckIn.route) {
            ConstrainedContent { CheckInScreen(innerPadding = innerPadding, viewModel = checkInViewModel) }
        }
        composable(Screen.History.route) {
            // History manages its own width (two-pane on expanded), so it is not width-capped here.
            HistoryScreen(innerPadding = innerPadding)
        }
        composable(Screen.Reports.route) {
            ConstrainedContent { ReportsScreen(innerPadding = innerPadding) }
        }
        composable(Screen.Settings.route) {
            ConstrainedContent {
                SettingsScreen(
                    innerPadding = innerPadding,
                    runningHabitName = runningHabitName,
                    onOpenHabits = {
                        navController.navigate(Screen.Habits.route) { launchSingleTop = true }
                    },
                    // launchSingleTop: a double tap on a row would otherwise push two identical
                    // entries, so the first back press appears to do nothing.
                    onOpenPrivacy = {
                        navController.navigate(Screen.Privacy.route) { launchSingleTop = true }
                    },
                    onOpenLicenses = {
                        navController.navigate(Screen.Licenses.route) { launchSingleTop = true }
                    },
                )
            }
        }
        composable(Screen.Habits.route) {
            // Both ways in are locked while a session runs, but a session can still start under an
            // open Habits screen — a nudge tap checks in through the root gate — so it closes then.
            if (runningHabitName != null) {
                LaunchedEffect(Unit) { navController.popBackStack(Screen.Habits.route, inclusive = true) }
            }
            // Choosing a habit here is the switch, so it returns to wherever the user came from.
            ConstrainedContent {
                HabitsScreen(innerPadding = innerPadding, onSelected = { navController.popBackStack() })
            }
        }
        composable(Screen.Privacy.route) {
            ConstrainedContent { PrivacyScreen(innerPadding = innerPadding) }
        }
        composable(Screen.Licenses.route) {
            ConstrainedContent { LicensesScreen(innerPadding = innerPadding) }
        }
    }
}
