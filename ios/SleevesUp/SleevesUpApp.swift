import SwiftUI

@main
struct SleevesUpApp: App {

    @Environment(\.scenePhase) private var scenePhase
    @State private var store: SessionStore?
    @State private var habits: HabitStore?
    @State private var openFailed = false

    var body: some Scene {
        WindowGroup {
            Group {
                if let store, let habits {
                    Group {
                        if let unnamed = habits.needingName {
                            NameHabitView(habits: habits, habit: unnamed)
                        } else {
                            CheckInView(store: store, habits: habits)
                        }
                    }
                    .task(id: store.openSession?.id) { await closeAtBoundary(store, habits) }
                    .onChange(of: habits.selected?.id) { _, id in
                        if let id { try? store.show(habitId: id) }
                    }
                } else if openFailed {
                    Text("Sleeves Up couldn't open its record.").padding()
                }
            }
            .task { open() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, let store, let habits { try? refresh(store, habits) }
        }
    }

    /// Opens the record and closes a forgotten session before the first screen is drawn.
    private func open() {
        guard store == nil else { return }
        do {
            let database = try RecordDatabase.openOnDisk()
            let openedHabits = HabitStore(database: database)
            let openedStore = SessionStore(database: database)
            try refresh(openedStore, openedHabits)
            habits = openedHabits
            store = openedStore
        } catch {
            openFailed = true
        }
    }

    /// Closes a forgotten session, then reads the habits (whose lock that close may have lifted)
    /// and shows the selected one.
    private func refresh(_ store: SessionStore, _ habits: HabitStore) throws {
        try store.refresh()
        try habits.reload()
        if let id = habits.selected?.id { try store.show(habitId: id) }
    }

    /// While the app is open over a session's midnight, closes it there rather than drawing a past
    /// day's session as running. A sleep stalls in the background; the refresh on return covers it.
    private func closeAtBoundary(_ store: SessionStore, _ habits: HabitStore) async {
        guard let open = store.openSession else { return }
        let waitMs = store.dayBoundary(of: open) - Clock.nowMs(Date())
        if waitMs > 0 {
            try? await Task.sleep(for: .milliseconds(waitMs))
        }
        if !Task.isCancelled { try? refresh(store, habits) }
    }
}
