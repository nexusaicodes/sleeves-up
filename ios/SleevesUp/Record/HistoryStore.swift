import Foundation
import GRDB
import Observation
import Shared

/// History for the shown habit: one month of days in the shared `CalendarMonth`'s states, and the
/// sessions of whichever day is tapped. It derives no figure; Reports does. Each day's state comes
/// from `CalendarMonth.days`, where shading ends from `ConsistencyStats.countedThrough` and the
/// forward step from `MonthSteps`, so the calendar draws what Android's draws.
@MainActor
@Observable
final class HistoryStore {

    /// A calendar month, stepped in Swift and handed to the shared code as a `YearMonth`.
    struct Month: Equatable {
        var year: Int32
        /// 1 to 12.
        var month: Int32

        init(year: Int32, month: Int32) {
            self.year = year
            self.month = month
        }

        init(_ date: Kotlinx_datetimeLocalDate) {
            self.init(year: date.year, month: Clock.monthNumber(date))
        }

        func adding(_ months: Int32) -> Month {
            let index = year * 12 + (month - 1) + months
            return Month(year: index / 12, month: index % 12 + 1)
        }

        var kotlin: Kotlinx_datetimeYearMonth { Kotlinx_datetimeYearMonth(year: year, month: month) }
    }

    private(set) var month: Month
    private(set) var today: Kotlinx_datetimeLocalDate
    /// The shown month's days, in order.
    private(set) var days: [CalendarDay] = []
    /// Empty cells before the month's first day, under the device's week start.
    private(set) var leadingBlanks = 0
    /// The shown month's days holding a completed session, by `date_key`.
    private(set) var summaries: [String: DailyAggregate] = [:]
    private(set) var selectedKey: String?
    /// The tapped day's sessions, an open one included, in check-in order.
    private(set) var selectedSessions: [SessionRow] = []
    private(set) var habitId: Int64

    private let database: DatabaseQueue
    private let now: () -> Date
    private let zone: () -> TimeZone
    private let calendar: () -> Calendar

    init(
        database: DatabaseQueue,
        now: @escaping () -> Date = Date.init,
        zone: @escaping () -> TimeZone = { TimeZone.current },
        calendar: @escaping () -> Calendar = { Calendar.current },
        habitId: Int64 = RecordDatabase.seedHabitId
    ) {
        self.database = database
        self.now = now
        self.zone = zone
        self.calendar = calendar
        self.habitId = habitId
        let today = Clock.localDate(now(), in: zone())
        self.today = today
        self.month = Month(today)
    }

    /// Whether a later month exists to step to: false on the month in progress.
    var hasNextMonth: Bool { MonthSteps.shared.hasMonthAfter(shown: month.kotlin, today: today) }

    /// The device's week start, as the shared code names weekdays.
    var weekStart: Kotlinx_datetimeDayOfWeek {
        // Foundation counts weekdays from Sunday = 1; ISO from Monday = 1.
        let iso = (calendar().firstWeekday + 5) % 7 + 1
        return Kotlinx_datetimeDayOfWeek.entries[iso - 1]
    }

    func show(habitId: Int64) throws {
        guard habitId != self.habitId else { return }
        self.habitId = habitId
        try reload()
    }

    func previousMonth() throws {
        month = month.adding(-1)
        selectedKey = nil
        try reload()
    }

    /// Stops at the month in progress: a month that has not begun holds no day to look back on.
    func nextMonth() throws {
        guard hasNextMonth else { return }
        month = month.adding(1)
        selectedKey = nil
        try reload()
    }

    /// Selects the day with `dateKey`, or clears the selection when it is already selected.
    func select(_ dateKey: String) throws {
        selectedKey = selectedKey == dateKey ? nil : dateKey
        try reload()
    }

    /// Re-reads the record for the shown month and habit. Today is read afresh; the shown month
    /// stays where it is.
    func reload() throws {
        let today = Clock.localDate(now(), in: zone())
        let shown = month.kotlin
        let firstKey = shown.firstDay.description()
        let lastKey = shown.lastDay.description()
        let todayKey = today.description()
        let (monthDays, todayDays, start, sessions) = try database.read { [habitId, selectedKey] db in
            (
                try DailyAggregates.fetch(db, habitId: habitId, from: firstKey, through: lastKey),
                try DailyAggregates.fetch(db, habitId: habitId, from: todayKey, through: todayKey),
                try String.fetchOne(db, sql: "SELECT MIN(date_key) FROM sessions WHERE habit_id = ?", arguments: [habitId]),
                try selectedKey.map { key in
                    try SessionRow.fetchAll(
                        db,
                        sql: "SELECT * FROM sessions WHERE habit_id = ? AND date_key = ? ORDER BY started_at ASC",
                        arguments: [habitId, key]
                    )
                } ?? []
            )
        }
        let countedThrough = ConsistencyStats.shared.countedThrough(
            summaries: DailyAggregates.byDateKey(todayDays), today: today
        )
        let byKey = DailyAggregates.byDateKey(monthDays)
        self.today = today
        summaries = byKey
        selectedSessions = sessions
        leadingBlanks = Int(CalendarMonth.shared.leadingBlanks(month: shown, firstDayOfWeek: weekStart))
        days = CalendarMonth.shared.days(
            month: shown,
            recordedKeys: Set(byKey.keys),
            trackingStart: start.flatMap(Clock.localDate(key:)),
            today: today,
            countedThrough: countedThrough
        )
    }
}
