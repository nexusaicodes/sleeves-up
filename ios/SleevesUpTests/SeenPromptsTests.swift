import XCTest
@testable import SleevesUp

/// The one-time prompts: a finished tour leaves the notification ask owed, a skip marks it asked
/// without asking, either way marks the widget page seen, and every mark survives a restart.
@MainActor
final class SeenPromptsTests: XCTestCase {

    private var defaults: UserDefaults!

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "SeenPromptsTests")!
        defaults.removePersistentDomain(forName: "SeenPromptsTests")
    }

    func testAFinishedTourLeavesTheAskOwed() {
        let prompts = SeenPrompts(defaults: defaults)
        prompts.endWelcome(asksNotifications: true)
        XCTAssertTrue(prompts.welcomeSeen)
        XCTAssertTrue(prompts.widgetPageSeen)
        XCTAssertFalse(prompts.notificationsAsked)
    }

    func testASkippedTourMarksTheAskWithoutAsking() {
        let prompts = SeenPrompts(defaults: defaults)
        prompts.endWelcome(asksNotifications: false)
        XCTAssertTrue(prompts.notificationsAsked)
    }

    func testMarksSurviveARestartUnderAndroidsKeys() {
        SeenPrompts(defaults: defaults).endWelcome(asksNotifications: false)
        let restarted = SeenPrompts(defaults: defaults)
        XCTAssertTrue(restarted.welcomeSeen && restarted.notificationsAsked && restarted.widgetPageSeen)
        XCTAssertTrue(defaults.bool(forKey: "welcome_seen"))
        XCTAssertTrue(defaults.bool(forKey: "notifications_asked"))
        XCTAssertTrue(defaults.bool(forKey: "widget_page_seen"))
    }
}
