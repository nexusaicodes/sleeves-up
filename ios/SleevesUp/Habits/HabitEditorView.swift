import Shared
import SwiftUI

/// The name field both the editor and the naming step use, with the rule's problem shown only once
/// the name has been edited or a save attempted, so an empty field opens without an error. A
/// refusal from the write is shown until the name is edited.
struct HabitNameField: View {

    @Binding var name: String
    @Binding var showProblem: Bool
    @Binding var refused: NameProblem?
    /// The shared rule, asked of the name as typed.
    let problemFor: (String) -> NameProblem?

    var problem: NameProblem? { refused ?? problemFor(name) }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            TextField("Name", text: $name)
                .textInputAutocapitalization(.sentences)
                .onChange(of: name) {
                    showProblem = true
                    refused = nil
                }
            if showProblem, let problem {
                Text(HabitText.problem(problem)).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}

/// New Habit and Edit Habit, as a sheet: the name, and the habit's reminder. `onSave` performs the
/// name write and returns what it did; the reminder is applied once the name has saved.
struct HabitEditorView: View {

    let title: String
    let problemFor: (String) -> NameProblem?
    let onSave: (String) -> HabitStore.Edit?
    let onSaved: (_ reminderOn: Bool, _ reminderMinute: Int) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var showProblem = false
    @State private var refused: NameProblem?
    @State private var failure: String?
    @State private var reminderOn: Bool
    @State private var reminderMinute: Int

    init(
        title: String,
        initialName: String = "",
        reminderOn: Bool = true,
        reminderMinute: Int = Int(NudgeSchedule.shared.DEFAULT_MINUTE),
        problemFor: @escaping (String) -> NameProblem?,
        onSave: @escaping (String) -> HabitStore.Edit?,
        onSaved: @escaping (_ reminderOn: Bool, _ reminderMinute: Int) -> Void
    ) {
        self.title = title
        self.problemFor = problemFor
        self.onSave = onSave
        self.onSaved = onSaved
        _name = State(initialValue: initialName)
        _reminderOn = State(initialValue: reminderOn)
        _reminderMinute = State(initialValue: reminderMinute)
    }

    var body: some View {
        NavigationStack {
            Form {
                HabitNameField(name: $name, showProblem: $showProblem, refused: $refused, problemFor: problemFor)
                    .onSubmit(save)
                if let failure { Text(failure).font(.footnote).foregroundStyle(.secondary) }
                Section {
                    ReminderFields(on: $reminderOn, minute: $reminderMinute)
                } footer: {
                    Text("\(ReminderText.rangeNote). Once a day at most, on days you haven't checked in yet.")
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
            }
        }
    }

    private func save() {
        showProblem = true
        guard refused == nil, problemFor(name) == nil else { return }
        switch onSave(name) {
        case .saved:
            onSaved(reminderOn, reminderMinute)
            dismiss()
        case .rejected(let problem): refused = problem
        case .missing: failure = HabitText.missing
        case nil: failure = HabitText.saveFailed
        }
    }
}

/// The first-run naming step: the seed habit is created unnamed, and this asks for its name, and the
/// time its reminder comes around, before anything else is shown.
struct NameHabitView: View {

    let habits: HabitStore
    let habit: HabitRow
    let settings: ReminderSettings

    @State private var name = ""
    @State private var reminderMinute = Int(NudgeSchedule.shared.DEFAULT_MINUTE)
    @State private var showProblem = false
    @State private var refused: NameProblem?
    @State private var failure: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            Text("What are you showing up for?").font(.title.bold())
            HabitNameField(name: $name, showProblem: $showProblem, refused: $refused) { typed in
                HabitRules.shared.nameProblem(name: typed, activeHabits: habits.active, forId: KotlinLong(value: habit.id))
            }
            .textFieldStyle(.roundedBorder)
            .onSubmit(save)
            Text("Add more in the app when you want.").font(.footnote).foregroundStyle(.secondary)
            ReminderFields(on: .constant(true), minute: $reminderMinute, showsToggle: false)
            Text("A reminder comes around then on days you haven't checked in yet.")
                .font(.footnote).foregroundStyle(.secondary)
            if let failure { Text(failure).font(.footnote).foregroundStyle(.secondary) }
            Spacer()
            Button("Continue", action: save)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
        }
        .padding()
    }

    private func save() {
        showProblem = true
        let typed = HabitRules.shared.nameProblem(
            name: name, activeHabits: habits.active, forId: KotlinLong(value: habit.id)
        )
        guard refused == nil, typed == nil else { return }
        do {
            // The time is stored first: the rename ends the naming step, and the replan it causes
            // must already read the chosen time.
            settings.setMinute(reminderMinute, for: habit.uuid)
            switch try habits.rename(habit.id, to: name) {
            case .saved: failure = nil
            case .rejected(let problem): refused = problem
            case .missing: failure = HabitText.missing
            }
        } catch {
            failure = HabitText.saveFailed
        }
    }
}
