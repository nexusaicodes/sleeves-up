import Shared
import SwiftUI

/// The History tab: a month calendar for the selected habit and the sessions of whichever day is
/// tapped. A day is shaded for holding a completed session, whatever it held, and only days outside
/// the record are faded; nothing here derives a figure beyond restating the rows on screen.
struct HistoryView: View {

    let history: HistoryStore
    let habits: HabitStore

    @State private var failed = false

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: Self.spacing) {
                        monthStepper
                        CalendarGridView(history: history, cellHeight: cellHeight(for: proxy.size.height)) { key in
                            act { try history.select(key) }
                        }
                        daySlot
                        if failed { Text(HistoryText.readFailed).font(AppFont.bodySmall).foregroundStyle(Palette.inkMuted) }
                    }
                    .padding(Self.padding)
                }
            }
            .background(Palette.paper.ignoresSafeArea())
            .habitSwitcher(habits)
            .onAppear { act { try history.reload() } }
        }
    }

    private static let padding: CGFloat = 16
    private static let spacing: CGFloat = 16
    private static let stepperHeight: CGFloat = 48
    private static let weekdayHeaderHeight: CGFloat = 20
    /// The slot under the grid, held at its tallest state (a selected day holding nothing) because
    /// the grid is sized before the slot is laid out. Erring high costs grid height; erring low would
    /// push the slot off screen.
    private static let detailSlotHeight: CGFloat = 180

    /// The cell height that fits the month and the day slot in the real viewport, as Android's
    /// History does: never under a 48-point target (a six-row month may then scroll slightly) and
    /// never over 80, where a cell stops reading as a day and becomes a ribbon.
    private func cellHeight(for viewport: CGFloat) -> CGFloat {
        let rows = CGFloat(max((history.leadingBlanks + history.days.count + 6) / 7, 1))
        let chrome = Self.padding * 2 + Self.spacing * 2 + Self.stepperHeight + Self.weekdayHeaderHeight
            + Self.detailSlotHeight + CalendarGridView.spacing * rows
        return min(max((viewport - chrome) / rows, 48), 80)
    }

    private var monthStepper: some View {
        HStack {
            Button { act { try history.previousMonth() } } label: { MonthArrow(systemName: "chevron.left") }
                .accessibilityLabel("Previous month")
            Spacer()
            Text(HistoryText.monthYear(history.month)).font(AppFont.titleMedium).foregroundStyle(Palette.ink)
            Spacer()
            Button { act { try history.nextMonth() } } label: { MonthArrow(systemName: "chevron.right") }
                .accessibilityLabel("Next month")
                .disabled(!history.hasNextMonth)
        }
    }

    /// Nothing selected, a selected day holding nothing (titled with its date, so the tap visibly
    /// landed), or the day's sessions under a count and the sum of the durations printed below it.
    @ViewBuilder
    private var daySlot: some View {
        if let key = history.selectedKey {
            VStack(alignment: .leading, spacing: 8) {
                Text(HistoryText.dateWithWeekday(key)).font(AppFont.titleMedium).foregroundStyle(Palette.ink)
                if history.selectedSessions.isEmpty {
                    Text("No sessions recorded on this day.").foregroundStyle(Palette.inkMuted)
                } else {
                    Text(HistoryText.daySummary(history.selectedSessions))
                        .font(AppFont.labelLarge)
                        .foregroundStyle(Palette.inkMuted)
                    ForEach(history.selectedSessions, id: \.id) { session in
                        SessionIntervalRow(session: session)
                    }
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text("No day selected").font(AppFont.titleMedium).foregroundStyle(Palette.ink)
                Text("Tap a day in the calendar to see its sessions.").foregroundStyle(Palette.inkMuted)
            }
        }
    }

    private func act(_ read: () throws -> Void) {
        do {
            try read()
            failed = false
        } catch {
            failed = true
        }
    }
}

/// The month's grid: weekday initials from the device's week start, then each day in the state
/// the shared `CalendarMonth` gives it.
private struct CalendarGridView: View {

    let history: HistoryStore
    let cellHeight: CGFloat
    let onTap: (String) -> Void

    static let spacing: CGFloat = 4
    private let columns = Array(repeating: GridItem(.flexible(), spacing: Self.spacing), count: 7)

