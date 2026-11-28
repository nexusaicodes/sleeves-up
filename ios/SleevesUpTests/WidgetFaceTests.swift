import XCTest
@testable import SleevesUp

/// The home-screen widget's face (iOS rule 5): one habit named, several reading "Check In", an open
/// session naming its habit, and no figure in any of them.
final class WidgetFaceTests: XCTestCase {

    func testOneActiveHabitIsNamed() {
        XCTAssertEqual(WidgetFace.of(activeNames: ["Gym"], openSession: nil).text, "Check in to Gym")
    }

    func testSeveralActiveHabitsReadCheckIn() {
        XCTAssertEqual(WidgetFace.of(activeNames: ["Gym", "Reading"], openSession: nil), .checkIn)
    }

    func testAnOpenSessionNamesItsHabitWhateverElseIsActive() {
        let face = WidgetFace.of(activeNames: ["Gym", "Reading"], openSession: .some("Reading"))
        XCTAssertEqual(face.text, "Check out of Reading")
        XCTAssertTrue(face.sessionOpen)
    }

    func testAHabitOwedANameTakesTheAppsFallback() {
        XCTAssertEqual(WidgetFace.of(activeNames: [nil], openSession: nil).text, "Check in to Unnamed habit")
        XCTAssertEqual(WidgetFace.of(activeNames: [nil], openSession: .some(nil)).text, "Check out of Unnamed habit")
    }

    func testNoFaceCarriesADigit() {
        let faces: [WidgetFace] = [.checkIn, .checkInTo("Gym"), .checkOutOf("Gym")]
        XCTAssertTrue(faces.allSatisfy { $0.text.rangeOfCharacter(from: .decimalDigits) == nil })
    }
}
