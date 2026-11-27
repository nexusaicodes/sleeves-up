import SwiftUI
import WidgetKit

/// The app's widget extension: today the open session's Live Activity alone.
@main
struct SleevesUpWidgets: WidgetBundle {
    var body: some Widget {
        SessionLiveActivity()
    }
}
