import Foundation
import Shared

/// Each habit's reminder minute and, on iOS only, whether its reminders are on (iOS rule 1), keyed by
/// the habit's uuid as Android's `reminder_prefs` are, so a rebuilt record never hands one habit's
/// setting to another. The minute is clamped to `NudgeSchedule.minuteRange` on read and write.
final class ReminderSettings {

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func minute(_ habitUuid: String) -> Int {
        let stored = defaults.object(forKey: minuteKey(habitUuid)) as? Int
        return Int(NudgeSchedule.shared.clampMinute(minute: Int32(stored ?? Int(NudgeSchedule.shared.DEFAULT_MINUTE))))
    }

    func setMinute(_ minute: Int, for habitUuid: String) {
        defaults.set(Int(NudgeSchedule.shared.clampMinute(minute: Int32(minute))), forKey: minuteKey(habitUuid))
    }

    /// On unless turned off: the user who has not formed the habit is the one who never goes looking.
    func isOn(_ habitUuid: String) -> Bool {
        defaults.object(forKey: onKey(habitUuid)) as? Bool ?? true
    }

    func setOn(_ on: Bool, for habitUuid: String) {
        defaults.set(on, forKey: onKey(habitUuid))
    }

    private func minuteKey(_ uuid: String) -> String { "reminder_minute_\(uuid)" }
    private func onKey(_ uuid: String) -> String { "reminder_on_\(uuid)" }
}
