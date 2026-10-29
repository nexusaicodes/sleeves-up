package com.checkin.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.adevinta.spark.tokens.SparkTypography
import com.adevinta.spark.tokens.sparkTypography
import com.checkin.app.R
import java.util.Locale

/**
 * Two Latin families, split by the size they are read at (Arabic replaces both — see `Arabic`).
 *
 * Outfit's x-height is 0.460 em against Manrope's 0.540 — a difference that decides the split rather
 * than following from it. Outfit reads as elegant on the 45sp gauge and would be thin in a 12sp
 * settings row; Manrope's larger x-height is what makes it hold up there. Using them the other way
 * round is the mistake this arrangement exists to avoid.
 *
 * Each weight is a **static instance** cut from the upstream variable font, not the variable file
 * itself. A variable font would be 331 KB smaller across the two families, but selecting a weight
 * from one needs `FontVariation.Settings`, which is `@ExperimentalTextApi`; without pinning the axis
 * the file loads at its default instance and the platform fakes the heavier weights by smearing the
 * glyphs. Four real cuts per family buys a stable API and genuine weights for that 331 KB.
 *
 * The four are the ones the app actually asks for: Normal, Medium, SemiBold and Bold. Instancing
 * preserves `tnum` and the embedded copyright notice, both of which are load-bearing here.
 */

/** Display, headline and title sizes — the app's voice. */
private val Display = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

/** Body and label sizes — everything read at length or at 14sp and below. */
private val Text = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/**
 * Arabic, at every size. Neither Latin family has an Arabic glyph, and a Compose `FontFamily` picks
 * one file per weight rather than falling back glyph by glyph, so Arabic cannot be added *to* the
 * families above — it replaces both. Plex Arabic carries its own Latin and digits (tabular by
 * default, all ten at 600 units), so a habit named in English and the Latin-digit clock still render
 * in one face beside the Arabic copy rather than in a system fallback.
 */
private val Arabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

// Only the family is swapped: every size, line height and letter spacing stays as Material 3 tuned
// it. Styles the app doesn't use yet are mapped too, so a future call site inherits the right family
// rather than silently falling back to the system font.
private val Default = Typography()

/**
 * The app's full Material 3 type for [locale]: the Outfit/Manrope split, or Plex Arabic throughout.
 *
 * Not the theme's typography: Spark's bridge builds that from [sparkTypographyFor]. This is the
 * fallback for what Spark cannot reach — the M3 time picker's local theme, and the language picker
 * naming each language in its own face — and the source [AppTypography] is cut from.
 */
fun typographyFor(locale: Locale): Typography {
    val arabic = locale.language == "ar"
    val display = if (arabic) Arabic else Display
    val text = if (arabic) Arabic else Text
    return Typography(
        displayLarge = Default.displayLarge.copy(fontFamily = display),
        displayMedium = Default.displayMedium.copy(fontFamily = display),
        displaySmall = Default.displaySmall.copy(fontFamily = display),
        headlineLarge = Default.headlineLarge.copy(fontFamily = display),
        headlineMedium = Default.headlineMedium.copy(fontFamily = display),
        headlineSmall = Default.headlineSmall.copy(fontFamily = display),
        titleLarge = Default.titleLarge.copy(fontFamily = display),
        titleMedium = Default.titleMedium.copy(fontFamily = display),
        titleSmall = Default.titleSmall.copy(fontFamily = display),
        bodyLarge = Default.bodyLarge.copy(fontFamily = text),
        bodyMedium = Default.bodyMedium.copy(fontFamily = text),
        bodySmall = Default.bodySmall.copy(fontFamily = text),
        labelLarge = Default.labelLarge.copy(fontFamily = text),
        labelMedium = Default.labelMedium.copy(fontFamily = text),
        labelSmall = Default.labelSmall.copy(fontFamily = text),
    )
}

/**
 * The one family Spark stamps onto every style it holds: Manrope, or Plex Arabic under Arabic.
 *
 * Spark's theme applies a single family to all of its typography, so the Outfit/Manrope split
 * cannot live inside it. Spark's own controls draw body and label text (button labels, fields), which
 * is Manrope's size range; Outfit's sizes are the app's, and live in [AppTypography].
 */
fun sparkFamilyFor(locale: Locale): FontFamily = if (locale.language == "ar") Arabic else Text

/**
 * Spark's typography at Material 3's metrics, so every fit budget sized against M3 text holds.
 *
 * Each style is the M3 style the bridge maps onto it: `body1` is `bodyLarge` (16/24), `body2`
 * `bodyMedium` (14/20, and the default for unstyled text), `caption` `bodySmall` (12/16), `small`
 * `labelSmall` (11/16 rather than Spark's 10/14, which the calendar's weekday header is sized for),
 * and `callout`, Spark's button label, `labelLarge` (14/20 medium). The highlight weight is Medium,
 * the weight the app's label styles carry. Display and headline keep Spark's defaults: the app's
 * display text reads [AppTypography], never these.
 *
 * The family here is a placeholder: the theme passes [sparkFamilyFor] and Spark stamps it on.
 */
fun sparkTypographyFor(locale: Locale): SparkTypography {
    val m3 = typographyFor(locale)
    return sparkTypography(
        body1 = m3.bodyLarge,
        body2 = m3.bodyMedium,
        caption = m3.bodySmall,
        small = m3.labelSmall,
        callout = m3.labelLarge,
        highlightWeight = FontWeight.Medium,
    )
}

/**
 * The display, headline and title styles, in Outfit (Plex Arabic under Arabic), read through
 * [LocalAppTypography].
 *
 * They sit outside Spark's typography because Spark stamps one family on everything it holds, and
 * outside `MaterialTheme.typography` because Spark's bridge maps `titleMedium` to its 32sp bold
 * `display2`. Only the six styles the app draws are here; a new size is a new field.
 */
@Immutable
data class AppTypography(
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
)

/** The [AppTypography] for [locale], at Material 3's metrics. */
fun appTypographyFor(locale: Locale): AppTypography {
    val m3 = typographyFor(locale)
    return AppTypography(
        displayMedium = m3.displayMedium,
        displaySmall = m3.displaySmall,
        headlineMedium = m3.headlineMedium,
        headlineSmall = m3.headlineSmall,
        titleLarge = m3.titleLarge,
        titleMedium = m3.titleMedium,
    )
}

/**
 * The reader's [AppTypography]. There is no default, for the reason `LocalTimePatterns` has none:
 * a composable reached outside `CheckInAppTheme` would otherwise draw in the wrong face with
 * nothing failing.
 */
val LocalAppTypography = staticCompositionLocalOf<AppTypography> {
    error("LocalAppTypography is provided by CheckInAppTheme")
}

/**
 * The same style with tabular figures — every digit set to one width.
 *
 * For a clock that ticks, and load-bearing rather than a nicety: Outfit's default `1` is 321 units
 * against `0` at 659, a 2.05x spread that would make the gauge lurch sideways every time a `1`
 * entered or left the readout. `tnum` is an OpenType feature, independent of which family the style
 * carries, and both families here resolve it to genuinely uniform widths (Outfit 590, Manrope 1240).
 * A face without the feature ignores it and renders unchanged.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
