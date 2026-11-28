import XCTest

/// The Check-In screen, History, Reports, the Habits screen and the presence gate, driven through the
/// real UI on a simulator. The simulator has no front camera, so the gate offers device unlock at once.
///
/// `testCheckInAndOutThroughDeviceUnlock` passes the gate with simulated Face ID, which only the
/// host can answer: it runs when `TEST_RUNNER_SIM_FACE_ID_HOST=1` is set on `xcodebuild`, with the
/// host sending `notifyutil -p com.apple.BiometricKit_Sim.pearl.match` to the booted simulator while
/// each prompt is up (see the iOS README), and is skipped otherwise.
final class GateUITests: XCTestCase {

    /// Launches the app on the Check-In screen with no session open, through the welcome tour and
    /// the naming step when they are owed.
    @MainActor
    private func launchBetweenSessions() throws -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        finishTheTourIfShown(app)
        nameTheSeedHabitIfAsked(app)
        allowNotificationsIfAsked()
        if action(app, "Check Out").waitForExistence(timeout: 3) {
            throw XCTSkip("A session is open from an earlier run; close it in the app first.")
        }
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 5))
        return app
    }

    /// The tour's four pages, Skip on all but the last, and Get started handing over to the app.
    @MainActor
    func testTheWelcomeTourPagesThroughToTheApp() throws {
        let app = XCUIApplication()
        // Read ahead of the stored value for this launch only, so the tour is owed.
        app.launchArguments = ["-welcome_seen", "NO"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Make it a habit"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Skip"].exists)
        let next = app.buttons["welcomeAction"]
        for title in ["Showing up is what counts", "On your home screen", "One habit at a time"] {
            next.tap()
            XCTAssertTrue(app.staticTexts[title].waitForExistence(timeout: 3), title)
        }
        XCTAssertEqual(next.label, "Get started")
        XCTAssertFalse(app.buttons["Skip"].exists)
        attach(app, "last page")
        next.tap()
        nameTheSeedHabitIfAsked(app)
        allowNotificationsIfAsked()
        XCTAssertTrue(app.buttons.matching(identifier: "sessionAction").firstMatch.waitForExistence(timeout: 5))
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
        // The celebration, the same at any length, closed by a tap.
        let celebration = app.buttons["celebration"]
        XCTAssertTrue(celebration.waitForExistence(timeout: 60))
        XCTAssertTrue(celebration.label.contains("You showed up"), celebration.label)
        attach(app, "celebration")
        celebration.tap()
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 5))
        let today = app.buttons["todaySessions"]
        XCTAssertTrue(today.waitForExistence(timeout: 5), "the pill states today's sessions")
        today.tap()
        // Earlier sessions today may be listed too, so look for the one just closed: a row ending in
        // its one-minute duration (" 1m", which an "11m" row does not end in).
        let closed = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == 'sessionRow' AND label ENDSWITH ' 1m'")).firstMatch
        XCTAssertTrue(closed.waitForExistence(timeout: 5), "today's list shows the closed session")
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
        XCTAssertTrue(app.switches["Reminders"].exists, "the editor sets the habit's reminder")
        field.tap()
        field.typeText(name)
        app.buttons["Save"].tap()
        attach(app, "habits")

        let reminderTime = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Around '")).firstMatch
        XCTAssertTrue(reminderTime.exists, "rows state each habit's reminder time")
        // Tapping the new row shows it and returns to Check-In.
        // A row's label is its name, then its reminder time.
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch.tap()
        XCTAssertTrue(action(app, "Check In").waitForExistence(timeout: 5))
        XCTAssertTrue(app.navigationBars.staticTexts[name].exists, "the switcher names the shown habit")

        switcher.tap()
        // The shown habit's row reads "<name>, <reminder time>, Showing".
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

    @MainActor
    func testReportsSwitchesScopeAndStopsAtTheMonthInProgress() throws {
        let app = try launchBetweenSessions()
        app.tabBars.buttons["Reports"].tap()
        let numbers = app.staticTexts["The Numbers"]
        let empty = app.staticTexts["Nothing recorded here"]
        XCTAssertTrue(numbers.waitForExistence(timeout: 5) || empty.exists, "a window's cards or the empty state")
        XCTAssertFalse(app.buttons["Next month"].isEnabled, "the stepper stops at the month in progress")
        let month = Date().formatted(.dateTime.month(.wide).year())
        XCTAssertTrue(app.staticTexts[month].exists)

        app.buttons["Previous month"].tap()
        XCTAssertTrue(app.buttons["Next month"].isEnabled)

        app.buttons["All time"].tap()
        XCTAssertTrue(app.staticTexts["All time"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["Previous month"].isEnabled, "no month to step under all time")
        attach(app, "reports-all-time")

        // With a counted day, the last card exports the window and opens the share sheet.
        let export = app.buttons["Export all time"]
        if numbers.exists {
            app.swipeUp()
            app.swipeUp()
            XCTAssertTrue(export.waitForExistence(timeout: 3))
            export.tap()
            let sheet = app.otherElements["ActivityListView"]
            XCTAssertTrue(sheet.waitForExistence(timeout: 5), "the share sheet opens over the file")
            attach(app, "reports-export")
            app.buttons["Close"].firstMatch.tap()
        } else {
            XCTAssertFalse(export.exists, "an empty scope offers no export")
        }

        app.buttons["Month"].tap()
        XCTAssertTrue(app.staticTexts[month].waitForExistence(timeout: 3), "Month returns to the month in progress")
        XCTAssertTrue(app.buttons["habitSwitcher"].exists, "the switcher sits on Reports too")
    }

    /// Settings: the Habits link, the privacy claims and the licences with their texts.
    @MainActor
    func testSettingsShowsPrivacyAndLicences() throws {
        let app = try launchBetweenSessions()
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Habits"].waitForExistence(timeout: 3))
        app.buttons["What this app collects"].tap()
        XCTAssertTrue(app.staticTexts["No network activity"].waitForExistence(timeout: 3))
        attach(app, "privacy")
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Licences"].tap()
        XCTAssertTrue(app.staticTexts["GRDB.swift"].waitForExistence(timeout: 3))
        let mit = app.buttons["MIT License (GRDB.swift)"]
        if !mit.isHittable { app.swipeUp() }
        mit.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Gwendal Roué'")).firstMatch.waitForExistence(timeout: 3))
        app.navigationBars.buttons.firstMatch.tap()
        app.navigationBars.buttons.firstMatch.tap()
        attach(app, "settings")
    }

    /// Check-In's start/stop control for `label` ("Check In" or "Check Out"), told apart from the
    /// Check In tab. It names the habit ("Check in to Gym"), so its label is matched by its start.
    @MainActor
    private func action(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(identifier: "sessionAction")
            .matching(NSPredicate(format: "label BEGINSWITH[c] %@", label)).firstMatch
    }

    /// The notification prompt, raised on the first start past the naming step on a fresh simulator.
    @MainActor
    private func allowNotificationsIfAsked() {
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow"]
        if allow.waitForExistence(timeout: 2) { allow.tap() }
    }

    /// Pages through the welcome tour with Next and ends it with Get started.
    @MainActor
    private func finishTheTourIfShown(_ app: XCUIApplication) {
        let next = app.buttons["welcomeAction"]
        guard next.waitForExistence(timeout: 3) else { return }
        while next.exists, next.label == "Next" { next.tap() }
        XCTAssertEqual(next.label, "Get started")
        next.tap()
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
