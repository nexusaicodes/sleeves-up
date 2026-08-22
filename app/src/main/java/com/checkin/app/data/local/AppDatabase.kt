package com.checkin.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.checkin.app.service.SessionSchedule
import java.time.ZoneId

@Database(entities = [CheckInSession::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInSessionDao(): CheckInSessionDao

    companion object {
        @Volatile
        private var cached: AppDatabase? = null

        /** Adds the presence-pause columns without dropping existing sessions. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN paused_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN pause_started_at INTEGER")
            }
        }

        /** Drops the vestigial selfie columns; selfies are transient and never persisted. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions DROP COLUMN punch_in_selfie")
                db.execSQL("ALTER TABLE sessions DROP COLUMN punch_out_selfie")
            }
        }

        /**
         * Drops the presence-pause columns along with the mechanism that wrote them.
         *
         * **Completed** rows are untouched: their `duration` was already stored net of paused time,
         * so only the audit trail of *why* it was shorter than the wall-clock span is lost, and
         * nothing reads that.
         *
         * A session still **open** at upgrade time loses whatever pause it had accumulated and is
         * recorded at its full wall-clock span when it closes. Deliberate, not overlooked: nothing
         * in this model subtracts from an interval, and the alternatives are worse — closing the row
         * silently ends a session the user may still be in, and folding the pause into `started_at`
         * rewrites the check-in time they see on screen. Over-counting one session beats editing a
         * row the app gives no way to edit. The blast radius is bounded by the day-boundary close,
         * which `SessionWatchdog` arms on the first app open after the upgrade: a session left open
         * past its own midnight is closed *at* that midnight, so at most a same-day pause survives.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions DROP COLUMN paused_ms")
                db.execSQL("ALTER TABLE sessions DROP COLUMN pause_started_at")
            }
        }

        /**
         * Records which sessions the day-boundary alarm closed, rather than the user.
         *
         * Additive and data-preserving: the column defaults to 0, and no existing value is rewritten.
         * Nothing in the app reads it — it exists for the CSV export alone.
         *
         * **The backfill is best-effort, and deliberately so.** Going forward the flag is written by
         * the one caller that closes a session at midnight, so it is exact. For rows already on
         * disk there is nothing to read but the stop instant, so the migration re-derives it: a
         * session stopped at exactly the midnight ending its own `date_key` was closed by the alarm,
         * because the gate takes seconds to pass and a user cannot land on that millisecond. It
         * misses two cases and cannot recover either — `onDayBoundaryFired` clamps its stop instant
         * with `coerceAtMost(now)`, and the armed midnight was computed in the zone the session
         * began in, so a session that crossed a zone change is stamped somewhere else entirely.
         * Both under-report, which is the safe direction for a column that asserts something about
         * the user's behaviour.
         *
         * The `DEFAULT 0` is matched by `@ColumnInfo(defaultValue = "0")` on the entity, so a fresh
         * install and a migrated one end up with the *same* CREATE TABLE. Without the annotation Room
         * emits the column with no default on a fresh install, and the two schemas differ in a way
         * nothing checks — any insert that does not name the column then succeeds on one and fails
         * on the other, which is a difference that only ever shows up on somebody else's device.
         *
         * `SessionSchedule.dayBoundaryOf` is reused rather than reimplemented in SQL: the midnight
         * rule goes through the calendar (so a DST day still ends at midnight), and a second copy of
         * it here is how the export would come to disagree with the alarm that wrote the rows.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN auto_closed INTEGER NOT NULL DEFAULT 0")
                val zone = ZoneId.systemDefault()
                val closedAtBoundary = mutableListOf<Long>()
                db.query("SELECT id, stopped_at, date_key FROM sessions WHERE stopped_at IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        val boundary = SessionSchedule.dayBoundaryOf(cursor.getString(2), zone)
                        if (boundary != null && boundary == cursor.getLong(1)) {
                            closedAtBoundary += cursor.getLong(0)
                        }
                    }
                }
                // Chunked so a long history cannot build one enormous expression. The ids come from
                // this table's own primary key, so they are inlined rather than bound.
                closedAtBoundary.chunked(BACKFILL_CHUNK).forEach { ids ->
                    db.execSQL("UPDATE sessions SET auto_closed = 1 WHERE id IN (${ids.joinToString(",")})")
                }
            }
        }

        /** Ids per backfill statement — see [MIGRATION_5_6]. */
        private const val BACKFILL_CHUNK = 500

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
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                .also { cached = it }
        }
    }
}
