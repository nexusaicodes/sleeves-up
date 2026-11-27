import GRDB
import XCTest
@testable import SleevesUp

/// The open session's Live Activity: started at check-in under the habit's name and ending at its
/// own midnight, renamed with the habit, ended by whatever closed the session, and never started
/// twice for one session, so a dismissed activity stays dismissed.
@MainActor
final class SessionActivityTests: XCTestCase {

    private final class FakeHost: LiveActivityHost {
        var shown: [Int64: (attributes: SessionActivityAttributes, name: String?)] = [:]
        var refuses = false
        var starts = 0

        func running() -> [(sessionId: Int64, habitName: String?)] {
            shown.map { ($0.key, $0.value.name) }
        }

        func start(_ attributes: SessionActivityAttributes, habitName: String?) -> Bool {
            guard !refuses else { return false }
            starts += 1
            shown[attributes.sessionId] = (attributes, habitName)
            return true
        }

        func rename(sessionId: Int64, to habitName: String?) { shown[sessionId]?.name = habitName }
        func end(sessionId: Int64) { shown[sessionId] = nil }
    }

    // 2026-06-15 09:00 UTC.
    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private let hour: Int64 = 3_600_000

    private var nowMs: Int64 = 0
    private var defaults: UserDefaults!
    private var host: FakeHost!

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "SessionActivityTests")!
        defaults.removePersistentDomain(forName: "SessionActivityTests")
        nowMs = utcJun15At0900
        host = FakeHost()
    }

    private var install = "first"

    private func makeStores(database existing: DatabaseQueue? = nil) throws -> (SessionStore, HabitStore) {
        let database = try existing ?? RecordDatabase.openInMemory(nowMs: 0)
        let now = { [unowned self] in Clock.date(ms: self.nowMs) }
        let sessions = SessionStore(
            database: database, zones: SessionZones(defaults: defaults), now: now,
            zone: { TimeZone(identifier: "UTC")! },
            activity: SessionActivity(host: host, defaults: defaults, install: install)
        )
        let habits = HabitStore(database: database, now: now)
        try habits.reload()
        return (sessions, habits)
    }

    func testACheckInStartsOneUnderTheHabitsNameEndingAtMidnight() throws {
        let (sessions, habits) = try makeStores()
        XCTAssertEqual(try habits.rename(RecordDatabase.seedHabitId, to: "Gym"), .saved)
        XCTAssertEqual(try sessions.checkIn(), .opened)

        let shown = try XCTUnwrap(host.shown.values.first)
        XCTAssertEqual(host.shown.count, 1)
        XCTAssertEqual(shown.name, "Gym")
        XCTAssertEqual(shown.attributes.startedAt, Clock.date(ms: utcJun15At0900))
        XCTAssertEqual(shown.attributes.endsAt, Clock.date(ms: utcJun15At0900 + 15 * hour))
    }

    func testARenameReachesItAndACheckOutEndsIt() throws {
        let (sessions, habits) = try makeStores()
        _ = try sessions.checkIn()
        XCTAssertNil(host.shown.values.first?.name)

        XCTAssertEqual(try habits.rename(RecordDatabase.seedHabitId, to: "Gym"), .saved)
        try sessions.refresh()
        XCTAssertEqual(host.shown.values.first?.name, "Gym")

        nowMs += hour
        _ = try sessions.checkOut()
        XCTAssertTrue(host.shown.isEmpty)
    }

    func testTheMidnightCloseEndsIt() throws {
        let (sessions, _) = try makeStores()
        _ = try sessions.checkIn()
        nowMs += 20 * hour
        try sessions.refresh()
        XCTAssertTrue(host.shown.isEmpty)
    }

    func testADismissedActivityIsNotStartedAgainForTheSameSession() throws {
        let (sessions, _) = try makeStores()
        _ = try sessions.checkIn()
        host.shown.removeAll()
        try sessions.refresh()
        XCTAssertTrue(host.shown.isEmpty)
        XCTAssertEqual(host.starts, 1)
    }

    func testANewInstallStartsItAgainForTheSessionStillOpen() throws {
        let database = try RecordDatabase.openInMemory(nowMs: 0)
        let (sessions, _) = try makeStores(database: database)
        _ = try sessions.checkIn()
        host.shown.removeAll()

        install = "second"
        let (updated, _) = try makeStores(database: database)
        try updated.refresh()
        XCTAssertEqual(host.starts, 2)
        XCTAssertEqual(host.shown.count, 1)
    }

    func testARefusedStartIsAskedAgainAndTheNextSessionGetsItsOwn() throws {
        let (sessions, _) = try makeStores()
        host.refuses = true
        _ = try sessions.checkIn()
        host.refuses = false
        try sessions.refresh()
        XCTAssertEqual(host.starts, 1, "Live Activities switched back on mid-session")

        nowMs += hour
        _ = try sessions.checkOut()
        nowMs += hour
        _ = try sessions.checkIn()
        XCTAssertEqual(host.starts, 2)
        XCTAssertEqual(host.shown.count, 1)
    }
}
