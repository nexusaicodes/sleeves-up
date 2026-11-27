import UserNotifications

/// The part of `UNUserNotificationCenter` the app schedules through, so the reminder plans can be
/// tested against a fake.
protocol NotificationScheduling: AnyObject {
    func add(_ request: UNNotificationRequest)
    func removePending(_ identifiers: [String])
    /// Takes notifications already shown out of Notification Center.
    func removeDelivered(_ identifiers: [String])
}

extension UNUserNotificationCenter: NotificationScheduling {
    func add(_ request: UNNotificationRequest) {
        add(request, withCompletionHandler: nil)
    }

    func removePending(_ identifiers: [String]) {
        removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    func removeDelivered(_ identifiers: [String]) {
        removeDeliveredNotifications(withIdentifiers: identifiers)
    }
}

/// The notification categories and actions the app registers once at launch.
enum NotificationKinds {
    /// The session reminder, with its Check Out action.
    static let sessionReminder = "SESSION_REMINDER"
    /// The session reminder's action, which opens the app on the presence gate for a check-out.
    static let checkOutAction = "CHECK_OUT"
    /// A habit's reminder, whose tap opens the presence gate for a check-in to that habit.
    static let habitReminder = "HABIT_REMINDER"
    /// The habit reminder's `userInfo` key holding the habit's uuid.
    static let habitUuidKey = "habitUuid"

    static func register(on center: UNUserNotificationCenter = .current()) {
        let checkOut = UNNotificationAction(identifier: checkOutAction, title: "Check Out", options: [.foreground])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: sessionReminder, actions: [checkOut], intentIdentifiers: []),
            UNNotificationCategory(identifier: habitReminder, actions: [], intentIdentifiers: []),
        ])
    }
}
