package com.checkin.app.notify.nudge

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The nudge send ledger, deliberately separate from the sessions DB (`_app`) — no schema change here
 * can put a user's session records at risk or widen what the CSV export covers.
 *
 * **Its first version was 1 with no migrations, and that is the point of the new filename.** The
 * predecessor was `engagement.db`, a general engagement log whose every column but these two had no
 * reader. Renaming a database does not move it, so the old file is *deleted* — [deleteRetiredFile],
 * called once per process — rather than left on disk as a file nothing would ever open again, the
 * same retirement the old work name and the old channel ids get. Carrying the old file forward
 * instead would have meant a migration from its schema, which is code that runs once and is then
 * wrong to delete but impossible to reach.
 *
 * What that costs is the current day's sends, on the one upgrade where it happens: at worst a
 * duplicate nudge that day. Session records are in the other database and are untouched.
 *
 * Version 3 adds [NudgeSend.posted] and [NudgeSend.habitUuid] in **one step from v3.0's schema**
 * ([MIGRATION_1_3]), through a real migration rather than the destructive backstop, so the day's
 * sends survive the upgrade that adds them. There is no version 2 to migrate from on any user's
 * device: it existed only on development builds, which the backstop rebuilds — and skipping the
 * number is what makes them rebuild, since a file at the same version with a different schema fails
 * Room's identity check outright rather than falling back.
 */
@Database(entities = [NudgeSend::class], version = 3, exportSchema = false)
abstract class NudgeSendDatabase : RoomDatabase() {
    abstract fun nudgeSendDao(): NudgeSendDao

    companion object {
        private const val DB_NAME = "nudges.db"

        /**
         * Every row from version 1 was a post that succeeded, hence `posted`'s default; and every one
         * was about the single record that install had, hence an empty `habit_uuid` — see
         * [NudgeSend.habitUuid] for how that is read.
         */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `nudge_sends` ADD COLUMN `posted` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `nudge_sends` ADD COLUMN `habit_uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_nudge_sends_habit_uuid_at` ON `nudge_sends` (`habit_uuid`, `at`)",
                )
            }
        }

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
                .addMigrations(MIGRATION_1_3)
                .fallbackToDestructiveMigration()
                .build()
                .also { cached = it }
        }
    }
}
