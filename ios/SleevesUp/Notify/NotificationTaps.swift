import Observation
import UIKit
import UserNotifications

/// What a tap on a notification asks the app to do, held until a screen acts on it. A tap can land
/// before the record is open (a cold launch), so it waits here rather than being acted on at once.
@MainActor
@Observable
final class NotificationTaps {

    enum Request: Equatable {
        /// The session reminder or its Check Out action: the presence gate, then a check-out.
        case checkOut
    }

    private(set) var pending: Request?

    func receive(_ request: Request) {
        pending = request
    }

    /// Hands the pending request to the screen acting on it, once.
    func take() -> Request? {
        defer { pending = nil }
        return pending
    }
}

/// Receives notification responses from launch on, which a SwiftUI scene cannot: the delegate must
/// be set before launch finishes, or a tap that launched the app is lost.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    @MainActor let taps = NotificationTaps()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        NotificationKinds.register()
        return true
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let category = response.notification.request.content.categoryIdentifier
        let action = response.actionIdentifier
        defer { completionHandler() }
        guard category == NotificationKinds.sessionReminder,
              action == NotificationKinds.checkOutAction || action == UNNotificationDefaultActionIdentifier
        else { return }
        Task { @MainActor in taps.receive(.checkOut) }
    }

    /// A reminder that arrives while the app is open is still shown, as Android posts it regardless.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}
