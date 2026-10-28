package com.checkin.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * One update check owed per activity start, settled by whatever is on screen when it falls due:
 * [AppUpdateOffer] claims it and asks Play; every other surface forfeits it ([ForfeitUpdateCheck]).
 *
 * A start that lands on the welcome tour, the naming step, either presence gate, the notification
 * ask or the check-out celebration is spent there, so leaving that surface for the nav host opens
 * no dialog — Play's dialog arrives only when the app is brought forward onto the nav host itself.
 */
@Stable
internal class UpdateCheckTurn {
    var requested by mutableIntStateOf(0)
        private set
    private var settled by mutableIntStateOf(0)

    val owed: Boolean get() = requested > settled

    fun onStart() {
        requested++
    }

    fun forfeit() {
        settled = requested
    }

    /** True once per [request], for the surface that asks Play about it. */
    fun claim(request: Int): Boolean = (request > settled).also { if (it) settled = request }
}

/** Spends an owed [UpdateCheckTurn] on a surface Play's dialog must not land on, or next to. */
@Composable
internal fun ForfeitUpdateCheck(turn: UpdateCheckTurn) {
    if (turn.owed) SideEffect { turn.forfeit() }
}
