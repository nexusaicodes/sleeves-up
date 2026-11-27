import SwiftUI

extension View {
    /// A tab's top bar: the selected habit's name as the switcher, which opens the Habits screen.
    /// While a session runs it shows a lock and says why it will not open. Every tab carries it.
    /// Setting `open` opens the Habits screen from elsewhere, as iOS Settings' link does.
    func habitSwitcher(_ habits: HabitStore, open: Binding<Bool> = .constant(false)) -> some View {
        modifier(HabitSwitcher(habits: habits, open: open))
    }
}

private struct HabitSwitcher: ViewModifier {

    let habits: HabitStore
    @Binding var open: Bool

    @State private var showingHabits = false
    @State private var notice: String?

    func body(content: Content) -> some View {
        content
            .toolbar { ToolbarItem(placement: .principal) { switcher } }
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(isPresented: $showingHabits) { HabitsView(habits: habits) }
            .onChange(of: open, initial: true) { _, asked in
                guard asked else { return }
                open = false
                openHabits()
            }
            .safeAreaInset(edge: .bottom) {
                if let notice {
                    Text(notice)
                        .font(.footnote)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(.thinMaterial)
                        .task(id: notice) {
                            try? await Task.sleep(for: .seconds(4))
                            self.notice = nil
                        }
                }
            }
    }

    private func openHabits() {
        if habits.isLocked {
            notice = HabitText.switchLocked(habits.selected)
        } else {
            notice = nil
            showingHabits = true
        }
    }

    private var switcher: some View {
        Button(action: openHabits) {
            HStack(spacing: 6) {
                Text(habits.selected.map(HabitText.name) ?? "").font(.headline)
                Image(systemName: habits.isLocked ? "lock.fill" : "chevron.up.chevron.down").font(.caption)
            }
        }
        .accessibilityIdentifier("habitSwitcher")
        .accessibilityLabel("Habit: \(habits.selected.map(HabitText.name) ?? ""). Switch habit")
    }
}
