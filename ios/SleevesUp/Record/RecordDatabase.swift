import Foundation
import GRDB
import Shared

/// The record: one SQLite database holding the `habits`, `sessions` and `app_state` tables, with the Android
/// app's column names, types and meanings, so the same SQL describes both apps' records and the
/// shared rules read them the same way. `habits` leaves out Android's `channel_id`, a notification
/// channel iOS has no counterpart for.
///
/// A migration is append-only: a shipped one is never edited, and a schema change is a new one
/// registered after it.
enum RecordDatabase {

    /// The seed habit's row id, `Habit.SEED_ID` on Android: the habit every install starts with.
    static let seedHabitId: Int64 = 1

    /// `app_state`'s one row, `AppState.ROW_ID` on Android.
    static let appStateRowId: Int64 = 0

    @MainActor private static var shared: DatabaseQueue?

    /// The process's one queue on the on-disk record, opened on first ask. The app and its background
    /// refresh share it, as GRDB wants one queue per database file.
    @MainActor static func sharedOnDisk() throws -> DatabaseQueue {
        if let shared { return shared }
        let queue = try openOnDisk()
        shared = queue
        return queue
    }

    /// The record in Application Support, created and migrated on first open.
    static func openOnDisk() throws -> DatabaseQueue {
        let folder = try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
        )
        return try open(path: folder.appendingPathComponent("record.sqlite").path, nowMs: Clock.nowMs(Date()))
    }

    /// The record at `path`, created and migrated on first open.
    static func open(path: String, nowMs: Int64) throws -> DatabaseQueue {
        let queue = try DatabaseQueue(path: path)
        try migrate(queue, nowMs: nowMs)
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
        // The selected habit, one row with id 0, as Android's `app_state`.
        migrator.registerMigration("v2") { db in
            try db.execute(sql: """
                CREATE TABLE app_state (
                    id INTEGER NOT NULL,
                    selected_habit_id INTEGER NOT NULL,
                    PRIMARY KEY(id)
                );
                """)
        }
        try migrator.migrate(queue)
        // Every open, as on Android: an unnamed seed habit on any record with no habit at all, and a
        // selection naming the first active habit on any record without one.
        try queue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO habits (id, uuid, name, archived, created_at)
                    SELECT ?, ?, NULL, 0, ? WHERE NOT EXISTS (SELECT 1 FROM habits)
                    """,
                arguments: [seedHabitId, UUID().uuidString.lowercased(), nowMs]
            )
            try db.execute(
                sql: """
                    INSERT OR IGNORE INTO app_state (id, selected_habit_id)
                    SELECT ?, MIN(id) FROM habits WHERE archived = 0
                    """,
                arguments: [appStateRowId]
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

/// One row of `habits`. A class because the shared habit rules take `HabitFacts`, an Objective-C
/// protocol only a class can adopt; it is immutable, and a write reads a fresh one.
final class HabitRow: NSObject, HabitFacts, FetchableRecord, @unchecked Sendable {
    let id: Int64
    let uuid: String
    /// Null while the habit is owed a name: only the seed habit starts that way.
    let name: String?
    let archived: Bool
    let createdAt: Int64

    init(row: Row) throws {
        id = row["id"]
        uuid = row["uuid"]
        name = row["name"]
        archived = row["archived"]
        createdAt = row["created_at"]
    }
}
