import WidgetKit

/// Asks the home-screen widget to read the record again. The widget holds one entry and never
/// wakes on its own, so whatever changes what it reads asks here.
enum WidgetRedraw {
    static func ask() {
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetFace.kind)
    }
}
