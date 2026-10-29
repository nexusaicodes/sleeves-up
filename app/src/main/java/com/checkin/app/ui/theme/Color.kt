package com.checkin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.adevinta.spark.tokens.SparkColors
import com.adevinta.spark.tokens.darkSparkColors
import com.adevinta.spark.tokens.lightSparkColors

/**
 * Mudda's roles for one theme, plus the values derived from them for slots mudda has no colour for
 * (`docs/spark-migration.md` 2.2 holds both tables, with the contrast of every pair that carries text).
 *
 * Mudda is the source and nothing gates the copy: a change in mudda is a change here by hand.
 */
private data class Palette(
    /** The page. */
    val paper: Color,
    /** A raised surface: cards, the time picker's dial. */
    val paper200: Color,
    /** A higher or pressed surface, and every neutral container. */
    val paper300: Color,
    /** Text and primary fills. */
    val ink: Color,
    /** Quieter text. */
    val inkMuted: Color,
    /** Marks and large labels; under 4.5:1 on light paper, so never small text. */
    val brass: Color,
    /** Brass surfaces under paper-coloured text. */
    val brassFill: Color,
    /** The one accent: focus, errors, what needs attention. Nothing decorative. */
    val vermilion: Color,
    /** Brass-fill at 16% over paper. */
    val supportContainer: Color,
    /** Brass-fill moved 25% toward ink (light) or white (dark). */
    val supportVariant: Color,
    /** Vermilion at 14% over paper. */
    val accentContainer: Color,
    /** Vermilion moved toward ink (light) or white (dark). */
    val accentVariant: Color,
    /** Ink-muted mixed into paper until it clears 3:1 on paper and paper-200. */
    val outline: Color,
    val surfaceDark: Color,
    val onSurfaceDark: Color,
    /**
     * The elevation overlay. Unused in light, where Spark returns a light surface unraised; in dark,
     * chosen so Spark's elevation steps land on mudda's night papers (6dp is exactly paper-200).
     */
    val surfaceTint: Color,
    /** The third chart hue, the one colour not in mudda; see [descriptiveHues]. */
    val teal: Color,
)

private val Light = Palette(
    paper = Color(0xFFF1E6CE),
    paper200 = Color(0xFFE9DCC0),
    paper300 = Color(0xFFDFD0B0),
    ink = Color(0xFF1B2A4A),
    inkMuted = Color(0xFF46536E),
    brass = Color(0xFF8C6A2E),
    brassFill = Color(0xFF7F5F28),
    vermilion = Color(0xFFB3341C),
    supportContainer = Color(0xFFDFD0B3),
    supportVariant = Color(0xFF665230),
    accentContainer = Color(0xFFE8CDB5),
    accentVariant = Color(0xFF8D3228),
    outline = Color(0xFF717886),
    surfaceDark = Color(0xFF1B2A4A),
    onSurfaceDark = Color(0xFFF1E6CE),
    surfaceTint = Color(0xFFF1E6CE),
    teal = Color(0xFF2C6A70),
)

private val Dark = Palette(
    paper = Color(0xFF0E1830),
    paper200 = Color(0xFF15233F),
    paper300 = Color(0xFF1C2C4D),
    ink = Color(0xFFEFE4CB),
    inkMuted = Color(0xFFA7B0C2),
    brass = Color(0xFFC9A56A),
    brassFill = Color(0xFFC9A56A),
    vermilion = Color(0xFFE5583B),
    supportContainer = Color(0xFF2C2F39),
    supportVariant = Color(0xFFD6BC8F),
    accentContainer = Color(0xFF2C2132),
    accentVariant = Color(0xFFEA7962),
    outline = Color(0xFF6A7388),
    surfaceDark = Color(0xFF15233F),
    onSurfaceDark = Color(0xFFEFE4CB),
    surfaceTint = Color(0xFF4F7EBC),
    teal = Color(0xFF79B2B0),
)

/**
 * Every Spark slot from [this] palette, following mudda's own intent mapping: primary → ink,
 * secondary → brass-fill, accent and error → vermilion, neutral and info → ink-muted, success → ink,
 * warning → brass-fill. A filled intent writes in paper; its container writes in ink.
 *
 * Every intent is set, used or not, so none of Spark's default (Leboncoin) colours ships. The app
 * uses no success, accent or attention intent for anything a session or a day is; those are set only
 * so nothing falls through to an orange.
 */
