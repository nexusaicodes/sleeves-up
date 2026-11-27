import SwiftUI
import UIKit

/// The Settings tab: the Habits link and About, and no state of its own. iOS has no language row
/// yet (the app is English only until its Arabic arrives) and no rating row (no App Store listing).
struct SettingsView: View {

    let habits: HabitStore

    @Environment(\.openURL) private var openURL
    @State private var openHabits = false
    @State private var notice: String?

    static let privacyPolicy = URL(string: "https://nexusai.world/sleeves-up/privacy")!

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button("Habits") { openHabits = true }
                        .disabled(habits.isLocked)
                } footer: {
                    if habits.isLocked { Text(HabitText.switchLocked(habits.active.first { $0.id == habits.runningHabitId })) }
                }
                Section("About") {
                    LabeledContent("Sleeves Up", value: "Version \(AppBuild.version) (build \(AppBuild.build))")
                    Text("Nexus AI Technology Labs").foregroundStyle(.secondary)
                    NavigationLink("What this app collects") { PrivacyView() }
                    Button("Privacy policy") { openURL(Self.privacyPolicy) }
                        .accessibilityHint("Opens outside the app")
                    Button("Send feedback", action: sendFeedback)
                        .accessibilityHint("Opens outside the app")
                    NavigationLink("Licences") { LicensesView() }
                }
                if let notice {
                    Section { Text(notice).font(.footnote).foregroundStyle(.secondary) }
                }
            }
            .navigationTitle("Settings")
            .navigationDestination(isPresented: $openHabits) { HabitsView(habits: habits) }
        }
    }

    /// Opens the draft in a mail app, or with none, copies the address, as Android does.
    private func sendFeedback() {
        let draft = Feedback.draft(
            version: AppBuild.version, build: AppBuild.build,
            model: AppBuild.model, system: UIDevice.current.systemVersion
        )
        guard let url = Feedback.link(draft) else { return }
        openURL(url) { opened in
            if !opened {
                UIPasteboard.general.string = Feedback.address
                notice = "No email app found. Address copied: \(Feedback.address)"
            }
        }
    }
}
