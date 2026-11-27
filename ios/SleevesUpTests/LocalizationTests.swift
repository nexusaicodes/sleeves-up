import Foundation
import XCTest
@testable import SleevesUp

/// The Arabic translation as iOS reads it from the app's `ar.lproj`: Android's unit letters and
/// plural forms, in Latin digits, and an Arabic value for every key in the string catalog.
final class LocalizationTests: XCTestCase {

    private func arabicBundle() throws -> Bundle {
        try XCTUnwrap(Bundle.main.path(forResource: "ar", ofType: "lproj").flatMap(Bundle.init(path:)))
    }

    private var arabicLatin: Locale { Locale(identifier: "ar@numbers=latn") }

    func testAppLocaleWritesLatinDigits() {
        XCTAssertEqual(AppLocale.current.numberingSystem, Locale.NumberingSystem("latn"))
    }

    func testAppLocaleKeepsTheDevicesRegion() {
        XCTAssertEqual(AppLocale.current.region, Locale.current.region)
    }

    func testArabicDurationsUseAndroidsUnitLetters() throws {
        let bundle = try arabicBundle()
        let h = 2, m = 5, s = 3
        XCTAssertEqual(String(localized: "\(h)h \(m)m", bundle: bundle, locale: arabicLatin), "2 س 5 د")
        XCTAssertEqual(String(localized: "\(m)m", bundle: bundle, locale: arabicLatin), "5 د")
        XCTAssertEqual(String(localized: "\(m)m \(s)s", bundle: bundle, locale: arabicLatin), "5 د 3 ث")
        XCTAssertEqual(String(localized: "\(h)h \(m)m \(s)s", bundle: bundle, locale: arabicLatin), "2 س 5 د 3 ث")
    }

    func testArabicCountsTakeEveryPluralForm() throws {
        let bundle = try arabicBundle()
        let expected = [0: "0 يوم", 1: "يوم واحد", 2: "يومان", 3: "3 أيام", 11: "11 يومًا", 100: "100 يوم"]
        for (count, text) in expected {
            XCTAssertEqual(String(localized: "\(count) days", bundle: bundle, locale: arabicLatin), text, "\(count)")
        }
        let one = 1, many = 12
        XCTAssertEqual(String(localized: "\(one) sessions", bundle: bundle, locale: arabicLatin), "جلسة واحدة")
        XCTAssertEqual(String(localized: "\(many) sessions", bundle: bundle, locale: arabicLatin), "12 جلسة")
    }

    /// Read from the repository through this file's path, so it runs on a simulator only.
    func testEveryCatalogKeyHasArabic() throws {
        let catalog = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Common/Localizable.xcstrings")
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: catalog)) as? [String: Any])
        let strings = try XCTUnwrap(json["strings"] as? [String: [String: Any]])
        XCTAssertFalse(strings.isEmpty)
        for (key, entry) in strings {
            let localizations = entry["localizations"] as? [String: Any]
            XCTAssertNotNil(localizations?["ar"], key)
        }
    }
}
