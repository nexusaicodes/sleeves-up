package com.checkin.app.data

/** What archiving a habit did, or why it was refused. */
enum class ArchiveResult {
    ARCHIVED,

    /** The habit has the open session; check out first. */
    RUNNING,

    /** The only active habit — the app always shows one, so it cannot be put away. */
    LAST_ACTIVE,
}

/**
 * When a habit may be selected, archived or restored, decided from the rows a write reads. Each app
 * reads its own record and asks here, so both refuse the same writes: one open session across all
 * habits locks the selection to it, the running habit and the last active one cannot be archived, and
 * a restored habit is held to the uniqueness rule an active one is.
 *
 * Every `activeHabits` is the active habits in creation order (`id` ascending), which is the order
 * the switcher lists them in and decides which habit an archive moves the selection to.
 */
object HabitWrites {

    /** Whether [target] may become the selection: it exists, is active, and no other habit is running. */
    fun canSelect(target: HabitFacts?, runningHabitId: Long?): Boolean =
        target != null && !target.archived && (runningHabitId == null || runningHabitId == target.id)

    /** Why [habitId] cannot be archived, or null when it can. */
    fun archiveRefusal(habitId: Long, runningHabitId: Long?, activeHabits: List<HabitFacts>): ArchiveResult? = when {
        runningHabitId == habitId -> ArchiveResult.RUNNING
        activeHabits.none { it.id != habitId } -> ArchiveResult.LAST_ACTIVE
        else -> null
    }

    /**
     * The selection to store once [habitId] is archived: the first active habit left when the
     * selection was [habitId], otherwise null for leaving it as it is.
     */
    fun selectionAfterArchive(habitId: Long, selectedId: Long?, activeHabits: List<HabitFacts>): Long? =
        if (selectedId == habitId) activeHabits.firstOrNull { it.id != habitId }?.id else null

    /** Why [habit] cannot be restored, or null when it can: its name taken by an active habit since. */
    fun restoreProblem(habit: HabitFacts, activeHabits: List<HabitFacts>): NameProblem? =
        habit.name?.let { HabitRules.nameProblem(it, activeHabits, forId = habit.id) }
}
