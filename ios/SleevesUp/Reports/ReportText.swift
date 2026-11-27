import Foundation
import Shared

/// Reports' copy and formats, the Android app's English strings and date patterns. English only for
/// now; the Android app's languages arrive with the iOS localization work.
enum ReportText {

    static let msPerHour: Int64 = 3_600_000

    static func days(_ count: Int32) -> String { days(Int(count)) }
    static func days(_ count: Int) -> String { count == 1 ? "1 day" : "\(count) days" }

    static func sessions(_ count: Int32) -> String { sessions(Int(count)) }
    static func sessions(_ count: Int) -> String { count == 1 ? "1 session" : "\(count) sessions" }

    /// One decimal place: a rhythm, and a second digit would imply a precision it lacks. Latin
    /// digits whatever the locale.
    static func average(_ value: Float) -> String {
        String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    static func daysSplit(_ figures: ReportFigures) -> String {
        "Days split: \(days(figures.showedUpDays)) showed up, \(days(figures.missedDays)) missed"
    }

    /// The scope as the selector's heading: the month's name, or "All time".
    static func scopeLabel(_ scope: any ReportScope) -> String {
        guard let month = scope as? ReportScopeMonth else { return "All time" }
        return format(month.month.firstDay, "MMMM yyyy")
    }

    /// The scope inside a sentence ("Export all time"): a month name reads the same either way.
    static func scopeInline(_ scope: any ReportScope) -> String {
        scope is ReportScopeMonth ? scopeLabel(scope) : "all time"
    }

    static func emptyMessage(_ scope: any ReportScope) -> String {
        guard scope is ReportScopeMonth else {
            return "No day has been checked out of yet. Check out of a session and it appears here."
        }
        return "No completed day in \(scopeLabel(scope)). Use the arrows to look at another month."
    }

    static func dateWithYear(_ date: Kotlinx_datetimeLocalDate) -> String { format(date, "d MMM yyyy") }

    static func axisDay(_ date: Kotlinx_datetimeLocalDate) -> String { format(date, "d MMM") }

    /// A month on the axis, with its year once the series spans two: a bare "Aug" lies once the
    /// record holds two Augusts.
    static func axisMonth(_ month: Kotlinx_datetimeYearMonth, multiYear: Bool) -> String {
        format(month.firstDay, multiYear ? "MMM ''yy" : "MMM")
    }

    private static func format(_ date: Kotlinx_datetimeLocalDate, _ pattern: String) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        guard let day = calendar.date(from: DateComponents(
            year: Int(date.year), month: Int(Clock.monthNumber(date)), day: Int(date.day)
        )) else { return date.description() }
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.locale = Locale(identifier: "en_GB")
        formatter.dateFormat = pattern
        return formatter.string(from: day)
    }
}
