import Foundation
import GRDB
import Observation
import Shared

/// Reports for the shown habit: the scope, the one window it resolves to, and every figure for that
/// window. The window comes from the shared `ReportScope.resolve`, the figures from `ReportFigures`
/// and the steps from `ReportScope`'s own, so the screen states what Android's states.
@MainActor
@Observable
final class ReportsStore {

    /// What span every figure describes. Opens on the month in progress.
    private(set) var scope: any ReportScope
    /// False until the record has been read: until then neither the cards nor the empty state is
    /// a true answer, so the screen draws neither.
    private(set) var loaded = false
    /// The window's figures, or nil when the scope holds no counted day: the empty state.
    private(set) var figures: ReportFigures?
    /// The habit's first session day, or nil while it has none.
    private(set) var trackingStart: Kotlinx_datetimeLocalDate?
    private(set) var today: Kotlinx_datetimeLocalDate
    private(set) var habitId: Int64

    private let database: DatabaseQueue
    private let now: () -> Date
    private let zone: () -> TimeZone

    init(
        database: DatabaseQueue,
        now: @escaping () -> Date = Date.init,
        zone: @escaping () -> TimeZone = { TimeZone.current },
        habitId: Int64 = RecordDatabase.seedHabitId
    ) {
        self.database = database
        self.now = now
        self.zone = zone
        self.habitId = habitId
        let today = Clock.localDate(now(), in: zone())
        self.today = today
        self.scope = ReportScopeKt.currentMonthScope(today: today)
    }

    var isAllTime: Bool { scope is ReportScopeAllTime }

    /// Whether a later month exists to step to: false under all time and on the month in progress.
    var hasNextMonth: Bool { ReportScopeKt.hasMonthAfter(scope, today: today) }

    /// Shows `habitId`. A month stepped to under one habit means nothing under the next, so a switch
    /// returns to the month in progress.
    func show(habitId: Int64) throws {
        guard habitId != self.habitId else { return }
        self.habitId = habitId
        scope = ReportScopeKt.currentMonthScope(today: today)
        try reload()
    }

    func previousMonth() throws {
        scope = ReportScopeKt.previousMonth(scope, today: today)
        try reload()
    }

    func nextMonth() throws {
        scope = ReportScopeKt.nextMonth(scope, today: today)
        try reload()
    }

    func selectMonth() throws {
        scope = ReportScopeKt.monthChosen(scope, today: today)
        try reload()
    }

    func selectAllTime() throws {
        scope = ReportScopeAllTime.shared
        try reload()
    }

    /// What an export wrote.
    enum Export: Equatable {
        case file(URL)
        /// The scope holds no completed day, so no file was written. Not a failure.
        case nothing
    }

    /// Re-reads the record for the shown scope and habit, with today read afresh.
    func reload() throws {
        let today = Clock.localDate(now(), in: zone())
        let (start, window) = try resolveWindow(today: today)

        var figures: ReportFigures?
        if let window {
            let firstKey = window.start.description()
            let lastKey = window.end.description()
            // Both read over the identical range, so the start-time split describes exactly the
            // sessions the rest of the screen counts.
            let (days, starts) = try database.read { [habitId] db in
                (
                    try DailyAggregates.fetch(db, habitId: habitId, from: firstKey, through: lastKey),
                    try Int64.fetchAll(
                        db,
                        sql: """
                            SELECT started_at FROM sessions
                            WHERE habit_id = ? AND date_key BETWEEN ? AND ? AND stopped_at IS NOT NULL
                            ORDER BY started_at ASC
                            """,
                        arguments: [habitId, firstKey, lastKey]
                    )
                )
            }
            figures = ReportFigures.companion.of(
                scope: scope,
                window: window,
                summaries: DailyAggregates.byDateKey(days),
                sessionStarts: starts.map { KotlinLong(value: $0) },
                zone: Clock.kotlinZone(zone())
            )
        }
        self.today = today
        trackingStart = start
        self.figures = figures
        loaded = true
    }

    /// Writes the shown habit's CSV for the screen's own window, through the shared `csvLines`, into
    /// `folder` as `exportFileName` names it. The window is resolved afresh from one reading of the
    /// clock, so the file and the screen describe the same days. A scope with no counted day, or a
    /// window holding no completed day, writes nothing: a file of pure gap-fill would assert absences
    /// the app never recorded.
    func export(
        habitName: String?,
        into folder: URL = FileManager.default.temporaryDirectory.appendingPathComponent("exports")
    ) throws -> Export {
        let today = Clock.localDate(now(), in: zone())
        guard case let (_, window?) = try resolveWindow(today: today) else { return .nothing }
        let startKey = window.start.description()
        let endKey = window.end.description()
        let days = try database.read { [habitId] db in
            try DailyAggregates.fetch(db, habitId: habitId, from: startKey, through: endKey)
        }
        guard !days.isEmpty else { return .nothing }

        // The clock columns read the device's zone, as the person exporting would read them.
        let lines = CsvFormatKt.csvLines(
            startKey: startKey, endKey: endKey, summaries: DailyAggregates.byDateKey(days), zone: Clock.kotlinZone(zone())
        ).iterator()
        var text = ""
        while lines.hasNext() {
            if let line = lines.next() as? String { text += line }
        }
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let file = folder.appendingPathComponent(
            CsvFormatKt.exportFileName(habitName: habitName, startKey: startKey, endKey: endKey)
        )
        try Data(text.utf8).write(to: file, options: .atomic)
        return .file(file)
    }

    /// The habit's first session day and the window the scope resolves to on `today`: shading ends
    /// at `ConsistencyStats.countedThrough`, so the window ends at yesterday until today is checked
    /// out of.
    private func resolveWindow(today: Kotlinx_datetimeLocalDate) throws
        -> (Kotlinx_datetimeLocalDate?, ReportWindow?) {
        let todayKey = today.description()
        let (startKey, todayDays) = try database.read { [habitId] db in
            (
                try String.fetchOne(db, sql: "SELECT MIN(date_key) FROM sessions WHERE habit_id = ?", arguments: [habitId]),
                try DailyAggregates.fetch(db, habitId: habitId, from: todayKey, through: todayKey)
            )
        }
        let start = startKey.flatMap(Clock.localDate(key:))
        let countedThrough = ConsistencyStats.shared.countedThrough(
            summaries: DailyAggregates.byDateKey(todayDays), today: today
        )
        return (start, ReportScopeKt.resolve(scope, trackingStart: start, countedThrough: countedThrough))
    }
}
