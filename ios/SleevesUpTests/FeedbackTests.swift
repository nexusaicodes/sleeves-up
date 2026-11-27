import XCTest
@testable import SleevesUp

/// The feedback email's draft and link, and the Licences screen's bundled texts.
final class FeedbackTests: XCTestCase {

    func testTheDraftNamesTheBuildAndDeviceBelowTheCursor() {
        let draft = Feedback.draft(version: "0.1", build: "1", model: "iPhone15,4", system: "27.2.1")
        XCTAssertEqual(draft.subject, "Sleeves Up feedback (0.1)")
        XCTAssertTrue(draft.body.hasPrefix("\n\n---\n"))
        XCTAssertTrue(draft.body.contains("App: Sleeves Up 0.1 (1)\n"))
        XCTAssertTrue(draft.body.contains("Device: iPhone15,4\n"))
        XCTAssertTrue(draft.body.contains("iOS: 27.2.1\n"))
    }

    func testTheLinkCarriesTheDraftToTheAddress() throws {
        let draft = Feedback.draft(version: "0.1", build: "1", model: "iPhone15,4", system: "27.2.1")
        let url = try XCTUnwrap(Feedback.link(draft))
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.scheme, "mailto")
        XCTAssertEqual(components.path, Feedback.address)
        XCTAssertEqual(components.queryItems?.first { $0.name == "body" }?.value, draft.body)
        XCTAssertEqual(components.queryItems?.first { $0.name == "subject" }?.value, draft.subject)
    }

    func testEveryLicenceTextShips() {
        for license in [OpenSourceLibrary.License.apache2, .mit] {
            let url = Bundle.main.url(forResource: license.resource, withExtension: "txt")
            let text = url.flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
            XCTAssertGreaterThan(text.count, 500, license.rawValue)
        }
    }
}
