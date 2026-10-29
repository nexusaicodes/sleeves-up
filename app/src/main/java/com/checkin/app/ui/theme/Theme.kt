package com.checkin.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.adevinta.spark.SparkFeatureFlag
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.tokens.SparkColors
import com.adevinta.spark.tokens.SparkFontFamily
import com.checkin.app.R
import com.checkin.app.util.TimePatterns
import com.checkin.app.util.displayLocale

/**
 * Spark's feature flags for the whole app: the rebranded `Button.*` family, and an edge-to-edge
 * activity (`MainActivity` calls `enableEdgeToEdge`), which Spark's `ModalScaffold` reads to lay its
 * window out under the system bars.
 */
private val AppSparkFlags = SparkFeatureFlag(useRebrandedButtons = true, isContainingActivityEdgeToEdge = true)

/** Luminance above which a background takes dark system-bar icons. */
private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f

@Composable
fun CheckInAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) SparkDark else SparkLight
    val lightBackground = colors.background.luminance() > LIGHT_BACKGROUND_LUMINANCE
    val view = LocalView.current

    // Skip the window side effect under @Preview (there is no host Activity to cast to).
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            window.isNavigationBarContrastEnforced = false
            insetsController.isAppearanceLightStatusBars = lightBackground
            insetsController.isAppearanceLightNavigationBars = lightBackground
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
    val typography = remember(locale) { sparkTypographyFor(locale) }
    val appTypography = remember(locale) { appTypographyFor(locale) }
    // Built once per locale: SparkFontFamily has no equality, so a fresh one on every recomposition
    // (as Spark's composable sparkFontFamily() builder returns) would change LocalAppSparkFontFamily
    // and recompose everything under the theme. No token highlighter, as the builder's default.
    val fontFamily = remember(locale) {
        SparkFontFamily(useSparkTokensHighlighter = false, fontFamily = sparkFamilyFor(locale))
    }

    SparkTheme(
        colors = colors,
        typography = typography,
        fontFamily = fontFamily,
        sparkFeatureFlag = AppSparkFlags,
    ) {
        CompositionLocalProvider(
            LocalTimePatterns provides patterns,
            LocalAppTypography provides appTypography,
            LocalAppSparkFontFamily provides fontFamily,
            content = content,
        )
    }
}

/**
 * The content under a copy of the current Spark colours, changed by [transform] — the one way to
 * give a Spark component colours it takes no parameter for (its button reads `main`/`onMain`).
 *
 * Every other value is the app's, read from the enclosing [CheckInAppTheme]: a `SparkTheme` called
 * bare re-defaults its font family to Nunito Sans, which the build discards, so it would crash, and
 * its feature flags to Spark's, which would swap the button family under this one subtree. This and
 * [CheckInAppTheme] are the only `SparkTheme` calls in the app (`SparkThemeCallSitesTest`).
 */
@Composable
fun SparkColorScope(transform: SparkColors.() -> SparkColors, content: @Composable () -> Unit) {
    SparkTheme(
        colors = SparkTheme.colors.transform(),
        shapes = SparkTheme.shapes,
        typography = SparkTheme.typography,
        sparkFeatureFlag = SparkTheme.featureFlag,
        fontFamily = LocalAppSparkFontFamily.current,
        content = content,
    )
}

/** The family [CheckInAppTheme] passed to Spark, for [SparkColorScope] to pass again. */
private val LocalAppSparkFontFamily = staticCompositionLocalOf<SparkFontFamily> {
    error("LocalAppSparkFontFamily is provided by CheckInAppTheme")
}

/**
 * The reader's [TimePatterns], for every `TimeFormat` call on screen. There is no default: a
 * composable reached outside [CheckInAppTheme] would otherwise format in English under any language
 * with nothing failing, which is exactly the drift resource-held patterns exist to prevent.
 */
val LocalTimePatterns = staticCompositionLocalOf<TimePatterns> {
    error("LocalTimePatterns is provided by CheckInAppTheme")
}