    var body: some View {
        LazyVGrid(columns: columns, spacing: Self.spacing) {
            ForEach(Array(HistoryText.weekdayInitials().enumerated()), id: \.offset) { _, initial in
                Text(initial).font(AppFont.labelSmall).foregroundStyle(Palette.inkMuted).accessibilityHidden(true)
            }
            ForEach(0..<history.leadingBlanks, id: \.self) { _ in Color.clear.frame(height: cellHeight) }
            ForEach(history.days, id: \.dateKey) { day in
                DayCell(
                    day: day,
                    summary: history.summaries[day.dateKey],
                    selected: day.dateKey == history.selectedKey,
                    height: cellHeight
                ) { onTap(day.dateKey) }
            }
        }
    }
}

/// One day. A recorded day takes one fixed tint whatever it held; the selected day is a ring over
/// the cell's own state, never a fill, so it still shows whether the day was recorded.
private struct DayCell: View {

    let day: CalendarDay
    let summary: DailyAggregate?
    let selected: Bool
    let height: CGFloat
    let onTap: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 8)
        Button(action: onTap) {
            VStack(spacing: 2) {
                Text(verbatim: "\(day.date.day)")
                    .font(day.today ? AppFont.bodyMediumBold : AppFont.bodyMedium)
                    .foregroundStyle(numberStyle)
                Circle().frame(width: 4, height: 4).opacity(day.today ? 1 : 0)
                    .foregroundStyle(Palette.ink)
            }
            .frame(maxWidth: .infinity, minHeight: height)
            .background(day.recorded ? Palette.dayColor.opacity(Palette.recordedDayAlpha) : .clear, in: shape)
            .overlay { if selected { shape.strokeBorder(Palette.ink, lineWidth: 2) } }
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("day-\(day.dateKey)")
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// The shade carries no hours, so a recorded day's are spoken.
    private var label: String {
        guard day.recorded, let summary else { return "\(day.date.day)" }
        return String(app: "\(Int(day.date.day)), \(SessionText.short(summary.totalDurationMs)) worked")
    }

    /// Only days outside the record are faded; an empty past day is drawn as plainly as any other.
    private var numberStyle: Color {
        day.outsideRecord && !day.today ? Palette.ink.opacity(0.38) : Palette.ink
    }
}

/// A month stepper's arrow at a 48-point target, shared by History and Reports.
struct MonthArrow: View {
    let systemName: String
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(Palette.ink.opacity(isEnabled ? 1 : 0.38))
            .frame(width: 48, height: 48)
            .contentShape(Rectangle())
    }
}

/// History's copy and dates, in the app's language with Latin digits.
enum HistoryText {

    static var readFailed: String { String(app: "That didn't load. Try again.") }

    static func monthYear(_ month: HistoryStore.Month) -> String {
        let date = Calendar.current.date(from: DateComponents(year: Int(month.year), month: Int(month.month), day: 1))
        return date?.formatted(.dateTime.month(.wide).year().locale(AppLocale.current)) ?? ""
    }

    static func dateWithWeekday(_ key: String) -> String {
        guard let date = Clock.localDate(key: key),
              let day = Calendar.current.date(
                  from: DateComponents(year: Int(date.year), month: Int(Clock.monthNumber(date)), day: Int(date.day))
              )
        else { return key }
        return day.formatted(.dateTime.weekday(.wide).day().month(.wide).year().locale(AppLocale.current))
    }

    /// The count of the rows shown and the sum of their printed durations; an open session adds to
    /// the count, not the total.
    static func daySummary(_ sessions: [SessionRow]) -> String {
        let total = sessions.reduce(Int64(0)) { $0 + ($1.duration ?? 0) }
        return String(app: "\(ReportText.sessions(sessions.count)) · \(SessionText.short(total))")
    }

    /// Narrow weekday initials in the app's language, starting on the device's week start.
    static func weekdayInitials(_ calendar: Calendar = .current) -> [String] {
        var calendar = calendar
        calendar.locale = AppLocale.current
        let symbols = calendar.veryShortStandaloneWeekdaySymbols
        let start = calendar.firstWeekday - 1
        return Array(symbols[start...] + symbols[..<start])
    }
}
