import SwiftUI

/// One upstream project the app ships, grouped by project rather than by artifact.
struct OpenSourceLibrary: Identifiable {
    enum License: String {
        case apache2 = "Apache License 2.0"
        case mit = "MIT License"

        /// The bundled text, which both licences oblige a shipped copy of.
        var resource: String {
            switch self {
            case .apache2: return "apache_2_0"
            case .mit: return "grdb_mit"
            }
        }
    }

    let name: String
    let copyright: String
    let license: License
    let note: String?
    var id: String { name }

    /// Everything linked into the app: GRDB through Swift Package Manager, and the shared Kotlin
    /// code's runtime dependencies (`:shared`'s iOS klibraries).
    static let all = [
        OpenSourceLibrary(name: "GRDB.swift", copyright: "Copyright (C) 2015-2025 Gwendal Roué", license: .mit, note: "The SQLite database the record is kept in."),
        OpenSourceLibrary(name: "Kotlin Standard Library", copyright: "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors", license: .apache2, note: "The runtime of the code shared with the Android app."),
        OpenSourceLibrary(name: "kotlinx-datetime", copyright: "Copyright JetBrains s.r.o. and contributors", license: .apache2, note: "Dates and time zones in the shared code."),
        OpenSourceLibrary(name: "kotlinx.serialization", copyright: "Copyright JetBrains s.r.o. and contributors", license: .apache2, note: "Its core, which kotlinx-datetime depends on."),
    ]
}

/// The Licences screen: each project with its copyright and licence, and the licence texts.
struct LicensesView: View {

    var body: some View {
        List {
            Section {
                Text("Sleeves Up is built on these projects. Grouped by upstream project rather than by artifact.")
                    .foregroundStyle(.secondary)
            }
            ForEach(OpenSourceLibrary.all) { library in
                VStack(alignment: .leading, spacing: 4) {
                    Text(library.name).font(.headline)
                    Text(library.copyright).font(.footnote)
                    Text(library.license.rawValue).font(.footnote).foregroundStyle(.secondary)
                    if let note = library.note { Text(note).font(.footnote).foregroundStyle(.secondary) }
                }
                .padding(.vertical, 4)
            }
            Section("Licence Texts") {
                ForEach([OpenSourceLibrary.License.apache2, .mit], id: \.self) { license in
                    NavigationLink(license == .mit ? "MIT License (GRDB.swift)" : license.rawValue) {
                        LicenseTextView(license: license)
                    }
                }
            }
        }
        .navigationTitle("Licences")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LicenseTextView: View {
    let license: OpenSourceLibrary.License

    var body: some View {
        ScrollView {
            Text(Self.text(license))
                .font(.footnote.monospaced())
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle(license.rawValue)
        .navigationBarTitleDisplayMode(.inline)
    }

    static func text(_ license: OpenSourceLibrary.License) -> String {
        Bundle.main.url(forResource: license.resource, withExtension: "txt")
            .flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
    }
}
