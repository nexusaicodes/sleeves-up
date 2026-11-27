import UserNotifications
@testable import SleevesUp

/// The notification center as the app schedules through it, keeping only what is still pending as
/// the system does, and recording what was taken out of Notification Center.
final class FakeNotificationCenter: NotificationScheduling {

    private(set) var pending: [String: UNNotificationRequest] = [:]
    private(set) var removedDelivered: [String] = []

    func add(_ request: UNNotificationRequest) { pending[request.identifier] = request }

    func removePending(_ identifiers: [String]) { identifiers.forEach { pending[$0] = nil } }

    func removeDelivered(_ identifiers: [String]) { removedDelivered += identifiers }

    /// Pending requests in firing order, as seconds from when they were scheduled.
    var intervals: [TimeInterval] {
        pending.values.compactMap { ($0.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval }.sorted()
    }

    /// The pending request firing `seconds` after it was scheduled.
    func request(firingAfter seconds: TimeInterval) -> UNNotificationRequest? {
        pending.values.first { ($0.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval == seconds }
    }
}
