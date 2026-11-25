import Foundation
import Shared

/// Conversions between Foundation's dates and zones and the record's epoch millis, `date_key`s and
/// the shared Kotlin code's zones.
enum Clock {

    static func nowMs(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded(.down))
    }

    static func date(ms: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(ms) / 1000)
    }

    /// The `date_key` of `date` in `zone`: its ISO calendar date, `yyyy-MM-dd`, as Android stores it.
    static func dateKey(_ date: Date, in zone: TimeZone) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let day = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04ld-%02ld-%02ld", day.year ?? 0, day.month ?? 0, day.day ?? 0)
    }

    /// The shared code's zone for a Foundation zone, by its IANA identifier.
    static func kotlinZone(_ zone: TimeZone) -> Kotlinx_datetimeTimeZone {
        Kotlinx_datetimeTimeZone.companion.of(zoneId: zone.identifier)
    }
}
