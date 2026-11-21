package com.checkin.app.ui.navigation

import com.adevinta.spark.icons.CalendarTextOutline
import com.adevinta.spark.icons.ClockOutline
import com.adevinta.spark.icons.GearOutline
import com.adevinta.spark.icons.GraphOutline
import com.adevinta.spark.icons.LeboncoinIcons
import com.adevinta.spark.icons.SparkIcon
import com.checkin.app.R

/**
 * A destination's [route] doubles as the id a **saved back stack** is restored against
 * (`route.hashCode()`), because `rememberNavController` writes the stack into the Activity's saved
 * instance state. `NavController` throws when a restored entry names a destination the graph no
 * longer has — so renaming a route is not the free internal rename it looks like: an install
 * updated while parked on the old route can crash on launch until the task is cleared. Deep links
 * are a separate concern and none are declared; this hazard exists without them.
 */
sealed class Screen(val route: String, val titleRes: Int) {

    /** A bottom-nav destination. */
    sealed class Tab(route: String, titleRes: Int, val icon: SparkIcon) : Screen(route, titleRes)

    /**
     * A destination pushed above [parent]: the top bar puts a back arrow before the title and [parent]
     * stays selected below, so a sub-screen still reads as part of its section.
     */
    sealed class Detail(route: String, titleRes: Int, val parent: Tab) : Screen(route, titleRes)

    data object CheckIn : Tab("checkin", R.string.nav_check_in, LeboncoinIcons.ClockOutline)
    data object History : Tab("history", R.string.nav_history, LeboncoinIcons.CalendarTextOutline)
    data object Reports : Tab("reports", R.string.nav_reports, LeboncoinIcons.GraphOutline)
    data object Settings : Tab("settings", R.string.nav_settings, LeboncoinIcons.GearOutline)
    data object Privacy : Detail("privacy", R.string.nav_privacy, Settings)
    data object Licenses : Detail("licenses", R.string.nav_licenses, Settings)
    data object Habits : Detail("habits", R.string.nav_habits, Settings)
}

/**
 * Every destination, in bottom-nav order with details after their parents. The one list a new screen
 * must be added to: [tabs] and [titledScreens] are both derived from it, so they cannot disagree.
 *
 * A route missing from here falls back to the start destination and silently mislabels the screen
 * rather than failing, which is why `ScreenTest` pins what can be checked without it.
 */
internal val allScreens: List<Screen> = listOf(
    Screen.CheckIn,
    Screen.History,
    Screen.Reports,
    Screen.Settings,
    Screen.Habits,
    Screen.Privacy,
    Screen.Licenses,
)

internal val tabs: List<Screen.Tab> = allScreens.filterIsInstance<Screen.Tab>()

/** Every destination the title bar can name. */
internal val titledScreens: List<Screen> = allScreens
