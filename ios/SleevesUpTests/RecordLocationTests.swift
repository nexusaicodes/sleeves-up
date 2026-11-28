import GRDB
import XCTest
@testable import SleevesUp

/// The record's one-time move into the app group's container: the database and its sidecars move
/// together, a record already there is never replaced, and a move cut short is finished next time.
final class RecordLocationTests: XCTestCase {

    private var source: URL!
    private var destination: URL!
    private let file = RecordLocation.fileName

    override func setUpWithError() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        source = root.appendingPathComponent("old")
        destination = root.appendingPathComponent("group")
        try FileManager.default.createDirectory(at: source, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
    }

    private func exists(_ folder: URL, _ file: String) -> Bool {
        FileManager.default.fileExists(atPath: folder.appendingPathComponent(file).path)
    }

    private func write(_ folder: URL, _ file: String, _ text: String) throws {
        try Data(text.utf8).write(to: folder.appendingPathComponent(file))
    }

    private func read(_ folder: URL, _ file: String) throws -> String {
        String(decoding: try Data(contentsOf: folder.appendingPathComponent(file)), as: UTF8.self)
    }

    func testARecordAndItsSidecarsMoveAndStillOpen() throws {
        let queue = try RecordDatabase.open(path: source.appendingPathComponent(file).path, nowMs: 0)
        try queue.write { db in try db.execute(sql: "UPDATE habits SET name = 'Gym' WHERE id = 1") }
        try queue.close()
        try write(source, file + "-journal", "journal")

        try RecordLocation.moveIfNeeded(fileName: file, from: source, to: destination)

        XCTAssertFalse(exists(source, file))
        XCTAssertFalse(exists(source, file + "-journal"))
        XCTAssertTrue(exists(destination, file + "-journal"))
        try FileManager.default.removeItem(at: destination.appendingPathComponent(file + "-journal"))
        let moved = try RecordDatabase.open(path: destination.appendingPathComponent(file).path, nowMs: 0)
        XCTAssertEqual(try moved.read { db in try String.fetchOne(db, sql: "SELECT name FROM habits WHERE id = 1") }, "Gym")
    }

    func testARecordAlreadyInTheGroupIsNeverReplaced() throws {
        try write(source, file, "old")
        try write(destination, file, "group")

        try RecordLocation.moveIfNeeded(fileName: file, from: source, to: destination)

        XCTAssertEqual(try read(destination, file), "group")
        XCTAssertEqual(try read(source, file), "old")
    }

    func testAMoveCutShortIsFinishedByTheNextOpen() throws {
        // A first attempt moved the write-ahead log and stopped before the database.
        try write(source, file, "record")
        try write(source, file + "-shm", "index")
        try write(destination, file + "-wal", "log")
        try write(destination, file + "-shm", "stale")

        try RecordLocation.moveIfNeeded(fileName: file, from: source, to: destination)

        XCTAssertEqual(try read(destination, file), "record")
        XCTAssertEqual(try read(destination, file + "-wal"), "log")
        XCTAssertEqual(try read(destination, file + "-shm"), "index")
    }

    func testNothingToMoveLeavesTheGroupEmpty() throws {
        try RecordLocation.moveIfNeeded(fileName: file, from: source, to: destination)
        XCTAssertFalse(exists(destination, file))
    }
}
