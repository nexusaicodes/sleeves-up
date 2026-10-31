package com.checkin.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.adevinta.spark.icons.DumbbellCriteria
import com.adevinta.spark.icons.LeboncoinIcons
import com.adevinta.spark.icons.LotusFlowerCriteria
import com.adevinta.spark.icons.Music
import com.adevinta.spark.icons.PaintPalette
import com.adevinta.spark.icons.SparkIcon
import com.checkin.app.R
import kotlinx.coroutines.delay

/** A common habit, as the icon and the word a screen about to name one shows for it. */
class HabitExample(val icon: SparkIcon, @StringRes val nameRes: Int)

/**
 * Activities, never targets: "Running", not "Run 5k". An example with a number in it would suggest
 * a habit is something to clear, and this app has no bar to clear.
 */
private val habitExamples = listOf(
    HabitExample(AppIcons.Walking, R.string.habit_example_running),
    HabitExample(AppIcons.Book, R.string.habit_example_reading),
    HabitExample(LeboncoinIcons.LotusFlowerCriteria, R.string.habit_example_meditation),
    HabitExample(LeboncoinIcons.Music, R.string.habit_example_guitar),
    HabitExample(LeboncoinIcons.DumbbellCriteria, R.string.habit_example_gym),
    HabitExample(LeboncoinIcons.PaintPalette, R.string.habit_example_drawing),
)

/**
 * The example on show, advancing every [EXAMPLE_DWELL_MS]. One source for the icon and the word, so a
 * screen showing both cannot pair a runner with "Guitar". It holds on the first example when
 * animations are off, since a cycle is motion whatever form it takes.
 */
@Composable
fun cyclingHabitExample(): HabitExample {
    val animated = animationsEnabled()
    var index by rememberSaveable { mutableIntStateOf(0) }
    if (animated) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(EXAMPLE_DWELL_MS)
                index = (index + 1) % habitExamples.size
            }
        }
    }
    return habitExamples[if (animated) index else 0]
}

/** [example]'s icon as a [MessageIcon], crossfading when the example changes. */
@Composable
fun HabitExampleIcon(example: HabitExample) {
    Crossfade(targetState = example, animationSpec = tween(CROSSFADE_MS), label = "habitExample") {
        MessageIcon(it.icon)
    }
}

private const val EXAMPLE_DWELL_MS = 1_800L
private const val CROSSFADE_MS = 400
