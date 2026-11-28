import Foundation
import GRDB
import Observation
import Shared

/// The habits and which one the app is showing, with every habit write. Each write re-reads its rows
/// inside one GRDB transaction and asks the shared `HabitRules` and `HabitWrites`, so it refuses
/// exactly what Android's `HabitRepository` refuses, whatever the screen that asked had drawn.
@MainActor
@Observable
final class HabitStore {

    /// What a name write did.
    enum Edit: Equatable {
        case saved
        case rejected(NameProblem)
        /// No habit has the id; nothing was written.
        case missing
    }

    /// Active habits in creation order, the switcher's order.
    private(set) var active: [HabitRow] = []
    private(set) var archived: [HabitRow] = []
    /// The habit every screen shows: the running habit while a session is open, otherwise the
    /// stored choice, falling back to the first active habit.
    private(set) var selected: HabitRow?
    /// The habit with the open session, which locks the selection to it.
    private(set) var runningHabitId: Int64?

    private let database: DatabaseQueue
    private let now: () -> Date
    private let newUuid: () -> String

    init(
        database: DatabaseQueue,
        now: @escaping () -> Date = Date.init,
        newUuid: @escaping () -> String = { UUID().uuidString.lowercased() }
    ) {
        self.database = database
        self.now = now
        self.newUuid = newUuid
    }

    /// The first active habit still owed a name, which the naming step asks for.
    var needingName: HabitRow? { active.first { $0.name == nil } }

    /// Whether the selection is locked to a running session.
    var isLocked: Bool { runningHabitId != nil }

    func reload() throws {
        let (all, stored, running) = try database.read { db in
            (
                try HabitRow.fetchAll(db, sql: "SELECT * FROM habits ORDER BY id ASC"),
                try Self.storedSelection(db),
                try Self.runningHabitId(db)
            )
        }
        active = all.filter { !$0.archived }
        archived = all.filter { $0.archived }
        runningHabitId = running
        selected = HabitRules.shared.effectiveSelection(
            storedId: stored.map { KotlinLong(value: $0) },
            runningHabitId: running.map { KotlinLong(value: $0) },
            activeHabits: active
        ) as? HabitRow
    }

    /// Stores `habitId` as the selection. False, storing nothing, when another habit is running or
    /// the habit is not active.
    @discardableResult
    func select(_ habitId: Int64) throws -> Bool {
        let stored = try database.write { db in
            let target = try HabitRow.fetchOne(db, sql: "SELECT * FROM habits WHERE id = ?", arguments: [habitId])
            let running = try Self.runningHabitId(db)
            guard HabitWrites.shared.canSelect(target: target, runningHabitId: running.map { KotlinLong(value: $0) })
            else { return false }
            try Self.storeSelection(db, habitId)
            return true
        }
        try reload()
        return stored
    }

    /// Creates a habit with `name`, normalised. The selection stays where it is.
    func create(name: String) throws -> Edit {
        let createdAt = Clock.nowMs(now())
        let uuid = newUuid()
        let edit: Edit = try database.write { db in
            if let problem = HabitRules.shared.nameProblem(name: name, activeHabits: try Self.active(db), forId: nil) {
                return .rejected(problem)
            }
            try db.execute(
                sql: "INSERT INTO habits (uuid, name, archived, created_at) VALUES (?, ?, 0, ?)",
                arguments: [uuid, HabitRules.shared.normalise(name: name), createdAt]
            )
            return .saved
        }
        try reload()
        return edit
    }

    func rename(_ habitId: Int64, to name: String) throws -> Edit {
        let edit: Edit = try database.write { db in
            guard try HabitRow.fetchOne(db, sql: "SELECT * FROM habits WHERE id = ?", arguments: [habitId]) != nil
            else { return .missing }
            let problem = HabitRules.shared.nameProblem(
                name: name, activeHabits: try Self.active(db), forId: KotlinLong(value: habitId)
            )
            if let problem { return .rejected(problem) }
            try db.execute(
                sql: "UPDATE habits SET name = ? WHERE id = ?",
                arguments: [HabitRules.shared.normalise(name: name), habitId]
            )
            return .saved
        }
        try reload()
        return edit
    }

    /// Puts `habitId` away. Its sessions stay exactly as they are, and the selection moves off it.
    func archive(_ habitId: Int64) throws -> ArchiveResult {
        let result: ArchiveResult = try database.write { db in
            let active = try Self.active(db)
            let running = try Self.runningHabitId(db)
            if let refusal = HabitWrites.shared.archiveRefusal(
                habitId: habitId, runningHabitId: running.map { KotlinLong(value: $0) }, activeHabits: active
            ) {
                return refusal
            }
            try db.execute(sql: "UPDATE habits SET archived = 1 WHERE id = ?", arguments: [habitId])
            let stored = try Self.storedSelection(db)
            if let next = HabitWrites.shared.selectionAfterArchive(
                habitId: habitId, selectedId: stored.map { KotlinLong(value: $0) }, activeHabits: active
            ) {
                try Self.storeSelection(db, next.int64Value)
            }
            return .archived
        }
        try reload()
        return result
    }

    /// Brings `habitId` back, refused when an active habit has taken its name since.
    func restore(_ habitId: Int64) throws -> Edit {
        let edit: Edit = try database.write { db in
            guard let habit = try HabitRow.fetchOne(db, sql: "SELECT * FROM habits WHERE id = ?", arguments: [habitId])
            else { return .missing }
            if let problem = HabitWrites.shared.restoreProblem(habit: habit, activeHabits: try Self.active(db)) {
                return .rejected(problem)
            }
            try db.execute(sql: "UPDATE habits SET archived = 0 WHERE id = ?", arguments: [habitId])
            return .saved
        }
        try reload()
        return edit
    }

    private nonisolated static func active(_ db: Database) throws -> [HabitRow] {
        try HabitRow.fetchAll(db, sql: "SELECT * FROM habits WHERE archived = 0 ORDER BY id ASC")
    }

    private nonisolated static func runningHabitId(_ db: Database) throws -> Int64? {
        try Int64.fetchOne(db, sql: "SELECT habit_id FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    }

    private nonisolated static func storedSelection(_ db: Database) throws -> Int64? {
        try Int64.fetchOne(
            db, sql: "SELECT selected_habit_id FROM app_state WHERE id = ?", arguments: [RecordDatabase.appStateRowId]
        )
    }

    private nonisolated static func storeSelection(_ db: Database, _ habitId: Int64) throws {
        try db.execute(
            sql: "INSERT OR REPLACE INTO app_state (id, selected_habit_id) VALUES (?, ?)",
            arguments: [RecordDatabase.appStateRowId, habitId]
        )
    }
}
