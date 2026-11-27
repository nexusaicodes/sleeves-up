import Foundation
import GRDB

/// The record: one SQLite database holding the `habits` and `sessions` tables, with the Android
/// app's column names, types and meanings, so the same SQL describes both apps' records and the
/// shared rules read them the same way. `habits` leaves out Android's `channel_id`, a notification
/// channel iOS has no counterpart for.
///
/// A migration is append-only: a shipped one is never edited, and a schema change is a new one
/// registered after it.
enum RecordDatabase {

    /// The seed habit's row id, `Habit.SEED_ID` on Android: the habit every install starts with.
    static let seedHabitId: Int64 = 1

    /// The record in Application Support, created and migrated on first open.
    static func openOnDisk() throws -> DatabaseQueue {
        let folder = try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
        )
        let queue = try DatabaseQueue(path: folder.appendingPathComponent("record.sqlite").path)
        try migrate(queue, nowMs: Clock.nowMs(Date()))
        return queue
    }

    /// A record held in memory, for tests.
    static func openInMemory(nowMs: Int64) throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try migrate(queue, nowMs: nowMs)
        return queue
    }

    private static func migrate(_ queue: DatabaseQueue, nowMs: Int64) throws {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1") { db in
            try db.execute(sql: """
                CREATE TABLE habits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uuid TEXT NOT NULL,
                    name TEXT,
                    archived INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                );
                CREATE UNIQUE INDEX index_habits_uuid ON habits (uuid);
                CREATE TABLE sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    started_at INTEGER NOT NULL,
                    stopped_at INTEGER,
                    duration INTEGER,
                    date_key TEXT NOT NULL,
                    closed_by TEXT DEFAULT NULL,
                    habit_id INTEGER NOT NULL DEFAULT 1
                );
                CREATE INDEX index_sessions_habit_id ON sessions (habit_id);
                """)
        }
        try migrator.migrate(queue)
        // Every open, as on Android: an unnamed seed habit on any record with no habit at all.
        try queue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO habits (id, uuid, name, archived, created_at)
                    SELECT ?, ?, NULL, 0, ? WHERE NOT EXISTS (SELECT 1 FROM habits)
                    """,
                arguments: [seedHabitId, UUID().uuidString.lowercased(), nowMs]
            )
        }
    }
}

/// One row of `sessions`. A row with no `stoppedAt` is the open session; there is at most one.
struct SessionRow: Codable, Equatable, Sendable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "sessions"

    var id: Int64?
    var startedAt: Int64
    var stoppedAt: Int64?
    var duration: Int64?
    var dateKey: String
    var closedBy: String?
    var habitId: Int64

    enum CodingKeys: String, CodingKey {
        case id
        case startedAt = "started_at"
        case stoppedAt = "stopped_at"
        case duration
        case dateKey = "date_key"
        case closedBy = "closed_by"
        case habitId = "habit_id"
    }

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}
