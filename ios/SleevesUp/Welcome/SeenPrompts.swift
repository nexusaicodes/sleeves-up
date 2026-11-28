import Foundation
import Observation

/// The one-time prompts already shown, under Android's `prompt_prefs` keys: the welcome tour, the
/// launch-time notification ask and the widget page. None is a fact about the record.
@MainActor
@Observable
final class SeenPrompts {

    static let welcomeSeenKey = "welcome_seen"
    static let notificationsAskedKey = "notifications_asked"
    static let widgetPageSeenKey = "widget_page_seen"

    private let defaults: UserDefaults

    private(set) var welcomeSeen: Bool
    private(set) var notificationsAsked: Bool
    private(set) var widgetPageSeen: Bool

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        welcomeSeen = defaults.bool(forKey: Self.welcomeSeenKey)
        notificationsAsked = defaults.bool(forKey: Self.notificationsAskedKey)
        widgetPageSeen = defaults.bool(forKey: Self.widgetPageSeenKey)
    }

    /// Ends the tour. Either way out marks the widget page seen, since the tour holds it; only a
    /// finished tour leaves the notification ask owed, as the shared `FirstRun.asksNotificationsAfter`
    /// decides, and a skip marks it asked without asking.
    func endWelcome(asksNotifications: Bool) {
        welcomeSeen = true
        defaults.set(true, forKey: Self.welcomeSeenKey)
        markWidgetPageSeen()
        if !asksNotifications { markNotificationsAsked() }
    }

    func markNotificationsAsked() {
        notificationsAsked = true
        defaults.set(true, forKey: Self.notificationsAskedKey)
    }

    func markWidgetPageSeen() {
        widgetPageSeen = true
        defaults.set(true, forKey: Self.widgetPageSeenKey)
    }
}
