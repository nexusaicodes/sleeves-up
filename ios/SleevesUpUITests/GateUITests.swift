import XCTest

/// The Check-In screen, History, the Habits screen and the presence gate, driven through the real UI
/// on a simulator. The simulator has no front camera, so the gate offers device unlock at once.
///
/// `testCheckInAndOutThroughDeviceUnlock` passes the gate with simulated Face ID, which only the
/// host can answer: it runs when `TEST_RUNNER_SIM_FACE_ID_HOST=1` is set on `xcodebuild`, with the
/// host sending `notifyutil -p com.apple.BiometricKit_Sim.pearl.match` to the booted simulator while
/// each prompt is up (see the iOS README), and is skipped otherwise.
final class GateUITests: XCTestCase {

    /// Launches the app on the Check-In screen with no session open, naming the seed habit first
    /// when the record is fresh.
    @MainActor
    private func launchBetweenSessions() throws -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        nameTheSeedHabitIfAsked(app)
        if action(app, "Check Out").waitForExistence(timeout: 3) {
            throw XCTSkip("A session is open from an earlier run; close it in the app first.")
        }
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 5))
        return app
    }

    @MainActor
    func testTheGateOffersDeviceUnlockAndNotNowWritesNothing() throws {
        let app = try launchBetweenSessions()
        action(app, "Check In").tap()

        XCTAssertTrue(app.staticTexts["This camera can't check for a face. Use device unlock instead."]
            .waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Use device unlock"].exists)
        attach(app, "gate")

        app.buttons["Not now"].tap()
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 5), "Not now must leave with nothing written")
        XCTAssertFalse(action(app, "Check Out").exists)
    }

    @MainActor
    func testCheckInAndOutThroughDeviceUnlock() throws {
        guard ProcessInfo.processInfo.environment["SIM_FACE_ID_HOST"] == "1" else {
            throw XCTSkip("Needs the host to answer simulated Face ID; see the iOS README.")
        }
        let app = try launchBetweenSessions()
        action(app, "Check In").tap()
        app.buttons["Use device unlock"].tap()
        // The host matches the Face ID prompt; the gate then passes and the session opens.
        XCTAssertTrue(action(app, "Check Out").waitForExistence(timeout: 60))
        attach(app, "checked-in")

        sleep(61)
        action(app, "Check Out").tap()
        app.buttons["Use device unlock"].tap()
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 60))
        XCTAssertTrue(app.staticTexts["1m"].waitForExistence(timeout: 5), "today's list shows the closed session")
        attach(app, "checked-out")
    }

    @MainActor
    func testHabitsCreateSwitchAndArchive() throws {
        let app = try launchBetweenSessions()
        let switcher = app.buttons["habitSwitcher"]
        XCTAssertTrue(switcher.waitForExistence(timeout: 5))

        switcher.tap()
        app.buttons["New habit"].tap()
        let name = "UI \(Int(Date().timeIntervalSince1970) % 1_000_000)"
        let field = app.textFields["Name"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Give it a name"].waitForExistence(timeout: 3), "an empty save shows the problem")
        field.tap()
        field.typeText(name)
        app.buttons["Save"].tap()
        attach(app, "habits")

        // Tapping the new row shows it and returns to Check-In.
        app.buttons[name].tap()
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 5))
        XCTAssertTrue(app.navigationBars.staticTexts[name].exists, "the switcher names the shown habit")

        switcher.tap()
        // The shown habit's row reads "<name>, Showing".
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.swipeLeft()
        app.buttons["Archive"].tap()
        XCTAssertTrue(app.staticTexts["\(name) archived. Its record is kept."].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["Archived"].exists)
        attach(app, "archived")

        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 5))
        XCTAssertFalse(app.navigationBars.staticTexts[name].exists, "the selection moved off the archived habit")
    }

    @MainActor
    func testHistoryStepsBackAndShowsATappedDay() throws {
        let app = try launchBetweenSessions()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["No day selected"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Next month"].isEnabled, "the stepper stops at the current month")

        app.buttons["Previous month"].tap()
        XCTAssertTrue(app.buttons["Next month"].isEnabled)
        let lastMonth = Calendar(identifier: .gregorian).date(byAdding: .month, value: -1, to: Date())!
        let parts = Calendar(identifier: .gregorian).dateComponents([.year, .month], from: lastMonth)
        let key = String(format: "%04ld-%02ld-01", parts.year!, parts.month!)
        let first = app.buttons["day-\(key)"]
        XCTAssertTrue(first.waitForExistence(timeout: 5))
        first.tap()
        XCTAssertTrue(app.staticTexts["No sessions recorded on this day."].waitForExistence(timeout: 3))
        attach(app, "history-day")

        first.tap()
        XCTAssertTrue(app.staticTexts["No day selected"].waitForExistence(timeout: 3), "a second tap clears it")
        XCTAssertTrue(app.buttons["habitSwitcher"].exists, "the switcher sits on History too")
    }

    /// Check-In's start/stop control reading `label`, told apart from the Check In tab.
    @MainActor
    private func action(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(identifier: "sessionAction").matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    @MainActor
    private func nameTheSeedHabitIfAsked(_ app: XCUIApplication) {
        guard app.staticTexts["What are you showing up for?"].waitForExistence(timeout: 3) else { return }
        let field = app.textFields["Name"]
        field.tap()
        field.typeText("Gym\n")
    }

    @MainActor
    private func attach(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }
}
