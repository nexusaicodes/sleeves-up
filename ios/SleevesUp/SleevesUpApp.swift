import SwiftUI

@main
struct SleevesUpApp: App {

    @Environment(\.scenePhase) private var scenePhase
    @State private var store: SessionStore?
    @State private var openFailed = false

    var body: some Scene {
        WindowGroup {
            Group {
                if let store {
                    CheckInView(store: store)
                        .task(id: store.openSession?.id) { await closeAtBoundary(store) }
                } else if openFailed {
                    Text("Sleeves Up couldn't open its record.").padding()
                }
            }
            .task { open() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { try? store?.refresh() }
        }
    }

    /// Opens the record and closes a forgotten session before the first screen is drawn.
    private func open() {
        guard store == nil else { return }
        do {
            let opened = SessionStore(database: try RecordDatabase.openOnDisk())
            try opened.refresh()
            store = opened
        } catch {
            openFailed = true
        }
    }

    /// While the app is open over a session's midnight, closes it there rather than drawing a past
    /// day's session as running. A sleep stalls in the background; the refresh on return covers it.
    private func closeAtBoundary(_ store: SessionStore) async {
        guard let open = store.openSession else { return }
        let waitMs = store.dayBoundary(of: open) - Clock.nowMs(Date())
        if waitMs > 0 {
            try? await Task.sleep(for: .milliseconds(waitMs))
        }
        if !Task.isCancelled { try? store.refresh() }
    }
}
