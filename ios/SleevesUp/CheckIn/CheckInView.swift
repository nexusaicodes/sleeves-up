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
    /// The widget's picker, open while there is a habit to choose.
    @State private var choosing = false
    @State private var celebration: CheckOutCompleted?
    /// A check-out's celebration, waiting for the gate to finish going.
    @State private var pendingCelebration: CheckOutCompleted?
    @State private var failure: String?
    @State private var openHabits = false

    private enum GateAction: Identifiable {
        case checkIn, checkOut
        /// A check-out asked for from the session reminder, recorded as `ClosedBy.reminderNotification`.
        case checkOutFromReminder
        /// A check-out asked for from the Live Activity, recorded as `ClosedBy.timerNotification`.
        case checkOutFromActivity
        /// A check-out asked for from the home-screen widget, recorded as `ClosedBy.widget`.
        case checkOutFromWidget
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
            case .checkOutFromActivity:
                if store.openSession != nil { gateFor = .checkOutFromActivity }
            case .widget:
                fromWidget()
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
        .sheet(isPresented: $choosing) {
            HabitPickerView(habits: habits.active, onPick: picked, onDismiss: { choosing = false })
        }
        .fullScreenCover(item: $celebration) { completed in
            CheckOutCelebrationView(completed: completed) { celebration = nil }
        }
        .fullScreenCover(item: $gateFor, onDismiss: {
            celebration = pendingCelebration
            pendingCelebration = nil
        }) { action in
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
        route(TapRouting.shared.forHabit(habit: habit, runningHabitId: runningHabitId))
    }

    /// The widget's tap, routed by the rows at the tap, so a face drawn before a change cannot ask
    /// for the wrong thing.
    private func fromWidget() {
        route(TapRouting.shared.forWidget(active: habits.active, runningHabitId: runningHabitId))
    }

    /// A habit picked in the widget's picker, routed as its reminder's tap would be.
    private func picked(_ habit: HabitRow) {
        choosing = false
        route(TapRouting.shared.forHabit(habit: habit, runningHabitId: runningHabitId))
    }

    private var runningHabitId: KotlinLong? { habits.runningHabitId.map { KotlinLong(value: $0) } }

    private func route(_ outcome: TapOutcome) {
        if outcome is TapOutcomeCheckOut {
            if store.openSession != nil { gateFor = .checkOutFromWidget }
        } else if outcome is TapOutcomeChooseHabit {
            choosing = true
        } else if let running = outcome as? TapOutcomeRunningElsewhere {
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
            var closed: SessionRow?
            switch action {
            case .checkIn:
                // A skipped tour asked nothing, so the first check-in asks, as Android's gate does.
                if try store.checkIn() == .opened { Task { await NotificationAsk.ifUndetermined() } }
            case .checkOut: closed = try store.checkOut()
            case .checkOutFromReminder: closed = try store.checkOut(closedBy: .reminderNotification)
            case .checkOutFromActivity: closed = try store.checkOut(closedBy: .timerNotification)
            case .checkOutFromWidget: closed = try store.checkOut(closedBy: .widget)
            }
            try habits.reload()
            // Every gated check-out celebrates, shown once the gate has gone; a close that closed
            // nothing (another closer got there first) does not, and nor does the midnight close.
            if let closed {
                let day = try store.daySummary(habitId: closed.habitId, dateKey: closed.dateKey)
                pendingCelebration = CheckOutCompleted(
                    sessionMs: closed.duration ?? 0, dayTotalMs: day.totalMs, daySessionCount: day.count,
                    habitName: (habits.active + habits.archived).first { $0.id == closed.habitId }?.name,
                    raisedAt: Date()
                )
            }
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
