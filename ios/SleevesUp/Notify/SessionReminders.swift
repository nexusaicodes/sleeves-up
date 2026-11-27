import Shared
import UserNotifications

/// The open session's "Still checked in?" reminders, every two hours from check-in up to the
/// session's own midnight, all scheduled at once since nothing runs when one fires (iOS rule 4).
/// Only a session's first reminder makes a sound; the rest arrive quietly, as on Android, where an
/// overnight session accumulates silently. One session is open at a time, so the reminders take
/// fixed slots, and every sync clears all of them before scheduling what is still ahead.
final class SessionReminders {

    static let identifiers = (0..<Int(SessionSchedule.shared.MAX_REMINDERS_PER_SESSION))
        .map { "session-reminder-\($0)" }

    private let center: NotificationScheduling

    init(center: NotificationScheduling = UNUserNotificationCenter.current()) {
        self.center = center
    }

    /// Schedules `open`'s reminders still ahead of `nowMs`, up to `dayBoundaryMs`, or clears them
    /// all when no session is open. Safe to call after every write and every refresh.
    func sync(open: SessionRow?, dayBoundaryMs: Int64?, nowMs: Int64) {
        center.removePending(Self.identifiers)
        guard let open, let dayBoundaryMs else { return }
        let first = SessionSchedule.shared.nextReminderAt(fromMs: open.startedAt)
        let ahead = SessionSchedule.shared.remindersAhead(
            startedAtMs: open.startedAt, nowMs: nowMs, dayBoundaryMs: dayBoundaryMs
        )
        for (slot, fireAt) in zip(Self.identifiers, ahead) {
            let content = UNMutableNotificationContent()
            content.title = "Still checked in?"
            content.body = "Your session is still running. It closes on its own at midnight."
            content.categoryIdentifier = NotificationKinds.sessionReminder
            if fireAt.int64Value == first {
                content.sound = .default
            } else {
                content.interruptionLevel = .passive
            }
            let interval = TimeInterval(fireAt.int64Value - nowMs) / 1000
            center.add(UNNotificationRequest(
                identifier: slot,
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(interval, 1), repeats: false)
            ))
        }
    }
}
