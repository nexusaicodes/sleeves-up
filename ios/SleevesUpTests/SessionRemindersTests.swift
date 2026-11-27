import Shared
import UserNotifications
import XCTest
@testable import SleevesUp

/// The open session's reminders: two-hourly from check-in up to its own midnight, only the first
/// with a sound, re-synced by every read of the record, and cleared by whatever closed the session.
@MainActor
final class SessionRemindersTests: XCTestCase {

    /// Records what was scheduled, keeping only what is still pending, as the system does.
    private final class FakeCenter: NotificationScheduling {
        private(set) var pending: [String: UNNotificationRequest] = [:]

        func add(_ request: UNNotificationRequest) { pending[request.identifier] = request }

        func removePending(_ identifiers: [String]) { identifiers.forEach { pending[$0] = nil } }

        /// Pending requests in firing order, as seconds from when they were scheduled.
        var intervals: [TimeInterval] {
            pending.values.compactMap { ($0.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval }.sorted()
        }

        func content(firingAfter seconds: TimeInterval) -> UNNotificationContent? {
            pending.values.first { ($0.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval == seconds }?.content
        }
    }

    // 2026-06-15 09:00 UTC.
    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private let hour: Int64 = 3_600_000

    private var nowMs: Int64 = 0
    private var defaults: UserDefaults!
    private var center: FakeCenter!

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "SessionRemindersTests")!
        defaults.removePersistentDomain(forName: "SessionRemindersTests")
        nowMs = utcJun15At0900
        center = FakeCenter()
    }

    private func makeStore() throws -> SessionStore {
        SessionStore(
            database: try RecordDatabase.openInMemory(nowMs: 0),
            zones: SessionZones(defaults: defaults),
            now: { [unowned self] in Clock.date(ms: self.nowMs) },
            zone: { TimeZone(identifier: "UTC")! },
            reminders: SessionReminders(center: center)
        )
    }

    func testACheckInSchedulesItsRemindersUpToMidnightAndOnlyTheFirstSounds() throws {
        let store = try makeStore()
        XCTAssertEqual(try store.checkIn(), .opened)

        XCTAssertEqual(center.intervals, (1...7).map { TimeInterval($0 * 2 * 3600) }, "11:00 to 23:00")
        let first = try XCTUnwrap(center.content(firingAfter: 2 * 3600))
        XCTAssertEqual(first.title, "Still checked in?")
        XCTAssertEqual(first.categoryIdentifier, NotificationKinds.sessionReminder)
        XCTAssertNotNil(first.sound)
        let later = try XCTUnwrap(center.content(firingAfter: 4 * 3600))
        XCTAssertNil(later.sound)
        XCTAssertEqual(later.interruptionLevel, .passive)
    }

    func testAReturnMidSessionKeepsOnlyTheRemindersAheadAndNoneSounds() throws {
        let store = try makeStore()
        _ = try store.checkIn()
        nowMs += 3 * hour
        try store.refresh()

        XCTAssertEqual(center.intervals, [1, 3, 5, 7, 9, 11].map { TimeInterval($0 * 3600) }, "13:00 to 23:00")
        XCTAssertTrue(center.pending.values.allSatisfy { $0.content.sound == nil })
    }

    func testACheckOutClearsThemAndRecordsWhatEndedTheSession() throws {
        let store = try makeStore()
        _ = try store.checkIn()
        nowMs += hour
        let closed = try store.checkOut(closedBy: .reminderNotification)

        XCTAssertEqual(closed?.closedBy, ClosedBy.reminderNotification.storedValue)
        XCTAssertTrue(center.pending.isEmpty)
    }

    func testTheMidnightCloseClearsThem() throws {
        let store = try makeStore()
        _ = try store.checkIn()
        nowMs += 20 * hour
        try store.refresh()

        XCTAssertNil(store.openSession)
        XCTAssertTrue(center.pending.isEmpty)
    }
}
