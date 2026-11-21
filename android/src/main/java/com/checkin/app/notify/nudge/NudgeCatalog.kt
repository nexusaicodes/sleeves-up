package com.checkin.app.notify.nudge

import com.checkin.app.R
import com.checkin.app.notify.nudge.NudgeVoice.Voice

/**
 * A title a nudge can take. Held as resource ids so copy stays localizable. [titleRes] takes the
 * habit's name as its one argument, because a reminder that does not say which habit it is about is
 * one a user with two habits cannot act on; [unnamedTitleRes] stands in for a habit still owed a
 * name — an install updated onto habits whose user has not yet opened the app.
 */
data class TitleVariant(override val key: String, val titleRes: Int, val unnamedTitleRes: Int) : NudgeVariant

/** A body a nudge can take. */
data class BodyVariant(override val key: String, val bodyRes: Int) : NudgeVariant

/** One voice's pools; [NudgeVoice.pick] chooses a title and a body from them independently. */
data class VoiceCopy(val titles: List<TitleVariant>, val bodies: List<BodyVariant>)

/**
 * The copy for each nudge, as a pool per [Voice].
 *
 * The pools exist so a line read every day does not stop being read. They are **not an experiment**:
 * nothing measures which wording works, and nothing may pick a wording by how a user responded. The
 * voice only lowers the ask after a lapse, never raises the energy (see [NudgeVoice]).
 *
 * Keys are recorded in the send ledger and read back to rotate, so they are frozen once shipped:
 * append a variant with a new key, never renumber or reuse one. A retired variant's key is simply
 * dropped; history naming it is ignored.
 */
object NudgeCatalog {

    private val notCheckedIn: Map<Voice, VoiceCopy> = mapOf(
        Voice.NOT_STARTED to VoiceCopy(
            titles = listOf(
                TitleVariant(
                    "not_started.t1",
                    R.string.nudge_not_started_title_1,
                    R.string.nudge_not_started_title_unnamed_1,
                ),
                TitleVariant(
                    "not_started.t2",
                    R.string.nudge_not_started_title_2,
                    R.string.nudge_not_started_title_unnamed_2,
                ),
                TitleVariant(
                    "not_started.t3",
                    R.string.nudge_not_started_title_3,
                    R.string.nudge_not_started_title_unnamed_3,
                ),
                TitleVariant(
                    "not_started.t4",
                    R.string.nudge_not_started_title_4,
                    R.string.nudge_not_started_title_unnamed_4,
                ),
            ),
            bodies = listOf(
                BodyVariant("not_started.b1", R.string.nudge_not_started_body_1),
                BodyVariant("not_started.b2", R.string.nudge_not_started_body_2),
                BodyVariant("not_started.b3", R.string.nudge_not_started_body_3),
                BodyVariant("not_started.b4", R.string.nudge_not_started_body_4),
                BodyVariant("not_started.b5", R.string.nudge_not_started_body_5),
                BodyVariant("not_started.b6", R.string.nudge_not_started_body_6),
                BodyVariant("not_started.b7", R.string.nudge_not_started_body_7),
                BodyVariant("not_started.b8", R.string.nudge_not_started_body_8),
                BodyVariant("not_started.b9", R.string.nudge_not_started_body_9),
                BodyVariant("not_started.b10", R.string.nudge_not_started_body_10),
                BodyVariant("not_started.b11", R.string.nudge_not_started_body_11),
                BodyVariant("not_started.b12", R.string.nudge_not_started_body_12),
            ),
        ),
        Voice.STEADY to VoiceCopy(
            titles = listOf(
                TitleVariant(
                    "steady.t1",
                    R.string.nudge_not_checked_in_title,
                    R.string.nudge_not_checked_in_unnamed_title,
                ),
                TitleVariant("steady.t2", R.string.nudge_steady_title_2, R.string.nudge_steady_title_unnamed_2),
                TitleVariant("steady.t3", R.string.nudge_steady_title_3, R.string.nudge_steady_title_unnamed_3),
                TitleVariant("steady.t4", R.string.nudge_steady_title_4, R.string.nudge_steady_title_unnamed_4),
            ),
            bodies = listOf(
                BodyVariant("steady.b1", R.string.nudge_not_checked_in_body),
                BodyVariant("steady.b2", R.string.nudge_steady_body_2),
                BodyVariant("steady.b3", R.string.nudge_steady_body_3),
                BodyVariant("steady.b4", R.string.nudge_steady_body_4),
                BodyVariant("steady.b5", R.string.nudge_steady_body_5),
                BodyVariant("steady.b6", R.string.nudge_steady_body_6),
                BodyVariant("steady.b7", R.string.nudge_steady_body_7),
                BodyVariant("steady.b8", R.string.nudge_steady_body_8),
                BodyVariant("steady.b9", R.string.nudge_steady_body_9),
                BodyVariant("steady.b10", R.string.nudge_steady_body_10),
                BodyVariant("steady.b11", R.string.nudge_steady_body_11),
                BodyVariant("steady.b12", R.string.nudge_steady_body_12),
            ),
        ),
        Voice.RETURNING to VoiceCopy(
            titles = listOf(
                TitleVariant(
                    "returning.t1",
                    R.string.nudge_returning_title_1,
                    R.string.nudge_returning_title_unnamed_1,
                ),
                TitleVariant(
                    "returning.t2",
                    R.string.nudge_returning_title_2,
                    R.string.nudge_returning_title_unnamed_2,
                ),
                TitleVariant(
                    "returning.t3",
                    R.string.nudge_returning_title_3,
                    R.string.nudge_returning_title_unnamed_3,
                ),
                TitleVariant(
                    "returning.t4",
                    R.string.nudge_returning_title_4,
                    R.string.nudge_returning_title_unnamed_4,
                ),
            ),
            bodies = listOf(
                BodyVariant("returning.b1", R.string.nudge_returning_body_1),
                BodyVariant("returning.b2", R.string.nudge_returning_body_2),
                BodyVariant("returning.b3", R.string.nudge_returning_body_3),
                BodyVariant("returning.b4", R.string.nudge_returning_body_4),
                BodyVariant("returning.b5", R.string.nudge_returning_body_5),
                BodyVariant("returning.b6", R.string.nudge_returning_body_6),
                BodyVariant("returning.b7", R.string.nudge_returning_body_7),
                BodyVariant("returning.b8", R.string.nudge_returning_body_8),
                BodyVariant("returning.b9", R.string.nudge_returning_body_9),
                BodyVariant("returning.b10", R.string.nudge_returning_body_10),
                BodyVariant("returning.b11", R.string.nudge_returning_body_11),
                BodyVariant("returning.b12", R.string.nudge_returning_body_12),
            ),
        ),
    )

    private val copy: Map<Nudge, Map<Voice, VoiceCopy>> = mapOf(Nudge.NOT_CHECKED_IN to notCheckedIn)

    /** Errors rather than returning null — a nudge with no copy could be selected and then fail to render. */
    fun copyFor(nudge: Nudge, voice: Voice): VoiceCopy =
        copy[nudge]?.get(voice) ?: error("No copy registered for $nudge in $voice")
}
