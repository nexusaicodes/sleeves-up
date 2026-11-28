import SwiftUI

/// One upstream project the app ships, grouped by project rather than by artifact.
struct OpenSourceLibrary: Identifiable {
    enum License: String {
        case apache2 = "Apache License 2.0"
        case mit = "MIT License"
        case ofl = "SIL Open Font License 1.1"

        /// The bundled text, which both licences oblige a shipped copy of.
        var resource: String {
            switch self {
            case .apache2: return "apache_2_0"
            case .mit: return "grdb_mit"
            case .ofl: return "ofl_1_1"
            }
        }
    }

    let name: String
    let copyright: String
    let license: License
    let note: String?
    var id: String { name }

    /// Everything linked into the app: GRDB through Swift Package Manager, the shared Kotlin code's
    /// runtime dependencies (`:shared`'s iOS klibraries), and the three typefaces, which Android ships
    /// too. Each font's copyright is the string in its own name table. Names, copyrights and licences stay
    /// English, as Android's do; only each note is in the app's language.
    static var all: [OpenSourceLibrary] {
        [
            OpenSourceLibrary(name: "GRDB.swift", copyright: "Copyright (C) 2015-2025 Gwendal Roué", license: .mit, note: String(app: "The SQLite database the record is kept in.")),
            OpenSourceLibrary(name: "Kotlin Standard Library", copyright: "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors", license: .apache2, note: String(app: "The runtime of the code shared with the Android app.")),
            OpenSourceLibrary(name: "kotlinx-datetime", copyright: "Copyright JetBrains s.r.o. and contributors", license: .apache2, note: String(app: "Dates and time zones in the shared code.")),
            OpenSourceLibrary(name: "kotlinx.serialization", copyright: "Copyright JetBrains s.r.o. and contributors", license: .apache2, note: String(app: "Its core, which kotlinx-datetime depends on.")),
            OpenSourceLibrary(name: "Outfit", copyright: "Copyright © 2021 The Outfit Project Authors", license: .ofl, note: String(app: "Display, headline and title sizes.")),
            OpenSourceLibrary(name: "Manrope", copyright: "Copyright © 2019 The Manrope Project Authors", license: .ofl, note: String(app: "Body and label sizes.")),
            OpenSourceLibrary(name: "IBM Plex Sans Arabic", copyright: "Copyright © 2019 IBM Corp.", license: .ofl, note: String(app: "All text when the app is in Arabic.")),
        ]
    }
}

/// The Licences screen: each project with its copyright and licence, and the licence texts.
struct LicensesView: View {

    var body: some View {
        List {
            Section {
                Text("Sleeves Up is built on these projects. Grouped by upstream project rather than by artifact.")
                    .foregroundStyle(Palette.inkMuted)
            }
            .rowOnPaper()
            ForEach(OpenSourceLibrary.all) { library in
                VStack(alignment: .leading, spacing: 4) {
                    Text(library.name).font(AppFont.cardTitle)
                    Text(library.copyright).font(AppFont.bodySmall)
                    Text(library.license.rawValue).font(AppFont.bodySmall).foregroundStyle(Palette.inkMuted)
                    if let note = library.note { Text(note).font(AppFont.bodySmall).foregroundStyle(Palette.inkMuted) }
                }
                .padding(.vertical, 4)
                .rowOnPaper()
            }
            Section("Licence Texts") {
                ForEach([OpenSourceLibrary.License.apache2, .mit, .ofl], id: \.self) { license in
                    NavigationLink(license == .mit ? "MIT License (GRDB.swift)" : license.rawValue) {
                        LicenseTextView(license: license)
                    }
                }
            }
            .rowOnPaper()
        }
        .screenBackground()
        .navigationTitle("Licences")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LicenseTextView: View {
    let license: OpenSourceLibrary.License

    var body: some View {
        ScrollView {
            Text(Self.text(license))
                .font(AppFont.bodySmall)
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Palette.paper.ignoresSafeArea())
        .navigationTitle(license.rawValue)
        .navigationBarTitleDisplayMode(.inline)
    }

    static func text(_ license: OpenSourceLibrary.License) -> String {
        Bundle.main.url(forResource: license.resource, withExtension: "txt")
            .flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
    }
}
