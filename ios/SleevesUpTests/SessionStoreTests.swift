import XCTest
@testable import SleevesUp

/// The record's writes on iOS: one open session, a check-out that closes once, and a forgotten
/// session closed at its own midnight in the zone it was checked in under (iOS rule 2). Instants are
/// epoch millis computed with java.time, the same reading the shared tests pin.
@MainActor
final class SessionStoreTests: XCTestCase {

    // 2026-06-15 09:00 and 2026-06-16 00:00 in UTC.
    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private let utcJun16Midnight: Int64 = 1_781_568_000_000
    // 2026-06-15 23:00 and 2026-06-16 00:00 in Asia/Kolkata.
    private let kolkataJun15At2300: Int64 = 1_781_544_600_000
    private let kolkataJun16Midnight: Int64 = 1_781_548_200_000
    private let hour: Int64 = 3_600_000

    private var nowMs: Int64 = 0
    private var zone = TimeZone(identifier: "UTC")!
    private var defaults: UserDefaults!

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "SessionStoreTests")!
        defaults.removePersistentDomain(forName: "SessionStoreTests")
    }

    private func makeStore() throws -> SessionStore {
        SessionStore(
            database: try RecordDatabase.openInMemory(nowMs: 0),
            zones: SessionZones(defaults: defaults),
            now: { [unowned self] in Clock.date(ms: self.nowMs) },
            zone: { [unowned self] in self.zone }
        )
    }

    func testCheckInOpensOneSessionKeyedToTodayInTheZone() throws {
        nowMs = kolkataJun15At2300
        zone = TimeZone(identifier: "Asia/Kolkata")!
        let store = try makeStore()

        XCTAssertEqual(try store.checkIn(), .opened)
        XCTAssertEqual(store.openSession?.startedAt, kolkataJun15At2300)
        // 17:30 UTC, but the 15th in Kolkata, where it was checked in.
        XCTAssertEqual(store.openSession?.dateKey, "2026-06-15")
        XCTAssertEqual(store.openSession?.habitId, RecordDatabase.seedHabitId)
    }

    func testASecondCheckInWritesNothing() throws {
        nowMs = utcJun15At0900
        let store = try makeStore()
        _ = try store.checkIn()

        nowMs += hour
        XCTAssertEqual(try store.checkIn(), .alreadyOpen)
        XCTAssertEqual(store.todaySessions.count, 1)
    }

    func testCheckOutClosesOnceWithExactDuration() throws {
        nowMs = utcJun15At0900
        let store = try makeStore()
        _ = try store.checkIn()

        nowMs += 45 * 60_000
        let closed = try store.checkOut()
        XCTAssertEqual(closed?.duration, 45 * 60_000)
        XCTAssertEqual(closed?.closedBy, "in_app")
        XCTAssertNil(store.openSession)
        XCTAssertNil(try store.checkOut())
    }

    func testAClockMovedBackClosesWithZeroDuration() throws {
        nowMs = utcJun15At0900
        let store = try makeStore()
        _ = try store.checkIn()

        nowMs -= hour
        XCTAssertEqual(try store.checkOut()?.duration, 0)
    }

    func testAForgottenSessionClosesAtItsMidnightNotWhenTheAppReturns() throws {
        nowMs = utcJun15At0900
        let store = try makeStore()
        _ = try store.checkIn()

        nowMs = utcJun16Midnight + 9 * hour
        try store.refresh()

        XCTAssertNil(store.openSession)
        let closed = try XCTUnwrap(try sessionsOnDay(at: utcJun15At0900, of: store).first)
        XCTAssertEqual(closed.stoppedAt, utcJun16Midnight)
        XCTAssertEqual(closed.duration, utcJun16Midnight - utcJun15At0900)
        XCTAssertEqual(closed.closedBy, "day_boundary")
    }

    func testASessionBeforeItsMidnightStaysOpen() throws {
        nowMs = utcJun15At0900
        let store = try makeStore()
        _ = try store.checkIn()

        nowMs = utcJun16Midnight - 1
        try store.refresh()
        XCTAssertNotNil(store.openSession)
    }

    /// Checked in at 23:00 in Kolkata and reopened in Los Angeles: Kolkata's midnight, one hour later,
    /// not Los Angeles's, which would stamp many hours nobody worked.
    func testTheCloseReadsTheZoneOfTheCheckIn() throws {
        nowMs = kolkataJun15At2300
        zone = TimeZone(identifier: "Asia/Kolkata")!
        let store = try makeStore()
        _ = try store.checkIn()

        zone = TimeZone(identifier: "America/Los_Angeles")!
        nowMs = kolkataJun16Midnight + 2 * hour
        try store.refresh()

        zone = TimeZone(identifier: "Asia/Kolkata")!
        let closed = try XCTUnwrap(try sessionsOnDay(at: kolkataJun15At2300, of: store).first)
        XCTAssertEqual(closed.stoppedAt, kolkataJun16Midnight)
        XCTAssertEqual(closed.duration, hour)
    }

    /// The sessions of the day holding `instant`, read as the screen reads them: by moving the clock
    /// back to that day and refreshing. Nothing is open by then, so the refresh closes nothing.
    private func sessionsOnDay(at instant: Int64, of store: SessionStore) throws -> [SessionRow] {
        nowMs = instant
        try store.refresh()
        return store.todaySessions
    }
}