@Suppress("LongMethod") // One line per Spark slot: split, no one place would show that every slot is set.
private fun Palette.slots(base: SparkColors): SparkColors = base.copy(
    main = ink,
    onMain = paper,
    mainContainer = paper300,
    onMainContainer = ink,
    mainVariant = inkMuted,
    onMainVariant = paper,
    support = brassFill,
    onSupport = paper,
    supportContainer = supportContainer,
    onSupportContainer = ink,
    supportVariant = supportVariant,
    onSupportVariant = paper,
    accent = vermilion,
    onAccent = paper,
    accentContainer = accentContainer,
    onAccentContainer = ink,
    accentVariant = accentVariant,
    onAccentVariant = paper,
    success = ink,
    onSuccess = paper,
    successContainer = paper300,
    onSuccessContainer = ink,
    alert = brassFill,
    onAlert = paper,
    alertContainer = supportContainer,
    onAlertContainer = ink,
    error = vermilion,
    onError = paper,
    errorContainer = accentContainer,
    onErrorContainer = ink,
    info = inkMuted,
    onInfo = paper,
    infoContainer = paper300,
    onInfoContainer = ink,
    neutral = inkMuted,
    onNeutral = paper,
    neutralContainer = paper300,
    onNeutralContainer = ink,
    ai = inkMuted,
    onAi = paper,
    aiContainer = paper300,
    onAiContainer = ink,
    focus = vermilion,
    onFocus = paper,
    focusContainer = paper300,
    onFocusContainer = ink,
    new = ink,
    onNew = paper,
    newContainer = paper300,
    onNewContainer = ink,
    business = inkMuted,
    onBusiness = paper,
    businessContainer = paper300,
    onBusinessContainer = ink,
    service = inkMuted,
    onService = paper,
    serviceContainer = paper300,
    onServiceContainer = ink,
    background = paper,
    onBackground = ink,
    // The bridge hands these to M3's surfaceVariant/onSurfaceVariant: SectionCard's raised paper and
    // the app's secondary text.
    backgroundVariant = paper200,
    onBackgroundVariant = inkMuted,
    // Surface equals the page, as in Spark's own defaults; a surface is raised by elevation.
    surface = paper,
    onSurface = ink,
    surfaceInverse = ink,
    onSurfaceInverse = paper,
    surfaceDark = surfaceDark,
    onSurfaceDark = onSurfaceDark,
    surfaceTint = surfaceTint,
    outline = outline,
    outlineHigh = ink,
)

/** Navy ink on cream paper. */
internal val SparkLight: SparkColors = Light.slots(lightSparkColors())

/** Cream ink on night-navy paper. */
internal val SparkDark: SparkColors = Dark.slots(darkSparkColors())

@Composable
@ReadOnlyComposable
private fun palette(): Palette = if (isSystemInDarkTheme()) Dark else Light

/**
 * Theme-aware colour for a day the user showed up: brass, mudda's colour for marks.
 *
 * There is deliberately **one** strength and no overload taking a fraction. An alpha proportional
 * to a day's hours against the user's longest day makes the shade a ranking: one nine-hour day
 * re-renders every ordinary day as a fainter version of it, for good. A day either has a session or
 * it does not, and that is the whole of what a cell says. There is no second colour either: days are
 * never classified, so there is no "worse" shade for one to mean.
 *
 * Callers that need this over a background at reduced weight apply their own alpha for legibility —
 * a fixed constant, never a figure derived from a duration.
 */
@Composable
@ReadOnlyComposable
fun dayColor(): Color = palette().brass

/** A filled button's container and the label on top of it. */
data class ActionColors(val container: Color, val content: Color)

/**
 * Colours for starting the clock: brass-fill under paper. The same hue the calendar draws a
 * recorded day in, in the deeper brass mudda keeps for surfaces under text (in dark the two brasses
 * are one value). Paper on it is 4.75:1 in light and 7.61:1 in dark.
 */
@Composable
@ReadOnlyComposable
fun startActionColors(): ActionColors = with(palette()) { ActionColors(brassFill, paper) }

/**
 * Colours for stopping the clock: ink under paper, the stop half of a start/stop control. There is
 * no red in the app: in mudda red means an error or something needing attention, and nothing about
 * checking out is either.
 */
@Composable
@ReadOnlyComposable
fun stopActionColors(): ActionColors = with(palette()) { ActionColors(ink, paper) }

/**
 * Three hues of equal visual weight for a descriptive split: ink, brass and teal.
 *
 * Deliberately not the day hue at three strengths: one colour at varying alpha is an intensity ramp,
 * and a ramp says one end is more than the other. These are three different colours saying three
 * different things. They are fixed tokens rather than scheme roles because Spark's M3 bridge folds
 * `tertiary` into `support`, which would leave two of the three the same brass. Teal sits across the
 * blue–yellow axis from brass, so the pair survives every common colour-vision deficiency.
 */
@Composable
@ReadOnlyComposable
fun descriptiveHues(): List<Color> = with(palette()) { listOf(ink, brass, teal) }
