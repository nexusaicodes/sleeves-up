import Shared
import SwiftUI

/// The Check-In screen for the selected habit: one start/stop control, the open session's time
/// counting up from zero, and the habit's sessions today. Every check-in and check-out goes through
/// the presence gate first. As on Android, the readout is the current session, never the day's
/// total, and nothing here grades a session or a day. The habit's name at the top is the switcher.
struct CheckInView: View {

    let store: SessionStore
    let habits: HabitStore
    let taps: NotificationTaps

    @State private var gateFor: GateAction?
    @State private var failure: String?
    @State private var openHabits = false

    private enum GateAction: Identifiable {
        case checkIn, checkOut
        /// A check-out asked for from the session reminder, recorded as `ClosedBy.reminderNotification`.
        case checkOutFromReminder
        var id: Self { self }
    }

    var body: some View {
        NavigationStack {
            screen.habitSwitcher(habits, open: $openHabits)
        }
        .onChange(of: taps.pending, initial: true) { _, pending in
            guard pending != nil else { return }
            switch taps.take() {
            case .checkOut:
                // A reminder for a session already closed has nothing left to ask.
                if store.openSession != nil { gateFor = .checkOutFromReminder }
            case .checkIn(let habitUuid):
                checkInFromReminder(habitUuid)
            case .reminderSettings:
                openHabits = true
            case nil:
                break
            }
        }
    }

    private var screen: some View {
        VStack(spacing: 24) {
            Spacer()
            readout
            Button(store.openSession == nil ? "Check In" : "Check Out") {
                gateFor = store.openSession == nil ? .checkIn : .checkOut
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("sessionAction")
            if let failure { Text(failure).font(.footnote).foregroundStyle(.secondary) }
            Spacer()
            sessions
        }
        .padding()
        .fullScreenCover(item: $gateFor) { action in
            PresenceGateView(
                onPassed: {
                    gateFor = nil
                    write(action)
                },
                onDismiss: { gateFor = nil }
            )
        }
    }

    /// The open session counting up from its check-in, or zero between sessions.
    private var readout: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let elapsed = store.openSession.map { Clock.nowMs(context.date) - $0.startedAt } ?? 0
            Text(SessionText.live(max(elapsed, 0)))
                .font(.system(size: 44, weight: .semibold).monospacedDigit())
        }
    }

    private var sessions: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(store.todaySessions, id: \.id) { session in
                SessionIntervalRow(session: session)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// A habit reminder's tap, routed by the rows at the tap: an archived or unknown habit, or one
    /// already running, asks nothing; another habit running says so; otherwise the habit is shown and
    /// the gate opens for it.
    private func checkInFromReminder(_ habitUuid: String) {
        let habit = (habits.active + habits.archived).first { $0.uuid == habitUuid }
        let outcome = TapRouting.shared.forHabit(
            habit: habit, runningHabitId: habits.runningHabitId.map { KotlinLong(value: $0) }
        )
        if let running = outcome as? TapOutcomeRunningElsewhere {
            let name = habits.active.first { $0.id == running.runningHabitId }.map(HabitText.name) ?? "A habit"
            failure = "\(name) is running. Check out of it first."
        } else if let checkIn = outcome as? TapOutcomeCheckIn {
            do {
                guard try habits.select(checkIn.habitId) else { return }
                try store.show(habitId: checkIn.habitId)
                failure = nil
                gateFor = .checkIn
            } catch {
                failure = HabitText.saveFailed
            }
        }
    }

    private func write(_ action: GateAction) {
        do {
            switch action {
            case .checkIn: _ = try store.checkIn()
            case .checkOut: try store.checkOut()
            case .checkOutFromReminder: try store.checkOut(closedBy: .reminderNotification)
            }
            try habits.reload()
            failure = nil
        } catch {
            failure = "That didn't save. Try again."
        }
    }
}

/// How sessions' times are written, on Check-In and History. English only for now; the Android
/// app's languages arrive with the iOS localization work.
enum SessionText {

    static func live(_ ms: Int64) -> String {
        let seconds = ms / 1000
        let h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60
        return h > 0 ? "\(h)h \(m)m \(s)s" : "\(m)m \(s)s"
    }

    static func short(_ ms: Int64) -> String {
        let minutes = ms / 60_000
        let h = minutes / 60, m = minutes % 60
        return h > 0 ? "\(h)h \(m)m" : "\(m)m"
    }

    static func range(_ session: SessionRow) -> String {
        let start = Clock.date(ms: session.startedAt).formatted(date: .omitted, time: .shortened)
        guard let stop = session.stoppedAt else { return start }
        return "\(start) – \(Clock.date(ms: stop).formatted(date: .omitted, time: .shortened))"
    }
}
