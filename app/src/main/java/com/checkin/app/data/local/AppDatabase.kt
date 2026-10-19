package com.checkin.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * The record: sessions, the habits they belong to, and the one row of app state that indexes them.
 *
 * **Every schema change is a real migration from here on.** Version 1 was a chain collapsed while
 * the Play install base was effectively zero, and that exemption is spent. [MIGRATION_1_2] carries
 * a v3.0 install's record into habits: it creates `habits` and `app_state`, adds `sessions.habit_id`
 * defaulting to [Habit.SEED_ID], and so files every session already recorded under the seed habit,
 * unnamed until the first-run naming step asks.
 *
 * Two things to know before writing the next one:
 *
 * - **`ALTER TABLE ... DROP COLUMN` is not available.** SQLite learned it in 3.35, which shipped in
 *   Android 14; `minSdk` is 33, and API 33 ships **3.32.2**, where it is a bare syntax error.
 *   Rebuild the table instead: create, `INSERT …
 *   SELECT`, drop, rename, with the new table spelled to match what Room generates for the entity
 *   exactly, since Room validates the schema on the next open and a stray `NOT NULL` or a missing
 *   `AUTOINCREMENT` fails there rather than in the migration.
 * - **A new column needs `@ColumnInfo(defaultValue = ...)` matching the migration's `DEFAULT`**, or
 *   Room emits it without one on a fresh install while the migration adds it with one, and nothing
 *   checks. An insert omitting that column then succeeds on an upgraded install and fails on a fresh
 *   one — a difference that only ever shows up on somebody else's device.
 *
 * The `CREATE` statements below are copied from what Room generates for the entities (see the
 * generated `AppDatabase_Impl.createAllTables`), for the validation reason in the first point.
 *
 * There is **no `androidTest` source set**, so no gate in this repo can catch a broken migration:
 * verify one by installing the previous build, seeding it, and installing over the top.
 *
 * [fallbackToDestructiveMigration] is the backstop, and it wipes rather than crashes when a file's
 * version has no path to this one. That is what a development device carrying an older schema
 * wants and what a user's device never does, so it is no substitute for the migration above it.
 */
@Database(entities = [CheckInSession::class, Habit::class, AppState::class], version = 2, exportSchema = false)
@TypeConverters(ClosedByConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInSessionDao(): CheckInSessionDao
    abstract fun habitDao(): HabitDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        /** v3.0's record into habits. See the class doc; the seed itself is [seedIfEmpty]'s. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_HABITS)
                db.execSQL(CREATE_HABITS_UUID_INDEX)
                db.execSQL(CREATE_APP_STATE)
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `habit_id` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_habit_id` ON `sessions` (`habit_id`)")
                seedIfEmpty(db, System.currentTimeMillis())
            }
        }

        private const val CREATE_HABITS = "CREATE TABLE IF NOT EXISTS `habits` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT, " +
            "`archived` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `channel_id` TEXT NOT NULL)"
        private const val CREATE_HABITS_UUID_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_habits_uuid` ON `habits` (`uuid`)"
        private const val CREATE_APP_STATE = "CREATE TABLE IF NOT EXISTS `app_state` (" +
            "`id` INTEGER NOT NULL, `selected_habit_id` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        /**
         * Gives a database with no habits its seed habit, selected. Idempotent, and run on every
         * open as well as at the end of the migration, because **one shape for every install** is
         * the invariant: an upgraded record, a fresh install and a destructive rebuild all start
         * with habit [Habit.SEED_ID] unnamed and selected. Open rather than create because the
         * destructive backstop calls neither `onCreate` nor anything after its tables exist.
         *
         * The seed keeps [Habit.SEED_CHANNEL_ID], so an updated install's existing reminder channel —
         * and every setting the user made on it — becomes this habit's. Its `created_at` is the
         * record's first session where there is one.
         */
        internal fun seedIfEmpty(db: SupportSQLiteDatabase, nowMillis: Long) {
            db.execSQL(
                "INSERT INTO `habits` (`id`, `uuid`, `name`, `archived`, `created_at`, `channel_id`) " +
                    "SELECT ?, ?, NULL, 0, COALESCE((SELECT MIN(`started_at`) FROM `sessions`), ?), ? " +
                    "WHERE NOT EXISTS (SELECT 1 FROM `habits`)",
                arrayOf<Any>(Habit.SEED_ID, UUID.randomUUID().toString(), nowMillis, Habit.SEED_CHANNEL_ID),
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `app_state` (`id`, `selected_habit_id`) " +
                    "SELECT ?, MIN(`id`) FROM `habits` WHERE `archived` = 0",
                arrayOf<Any>(AppState.ROW_ID),
            )
        }

        private val seedOnOpen = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                seedIfEmpty(db, System.currentTimeMillis())
            }
        }

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
            ).addMigrations(MIGRATION_1_2)
                .addCallback(seedOnOpen)
                .fallbackToDestructiveMigration()
                .build()
                .also { cached = it }
        }
    }
}
