import UserNotifications

/// The system's notification prompt, which it shows only while the answer is undetermined.
enum NotificationAsk {
    static func ifUndetermined() async {
        let center = UNUserNotificationCenter.current()
        guard await center.notificationSettings().authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .providesAppNotificationSettings])
    }
}
