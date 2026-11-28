import SwiftUI

/// What a gated check-out just completed: the closed session, its own day's completed sessions for
/// its habit, and the habit's name. Read for the closed session's `date_key`, so a session closed
/// after midnight reports the day it belongs to.
struct CheckOutCompleted: Identifiable, Equatable {
    let sessionMs: Int64
    let dayTotalMs: Int64
    let daySessionCount: Int
    let habitName: String?
    let raisedAt: Date
    var id: Date { raisedAt }

    /// A celebration left behind (Home, then a return much later) is closed rather than greeting a
    /// later visit, sooner than the gate's ten minutes since it reacts to a moment.
    static let expiry: TimeInterval = 2 * 60
}

/// The check-out celebration: the mark settling once, the habit, "You showed up", the session's
/// length and the day so far. Identical at twenty minutes and at nine hours, since anything that
/// warms with the duration is a target in disguise. Nothing retires it on a timer: a tap closes it.
struct CheckOutCelebrationView: View {

    let completed: CheckOutCompleted
    let onDismiss: () -> Void

    @Environment(\.scenePhase) private var scenePhase

    private var dayText: String {
        String(app: "\(ReportText.sessions(completed.daySessionCount)) · \(SessionText.short(completed.dayTotalMs)) today")
    }

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            VStack(spacing: 12) {
                BrandMark(mode: .settle, color: Palette.ink).frame(width: 96, height: 96).padding(.bottom, 12)
                if let name = completed.habitName {
                    Text(name).font(AppFont.bodyMediumEmphasis).foregroundStyle(Palette.inkMuted)
                }
                Text("You showed up").font(AppFont.heading).foregroundStyle(Palette.ink)
                Text(SessionText.short(completed.sessionMs))
                    .font(AppFont.readout)
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .foregroundStyle(Palette.ink)
                Text(dayText).font(AppFont.bodyMedium).foregroundStyle(Palette.inkMuted)
            }
            .multilineTextAlignment(.center)
            .padding(32)
            VStack {
                Spacer()
                Text("Tap to close").font(AppFont.labelSmall).foregroundStyle(Palette.inkMuted).padding(.bottom, 48)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onDismiss)
        // One announcement for the whole surface rather than four fragments, and a tap closes it.
        .accessibilityRepresentation {
            Button(
                [completed.habitName, String(app: "You showed up"), SessionText.short(completed.sessionMs), dayText]
                    .compactMap { $0 }.joined(separator: ". "),
                action: onDismiss
            )
            .accessibilityIdentifier("celebration")
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, Date().timeIntervalSince(completed.raisedAt) > CheckOutCompleted.expiry {
                onDismiss()
            }
        }
    }
}
