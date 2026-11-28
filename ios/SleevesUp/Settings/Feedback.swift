import Foundation

/// The feedback email: a draft the user reads before sending, never a request the app makes. The
/// subject and footer stay English, as on Android, since they are read by the developer.
enum Feedback {

    static let address = "saksham@nexusai.world"

    struct Draft: Equatable {
        let subject: String
        let body: String
    }

    static func draft(version: String, build: String, model: String, system: String) -> Draft {
        Draft(
            subject: "Sleeves Up feedback (\(version))",
            // Leading blank lines put the cursor above the footer.
            body: "\n\n---\n"
                + "These lines help me reproduce problems. Delete them if you'd rather not share.\n"
                + "App: Sleeves Up \(version) (\(build))\n"
                + "Device: \(model)\n"
                + "iOS: \(system)\n"
        )
    }

    /// The `mailto:` link opening the draft in a mail app.
    static func link(_ draft: Draft) -> URL? {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = address
        components.queryItems = [
            URLQueryItem(name: "subject", value: draft.subject),
            URLQueryItem(name: "body", value: draft.body),
        ]
        return components.url
    }
}

/// This build and device, as the feedback footer and About state them.
enum AppBuild {
    static var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "" }
    static var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "" }

    /// The hardware model identifier ("iPhone15,4"), which names the exact device.
    static var model: String {
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { bytes in
            String(decoding: bytes.prefix { $0 != 0 }, as: UTF8.self)
        }
    }
}
