import Foundation
import GRDB

/// Which habit reminders are scheduled and which were shown, in a database of its own
/// (`nudges.sqlite`), apart from the session record, as Android keeps `nudges.db`: no change here can
/// put a session at migration risk, and losing it costs at most a repeated wording.
///
/// Nothing runs when a scheduled reminder fires, so a planned reminder whose instant has passed
/// without being replanned is taken as shown (`settle`). A check-in replans first, so a reminder it
/// made untrue is never recorded.
final class NudgeLedger {

    /// A scheduled reminder, under the notification identifier it was added with.
    struct Planned: Equatable {
        let identifier: String
        let habitUuid: String
        let fireAt: Int64
        let titleKey: String
        let bodyKey: String
    }

    private let queue: DatabaseQueue

    private init(_ queue: DatabaseQueue) throws {
        self.queue = queue
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1") { db in
            try db.execute(sql: """
                CREATE TABLE planned (
                    identifier TEXT NOT NULL PRIMARY KEY,
                    habit_uuid TEXT NOT NULL,
                    fire_at INTEGER NOT NULL,
                    title_key TEXT NOT NULL,
                    body_key TEXT NOT NULL
                );
                CREATE TABLE shown (
                    habit_uuid TEXT NOT NULL,
                    at INTEGER NOT NULL,
                    title_key TEXT NOT NULL,
                    body_key TEXT NOT NULL
                );
                CREATE INDEX shown_habit_at ON shown (habit_uuid, at);
                """)
        }
        try migrator.migrate(queue)
    }

    @MainActor private static var shared: NudgeLedger?

    /// The process's one ledger on disk, opened on first ask and shared as the record's queue is.
    @MainActor static func sharedOnDisk() throws -> NudgeLedger {
        if let shared { return shared }
        let ledger = try openOnDisk()
        shared = ledger
        return ledger
    }

    static func openOnDisk() throws -> NudgeLedger {
        let folder = try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
        )
        return try NudgeLedger(DatabaseQueue(path: folder.appendingPathComponent("nudges.sqlite").path))
    }

    static func openInMemory() throws -> NudgeLedger {
        try NudgeLedger(DatabaseQueue())
    }

    /// Records every planned reminder whose instant has passed as shown, and drops shown rows older
    /// than `keepSince`, the reach of the wording rotation.
    func settle(nowMs: Int64, keepSince: Int64) throws {
        try queue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO shown (habit_uuid, at, title_key, body_key)
                    SELECT habit_uuid, fire_at, title_key, body_key FROM planned WHERE fire_at <= ?
                    """,
                arguments: [nowMs]
            )
            try db.execute(sql: "DELETE FROM planned WHERE fire_at <= ?", arguments: [nowMs])
            try db.execute(sql: "DELETE FROM shown WHERE at < ?", arguments: [keepSince])
        }
    }

    func plannedIdentifiers() throws -> [String] {
        try queue.read { db in try String.fetchAll(db, sql: "SELECT identifier FROM planned") }
    }

    /// Replaces the plan with `plan`.
    func replacePlan(_ plan: [Planned]) throws {
        try queue.write { db in
            try db.execute(sql: "DELETE FROM planned")
            for entry in plan {
                try db.execute(
                    sql: "INSERT INTO planned (identifier, habit_uuid, fire_at, title_key, body_key) VALUES (?, ?, ?, ?, ?)",
                    arguments: [entry.identifier, entry.habitUuid, entry.fireAt, entry.titleKey, entry.bodyKey]
                )
            }
        }
    }

    /// Each wording key shown for `habitUuid` since `sinceMs`, and when it was last shown.
    func shownWording(habitUuid: String, sinceMs: Int64) throws -> [String: Int64] {
        try queue.read { db in
            var lastShown: [String: Int64] = [:]
            for row in try Row.fetchAll(
                db,
                sql: "SELECT title_key, body_key, at FROM shown WHERE habit_uuid = ? AND at >= ?",
                arguments: [habitUuid, sinceMs]
            ) {
                let at: Int64 = row["at"]
                for key in [row["title_key"] as String, row["body_key"] as String] {
                    lastShown[key] = max(lastShown[key] ?? .min, at)
                }
            }
            return lastShown
        }
    }

    /// The last reminder of any habit shown since `sinceMs`, from which the gap runs.
    func lastShown(sinceMs: Int64) throws -> Int64? {
        try queue.read { db in
            try Int64.fetchOne(db, sql: "SELECT MAX(at) FROM shown WHERE at >= ?", arguments: [sinceMs])
        }
    }

    /// Whether `habitUuid`'s reminder has been shown since `sinceMs`.
    func wasShown(habitUuid: String, sinceMs: Int64) throws -> Bool {
        try queue.read { db in
            try Bool.fetchOne(
                db, sql: "SELECT EXISTS (SELECT 1 FROM shown WHERE habit_uuid = ? AND at >= ?)",
                arguments: [habitUuid, sinceMs]
            ) ?? false
        }
    }
}
