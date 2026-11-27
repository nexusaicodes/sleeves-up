import XCTest

/// The Check-In screen, the Habits screen and the presence gate, driven through the real UI on a
/// simulator. The simulator has no front camera, so the gate offers device unlock at once.
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
        if app.buttons["Check Out"].waitForExistence(timeout: 3) {
            throw XCTSkip("A session is open from an earlier run; close it in the app first.")
        }
        XCTAssertTrue(app.buttons["Check In"].waitForExistence(timeout: 5))
        return app
    }

    @MainActor
    func testTheGateOffersDeviceUnlockAndNotNowWritesNothing() throws {
        let app = try launchBetweenSessions()
        app.buttons["Check In"].tap()

        XCTAssertTrue(app.staticTexts["This camera can't check for a face. Use device unlock instead."]
            .waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Use device unlock"].exists)
        attach(app, "gate")

        app.buttons["Not now"].tap()
        XCTAssertTrue(app.buttons["Check In"].waitForExistence(timeout: 5), "Not now must leave with nothing written")
        XCTAssertFalse(app.buttons["Check Out"].exists)
    }

    @MainActor
    func testCheckInAndOutThroughDeviceUnlock() throws {
        guard ProcessInfo.processInfo.environment["SIM_FACE_ID_HOST"] == "1" else {
            throw XCTSkip("Needs the host to answer simulated Face ID; see the iOS README.")
        }
        let app = try launchBetweenSessions()
        app.buttons["Check In"].tap()
        app.buttons["Use device unlock"].tap()
        // The host matches the Face ID prompt; the gate then passes and the session opens.
        XCTAssertTrue(app.buttons["Check Out"].waitForExistence(timeout: 60))
        attach(app, "checked-in")

        sleep(61)
        app.buttons["Check Out"].tap()
        app.buttons["Use device unlock"].tap()
        XCTAssertTrue(app.buttons["Check In"].waitForExistence(timeout: 60))
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
        XCTAssertTrue(app.buttons["Check In"].waitForExistence(timeout: 5))
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
        XCTAssertTrue(app.buttons["Check In"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.navigationBars.staticTexts[name].exists, "the selection moved off the archived habit")
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
