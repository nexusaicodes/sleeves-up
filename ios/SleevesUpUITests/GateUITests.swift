import XCTest

/// The Check-In screen and the presence gate, driven through the real UI on a simulator. The
/// simulator has no front camera, so the gate offers device unlock at once.
///
/// `testCheckInAndOutThroughDeviceUnlock` passes the gate with simulated Face ID, which only the
/// host can answer: it runs when `TEST_RUNNER_SIM_FACE_ID_HOST=1` is set on `xcodebuild`, with the
/// host sending `notifyutil -p com.apple.BiometricKit_Sim.pearl.match` to the booted simulator while
/// each prompt is up (see the iOS README), and is skipped otherwise.
final class GateUITests: XCTestCase {

    /// Launches the app on the Check-In screen with no session open.
    @MainActor
    private func launchBetweenSessions() throws -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
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
    private func attach(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }
}
