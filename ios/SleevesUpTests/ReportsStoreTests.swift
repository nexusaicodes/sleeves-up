import Shared
import XCTest
@testable import SleevesUp

/// Reports on iOS, against an in-memory record: an empty scope until a day is checked out of, the
/// window ending at yesterday until today is, figures for the shown habit alone, and the Month /
/// All time steps.
@MainActor
final class ReportsStoreTests: XCTestCase {

    // 2026-06-10 09:00 and 2026-06-15 09:00 UTC.
    private let utcJun10At0900: Int64 = 1_781_082_000_000
    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private let hour: Int64 = 3_600_000

    private var nowMs: Int64 = 0
    private var defaults: UserDefaults!
    private var uuids = 0

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "ReportsStoreTests")!
        defaults.removePersistentDomain(forName: "ReportsStoreTests")
        nowMs = utcJun15At0900
    }

    private func makeStores() throws -> (ReportsStore, SessionStore, HabitStore) {
        let database = try RecordDatabase.openInMemory(nowMs: 0)
        let now = { [unowned self] in Clock.date(ms: self.nowMs) }
        let utc = { TimeZone(identifier: "UTC")! }
        let reports = ReportsStore(database: database, now: now, zone: utc)
        let sessions = SessionStore(database: database, zones: SessionZones(defaults: defaults), now: now, zone: utc)
        let habits = HabitStore(database: database, now: now) { [unowned self] in
            uuids += 1
            return "uuid-\(uuids)"
        }
        try reports.reload()
        return (reports, sessions, habits)
    }

    /// One hour on the 10th, read back on the 15th.
    private func sessionOnJune10(_ sessions: SessionStore) throws {
        nowMs = utcJun10At0900
        XCTAssertEqual(try sessions.checkIn(), .opened)
        nowMs += hour
        try sessions.checkOut()
        nowMs = utcJun15At0900
    }

    func testAFreshRecordIsAnEmptyScopeOnTheMonthInProgress() throws {
        let (reports, _, _) = try makeStores()
        XCTAssertTrue(reports.loaded)
        XCTAssertNil(reports.figures)
        XCTAssertEqual(ReportText.scopeLabel(reports.scope), "June 2026")
        XCTAssertFalse(reports.hasNextMonth)
    }

    func testTheWindowEndsAtYesterdayUntilTodayIsCheckedOutOf() throws {
        let (reports, sessions, _) = try makeStores()
        try sessionOnJune10(sessions)
        _ = try sessions.checkIn()
        try reports.reload()

        var figures = try XCTUnwrap(reports.figures)
        XCTAssertEqual(figures.totalDays, 5, "the 10th to the 14th")
        XCTAssertEqual(figures.showedUpDays, 1)
        XCTAssertEqual(figures.missedDays, 4)
        XCTAssertEqual(figures.totalWorkedMs, hour)
        XCTAssertEqual(figures.startBuckets[StartBucket.morning]?.int32Value, 1)

        nowMs += 2 * hour
        try sessions.checkOut()
        try reports.reload()
        figures = try XCTUnwrap(reports.figures)
        XCTAssertEqual(figures.totalDays, 6)
        XCTAssertEqual(figures.showedUpDays, 2)
        XCTAssertEqual(figures.totalWorkedMs, 3 * hour)
        XCTAssertEqual(figures.dailySeries.count, 6)
    }

    func testAnOpenFirstSessionIsStillAnEmptyScope() throws {
        let (reports, sessions, _) = try makeStores()
        _ = try sessions.checkIn()
        try reports.reload()
        XCTAssertNil(reports.figures, "today in progress is the record's only day, and it is not counted yet")
    }

    func testFiguresAreTheShownHabitsAloneAndASwitchReturnsToTheMonthInProgress() throws {
        let (reports, sessions, habits) = try makeStores()
        try sessionOnJune10(sessions)
        XCTAssertEqual(try habits.create(name: "Reading"), .saved)
        try reports.previousMonth()

        try reports.show(habitId: 2)
        XCTAssertEqual(ReportText.scopeLabel(reports.scope), "June 2026")
        XCTAssertNil(reports.figures)

        try reports.show(habitId: 1)
        XCTAssertEqual(reports.figures?.showedUpDays, 1)
    }

    func testTheMonthAndAllTimeSteps() throws {
        let (reports, sessions, _) = try makeStores()
        try sessionOnJune10(sessions)
        try reports.reload()

        try reports.previousMonth()
        XCTAssertEqual(ReportText.scopeLabel(reports.scope), "May 2026")
        XCTAssertNil(reports.figures, "a month before the record is an empty scope")
        XCTAssertTrue(reports.hasNextMonth)

        try reports.selectAllTime()
        XCTAssertTrue(reports.isAllTime)
        XCTAssertFalse(reports.hasNextMonth)
        XCTAssertEqual(reports.figures?.totalDays, 5)
        try reports.nextMonth()
        XCTAssertTrue(reports.isAllTime, "under all time the stepper moves nothing")

        try reports.selectMonth()
        XCTAssertEqual(ReportText.scopeLabel(reports.scope), "June 2026", "Month returns to the month in progress")
    }
}
