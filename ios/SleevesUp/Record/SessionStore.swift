import Foundation
import GRDB
import Observation
import Shared

/// The open session (across all habits), the shown habit's sessions today, and the only two writes,
/// check-in and check-out, plus the day-boundary close of a forgotten session. Every rule a write follows comes
/// from the shared `SessionRules`, so a session ends, lasts and is refused exactly as on Android.
/// Each write is one GRDB transaction, which serializes it against every other write.
@MainActor
@Observable
final class SessionStore {

    enum CheckInOutcome: Equatable {
        case opened
        case alreadyOpen
        case otherHabitOpen
    }

    private(set) var openSession: SessionRow?
    private(set) var todaySessions: [SessionRow] = []

    private let database: DatabaseQueue
    private let zones: SessionZones
    private let now: () -> Date
    private let zone: () -> TimeZone
    /// The open session's reminders, re-synced on every read of the record, so whichever write
    /// opened or closed the session (a check-in, a check-out, the midnight close) also arms or clears
    /// them, as Android's `SessionActions` does.
    private let reminders: SessionReminders?
    /// The open session's Live Activity, re-synced with its reminders.
    private let activity: SessionActivity?
    /// The habit check-in writes to and today's list reads, set through `show(habitId:)`.
    private(set) var habitId: Int64

    init(
        database: DatabaseQueue,
        zones: SessionZones = SessionZones(),
        now: @escaping () -> Date = Date.init,
        zone: @escaping () -> TimeZone = { TimeZone.current },
        reminders: SessionReminders? = nil,
        activity: SessionActivity? = nil,
        habitId: Int64 = RecordDatabase.seedHabitId
    ) {
        self.reminders = reminders
        self.activity = activity
        self.habitId = habitId
        self.database = database
        self.zones = zones
        self.now = now
        self.zone = zone
    }

    /// Closes a forgotten session, then reads the record. Run before anything is drawn and on every
    /// return to the foreground, so no past day's session is ever shown as running (iOS rule 2).
    func refresh() throws {
        try closeForgottenSession()
        try reload()
    }

    /// `habitId`'s completed sessions on `dateKey`: how many, and their summed duration.
    func daySummary(habitId: Int64, dateKey: String) throws -> (count: Int, totalMs: Int64) {
        try database.read { db in
            let row = try Row.fetchOne(
                db,
                sql: """
                    SELECT COUNT(*) AS count, COALESCE(SUM(duration), 0) AS total FROM sessions
                    WHERE habit_id = ? AND date_key = ? AND stopped_at IS NOT NULL
                    """,
                arguments: [habitId, dateKey]
            )
            return (row?["count"] ?? 0, row?["total"] ?? 0)
        }
    }

    /// How many days hold a session of the shown habit, open ones included. Read only to decide
    /// whether the widget page is owed, and never displayed.
    func daysWithSession() throws -> Int {
        try database.read { db in
            try Int.fetchOne(
                db, sql: "SELECT COUNT(DISTINCT date_key) FROM sessions WHERE habit_id = ?", arguments: [habitId]
            ) ?? 0
        }
    }

    /// Shows `habitId`: the habit the next check-in writes to and today's list reads.
    func show(habitId: Int64) throws {
        guard habitId != self.habitId else { return }
        self.habitId = habitId
        try reload()
    }

    func checkIn() throws -> CheckInOutcome {
        let nowDate = now()
        let currentZone = zone()
        let outcome: CheckInOutcome = try database.write { db in
            let open = try SessionRow.fetchOne(db, sql: "SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
            let conflict = SessionRules.shared.conflictFor(
                openHabitId: open.map { KotlinLong(value: $0.habitId) },
                habitId: habitId
            )
            switch conflict {
            case .sameHabit: return .alreadyOpen
            case .otherHabit: return .otherHabitOpen
            default:
                var row = SessionRow(
                    id: nil,
                    startedAt: Clock.nowMs(nowDate),
                    stoppedAt: nil,
                    duration: nil,
                    dateKey: Clock.dateKey(nowDate, in: currentZone),
                    closedBy: nil,
                    habitId: habitId
                )
                try row.insert(db)
                if let id = row.id { zones.record(sessionId: id, zone: currentZone) }
                return .opened
            }
        }
        try reload()
        return outcome
    }

    /// Closes the open session at now, recording what ended it (`ClosedBy`, a CSV column). Returns
    /// the closed row, or nil when nothing was open.
    @discardableResult
    func checkOut(closedBy: ClosedBy = .inApp) throws -> SessionRow? {
        guard let open = try database.read({ db in
            try SessionRow.fetchOne(db, sql: "SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
        }) else { return nil }
        let closed = try close(open, at: Clock.nowMs(now()), closedBy: closedBy)
        try reload()
        return closed
    }

    /// The instant the open session's day ends, read in the zone it was checked in under.
    func dayBoundary(of session: SessionRow) -> Int64 {
        let checkInZone = session.id.flatMap(zones.zone(of:)) ?? zone()
        return SessionRules.shared.dayBoundaryFor(
            dateKey: session.dateKey,
            startedAtMs: session.startedAt,
            zone: Clock.kotlinZone(checkInZone)
        )
    }

    private func closeForgottenSession() throws {
        guard let open = try database.read({ db in
            try SessionRow.fetchOne(db, sql: "SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
        }) else { return }
        let nowMs = Clock.nowMs(now())
        let boundary = dayBoundary(of: open)
        guard nowMs >= boundary else { return }
        let stamp = SessionRules.shared.dayBoundaryStamp(
            boundaryAtMs: boundary, startedAtMs: open.startedAt, nowMs: nowMs
        )
        try close(open, at: stamp, closedBy: ClosedBy.dayBoundary)
    }

    /// Closes `session` once: the `UPDATE` carries `stopped_at IS NULL`, so a second closer racing
    /// this one changes nothing and gets nil, as `CheckInSessionDao.closeIfOpen` does on Android.
    @discardableResult
    private func close(_ session: SessionRow, at stopMs: Int64, closedBy: ClosedBy) throws -> SessionRow? {
        guard let id = session.id else { return nil }
        let duration = SessionRules.shared.durationMs(startedAtMs: session.startedAt, stoppedAtMs: stopMs)
        let changed = try database.write { db in
            try db.execute(
                sql: """
                    UPDATE sessions SET stopped_at = ?, duration = ?, closed_by = ?
                    WHERE id = ? AND stopped_at IS NULL
                    """,
                arguments: [stopMs, duration, closedBy.storedValue, id]
            )
            return db.changesCount
        }
        guard changed > 0 else { return nil }
        zones.clear()
        var closed = session
        closed.stoppedAt = stopMs
        closed.duration = duration
        closed.closedBy = closedBy.storedValue
        return closed
    }

    private func reload() throws {
        let todayKey = Clock.dateKey(now(), in: zone())
        let (open, openName, today) = try database.read { db in
            let open = try SessionRow.fetchOne(db, sql: "SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
            return (
                open,
                try open.flatMap {
                    try String.fetchOne(db, sql: "SELECT name FROM habits WHERE id = ?", arguments: [$0.habitId])
                },
                try SessionRow.fetchAll(
                    db,
                    sql: "SELECT * FROM sessions WHERE habit_id = ? AND date_key = ? ORDER BY started_at ASC",
                    arguments: [habitId, todayKey]
                )
            )
        }
        openSession = open
        todaySessions = today
        let boundary = open.map(dayBoundary(of:))
        reminders?.sync(open: open, dayBoundaryMs: boundary, nowMs: Clock.nowMs(now()))
        activity?.sync(open: open, habitName: openName, dayBoundaryMs: boundary)
    }
}
