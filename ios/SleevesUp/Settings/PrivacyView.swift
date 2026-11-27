import SwiftUI

/// What the app does not do. Every line is a claim about a mechanism, each naming what makes it
/// true, and the last says how to check, since a negative nobody can check is worth nothing.
/// Re-check the network line before a release: a new dependency can make it false.
struct PrivacyView: View {

    private let claims: [(title: String, body: String)] = [
        ("No network activity", "The app makes no network requests and contains nothing that does, so it cannot upload anything."),
        ("No account, no server", "There is nothing to sign in to. Your sessions live in a database on this iPhone. If iCloud Backup is on, your iPhone's backup in your own iCloud account includes it."),
        ("No photo is taken", "Your camera reports whether a face is in frame. The app reads that and nothing else."),
        ("No analytics, no ads", "Nothing here measures how you use the app, and nothing asks anyone else anything."),
    ]

    var body: some View {
        List {
            Section("What This App Does Not Do") {
                ForEach(claims, id: \.title) { claim in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(claim.title).font(.headline)
                        Text(claim.body).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
            Section("How to Check") {
                Text("You don't have to take this on trust. Turn on App Privacy Report under Settings, Privacy & Security: it lists every domain each app contacts. This app contacts none.")
            }
        }
        .navigationTitle("What this app collects")
        .navigationBarTitleDisplayMode(.inline)
    }
}
