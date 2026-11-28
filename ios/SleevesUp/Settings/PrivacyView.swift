import SwiftUI

/// What the app does not do. Every line is a claim about a mechanism, each naming what makes it
/// true, and the last says how to check, since a negative nobody can check is worth nothing.
/// Re-check the network line before a release: a new dependency can make it false.
struct PrivacyView: View {

    private var claims: [(title: String, body: String)] {
        [
            (String(app: "No network activity"), String(app: "The app makes no network requests and contains nothing that does, so it cannot upload anything.")),
            (String(app: "No account, no server"), String(app: "There is nothing to sign in to. Your sessions live in a database on this iPhone. If iCloud Backup is on, your iPhone's backup in your own iCloud account includes it.")),
            (String(app: "No photo is taken"), String(app: "Your camera reports whether a face is in frame. The app reads that and nothing else.")),
            (String(app: "No analytics, no ads"), String(app: "Nothing here measures how you use the app, and nothing asks anyone else anything.")),
        ]
    }

    var body: some View {
        List {
            Section("What This App Does Not Do") {
                ForEach(claims, id: \.title) { claim in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(claim.title).font(AppFont.cardTitle)
                        Text(claim.body).foregroundStyle(Palette.inkMuted)
                    }
                    .padding(.vertical, 4)
                }
            }
            .rowOnPaper()
            Section("How to Check") {
                Text("You don't have to take this on trust. Turn on App Privacy Report under Settings, Privacy & Security: it lists every domain each app contacts. This app contacts none.")
            }
            .rowOnPaper()
        }
        .screenBackground()
        .navigationTitle("What this app collects")
        .navigationBarTitleDisplayMode(.inline)
    }
}
