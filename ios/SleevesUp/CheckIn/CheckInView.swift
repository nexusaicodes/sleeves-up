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
    @State private var sessionsExpanded = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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

    /// One viewport with the action in thumb reach; on a short screen or at an accessibility text
    /// size the same content scrolls instead, since a fixed layout would clip rather than shrink.
    private var screen: some View {
        GeometryReader { proxy in
            let compact = proxy.size.height < Self.compactHeight || dynamicTypeSize.isAccessibilitySize
            Group {
                if compact {
                    ScrollView {
                        content(compact: true).frame(minHeight: proxy.size.height)
                    }
                } else {
                    content(compact: false).frame(height: proxy.size.height)
                }
            }
        }
        .background(Palette.paper.ignoresSafeArea())
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

    /// Android's `COMPACT_HEIGHT_THRESHOLD`: below it the screen scrolls.
    private static let compactHeight: CGFloat = 560

    private func content(compact: Bool) -> some View {
        VStack(spacing: 0) {
            Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(AppLocale.current)))
                .font(AppFont.bodyMediumEmphasis)
                .foregroundStyle(Palette.inkMuted)
            if compact { Spacer().frame(height: 8) } else { Spacer(minLength: 8).frame(maxHeight: 80) }
            if store.hasEverTracked { gauge } else { welcome }
            if compact { Spacer().frame(height: 12) } else { Spacer(minLength: 12) }
            if let failure {
                Text(failure).font(AppFont.bodySmall).foregroundStyle(Palette.inkMuted)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 12)
            }
            if !store.todaySessions.isEmpty {
                sessions(scrollsInside: !compact).padding(.bottom, 12)
            }
            action
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity)
    }

    /// The first-run empty state, in place of a gauge that would only ever read zero: the mark and a
    /// title, no message, since the action below already says what to do.
    private var welcome: some View {
        VStack(spacing: 20) {
            BrandMark(color: Palette.ink).frame(width: 96, height: 96)
            Text("Ready when you are")
                .font(AppFont.heading)
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
        }
    }

    /// The brand lattice breathing while a session is open, still at rest, above the open session's
    /// time counting up from its check-in (zero between sessions). The mark says one thing, whether a
    /// session is open; the readout is the current session, never the day's total.
    private var gauge: some View {
        VStack(spacing: 20) {
            BrandMark(mode: .pulse(running: store.openSession != nil), color: Palette.ink)
                .frame(maxWidth: markSide, maxHeight: markSide)
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let elapsed = store.openSession.map { Clock.nowMs(context.date) - $0.startedAt } ?? 0
                Text(SessionText.live(max(elapsed, 0)))
                    .font(dynamicTypeSize.isAccessibilitySize ? AppFont.readoutCompact : AppFont.readout)
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .foregroundStyle(Palette.ink)
            }
        }
    }

    /// The mark's side: generous on a tall phone, smaller at large Dynamic Type so the readout,
    /// the pill and the action still share one screen.
    private var markSide: CGFloat { dynamicTypeSize.isAccessibilitySize ? 120 : 200 }

    /// Today's sessions as a pill stating the day once (`n sessions · Xh Ym today`, the open interval
    /// included live), which opens into the ledger rows.
    private func sessions(scrollsInside: Bool) -> some View {
        VStack(spacing: 8) {
            Button {
                withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.2)) { sessionsExpanded.toggle() }
            } label: {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    HStack(spacing: 4) {
                        Text(String(app: "\(ReportText.sessions(store.todaySessions.count)) · \(SessionText.short(todayTotal(at: context.date))) today"))
                            .font(AppFont.bodyMedium)
                        Image(systemName: sessionsExpanded ? "chevron.up" : "chevron.down")
                            .font(AppFont.bodySmall)
                            .accessibilityHidden(true)
                    }
                }
                .foregroundStyle(Palette.inkMuted)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .frame(minHeight: 48)
                .background(Palette.paper200, in: Capsule())
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("todaySessions")
            .accessibilityHint(sessionsExpanded ? String(app: "Hide today's sessions") : String(app: "Show today's sessions"))
            if sessionsExpanded {
                // Bounded and scrolling inside itself in the one-viewport layout; in full when the
                // whole screen already scrolls.
                if scrollsInside {
                    ScrollView { rows }
                        .scrollBounceBehavior(.basedOnSize)
                        .frame(maxHeight: 160)
                        .transition(.opacity)
                } else {
                    rows.transition(.opacity)
                }
            }
        }
    }

    /// "Check in to Gym" for the shown habit, "Check out of Gym" for the running one, or the bare
    /// action while the habit is unnamed.
    private func actionLabel(running: Bool) -> String {
        if running {
            let name = (habits.active + habits.archived).first { $0.id == store.openSession?.habitId }?.name
            return name.map { String(app: "Check out of \($0)") } ?? String(app: "Check Out")
        }
        return habits.selected?.name.map { String(app: "Check in to \($0)") } ?? String(app: "Check In")
    }

    private var rows: some View {
        VStack(spacing: 4) {
            ForEach(store.todaySessions, id: \.id) { session in
                SessionIntervalRow(session: session)
            }
        }
    }

    /// The day so far: every completed session, plus the open one's time to `date`.
    private func todayTotal(at date: Date) -> Int64 {
        store.todaySessions.reduce(Int64(0)) { sum, session in
            sum + (session.duration ?? max(Clock.nowMs(date) - session.startedAt, 0))
        }
    }

    /// The start/stop control, coloured by what it does: brass-fill to check in, ink to check out,
    /// animated across the switch so it reads as one control changing state. Never red.
    private var action: some View {
        let running = store.openSession != nil
        let colors = running ? Palette.stopAction : Palette.startAction
        return Button {
            gateFor = running ? .checkOut : .checkIn
        } label: {
            HStack(spacing: 12) {
                Image(systemName: running ? "square.and.arrow.up" : "square.and.arrow.down")
                    .font(.system(size: 22, weight: .semibold))
                    .accessibilityHidden(true)
                Text(actionLabel(running: running))
                    .font(AppFont.actionLabel)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .foregroundStyle(colors.label)
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(colors.fill, in: Capsule())
            .contentShape(Capsule())
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: running)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("sessionAction")
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
            if let name = habits.active.first(where: { $0.id == running.runningHabitId }).map(HabitText.name) {
                failure = String(app: "\(name) is running. Check out of it first.")
            } else {
                failure = String(app: "A habit is running. Check out of it first.")
            }
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
            failure = HabitText.saveFailed
        }
    }
}

/// How sessions' times are written, on Check-In and History, with Android's unit letters per
/// language ("2h 5m", "2 س 5 د") and Latin digits.
enum SessionText {

    static func live(_ ms: Int64) -> String {
        let seconds = ms / 1000
        let h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60
        return h > 0 ? String(app: "\(h)h \(m)m \(s)s") : String(app: "\(m)m \(s)s")
    }

    static func short(_ ms: Int64) -> String {
        let minutes = ms / 60_000
        let h = minutes / 60, m = minutes % 60
        return h > 0 ? String(app: "\(h)h \(m)m") : String(app: "\(m)m")
    }

    static func range(_ session: SessionRow) -> String {
        let start = AppLocale.clock(Clock.date(ms: session.startedAt))
        let end = session.stoppedAt.map { AppLocale.clock(Clock.date(ms: $0)) } ?? String(app: "ongoing")
        return String(app: "\(start) – \(end)")
    }
}
