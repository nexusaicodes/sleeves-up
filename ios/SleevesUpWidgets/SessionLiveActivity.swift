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
                    Text(SessionActivityText.title(context.state.habitName))
                        .font(.headline)
                        .lineLimit(2)
                    SessionClock(attributes: context.attributes)
                        .font(.title2.monospacedDigit())
                }
                Spacer()
                Link(destination: SessionActivityLink.checkOut) {
                    Text(SessionActivityText.checkOut)
                        .font(.callout.weight(.semibold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(.tint, in: Capsule())
                        .foregroundStyle(.white)
                }
            }
            .padding()
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "square.grid.3x3.fill")
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
                    }
                }
            } compactLeading: {
                Image(systemName: "square.grid.3x3.fill")
            } compactTrailing: {
                SessionClock(attributes: context.attributes)
                    .monospacedDigit()
                    .frame(maxWidth: 64)
            } minimal: {
                Image(systemName: "square.grid.3x3.fill")
            }
        }
    }
}

/// The session's clock, counting up from check-in and stopping at its own midnight.
private struct SessionClock: View {
    let attributes: SessionActivityAttributes

    var body: some View {
        Text(
            timerInterval: attributes.startedAt...max(attributes.startedAt, attributes.endsAt),
            countsDown: false
        )
        .multilineTextAlignment(.trailing)
    }
}

/// The activity's copy, Android's timer notification strings.
enum SessionActivityText {
    static func title(_ habitName: String?) -> String {
        habitName.map { "Checked In: \($0)" } ?? "Checked In"
    }

    static let checkOut = "Check Out"
}
