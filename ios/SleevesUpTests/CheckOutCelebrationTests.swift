import XCTest
@testable import SleevesUp

/// The celebration's figures and its mark: the closed session's own day for its habit, open and
/// other habits' sessions left out, and Android's settle schedule.
@MainActor
final class CheckOutCelebrationTests: XCTestCase {

    // 2026-06-15 09:00 UTC.
    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private let hour: Int64 = 3_600_000

    func testTheDaySummaryCountsCompletedSessionsOfThatHabitAndDay() throws {
        var nowMs = utcJun15At0900
        let defaults = UserDefaults(suiteName: "CheckOutCelebrationTests")!
        defaults.removePersistentDomain(forName: "CheckOutCelebrationTests")
        let store = SessionStore(
            database: try RecordDatabase.openInMemory(nowMs: 0), zones: SessionZones(defaults: defaults),
            now: { Clock.date(ms: nowMs) }, zone: { TimeZone(identifier: "UTC")! }
        )
        _ = try store.checkIn()
        nowMs += hour
        let first = try XCTUnwrap(try store.checkOut())
        nowMs += hour
        _ = try store.checkIn()
        nowMs += 2 * hour
        let second = try XCTUnwrap(try store.checkOut())
        nowMs += hour
        _ = try store.checkIn()

        let day = try store.daySummary(habitId: second.habitId, dateKey: second.dateKey)
        XCTAssertEqual(day.count, 2, "the open session counts for nothing")
        XCTAssertEqual(day.totalMs, (first.duration ?? 0) + (second.duration ?? 0))
        XCTAssertEqual(try store.daySummary(habitId: 2, dateKey: second.dateKey).count, 0)
    }

    func testTheMarkSettlesInReadingOrderOverAndroidsSchedule() {
        XCTAssertEqual(MarkSettle.cells.count, 7)
        XCTAssertEqual(MarkSettle.settledMs, 6 * 70 + 260)
        XCTAssertEqual(MarkSettle.progress(index: 0, elapsedMs: 0), 0)
        XCTAssertEqual(MarkSettle.progress(index: 1, elapsedMs: 70 + 130), 0.5)
        XCTAssertEqual(MarkSettle.progress(index: 6, elapsedMs: MarkSettle.settledMs), 1)
        XCTAssertTrue((0..<7).allSatisfy { MarkSettle.progress(index: $0, elapsedMs: MarkSettle.settledMs) == 1 })
    }
}
