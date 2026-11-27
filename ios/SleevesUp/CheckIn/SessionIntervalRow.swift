import SwiftUI

/// One session as Check-In's today list and History's selected day both draw it: the clock range,
/// then the duration in a column of its own, or "ongoing" for the open session. Nothing in it ticks
/// and nothing grades the session.
struct SessionIntervalRow: View {

    let session: SessionRow

    var body: some View {
        HStack {
            Text(SessionText.range(session))
            Spacer()
            Text(session.duration.map(SessionText.short) ?? String(app: "ongoing"))
                .monospacedDigit()
        }
        .font(.callout)
        .accessibilityElement(children: .combine)
    }
}
