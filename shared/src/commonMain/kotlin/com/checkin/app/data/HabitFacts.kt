package com.checkin.app.data

/**
 * What the shared habit rules read of a habit: its row id, its permanent uuid, its name (null while
 * it is owed one) and whether it is archived. The Android app's Room entity `Habit` implements it,
 * so the rules take the stored rows as they are, with no mapping and no storage type in `:shared`.
 * Platform-only fields, such as Android's notification channel id, stay on the platform's own type.
 */
interface HabitFacts {
    val id: Long
    val uuid: String
    val name: String?
    val archived: Boolean
}
