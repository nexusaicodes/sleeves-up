import Foundation
import GRDB
import Observation
import Shared

/// The seed habit's sessions: the open one, today's, and the only two writes, check-in and
/// check-out, plus the day-boundary close of a forgotten session. Every rule a write follows comes
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
    private let habitId = RecordDatabase.seedHabitId

    init(
        database: DatabaseQueue,
        zones: SessionZones = SessionZones(),
        now: @escaping () -> Date = Date.init,
        zone: @escaping () -> TimeZone = { TimeZone.current }
    ) {
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

    /// Closes the open session at now. Returns the closed row, or nil when nothing was open.
    @discardableResult
    func checkOut() throws -> SessionRow? {
        guard let open = try database.read({ db in
            try SessionRow.fetchOne(db, sql: "SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
        }) else { return nil }
        let closed = try close(open, at: Clock.nowMs(now()), closedBy: ClosedBy.inApp)
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
        let (open, today) = try database.read { db in
            (
                try SessionRow.fetchOne(db, sql: "SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1"),
                try SessionRow.fetchAll(
                    db,
                    sql: "SELECT * FROM sessions WHERE habit_id = ? AND date_key = ? ORDER BY started_at ASC",
                    arguments: [habitId, todayKey]
                )
            )
        }
        openSession = open
        todaySessions = today
    }
}
