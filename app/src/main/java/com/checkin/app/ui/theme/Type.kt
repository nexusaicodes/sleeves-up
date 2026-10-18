package com.checkin.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.checkin.app.R
import java.util.Locale

/**
 * Two families, split by the size they are read at.
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

/** The app's type for [locale]: the Outfit/Manrope split, or Plex Arabic throughout for Arabic. */
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
 * The same style with tabular figures — every digit set to one width.
 *
 * For a clock that ticks, and load-bearing rather than a nicety: Outfit's default `1` is 321 units
 * against `0` at 659, a 2.05x spread that would make the gauge lurch sideways every time a `1`
 * entered or left the readout. `tnum` is an OpenType feature, independent of which family the style
 * carries, and both families here resolve it to genuinely uniform widths (Outfit 590, Manrope 1240).
 * A face without the feature ignores it and renders unchanged.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
