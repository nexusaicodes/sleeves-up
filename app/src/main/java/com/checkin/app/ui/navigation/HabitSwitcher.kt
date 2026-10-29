package com.checkin.app.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.habits.HabitsUiState
import com.checkin.app.ui.theme.LocalAppTypography

/**
 * The habit switcher in the top bar: the habit every tab is showing, in the title's own type so the
 * bar reads as one line, and a swap mark saying a tap leads to the others. A tap opens the Habits
 * screen, which is where switching happens — one list for choosing, adding and editing, rather than
 * a menu restating half of it. It names the selected habit and nothing else, since a figure beside
 * it would invite comparing.
 *
 * **Locked while a session runs**, on every tab. There is one open session across all habits, so
 * the app is about the running habit until it is checked out of; the swap mark becomes a lock and a
 * tap calls [onLocked] to say why. The lock is the repository's too
 * ([com.checkin.app.data.repository.HabitRepository.select] refuses), so this is the explanation
 * rather than the enforcement.
 */
@Composable
internal fun HabitSwitcher(state: HabitsUiState, onOpen: () -> Unit, onLocked: () -> Unit) {
    val selected = state.selected ?: return
    val selectedName = selected.name ?: stringResource(R.string.habit_unnamed)
    val description = stringResource(R.string.habit_switcher_cd, selectedName)
    val locked = state.running != null

    TextButton(
        onClick = if (locked) onLocked else onOpen,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selectedName,
                style = LocalAppTypography.current.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Room for a tab title beside it; a name past this ellipsizes.
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (locked) Icons.Default.Lock else Icons.Default.SwapHoriz,
                contentDescription = null,
            )
        }
    }
}
