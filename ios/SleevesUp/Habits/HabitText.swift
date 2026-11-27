import Shared

/// The habit screens' copy, the Android app's strings in the app's language.
enum HabitText {

    static func name(_ habit: HabitRow) -> String { habit.name ?? String(app: "Unnamed habit") }

    static func problem(_ problem: NameProblem) -> String {
        switch problem {
        case .blank: return String(app: "Give it a name")
        case .tooLong:
            let limit = Int(HabitRules.shared.MAX_NAME_LENGTH)
            return String(app: "Keep it to \(limit) characters")
        case .duplicate: return String(app: "You already have a habit with this name")
        default: return String(app: "That name can't be saved")
        }
    }

    static func archived(_ habit: HabitRow) -> String { String(app: "\(name(habit)) archived. Its record is kept.") }

    static func archiveRefused(_ result: ArchiveResult, _ habit: HabitRow) -> String? {
        switch result {
        case .running: return String(app: "Check out of \(name(habit)) before archiving it")
        case .lastActive: return String(app: "Keep at least one habit")
        default: return nil
        }
    }

    static func restoreRefused(_ habit: HabitRow) -> String { String(app: "Another habit is already called \(name(habit))") }

    static var missing: String { String(app: "That habit is no longer here, so nothing was saved") }

    static func switchLocked(_ running: HabitRow?) -> String {
        String(app: "Check out of \(running.map(name) ?? String(app: "the running habit")) to switch or manage habits")
    }

    static var saveFailed: String { String(app: "That didn't save. Try again.") }
}
