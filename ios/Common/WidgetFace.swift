import Foundation

/// What the home-screen widget says (iOS rule 5): names and whether a session is open, never a
/// figure. It does not cycle names as Android's does: one active habit is named, several read "Check
/// In" and open the picker, and an open session names its habit. It decides nothing; the tap is
/// routed in the app from the rows at the tap, by the shared `TapRouting.forWidget`.
enum WidgetFace: Equatable {
    case checkInTo(String)
    case checkIn
    case checkOutOf(String)

    /// The habit's name as the app shows it, `HabitText.name`'s fallback for one owed a name.
    static func name(_ name: String?) -> String { name ?? "Unnamed habit" }

    /// The face for the active habits' names and, when a session is open, its habit's name, which
    /// may be nil itself; `openSession` is nil when none is open.
    static func of(activeNames: [String?], openSession: String??) -> WidgetFace {
        if let open = openSession { return .checkOutOf(name(open)) }
        if activeNames.count == 1 { return .checkInTo(name(activeNames[0])) }
        return .checkIn
    }

    var text: String {
        switch self {
        case .checkInTo(let name): return "Check in to \(name)"
        case .checkIn: return "Check In"
        case .checkOutOf(let name): return "Check out of \(name)"
        }
    }

    var sessionOpen: Bool {
        if case .checkOutOf = self { return true }
        return false
    }

    /// The link every face opens: the app, which routes it.
    static let tap = URL(string: "sleevesup://widget/tap")!

    /// The widget's kind, which a placed widget names: frozen once one is on a home screen.
    static let kind = "CheckInWidget"
}
