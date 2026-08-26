package com.checkin.app.notify.log

/**
 * Which subsystem wrote a row, and the isolation the whole engagement layer rests on.
 *
 * It sits in its own file because every scoping decision in `notify/` turns on it: the two queries
 * that drive behaviour are scoped by it, `FakeEngagementLog` mirrors that scoping so a test cannot
 * prove only the fake right, and `EngagementLogSourceTest` pins the lot. As one declaration among
 * four in the file named for the Room row, the type a reader most needs was the one they were
 * least likely to find.
 *
 * **Every name here is stored in `engagement.db` and is therefore frozen** — a rename orphans the
 * rows the daily cap counts from.
 */
enum class EngagementSource {
    /** An optional encouragement nudge, on by default and experiment-tracked. */
    NUDGE,

    /**
     * The periodic session reminder. Recorded for visibility only; it drives no rules.
     *
     * The name does not match the wording used elsewhere for that reminder, and is frozen anyway:
     * this string is stored in `engagement.db` and is what the cap and attribution queries scope on,
     * so renaming it orphans every row already written under it.
     */
    PRESENCE,

    /**
     * Background-machinery lifecycle — the foreground service, the session alarms, and the nudge
     * checkpoint alarm. Recorded for visibility only; it drives no rules.
     *
     * These rows are the only trace a session that silently loses its service leaves: the
     * notification is gone, the DB row still looks open, and the app keeps rendering a running timer
     * from it. Without them, diagnosis means inferring backwards from a wrong duration. The nudge
     * checkpoint shares the source because it is the same kind of fact — a wake-up fired — and
     * because anything scoped [NUDGE] is counted as an impression by the cap and by attribution.
     */
    SERVICE,
}

/**
 * The [EngagementEvent.key] the session reminder is logged under.
 *
 * Deliberately a bare constant rather than a `Nudge` entry: adding it to that enum would make it
 * selectable by `NudgeEligibility` and countable by the daily cap, neither of which applies to a
 * reminder that belongs to one open session.
 *
 * Frozen for the same reason as [EngagementSource.PRESENCE]: the string is stored in `engagement.db`.
 */
const val PRESENCE_CHECK_KEY = "PRESENCE_CHECK"
