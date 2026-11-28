import ActivityKit
import SwiftUI
import WidgetKit

/// The open session on the Lock Screen and in the Dynamic Island (iOS rule 4): the habit, a clock
/// counting the session up from check-in, and Check Out, which opens the gate in the app. Like
/// Android's timer notification it says one thing, that a session is open, and nothing ticks but
/// the clock. The clock stops at the session's own midnight, where the record closes it.
struct SessionLiveActivity: Widget {

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SessionActivityAttributes.self) { context in
            HStack(alignment: .center, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        MarkImage().frame(width: 18, height: 18).foregroundStyle(Palette.ink)
                        Text(SessionActivityText.title(context.state.habitName))
                            .font(.headline)
                            .lineLimit(2)
                    }
                    SessionClock(attributes: context.attributes)
                        .font(.title2.weight(.semibold).monospacedDigit())
                }
                .foregroundStyle(Palette.ink)
                Spacer()
                Link(destination: SessionActivityLink.checkOut) {
                    Text(SessionActivityText.checkOut)
                        .font(.callout.weight(.semibold))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Palette.ink, in: Capsule())
                        .foregroundStyle(Palette.paper)
                }
            }
            .padding()
            .activityBackgroundTint(Palette.paper)
            .activitySystemActionForegroundColor(Palette.ink)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    MarkImage().frame(width: 24, height: 24).foregroundStyle(Palette.nightInk)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    SessionClock(attributes: context.attributes)
                        .font(.title3.monospacedDigit())
                        .frame(maxWidth: 110)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Text(SessionActivityText.title(context.state.habitName)).lineLimit(1)
                        Spacer()
                        Link(SessionActivityText.checkOut, destination: SessionActivityLink.checkOut)
                            .font(.callout.weight(.semibold))
                            .foregroundStyle(Palette.nightInk)
                    }
                }
            } compactLeading: {
                MarkImage().frame(width: 16, height: 16).foregroundStyle(Palette.nightInk)
            } compactTrailing: {
                SessionClock(attributes: context.attributes)
                    .monospacedDigit()
                    .frame(maxWidth: 64)
            } minimal: {
                MarkImage().frame(width: 16, height: 16).foregroundStyle(Palette.nightInk)
            }
            .keylineTint(Palette.nightInk)
        }
    }
}

/// The session's clock, counting up from check-in and stopping at its own midnight, in Latin digits.
/// The brand mark as the generated template image, tinted by its caller.
private struct MarkImage: View {
    var body: some View {
        Image("Mark").renderingMode(.template).resizable().accessibilityHidden(true)
    }
}

private struct SessionClock: View {
    let attributes: SessionActivityAttributes

    var body: some View {
        Text(
            timerInterval: attributes.startedAt...max(attributes.startedAt, attributes.endsAt),
            countsDown: false
        )
        .multilineTextAlignment(.trailing)
        .environment(\.locale, AppLocale.current)
    }
}

/// The activity's copy, Android's timer notification strings in the app's language.
enum SessionActivityText {
    static func title(_ habitName: String?) -> String {
        habitName.map { String(app: "Checked In: \($0)") } ?? String(app: "Checked In")
    }

    static var checkOut: String { String(app: "Check Out") }
}
