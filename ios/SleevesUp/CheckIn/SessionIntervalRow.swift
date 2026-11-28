import SwiftUI

/// One session as Check-In's today list and History's selected day both draw it: the clock range,
/// then the duration in a column of its own, or "ongoing" for the open session. Nothing in it ticks
/// and nothing grades the session.
struct SessionIntervalRow: View {

    let session: SessionRow

    /// The duration column: wide enough for "12h 59m" in either language, growing with Dynamic Type
    /// rather than cutting a figure short.
    @ScaledMetric(relativeTo: .subheadline) private var durationColumn: CGFloat = 64

    var body: some View {
        HStack(spacing: 12) {
            Text(SessionText.range(session))
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(session.duration.map(SessionText.short) ?? String(app: "ongoing"))
                .monospacedDigit()
                .lineLimit(1)
                .foregroundStyle(session.duration == nil ? Palette.inkMuted : Palette.ink)
                .frame(minWidth: min(durationColumn, 128), alignment: .trailing)
        }
        .font(AppFont.bodyMedium)
        .foregroundStyle(Palette.ink)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Palette.paper200, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
    }
}
