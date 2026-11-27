import Shared
import SwiftUI

@main
struct SleevesUpApp: App {

    @Environment(\.scenePhase) private var scenePhase
    @State private var store: SessionStore?
    @State private var habits: HabitStore?
    @State private var history: HistoryStore?
    @State private var openFailed = false

    var body: some Scene {
        WindowGroup {
            Group {
                if let store, let habits, let history {
                    Group {
                        if let unnamed = habits.needingName {
                            NameHabitView(habits: habits, habit: unnamed)
                        } else {
                            TabView {
                                CheckInView(store: store, habits: habits)
                                    .tabItem { Label("Check In", systemImage: "checkmark.circle") }
                                HistoryView(history: history, habits: habits)
                                    .tabItem { Label("History", systemImage: "calendar") }
                            }
                        }
                    }
                    .task(id: store.openSession?.id) { await closeAtBoundary(store, habits, history) }
                    .task { await followMidnight(store, habits, history) }
                    .onChange(of: habits.selected?.id) { _, id in
                        if let id {
                            try? store.show(habitId: id)
                            try? history.show(habitId: id)
                        }
                    }
                } else if openFailed {
                    Text("Sleeves Up couldn't open its record.").padding()
                }
            }
            .task { open() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, let store, let habits, let history { try? refresh(store, habits, history) }
        }
    }

    /// Opens the record and closes a forgotten session before the first screen is drawn.
    private func open() {
        guard store == nil else { return }
        do {
            let database = try RecordDatabase.openOnDisk()
            let openedHabits = HabitStore(database: database)
            let openedStore = SessionStore(database: database)
            let openedHistory = HistoryStore(database: database)
            try refresh(openedStore, openedHabits, openedHistory)
            habits = openedHabits
            store = openedStore
            history = openedHistory
        } catch {
            openFailed = true
        }
    }

    /// Closes a forgotten session, then reads the habits (whose lock that close may have lifted)
    /// and shows the selected one on every tab.
    private func refresh(_ store: SessionStore, _ habits: HabitStore, _ history: HistoryStore) throws {
        try store.refresh()
        try habits.reload()
        if let id = habits.selected?.id {
            try store.show(habitId: id)
            try history.show(habitId: id)
        }
        try history.reload()
    }

    /// While the app is open over a session's midnight, closes it there rather than drawing a past
    /// day's session as running. A sleep stalls in the background; the refresh on return covers it.
    private func closeAtBoundary(_ store: SessionStore, _ habits: HabitStore, _ history: HistoryStore) async {
        guard let open = store.openSession else { return }
        let waitMs = store.dayBoundary(of: open) - Clock.nowMs(Date())
        if waitMs > 0 {
            try? await Task.sleep(for: .milliseconds(waitMs))
        }
        if !Task.isCancelled { try? refresh(store, habits, history) }
    }

    /// While the app is open, re-reads every tab when the day changes, so today's list and the
    /// calendar's today roll over at midnight. `DayClock` caps each wait, since a sleep stalls in the
    /// background and the zone can change; the refresh on return covers the rest.
    private func followMidnight(_ store: SessionStore, _ habits: HabitStore, _ history: HistoryStore) async {
        var day = Clock.dateKey(Date(), in: .current)
        while !Task.isCancelled {
            let waitMs = DayClock.shared.millisUntilNextMidnight(
                nowMs: Clock.nowMs(Date()), zone: Clock.kotlinZone(.current)
            )
            try? await Task.sleep(for: .milliseconds(max(waitMs, 1)))
            let next = Clock.dateKey(Date(), in: .current)
            if next != day, !Task.isCancelled {
                day = next
                try? refresh(store, habits, history)
            }
        }
    }
}
