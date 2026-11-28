import Foundation
import XCTest
@testable import SleevesUp

/// The iOS reminder copy against the Android app's: every key in `NudgeCatalog.kt`, under the same
/// voice and with the same English text from `strings.xml` and Arabic from `values-ar/strings.xml`
/// (the Arabic read from the app's `ar.lproj`), and nothing iOS holds that Android does not. Read
/// from the repository through this file's path, so it runs on a simulator only.
final class NudgeCatalogTests: XCTestCase {

    private var repository: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    }

    private func androidStrings(_ folder: String = "values") throws -> [String: String] {
        let xml = try String(
            contentsOf: repository.appendingPathComponent("android/src/main/res/\(folder)/strings.xml"), encoding: .utf8
        )
        let pattern = try NSRegularExpression(pattern: #"<string name="([^"]+)"[^>]*>(.*?)</string>"#, options: [.dotMatchesLineSeparators])
        var strings: [String: String] = [:]
        for match in pattern.matches(in: xml, range: NSRange(xml.startIndex..., in: xml)) {
            let name = String(xml[Range(match.range(at: 1), in: xml)!])
            var value = String(xml[Range(match.range(at: 2), in: xml)!]).trimmingCharacters(in: .whitespaces)
            if value.count >= 2, value.hasPrefix("\""), value.hasSuffix("\"") { value = String(value.dropFirst().dropLast()) }
            strings[name] = value.replacingOccurrences(of: "\\'", with: "'").replacingOccurrences(of: "\\\"", with: "\"")
        }
        return strings
    }

    /// The app's Arabic translations, as iOS reads them with the app in Arabic.
    private func arabicBundle() throws -> Bundle {
        try XCTUnwrap(Bundle.main.path(forResource: "ar", ofType: "lproj").flatMap(Bundle.init(path:)))
    }

    private func titleVariants() throws -> [[String]] {
        try matches(#"TitleVariant\(\s*"([^"]+)",\s*R\.string\.(\w+),\s*R\.string\.(\w+),?\s*\)"#, in: try androidCatalog())
    }

    private func bodyVariants() throws -> [[String]] {
        try matches(#"BodyVariant\(\s*"([^"]+)",\s*R\.string\.(\w+)\s*\)"#, in: try androidCatalog())
    }

    private func androidCatalog() throws -> String {
        try String(
            contentsOf: repository.appendingPathComponent(
                "android/src/main/java/com/checkin/app/notify/nudge/NudgeCatalog.kt"
            ),
            encoding: .utf8
        )
    }

    private func matches(_ pattern: String, in text: String) throws -> [[String]] {
        let regex = try NSRegularExpression(pattern: pattern)
        return regex.matches(in: text, range: NSRange(text.startIndex..., in: text)).map { match in
            (1..<match.numberOfRanges).map { String(text[Range(match.range(at: $0), in: text)!]) }
        }
    }

    func testTitlesMatchAndroidKeyForKey() throws {
        let strings = try androidStrings()
        let android = try titleVariants()
        XCTAssertFalse(android.isEmpty)
        let ios = NudgeCatalog.titles.values.joined()
        XCTAssertEqual(Set(ios.map(\.key)), Set(android.map { $0[0] }))
        for entry in android {
            let title = try XCTUnwrap(ios.first { $0.key == entry[0] })
            XCTAssertEqual(title.named, strings[entry[1]], entry[0])
            XCTAssertEqual(title.unnamed, strings[entry[2]], entry[0])
            XCTAssertTrue(NudgeCatalog.titles[String(entry[0].split(separator: ".")[0])]?.contains { $0.key == entry[0] } == true)
        }
    }

    func testBodiesMatchAndroidKeyForKey() throws {
        let strings = try androidStrings()
        let android = try bodyVariants()
        XCTAssertFalse(android.isEmpty)
        let ios = NudgeCatalog.bodies.values.joined()
        XCTAssertEqual(Set(ios.map(\.key)), Set(android.map { $0[0] }))
        for entry in android {
            XCTAssertEqual(ios.first { $0.key == entry[0] }?.text, strings[entry[1]], entry[0])
        }
    }

    func testArabicTitlesMatchAndroidKeyForKey() throws {
        let arabic = try androidStrings("values-ar")
        let bundle = try arabicBundle()
        let android = try titleVariants()
        XCTAssertFalse(android.isEmpty)
        for entry in android {
            let named = try XCTUnwrap(arabic[entry[1]], entry[1])
            XCTAssertTrue(named.contains("%1$s"), entry[1])
            XCTAssertEqual(
                NudgeCatalog.title(entry[0], habitName: "الجري", in: bundle),
                named.replacingOccurrences(of: "%1$s", with: "الجري"),
                entry[0]
            )
            XCTAssertEqual(NudgeCatalog.title(entry[0], habitName: nil, in: bundle), arabic[entry[2]], entry[0])
        }
    }

    func testArabicBodiesMatchAndroidKeyForKey() throws {
        let arabic = try androidStrings("values-ar")
        let bundle = try arabicBundle()
        let android = try bodyVariants()
        XCTAssertFalse(android.isEmpty)
        for entry in android {
            let body = try XCTUnwrap(arabic[entry[1]], entry[1])
            XCTAssertEqual(NudgeCatalog.body(entry[0], in: bundle), body, entry[0])
        }
    }
}
