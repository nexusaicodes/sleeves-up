import SwiftUI

/// One session as Check-In's today list and History's selected day both draw it: the clock range,
/// then the duration in a column of its own, or "ongoing" for the open session. Nothing in it ticks
/// and nothing grades the session.
struct SessionIntervalRow: View {

    let session: SessionRow

    /// The duration column: wide enough for "12h 59m" in either language at the default size, and
    /// growing with Dynamic Type. The duration always keeps its full width; only the range gives way.
    @ScaledMetric(relativeTo: .subheadline) private var durationColumn: CGFloat = 64

    var body: some View {
        HStack(spacing: 12) {
            Text(SessionText.range(session))
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
            Group {
                if let duration = session.duration {
                    Text(SessionText.short(duration)).monospacedDigit().lineLimit(1).fixedSize()
                } else {
                    // The range already ends in "ongoing"; a second live number would compete with
                    // the gauge, so the open row's column carries a pulse and no text.
                    OngoingPulse()
                }
            }
            .frame(minWidth: min(durationColumn, 128), alignment: .trailing)
            .layoutPriority(1)
        }
        .font(AppFont.bodyMedium)
        .foregroundStyle(Palette.ink)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Palette.paper200, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("sessionRow")
    }
}

/// Three dots fading in turn, Android's `OngoingPulse`: the open session's sign in a table row.
/// Solid under Reduce Motion, still distinct from a duration.
private struct OngoingPulse: View {

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let dots = 3
    private static let cycle = 0.6
    private static let stagger = 0.2
    private static let minAlpha = 0.25

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { context in
            HStack(spacing: 3) {
                ForEach(0..<Self.dots, id: \.self) { dot in
                    Circle()
                        .fill(Palette.ink)
                        .frame(width: 5, height: 5)
                        .opacity(reduceMotion ? 1 : alpha(dot, context.date.timeIntervalSinceReferenceDate))
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// Each dot runs 0.25 → 1 → 0.25, one cycle out and one back, a stagger after the one before.
    private func alpha(_ dot: Int, _ seconds: Double) -> Double {
        let t = (seconds - Double(dot) * Self.stagger).truncatingRemainder(dividingBy: 2 * Self.cycle)
        let phase = (t < 0 ? t + 2 * Self.cycle : t) / Self.cycle
        let progress = phase <= 1 ? phase : 2 - phase
        return Self.minAlpha + (1 - Self.minAlpha) * progress
    }
}
