package com.checkin.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.adevinta.spark.ExperimentalSparkApi
import com.adevinta.spark.components.appbar.NavigationBar
import com.adevinta.spark.components.appbar.NavigationBarItem
import com.adevinta.spark.components.text.Text

@OptIn(ExperimentalSparkApi::class)
@Composable
internal fun BottomNavigationBar(navController: NavController, currentScreen: Screen, selectedTab: Screen.Tab) {
    NavigationBar {
        tabs.forEach { screen ->
            val title = stringResource(screen.titleRes)
            NavigationBarItem(
                // Spark draws the icon with no description: the label beneath names the tab.
                icon = screen.icon,
                label = { Text(title) },
                selected = selectedTab == screen,
                onClick = {
                    // Details are popped first, down to whichever tab sits beneath them. `saveState`
                    // would otherwise store a detail as part of that tab's stack and `restoreState`
                    // would put the user back on it next time they tap the tab — Licenses reopening
                    // under Settings, or Habits (pushed from any tab's switcher, so often above a tab
                    // that is not its parent) reopening under that tab.
                    if (currentScreen is Screen.Detail) {
                        while (navController.currentScreen() is Screen.Detail && navController.popBackStack()) continue
                        // Popping already landed on the tapped tab; navigating again would re-save it.
                        if (navController.currentScreen() == screen) return@NavigationBarItem
                    }

                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

private fun NavController.currentScreen(): Screen? = allScreens.firstOrNull { it.route == currentDestination?.route }
