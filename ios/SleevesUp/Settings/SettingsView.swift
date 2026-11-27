import SwiftUI
import UIKit

/// The Settings tab: the Habits link, the app's language and About, and no state of its own. The
/// language row opens the app's page in iOS Settings, where iOS keeps the per-app language, so the
/// app stores no choice of its own. No rating row (no App Store listing).
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
                Section("Language") {
                    Button("App language") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                    .accessibilityHint("Opens outside the app")
                }
                Section("About") {
                    LabeledContent {
                        Text(String(app: "Version \(AppBuild.version) (build \(AppBuild.build))"))
                    } label: {
                        Text(verbatim: "Sleeves Up")
                    }
                    Text(verbatim: "Nexus AI Technology Labs").foregroundStyle(.secondary)
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
                notice = String(app: "No email app found. Address copied: \(Feedback.address)")
            }
        }
    }
}
