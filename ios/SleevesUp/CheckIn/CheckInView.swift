import SwiftUI

/// The Check-In screen: one start/stop control, the open session's time counting up from zero, and
/// today's sessions. Every check-in and check-out goes through the presence gate first. As on
/// Android, the readout is the current session, never the day's total, and nothing here grades a
/// session or a day.
struct CheckInView: View {

    let store: SessionStore

    @State private var gateFor: GateAction?
    @State private var failure: String?

    private enum GateAction: Identifiable {
        case checkIn, checkOut
        var id: Self { self }
    }

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            readout
            Button(store.openSession == nil ? "Check In" : "Check Out") {
                gateFor = store.openSession == nil ? .checkIn : .checkOut
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
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
                HStack {
                    Text(SessionText.range(session))
                    Spacer()
                    Text(session.duration.map(SessionText.short) ?? "ongoing")
                        .monospacedDigit()
                }
                .font(.callout)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func write(_ action: GateAction) {
        do {
            switch action {
            case .checkIn: _ = try store.checkIn()
            case .checkOut: try store.checkOut()
            }
            failure = nil
        } catch {
            failure = "That didn't save. Try again."
        }
    }
}

/// How the Check-In screen writes times. English only for now; the Android app's languages arrive
/// with the iOS localization work.
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
