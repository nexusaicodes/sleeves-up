import Shared
import UserNotifications
import XCTest
@testable import SleevesUp

/// Habit reminders on iOS: planned by the shared `NudgePlan` against the in-memory record and
/// ledger, replanned after a check-in, settled into the ledger once their instant passes, and left
/// out for a habit whose reminders are off or that is archived.
@MainActor
final class HabitRemindersTests: XCTestCase {

    // 2026-06-15 09:00 UTC.
    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private let hour: Int64 = 3_600_000

    private var nowMs: Int64 = 0
    private var defaults: UserDefaults!
    private var center: FakeNotificationCenter!
    private var uuids = 0

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "HabitRemindersTests")!
        defaults.removePersistentDomain(forName: "HabitRemindersTests")
        nowMs = utcJun15At0900
        center = FakeNotificationCenter()
    }

    private func makeStores() throws -> (HabitReminders, SessionStore, HabitStore, ReminderSettings) {
        let database = try RecordDatabase.openInMemory(nowMs: 0)
        let now = { [unowned self] in Clock.date(ms: self.nowMs) }
        let utc = { TimeZone(identifier: "UTC")! }
        let settings = ReminderSettings(defaults: defaults)
        let reminders = HabitReminders(
            record: database, ledger: try NudgeLedger.openInMemory(), settings: settings,
            center: center, now: now, zone: utc
        )
        let sessions = SessionStore(database: database, zones: SessionZones(defaults: defaults), now: now, zone: utc)
        let habits = HabitStore(database: database, now: now) { [unowned self] in
            uuids += 1
            return "uuid-\(uuids)"
        }
        try habits.reload()
        return (reminders, sessions, habits, settings)
    }

    private var seedUuid: String { "seed" }

    func testAHabitIsRemindedDailyAtItsMinuteUnderItsName() throws {
        let (reminders, _, habits, _) = try makeStores()
        XCTAssertEqual(try habits.rename(RecordDatabase.seedHabitId, to: "Gym"), .saved)
        try reminders.replan()

        XCTAssertEqual(center.pending.count, Int(NudgePlan.shared.MAX_DAYS))
        let first = try XCTUnwrap(center.request(firingAfter: 3600))
        XCTAssertTrue(first.identifier.hasSuffix("-2026-06-15"))
        XCTAssertTrue(first.content.title.hasPrefix("Gym: "), first.content.title)
        XCTAssertFalse(first.content.body.isEmpty)
        XCTAssertEqual(first.content.categoryIdentifier, NotificationKinds.habitReminder)
        XCTAssertNotNil(first.content.userInfo[NotificationKinds.habitUuidKey] as? String)
    }

    func testAnUnnamedHabitTakesTheUnnamedTitle() throws {
        let (reminders, _, _, _) = try makeStores()
        try reminders.replan()
        let first = try XCTUnwrap(center.request(firingAfter: 3600))
        XCTAssertFalse(first.content.title.contains("%1$s"))
        XCTAssertFalse(first.content.title.contains(":"), first.content.title)
    }

    func testACheckInRemovesTodaysReminderAndTakesAShownOneAway() throws {
        let (reminders, sessions, habits, _) = try makeStores()
        try reminders.replan()
        _ = try sessions.checkIn()
        try reminders.replan()

        let uuid = try XCTUnwrap(habits.active.first?.uuid)
        let today = HabitReminders.identifier(habitUuid: uuid, dateKey: "2026-06-15")
        XCTAssertNil(center.pending[today])
        XCTAssertEqual(center.pending.count, Int(NudgePlan.shared.MAX_DAYS) - 1)
        XCTAssertTrue(center.removedDelivered.contains(today))
    }

    func testAReminderPastItsInstantCountsAsShownAndTheWordingMovesOn() throws {
        let (reminders, _, habits, _) = try makeStores()
        try reminders.replan()
        let uuid = try XCTUnwrap(habits.active.first?.uuid)
        let shownTitle = try XCTUnwrap(center.request(firingAfter: 3600)?.content.title)

        nowMs += 2 * hour
        try reminders.replan()
        let today = HabitReminders.identifier(habitUuid: uuid, dateKey: "2026-06-15")
        XCTAssertNil(center.pending[today], "today's was shown, so today is spent")
        let tomorrow = try XCTUnwrap(center.pending[HabitReminders.identifier(habitUuid: uuid, dateKey: "2026-06-16")])
        XCTAssertNotEqual(tomorrow.content.title, shownTitle, "a shown title waits its turn")
    }

    func testAHabitWithRemindersOffOrArchivedIsNotReminded() throws {
        let (reminders, _, habits, settings) = try makeStores()
        XCTAssertEqual(try habits.rename(RecordDatabase.seedHabitId, to: "Gym"), .saved)
        XCTAssertEqual(try habits.create(name: "Reading"), .saved)
        XCTAssertEqual(try habits.create(name: "Guitar"), .saved)
        settings.setOn(false, for: try XCTUnwrap(habits.active.first { $0.name == "Reading" }?.uuid))
        XCTAssertEqual(try habits.archive(3), .archived)
        try reminders.replan()

        let titles = center.pending.values.map(\.content.title)
        XCTAssertTrue(titles.allSatisfy { $0.hasPrefix("Gym: ") }, "\(Set(titles))")
        XCTAssertEqual(titles.count, Int(NudgePlan.shared.MAX_DAYS))
    }

    func testTheReminderMinuteIsClampedToTheAllowedRange() throws {
        let (reminders, _, habits, settings) = try makeStores()
        let uuid = try XCTUnwrap(habits.active.first?.uuid)
        settings.setMinute(23 * 60, for: uuid)
        XCTAssertEqual(settings.minute(uuid), 21 * 60)
        try reminders.replan()
        XCTAssertNotNil(center.request(firingAfter: 12 * 3600), "21:00 today, twelve hours from 09:00")
    }
}
