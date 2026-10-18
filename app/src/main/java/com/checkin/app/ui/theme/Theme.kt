package com.checkin.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.checkin.app.R
import com.checkin.app.util.TimePatterns
import com.checkin.app.util.displayLocale

// CheckIn brand scheme, seeded from the launcher indigo (#3F51B5). Dynamic color is intentionally
// not used, so the app presents one consistent brand identity on every device.
private val BrandLight = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF171B2C),
    tertiary = Color(0xFF76546D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2EFF7),
    inversePrimary = Color(0xFFBAC3FF),
    surfaceTint = Color(0xFF4355B9),
    scrim = Color(0xFF000000),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF11227A),
    primaryContainer = Color(0xFF2A3BA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFFE5BAD8),
    onTertiary = Color(0xFF44273E),
    tertiaryContainer = Color(0xFF5D3D55),
    onTertiaryContainer = Color(0xFFFFD7F1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    inverseSurface = Color(0xFFE4E1E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    inversePrimary = Color(0xFF4355B9),
    surfaceTint = Color(0xFFBAC3FF),
    scrim = Color(0xFF000000),
)

@Composable
fun CheckInAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) BrandDark else BrandLight
    val view = LocalView.current

    // Skip the window side effect under @Preview (there is no host Activity to cast to).
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            window.isNavigationBarContrastEnforced = false
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val configuration = LocalConfiguration.current
    val language = stringResource(R.string.language_code)
    val locale = remember(configuration, language) {
        val locales = configuration.locales
        displayLocale((0 until locales.size()).map { locales[it] }, language)
    }
    val hoursMinutes = stringResource(R.string.time_hours_minutes)
    val minutesSeconds = stringResource(R.string.time_minutes_seconds)
    val clock = stringResource(R.string.time_clock_pattern)
    val dateWithYear = stringResource(R.string.date_with_year_pattern)
    val dateWithWeekday = stringResource(R.string.date_with_weekday_pattern)
    val axisDay = stringResource(R.string.date_axis_day_pattern)
    val axisMonth = stringResource(R.string.date_axis_month_pattern)
    val axisMonthWithYear = stringResource(R.string.date_axis_month_year_pattern)
    val monthYear = stringResource(R.string.date_month_year_pattern)
    // Keyed on the locale alone: the patterns are resources of that same configuration, so a
    // language change recreates the activity and rebuilds both together.
    val patterns = remember(locale) {
        TimePatterns(
            locale = locale,
            hoursMinutes = hoursMinutes,
            minutesSeconds = minutesSeconds,
            clock = clock,
            dateWithYear = dateWithYear,
            dateWithWeekday = dateWithWeekday,
            axisDay = axisDay,
            axisMonth = axisMonth,
            axisMonthWithYear = axisMonthWithYear,
            monthYear = monthYear,
        )
    }
    val typography = remember(locale) { typographyFor(locale) }

    CompositionLocalProvider(LocalTimePatterns provides patterns) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

/**
 * The reader's [TimePatterns], for every `TimeFormat` call on screen. There is no default: a
 * composable reached outside [CheckInAppTheme] would otherwise format in English under any language
 * with nothing failing, which is exactly the drift resource-held patterns exist to prevent.
 */
val LocalTimePatterns = staticCompositionLocalOf<TimePatterns> {
    error("LocalTimePatterns is provided by CheckInAppTheme")
}
