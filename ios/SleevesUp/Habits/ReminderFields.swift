import Shared
import SwiftUI

/// A habit's reminder as its editor and the naming step set it: on or off (iOS only, rule 1) and the
/// time it comes around, held to `NudgeSchedule.minuteRange` by the picker itself rather than by
/// moving a visible choice afterwards.
struct ReminderFields: View {

    @Binding var on: Bool
    @Binding var minute: Int
    /// The naming step sets only the time; the on/off is the editor's.
    var showsToggle = true

    var body: some View {
        if showsToggle { Toggle("Reminders", isOn: $on) }
        if on || !showsToggle {
            DatePicker("Around", selection: time, in: ReminderText.allowedTimes, displayedComponents: .hourAndMinute)
        }
    }

    private var time: Binding<Date> {
        Binding(get: { ReminderText.date(minute: minute) }, set: { minute = ReminderText.minute(of: $0) })
    }
}

/// How a reminder's time is written and picked. English only for now.
enum ReminderText {

    static var range: ClosedRange<Int> {
        let minutes = NudgeSchedule.shared.minuteRange
        return Int(minutes.start)...Int(minutes.endInclusive)
    }

    /// Today's instants at the earliest and latest allowed minute, for the picker's range.
    static var allowedTimes: ClosedRange<Date> { date(minute: range.lowerBound)...date(minute: range.upperBound) }

    static func date(minute: Int) -> Date {
        Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: Date()) ?? Date()
    }

    static func minute(of date: Date) -> Int {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    static func clock(_ minute: Int) -> String {
        date(minute: minute).formatted(date: .omitted, time: .shortened)
    }

    /// A habit's row: "Around 10:00", or that its reminders are off. A time, never a figure.
    static func summary(on: Bool, minute: Int) -> String {
        on ? "Around \(clock(minute))" : "Reminders off"
    }

    static var rangeNote: String { "Between \(clock(range.lowerBound)) and \(clock(range.upperBound))" }
}
