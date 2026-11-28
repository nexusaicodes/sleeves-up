import SwiftUI
import WidgetKit

/// The app's widget extension: the home-screen widget and the open session's Live Activity.
@main
struct SleevesUpWidgets: WidgetBundle {
    var body: some Widget {
        CheckInWidget()
        SessionLiveActivity()
    }
}
