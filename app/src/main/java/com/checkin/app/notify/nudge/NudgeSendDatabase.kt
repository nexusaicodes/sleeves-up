package com.checkin.app.notify.nudge

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The nudge send ledger, deliberately separate from the sessions DB (`_app`) — no schema change here
 * can put a user's session records at risk or widen what the CSV export covers.
 *
 * **It starts at version 1 with no migrations, and that is the point of the new filename.** The
 * predecessor was `engagement.db`, a general engagement log whose every column but these two had no
 * reader. Renaming a database does not move it, so the old file is *deleted* — [deleteRetiredFile],
 * called once per process — rather than left on disk as a file nothing would ever open again, the
 * same retirement the old work name and the old channel ids get. Carrying the old file forward
 * instead would have meant a migration from its schema, which is code that runs once and is then
 * wrong to delete but impossible to reach.
 *
 * What that costs is the current day's sends, on the one upgrade where it happens: at worst a
 * duplicate nudge that day. Session records are in the other database and are untouched.
 */
@Database(entities = [NudgeSend::class], version = 1, exportSchema = false)
abstract class NudgeSendDatabase : RoomDatabase() {
    abstract fun nudgeSendDao(): NudgeSendDao

    companion object {
        private const val DB_NAME = "nudges.db"

        /**
         * The pre-rename filename. Deleted rather than migrated, for the reason in the class doc.
         * Keep this for as long as any install may still hold the file — nothing reports back that
         * it is gone, and deleting a database that does not exist is a no-op.
         */
        private const val RETIRED_DB = "engagement.db"

        @Volatile
        private var cached: NudgeSendDatabase? = null

        /**
         * Removes the retired file. Kept out of [getDatabase] because that runs on the main thread
         * of every cold start — the check-in path reaches it through the container's lazies — and
         * this is blocking filesystem work whose result nothing waits on. Call it from a background
         * scope once per process; see `CheckInApplication.onCreate`.
         */
        fun deleteRetiredFile(context: Context) {
            context.applicationContext.deleteDatabase(RETIRED_DB)
        }

        /**
         * The re-check inside the lock is load-bearing for the reason spelled out in
         * `AppDatabase.getDatabase`: without it two threads both build an instance, each with its
         * own connection pool over the one file, and the loser's caller holds an orphan.
         */
        fun getDatabase(context: Context): NudgeSendDatabase = cached ?: synchronized(this) {
            cached ?: Room.databaseBuilder(context.applicationContext, NudgeSendDatabase::class.java, DB_NAME)
                // Backstop only, and safe here in a way it would never be for `_app`: no session
                // record is at risk, and the most a wipe costs is one duplicate nudge that day.
                .fallbackToDestructiveMigration()
                .build()
                .also { cached = it }
        }
    }
}
