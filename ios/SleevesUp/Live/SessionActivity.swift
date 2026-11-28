import ActivityKit
import Foundation

/// Where the session's Live Activity is started, renamed and ended: ActivityKit in the app, a fake
/// in tests.
@MainActor
protocol LiveActivityHost {
    /// The activities still showing, by session id, with the name each shows.
    func running() -> [(sessionId: Int64, habitName: String?)]
    /// Starts one, returning whether the system took it (Live Activities can be switched off).
    func start(_ attributes: SessionActivityAttributes, habitName: String?) -> Bool
    func rename(sessionId: Int64, to habitName: String?)
    func end(sessionId: Int64)
}

/// The open session's Live Activity (iOS rule 4), re-synced on every read of the record as its
/// reminders are, so whichever write opened or closed the session starts or ends it. Each session
/// gets one per install: a session whose activity was dismissed, or ended by iOS's eight-hour
/// limit, is not shown again, as Android re-posts no timer notification a user swiped away. An
/// update ends the activities of the install it replaces, so a new install starts it again, as
/// Android's watchdog revives the timer after one.
@MainActor
final class SessionActivity {

    /// The last session an activity was started for, and under which install.
    static let startedForKey = "live_activity_started_for"

    private let host: LiveActivityHost
    private let defaults: UserDefaults
    private let install: String

    init(
        host: LiveActivityHost = ActivityKitHost(),
        defaults: UserDefaults = .standard,
        install: String = SessionActivity.currentInstall()
    ) {
        self.host = host
        self.defaults = defaults
        self.install = install
    }

    /// This install, told apart from the one it replaced by the folder iOS installed it in, which
    /// every install and update gives a new name.
    static func currentInstall() -> String {
        Bundle.main.bundleURL.deletingLastPathComponent().lastPathComponent
    }

    /// Ends every activity but `open`'s, then starts `open`'s if it never had one, or renames it.
    func sync(open: SessionRow?, habitName: String?, dayBoundaryMs: Int64?) {
        let running = host.running()
        for activity in running where activity.sessionId != open?.id {
            host.end(sessionId: activity.sessionId)
        }
        guard let open, let id = open.id, let dayBoundaryMs else { return }
        if let shown = running.first(where: { $0.sessionId == id }) {
            if shown.habitName != habitName { host.rename(sessionId: id, to: habitName) }
            return
        }
        let startedFor = "\(id)@\(install)"
        guard defaults.string(forKey: Self.startedForKey) != startedFor else { return }
        let attributes = SessionActivityAttributes(
            sessionId: id, startedAt: Clock.date(ms: open.startedAt), endsAt: Clock.date(ms: dayBoundaryMs)
        )
        if host.start(attributes, habitName: habitName) {
            defaults.set(startedFor, forKey: Self.startedForKey)
        }
    }
}

/// ActivityKit. A start is refused in the background and when the user has switched Live
/// Activities off; either way the session and its reminders carry on without one.
struct ActivityKitHost: LiveActivityHost {

    private var activities: [Activity<SessionActivityAttributes>] {
        Activity<SessionActivityAttributes>.activities.filter {
            $0.activityState == .active || $0.activityState == .stale
        }
    }

    func running() -> [(sessionId: Int64, habitName: String?)] {
        activities.map { ($0.attributes.sessionId, $0.content.state.habitName) }
    }

    func start(_ attributes: SessionActivityAttributes, habitName: String?) -> Bool {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return false }
        let content = ActivityContent(
            state: SessionActivityAttributes.ContentState(habitName: habitName), staleDate: attributes.endsAt
        )
        return (try? Activity.request(attributes: attributes, content: content, pushType: nil)) != nil
    }

    func rename(sessionId: Int64, to habitName: String?) {
        for activity in activities where activity.attributes.sessionId == sessionId {
            let content = ActivityContent(
                state: SessionActivityAttributes.ContentState(habitName: habitName),
                staleDate: activity.attributes.endsAt
            )
            Task { await activity.update(content) }
        }
    }

    func end(sessionId: Int64) {
        for activity in activities where activity.attributes.sessionId == sessionId {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }
}
