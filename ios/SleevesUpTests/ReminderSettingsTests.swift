import XCTest
@testable import SleevesUp

/// Each habit's reminder settings: on by default at 10:00, kept per uuid, held to the allowed range,
/// and observable through a revision bumped on every write, so a change replans.
@MainActor
final class ReminderSettingsTests: XCTestCase {

    private var settings: ReminderSettings!

    override func setUp() async throws {
        let defaults = UserDefaults(suiteName: "ReminderSettingsTests")!
        defaults.removePersistentDomain(forName: "ReminderSettingsTests")
        settings = ReminderSettings(defaults: defaults)
    }

    func testAHabitStartsOnAtTenAndKeepsItsOwnSettings() {
        XCTAssertTrue(settings.isOn("a"))
        XCTAssertEqual(settings.minute("a"), 10 * 60)

        settings.setOn(false, for: "a")
        settings.setMinute(7 * 60 + 30, for: "a")
        XCTAssertFalse(settings.isOn("a"))
        XCTAssertEqual(settings.minute("a"), 7 * 60 + 30)
        XCTAssertTrue(settings.isOn("b"), "another habit's settings are untouched")
        XCTAssertEqual(settings.minute("b"), 10 * 60)
    }

    func testTheMinuteIsHeldToTheAllowedRange() {
        settings.setMinute(3 * 60, for: "a")
        XCTAssertEqual(settings.minute("a"), 5 * 60)
        XCTAssertEqual(ReminderText.range, (5 * 60)...(21 * 60))
    }

    func testEveryWriteBumpsTheRevision() {
        let start = settings.revision
        settings.setMinute(9 * 60, for: "a")
        settings.setOn(false, for: "a")
        XCTAssertEqual(settings.revision, start + 2)
    }

    func testARowStatesATimeOrThatRemindersAreOff() {
        XCTAssertTrue(ReminderText.summary(on: true, minute: 10 * 60).hasPrefix("Around "))
        XCTAssertEqual(ReminderText.summary(on: false, minute: 10 * 60), "Reminders off")
        XCTAssertEqual(ReminderText.minute(of: ReminderText.date(minute: 7 * 60 + 45)), 7 * 60 + 45)
    }
}
