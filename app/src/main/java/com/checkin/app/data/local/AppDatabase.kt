package com.checkin.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The sessions database, at **version 1 with no migrations, deliberately**.
 *
 * Version 1 here means *collapsed*, not *new*: the schema [CheckInSession] declares is the settled
 * end of a migration chain, flattened while the Play install base was still effectively zero.
 *
 * **That was a one-time exemption and it is spent.** Every future schema change needs a real
 * migration — and two things are worth knowing before writing the first one:
 *
 * - **`ALTER TABLE ... DROP COLUMN` is not available.** SQLite learned it in 3.35, which shipped in
 *   Android 14; `minSdk` is 33, and API 33 ships **3.32.2**, where it is a bare syntax error —
 *   measured on an API 33 emulator, not recalled. Rebuild the table instead: create, `INSERT …
 *   SELECT`, drop, rename, with the new table spelled to match what Room generates for the entity
 *   exactly, since Room validates the schema on the next open and a stray `NOT NULL` or a missing
 *   `AUTOINCREMENT` fails there rather than in the migration.
 * - **A new column needs `@ColumnInfo(defaultValue = ...)` matching the migration's `DEFAULT`**, or
 *   Room emits it without one on a fresh install while the migration adds it with one, and nothing
 *   checks. An insert omitting that column then succeeds on an upgraded install and fails on a fresh
 *   one — a difference that only ever shows up on somebody else's device.
 *
 * There is **no `androidTest` source set**, so no gate in this repo can catch a broken migration:
 * verify one by installing the previous build, seeding it, and installing over the top.
 *
 * [fallbackToDestructiveMigration] is the backstop, and it wipes rather than crashes when a file's
 * version has no path to this one. That is what a development device carrying an older schema
 * wants and what a user's device never does, so it is no substitute for the migration above it.
 */
@Database(entities = [CheckInSession::class], version = 1, exportSchema = false)
@TypeConverters(ClosedByConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInSessionDao(): CheckInSessionDao

    companion object {
        @Volatile
        private var cached: AppDatabase? = null

        /**
         * The re-check inside the lock is load-bearing, not boilerplate. Without it, two threads that
         * both read a null `cached` serialize and *both* build an `AppDatabase`, each with its own
         * connection pool; the second overwrites the field and the first caller is left holding an
         * orphan. `CheckInApplication.onCreate` racing an alarm or boot broadcast in a freshly created
         * process is enough to reach it.
         */
        fun getDatabase(context: Context): AppDatabase = cached ?: synchronized(this) {
            cached ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "_app",
            ).fallbackToDestructiveMigration()
                .build()
                .also { cached = it }
        }
    }
}
