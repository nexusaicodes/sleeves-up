import ActivityKit
import Foundation

/// The open session as its Live Activity shows it, compiled into the app and its widget extension.
/// It carries the habit's name and the session's span, never a figure: the clock is drawn by the
/// system from `startedAt`, as Android's timer notification is drawn by a platform chronometer.
struct SessionActivityAttributes: ActivityAttributes {

    struct ContentState: Codable, Hashable {
        /// The habit's name, nil while it is owed one. A state, so a rename reaches the activity.
        var habitName: String?
    }

    /// The session's row id, which says which session an activity belongs to.
    var sessionId: Int64
    var startedAt: Date
    /// The session's own midnight, where the day-boundary close stamps it and the clock stops.
    var endsAt: Date
}

/// The link the activity's Check Out opens: the app, then the presence gate for a check-out,
/// recorded as `ClosedBy.timerNotification`, as Android's timer notification's Check Out is.
enum SessionActivityLink {
    static let checkOut = URL(string: "sleevesup://session/check-out")!
}
