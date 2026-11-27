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
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    monthStepper
                    CalendarGridView(history: history) { key in act { try history.select(key) } }
                    daySlot
                    if failed { Text(HistoryText.readFailed).font(.footnote).foregroundStyle(.secondary) }
                }
                .padding()
            }
            .habitSwitcher(habits)
            .onAppear { act { try history.reload() } }
        }
    }

    private var monthStepper: some View {
        HStack {
            Button { act { try history.previousMonth() } } label: { Image(systemName: "chevron.left") }
                .accessibilityLabel("Previous month")
            Spacer()
            Text(HistoryText.monthYear(history.month)).font(.headline)
            Spacer()
            Button { act { try history.nextMonth() } } label: { Image(systemName: "chevron.right") }
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
                Text(HistoryText.dateWithWeekday(key)).font(.headline)
                if history.selectedSessions.isEmpty {
                    Text("No sessions recorded on this day.").foregroundStyle(.secondary)
                } else {
                    Text(HistoryText.daySummary(history.selectedSessions))
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(.secondary)
                    ForEach(history.selectedSessions, id: \.id) { session in
                        SessionIntervalRow(session: session)
                    }
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text("No day selected").font(.headline)
                Text("Tap a day in the calendar to see its sessions.").foregroundStyle(.secondary)
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
    let onTap: (String) -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 4) {
            ForEach(Array(HistoryText.weekdayInitials().enumerated()), id: \.offset) { _, initial in
                Text(initial).font(.caption2).foregroundStyle(.secondary).accessibilityHidden(true)
            }
            ForEach(0..<history.leadingBlanks, id: \.self) { _ in Color.clear.frame(height: 44) }
            ForEach(history.days, id: \.dateKey) { day in
                DayCell(
                    day: day,
                    summary: history.summaries[day.dateKey],
                    selected: day.dateKey == history.selectedKey
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
    let onTap: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 8)
        Button(action: onTap) {
            VStack(spacing: 2) {
                Text("\(day.date.day)")
                    .font(.callout.weight(day.today ? .bold : .regular))
                    .foregroundStyle(numberStyle)
                Circle().frame(width: 4, height: 4).opacity(day.today ? 1 : 0)
                    .foregroundStyle(Color.accentColor)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(day.recorded ? Color.accentColor.opacity(0.4) : .clear, in: shape)
            .overlay { if selected { shape.strokeBorder(.primary, lineWidth: 2) } }
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
        return "\(day.date.day), \(SessionText.short(summary.totalDurationMs)) worked"
    }

    private var numberStyle: AnyShapeStyle {
        if day.today { return AnyShapeStyle(Color.accentColor) }
        if day.outsideRecord { return AnyShapeStyle(.tertiary) }
        return AnyShapeStyle(.primary)
    }
}

/// History's copy, the Android app's English strings. English only for now.
enum HistoryText {

    static let readFailed = "That didn't load. Try again."

    static func monthYear(_ month: HistoryStore.Month) -> String {
        let date = Calendar.current.date(from: DateComponents(year: Int(month.year), month: Int(month.month), day: 1))
        return date?.formatted(.dateTime.month(.wide).year()) ?? ""
    }

    static func dateWithWeekday(_ key: String) -> String {
        guard let date = Clock.localDate(key: key),
              let day = Calendar.current.date(
                  from: DateComponents(year: Int(date.year), month: Int(Clock.monthNumber(date)), day: Int(date.day))
              )
        else { return key }
        return day.formatted(.dateTime.weekday(.wide).day().month(.wide).year())
    }

    /// The count of the rows shown and the sum of their printed durations; an open session adds to
    /// the count, not the total.
    static func daySummary(_ sessions: [SessionRow]) -> String {
        let count = sessions.count == 1 ? "1 session" : "\(sessions.count) sessions"
        let total = sessions.reduce(Int64(0)) { $0 + ($1.duration ?? 0) }
        return "\(count) · \(SessionText.short(total))"
    }

    /// Narrow weekday initials, starting on the device's week start.
    static func weekdayInitials(_ calendar: Calendar = .current) -> [String] {
        let symbols = calendar.veryShortStandaloneWeekdaySymbols
        let start = calendar.firstWeekday - 1
        return Array(symbols[start...] + symbols[..<start])
    }
}
