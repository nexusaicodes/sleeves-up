import BackgroundTasks
import Foundation

/// Runs app code while the app is not opened, so a user who stays away still has reminders planned
/// ahead and a forgotten session still closes at its own midnight (iOS rules 2 and 3). The system
/// decides when, if ever, it runs; each run asks for the next one.
enum BackgroundRefresh {

    /// Listed under `BGTaskSchedulerPermittedIdentifiers` in `ios/SleevesUpInfo.plist`.
    static let identifier = "com.nexusai.checkin.app.refresh"

    /// The earliest a refresh is asked for after the last: often enough to keep a two-week plan full,
    /// rarely enough to cost nothing.
    static let interval: TimeInterval = 6 * 60 * 60

    /// Registers the handler. Must run before launch finishes.
    static func register() {
        // Delivered on the main queue, so the work runs on the main actor, where the stores live.
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { task in
            let succeeded = MainActor.assumeIsolated { () -> Bool in
                schedule()
                return (try? run()) != nil
            }
            task.setTaskCompleted(success: succeeded)
        }
    }

    /// Asks for the next refresh. A refusal (simulator, Background App Refresh off) costs only it.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// Closes a forgotten session, re-syncs the open session's reminders and Live Activity, and
    /// replans the habits' reminders.
    @MainActor
    static func run() throws {
        let record = try RecordDatabase.sharedOnDisk()
        try SessionStore(database: record, reminders: SessionReminders(), activity: SessionActivity()).refresh()
        try HabitReminders(record: record, ledger: try NudgeLedger.sharedOnDisk(), settings: ReminderSettings()).replan()
    }
}
