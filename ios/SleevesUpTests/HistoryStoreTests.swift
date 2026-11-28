import Shared
import XCTest
@testable import SleevesUp

/// History on iOS, against an in-memory record: a day shades at check-out and an open session is
/// listed but not shaded, shading is per habit, the month stepper stops at the current month, and
/// the grid starts on the calendar's week start.
@MainActor
final class HistoryStoreTests: XCTestCase {

    // 2026-06-15 09:00 UTC, a Monday; 1 June 2026 is a Monday too.
    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private let hour: Int64 = 3_600_000

    private var nowMs: Int64 = 0
    private var firstWeekday = 2
    private var defaults: UserDefaults!
    private var uuids = 0

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "HistoryStoreTests")!
        defaults.removePersistentDomain(forName: "HistoryStoreTests")
        nowMs = utcJun15At0900
    }

    private func makeStores() throws -> (HistoryStore, SessionStore, HabitStore) {
        let database = try RecordDatabase.openInMemory(nowMs: 0)
        let now = { [unowned self] in Clock.date(ms: self.nowMs) }
        let utc = { TimeZone(identifier: "UTC")! }
        let history = HistoryStore(database: database, now: now, zone: utc) { [unowned self] in
            var calendar = Calendar(identifier: .gregorian)
            calendar.firstWeekday = self.firstWeekday
            return calendar
        }
        let sessions = SessionStore(database: database, zones: SessionZones(defaults: defaults), now: now, zone: utc)
        let habits = HabitStore(database: database, now: now) { [unowned self] in
            uuids += 1
            return "uuid-\(uuids)"
        }
        try history.reload()
        return (history, sessions, habits)
    }

    private func day(_ history: HistoryStore, _ dayOfMonth: Int) -> CalendarDay {
        history.days[dayOfMonth - 1]
    }

    func testAFreshRecordDrawsEveryDayOutsideTheRecord() throws {
        let (history, _, _) = try makeStores()
        XCTAssertEqual(history.days.count, 30)
        XCTAssertTrue(history.days.allSatisfy { $0.outsideRecord })
        XCTAssertFalse(history.days.contains { $0.recorded })
        XCTAssertTrue(day(history, 15).today)
    }

    func testTodayShadesAtCheckOutAndAnOpenSessionIsListedButNotShaded() throws {
        let (history, sessions, _) = try makeStores()
        XCTAssertEqual(try sessions.checkIn(), .opened)
        try history.select("2026-06-15")

        XCTAssertFalse(day(history, 15).recorded)
        XCTAssertFalse(day(history, 15).outsideRecord, "the first session's day begins the record")
        XCTAssertEqual(history.selectedSessions.count, 1)
        XCTAssertNil(history.selectedSessions.first?.stoppedAt)

        nowMs += hour
        try sessions.checkOut()
        try history.reload()
        XCTAssertTrue(day(history, 15).recorded)
        XCTAssertEqual(history.summaries["2026-06-15"]?.totalDurationMs, hour)
        XCTAssertEqual(history.selectedSessions.first?.duration, hour)
        XCTAssertTrue(day(history, 14).outsideRecord, "a day before the first session")
        XCTAssertTrue(day(history, 16).outsideRecord, "the future")
    }

    func testShadingIsTheShownHabitsAlone() throws {
        let (history, sessions, habits) = try makeStores()
        XCTAssertEqual(try habits.create(name: "Reading"), .saved)
        _ = try sessions.checkIn()
        nowMs += hour
        try sessions.checkOut()

        try history.show(habitId: 2)
        XCTAssertFalse(history.days.contains { $0.recorded })
        XCTAssertTrue(history.days.allSatisfy { $0.outsideRecord }, "Reading has no session yet")

        try history.show(habitId: 1)
        XCTAssertTrue(day(history, 15).recorded)
    }

    func testTheStepperStopsAtTheCurrentMonthAndAStepClearsTheSelection() throws {
        let (history, _, _) = try makeStores()
        XCTAssertFalse(history.hasNextMonth)
        try history.nextMonth()
        XCTAssertEqual(history.month, HistoryStore.Month(year: 2026, month: 6))

        try history.select("2026-06-03")
        try history.previousMonth()
        XCTAssertEqual(history.month, HistoryStore.Month(year: 2026, month: 5))
        XCTAssertNil(history.selectedKey)
        XCTAssertEqual(history.days.count, 31)
        XCTAssertTrue(history.hasNextMonth)
    }

    func testTappingTheSelectedDayAgainClearsIt() throws {
        let (history, _, _) = try makeStores()
        try history.select("2026-06-03")
        XCTAssertEqual(history.selectedKey, "2026-06-03")
        try history.select("2026-06-03")
        XCTAssertNil(history.selectedKey)
    }

    func testTheGridStartsOnTheCalendarsWeekStart() throws {
        firstWeekday = 2
        let (monday, _, _) = try makeStores()
        XCTAssertEqual(monday.leadingBlanks, 0)

        firstWeekday = 1
        let (sunday, _, _) = try makeStores()
        XCTAssertEqual(sunday.leadingBlanks, 1)

        firstWeekday = 7
        let (saturday, _, _) = try makeStores()
        XCTAssertEqual(saturday.leadingBlanks, 2)
    }

    func testAYearEndStepKeepsTheMonthInRange() {
        XCTAssertEqual(HistoryStore.Month(year: 2026, month: 1).adding(-1), HistoryStore.Month(year: 2025, month: 12))
        XCTAssertEqual(HistoryStore.Month(year: 2025, month: 12).adding(1), HistoryStore.Month(year: 2026, month: 1))
    }
}
