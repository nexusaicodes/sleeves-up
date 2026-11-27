import Shared

/// The habit screens' copy, the Android app's English strings. English only for now; the Android
/// app's languages arrive with the iOS localization work.
enum HabitText {

    static func name(_ habit: HabitRow) -> String { habit.name ?? "Unnamed habit" }

    static func problem(_ problem: NameProblem) -> String {
        switch problem {
        case .blank: return "Give it a name"
        case .tooLong:
            let limit = Int(HabitRules.shared.MAX_NAME_LENGTH)
            return limit == 1 ? "Keep it to \(limit) character" : "Keep it to \(limit) characters"
        case .duplicate: return "You already have a habit with this name"
        default: return "That name can't be saved"
        }
    }

    static func archived(_ habit: HabitRow) -> String { "\(name(habit)) archived. Its record is kept." }

    static func archiveRefused(_ result: ArchiveResult, _ habit: HabitRow) -> String? {
        switch result {
        case .running: return "Check out of \(name(habit)) before archiving it"
        case .lastActive: return "Keep at least one habit"
        default: return nil
        }
    }

    static func restoreRefused(_ habit: HabitRow) -> String { "Another habit is already called \(name(habit))" }

    static let missing = "That habit is no longer here, so nothing was saved"

    static func switchLocked(_ running: HabitRow?) -> String {
        "Check out of \(running.map(name) ?? "the running habit") to switch or manage habits"
    }

    static let saveFailed = "That didn't save. Try again."
}
