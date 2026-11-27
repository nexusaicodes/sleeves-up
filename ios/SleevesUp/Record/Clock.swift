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

    /// The calendar date of `date` in `zone`, as the shared code's `LocalDate`.
    static func localDate(_ date: Date, in zone: TimeZone) -> Kotlinx_datetimeLocalDate {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let day = calendar.dateComponents([.year, .month, .day], from: date)
        return Kotlinx_datetimeLocalDate(year: Int32(day.year ?? 0), month: Int32(day.month ?? 1), day: Int32(day.day ?? 1))
    }

    /// The `LocalDate` a `date_key` names, or nil for a malformed key.
    static func localDate(key: String) -> Kotlinx_datetimeLocalDate? {
        let parts = key.split(separator: "-").compactMap { Int32($0) }
        // The Kotlin constructor throws on an impossible date, which Swift cannot catch, so the
        // date is checked here first.
        guard parts.count == 3,
              DateComponents(year: Int(parts[0]), month: Int(parts[1]), day: Int(parts[2]))
                  .isValidDate(in: Calendar(identifier: .gregorian))
        else { return nil }
        return Kotlinx_datetimeLocalDate(year: parts[0], month: parts[1], day: parts[2])
    }

    /// `date`'s month, 1 to 12. Kotlin's `Month.number` is an extension Swift does not see.
    static func monthNumber(_ date: Kotlinx_datetimeLocalDate) -> Int32 {
        date.month.ordinal + 1
    }

    /// The shared code's zone for a Foundation zone, by its IANA identifier.
    static func kotlinZone(_ zone: TimeZone) -> Kotlinx_datetimeTimeZone {
        Kotlinx_datetimeTimeZone.companion.of(zoneId: zone.identifier)
    }
}
