import Shared
import SwiftUI

/// The Habits screen: one list for choosing, adding, renaming, timing, archiving and restoring.
/// Tapping a row shows that habit and returns once the write has landed. Rows show names and
/// reminder times only: a figure per habit would rank them.
struct HabitsView: View {

    let habits: HabitStore

    @Environment(\.dismiss) private var dismiss
    @Environment(ReminderSettings.self) private var settings
    @State private var editing: EditorTarget?
    @State private var notice: String?

    private enum EditorTarget: Identifiable {
        case new
        case rename(HabitRow)
        var id: String {
            switch self {
            case .new: return "new"
            case .rename(let habit): return "rename-\(habit.id)"
            }
        }
    }

    var body: some View {
        List {
            Section {
                ForEach(habits.active, id: \.id) { habit in
                    activeRow(habit)
                }
            }
            if !habits.archived.isEmpty {
                Section {
                    ForEach(habits.archived, id: \.id) { habit in
                        archivedRow(habit)
                    }
                } header: {
                    Text("Archived")
                } footer: {
                    Text("Archived habits keep their records and send no reminders.")
                }
            }
        }
        .navigationTitle("Habits")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editing = .new } label: { Label("New habit", systemImage: "plus") }
            }
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
        .sheet(item: $editing) { target in editor(for: target) }
    }

    private func activeRow(_ habit: HabitRow) -> some View {
        Button {
            select(habit)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(HabitText.name(habit)).foregroundStyle(.primary)
                    Text(ReminderText.summary(on: settings.isOn(habit.uuid), minute: settings.minute(habit.uuid)))
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Spacer()
                if habit.id == habits.selected?.id {
                    Text("Showing").font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityHint("Show this habit")
        .swipeActions {
            Button("Archive") { archive(habit) }
            Button("Edit") { editing = .rename(habit) }
        }
        .contextMenu {
            Button("Edit") { editing = .rename(habit) }
            Button("Archive") { archive(habit) }
        }
    }

    private func archivedRow(_ habit: HabitRow) -> some View {
        HStack {
            Text(HabitText.name(habit)).foregroundStyle(.secondary)
            Spacer()
            Button("Restore") { restore(habit) }.buttonStyle(.borderless)
        }
    }

    @ViewBuilder
    private func editor(for target: EditorTarget) -> some View {
        switch target {
        case .new:
            HabitEditorView(
                title: "New Habit",
                problemFor: { HabitRules.shared.nameProblem(name: $0, activeHabits: habits.active, forId: nil) },
                onSave: { try? habits.create(name: $0) },
                onSaved: { on, minute in
                    // Created last, so it is the newest active habit.
                    guard let created = habits.active.max(by: { $0.id < $1.id }) else { return }
                    settings.setMinute(minute, for: created.uuid)
                    settings.setOn(on, for: created.uuid)
                }
            )
        case .rename(let habit):
            HabitEditorView(
                title: "Edit Habit",
                initialName: habit.name ?? "",
                reminderOn: settings.isOn(habit.uuid),
                reminderMinute: settings.minute(habit.uuid),
                problemFor: {
                    HabitRules.shared.nameProblem(name: $0, activeHabits: habits.active, forId: KotlinLong(value: habit.id))
                },
                onSave: { try? habits.rename(habit.id, to: $0) },
                onSaved: { on, minute in
                    settings.setMinute(minute, for: habit.uuid)
                    settings.setOn(on, for: habit.uuid)
                }
            )
        }
    }

    private func select(_ habit: HabitRow) {
        do {
            if try habits.select(habit.id) {
                dismiss()
            } else {
                notice = HabitText.switchLocked(habits.active.first { $0.id == habits.runningHabitId })
            }
        } catch {
            notice = HabitText.saveFailed
        }
    }

    private func archive(_ habit: HabitRow) {
        do {
            let result = try habits.archive(habit.id)
            notice = HabitText.archiveRefused(result, habit) ?? HabitText.archived(habit)
        } catch {
            notice = HabitText.saveFailed
        }
    }

    private func restore(_ habit: HabitRow) {
        do {
            switch try habits.restore(habit.id) {
            case .saved: notice = nil
            case .rejected: notice = HabitText.restoreRefused(habit)
            case .missing: notice = HabitText.missing
            }
        } catch {
            notice = HabitText.saveFailed
        }
    }
}
