import Foundation
import GRDB
import Shared
import UserNotifications

/// Each active habit's reminders, planned ahead by the shared `NudgePlan` and redone whenever app
/// code runs (iOS rule 3): on open, on return to the foreground, at midnight, after every check-in
/// and check-out and after any change to the habits or their reminder settings. A replan first
/// settles what was shown, then replaces every pending habit reminder with the new plan, so a
/// check-in, which leaves today holding a session, removes that day's reminder. A reminder already
/// shown for a habit checked in to since is taken out of Notification Center, as Android's
/// `PostedNudges.retire` does.
@MainActor
final class HabitReminders {

    private let record: DatabaseQueue
    private let ledger: NudgeLedger
    private let settings: ReminderSettings
    private let center: NotificationScheduling
    private let now: () -> Date
    private let zone: () -> TimeZone

    init(
        record: DatabaseQueue,
        ledger: NudgeLedger,
        settings: ReminderSettings,
        center: NotificationScheduling = UNUserNotificationCenter.current(),
        now: @escaping () -> Date = Date.init,
        zone: @escaping () -> TimeZone = { TimeZone.current }
    ) {
        self.record = record
        self.ledger = ledger
        self.settings = settings
        self.center = center
        self.now = now
        self.zone = zone
    }

    /// The identifier a habit's reminder for `dateKey` is scheduled under.
    static func identifier(habitUuid: String, dateKey: String) -> String {
        "nudge-\(habitUuid)-\(dateKey)"
    }

    func replan() throws {
        let nowDate = now()
        let nowMs = Clock.nowMs(nowDate)
        let currentZone = zone()
        let todayKey = Clock.dateKey(nowDate, in: currentZone)
        let startOfToday = Clock.startOfDayMs(nowDate, in: currentZone)
        // Everything the last plan scheduled is replaced, the reminders settled as shown included.
        let previous = try ledger.plannedIdentifiers()
        try ledger.settle(nowMs: nowMs, keepSince: nowMs - NudgeVoice.shared.HISTORY_MS)

        let habits = try record.read { db in
            try HabitRow.fetchAll(db, sql: "SELECT * FROM habits WHERE archived = 0 ORDER BY id ASC").map { habit in
                (
                    habit,
                    try Bool.fetchOne(
                        db, sql: "SELECT EXISTS (SELECT 1 FROM sessions WHERE habit_id = ? AND date_key = ?)",
                        arguments: [habit.id, todayKey]
                    ) ?? false,
                    try String.fetchOne(
                        db, sql: "SELECT MAX(date_key) FROM sessions WHERE habit_id = ? AND date_key < ?",
                        arguments: [habit.id, todayKey]
                    )
                )
            }
        }
        let checkedInToday = habits.filter { $0.1 }.map { Self.identifier(habitUuid: $0.0.uuid, dateKey: todayKey) }
        center.removeDelivered(checkedInToday)

        let reminded = habits.filter { settings.isOn($0.0.uuid) }
        let planHabits = try reminded.map { habit, sessionToday, lastKey in
            PlanHabit(
                habitId: habit.id,
                uuid: habit.uuid,
                reminderMinute: Int32(settings.minute(habit.uuid)),
                lastSessionDayBeforeToday: lastKey.flatMap(Clock.localDate(key:)),
                hasSessionToday: sessionToday,
                shownToday: try ledger.wasShown(habitUuid: habit.uuid, sinceMs: startOfToday),
                shownWording: try ledger.shownWording(
                    habitUuid: habit.uuid, sinceMs: nowMs - NudgeVoice.shared.HISTORY_MS
                ).mapValues { KotlinLong(value: $0) }
            )
        }
        let plan = NudgePlan.shared.plan(
            habits: planHabits,
            nowMs: nowMs,
            zone: Clock.kotlinZone(currentZone),
            lastShownAtMs: try ledger.lastShown(sinceMs: startOfToday).map { KotlinLong(value: $0) },
            copy: { NudgeCatalog.keys($0) }
        )

        center.removePending(previous)
        let byId = Dictionary(uniqueKeysWithValues: reminded.map { ($0.0.id, $0.0) })
        var scheduled: [NudgeLedger.Planned] = []
        for entry in plan {
            guard let habit = byId[entry.habitId],
                  let title = NudgeCatalog.title(entry.titleKey, habitName: habit.name),
                  let body = NudgeCatalog.body(entry.bodyKey)
            else { continue }
            let identifier = Self.identifier(habitUuid: habit.uuid, dateKey: entry.date.description())
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = .default
            content.categoryIdentifier = NotificationKinds.habitReminder
            content.userInfo = [NotificationKinds.habitUuidKey: habit.uuid]
            let interval = TimeInterval(entry.fireAtMs - nowMs) / 1000
            center.add(UNNotificationRequest(
                identifier: identifier,
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(interval, 1), repeats: false)
            ))
            scheduled.append(NudgeLedger.Planned(
                identifier: identifier, habitUuid: habit.uuid, fireAt: entry.fireAtMs,
                titleKey: entry.titleKey, bodyKey: entry.bodyKey
            ))
        }
        try ledger.replacePlan(scheduled)
    }
}
