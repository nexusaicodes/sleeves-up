import Shared
import SwiftUI

/// The widget's "Check in to…" picker, opened when there is a habit to choose: the active habits'
/// names and nothing else, no times, adding or editing. A pick goes on to the gate, routed as the
/// habit's reminder would be; Not now leaves with nothing written.
struct HabitPickerView: View {

    let habits: [HabitRow]
    let onPick: (HabitRow) -> Void
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            List(habits, id: \.id) { habit in
                Button(HabitText.name(habit)) { onPick(habit) }
                    .foregroundStyle(.primary)
            }
            .navigationTitle("Check in to…")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Not now", action: onDismiss)
                }
            }
        }
    }
}
