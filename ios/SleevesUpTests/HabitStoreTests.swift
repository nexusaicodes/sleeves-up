import Shared
import XCTest
@testable import SleevesUp

/// The habit writes on iOS, against an in-memory record: the seed habit selected and owed a name,
/// the selection locked to a running session, archive refusals and where an archive moves the
/// selection, a restore held to the name rule, and the shown habit's own sessions on Check-In.
@MainActor
final class HabitStoreTests: XCTestCase {

    private let utcJun15At0900: Int64 = 1_781_514_000_000
    private var defaults: UserDefaults!
    private var uuids = 0

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "HabitStoreTests")!
        defaults.removePersistentDomain(forName: "HabitStoreTests")
    }

    private func makeStores() throws -> (HabitStore, SessionStore) {
        let database = try RecordDatabase.openInMemory(nowMs: 0)
        let now = { [utcJun15At0900] in Clock.date(ms: utcJun15At0900) }
        let habits = HabitStore(database: database, now: now) { [unowned self] in
            uuids += 1
            return "uuid-\(uuids)"
        }
        let sessions = SessionStore(
            database: database,
            zones: SessionZones(defaults: defaults),
            now: now,
            zone: { TimeZone(identifier: "UTC")! }
        )
        try habits.reload()
        return (habits, sessions)
    }

    /// Names the seed habit Gym and adds Reading and Guitar, ids 1, 2 and 3.
    private func threeHabits(_ habits: HabitStore) throws {
        XCTAssertEqual(try habits.rename(RecordDatabase.seedHabitId, to: "Gym"), .saved)
        XCTAssertEqual(try habits.create(name: "Reading"), .saved)
        XCTAssertEqual(try habits.create(name: "Guitar"), .saved)
    }

    func testAFreshRecordShowsTheUnnamedSeedHabit() throws {
        let (habits, _) = try makeStores()
        XCTAssertEqual(habits.active.map(\.id), [RecordDatabase.seedHabitId])
        XCTAssertEqual(habits.selected?.id, RecordDatabase.seedHabitId)
        XCTAssertEqual(habits.needingName?.id, RecordDatabase.seedHabitId)

        XCTAssertEqual(try habits.rename(RecordDatabase.seedHabitId, to: "  Morning   run "), .saved)
        XCTAssertEqual(habits.selected?.name, "Morning run")
        XCTAssertNil(habits.needingName)
    }

    func testCreatingKeepsTheSelectionAndRefusesADuplicate() throws {
        let (habits, _) = try makeStores()
        try threeHabits(habits)
        XCTAssertEqual(habits.active.map(\.name), ["Gym", "Reading", "Guitar"])
        XCTAssertEqual(habits.selected?.id, 1)
        XCTAssertEqual(try habits.create(name: " gYM "), .rejected(.duplicate))
        XCTAssertEqual(try habits.create(name: "   "), .rejected(.blank))
        XCTAssertEqual(habits.active.count, 3)
    }

    func testRenamingKeepsItsOwnNameAndRefusesAnother() throws {
        let (habits, _) = try makeStores()
        try threeHabits(habits)
        XCTAssertEqual(try habits.rename(2, to: "reading"), .saved)
        XCTAssertEqual(try habits.rename(2, to: "Gym"), .rejected(.duplicate))
        XCTAssertEqual(try habits.rename(99, to: "Swim"), .missing)
    }

    func testTheSelectionIsLockedToTheRunningHabit() throws {
        let (habits, sessions) = try makeStores()
        try threeHabits(habits)
        XCTAssertTrue(try habits.select(2))
        try sessions.show(habitId: 2)
        XCTAssertEqual(try sessions.checkIn(), .opened)
        try habits.reload()

        XCTAssertTrue(habits.isLocked)
        XCTAssertFalse(try habits.select(1))
        XCTAssertEqual(habits.selected?.id, 2)

        try sessions.checkOut()
        try habits.reload()
        XCTAssertFalse(habits.isLocked)
        XCTAssertTrue(try habits.select(1))
        XCTAssertEqual(habits.selected?.id, 1)
    }

    func testArchiveRefusesTheRunningAndTheLastHabit() throws {
        let (habits, sessions) = try makeStores()
        XCTAssertEqual(try habits.archive(1), .lastActive)

        XCTAssertEqual(try habits.create(name: "Reading"), .saved)
        _ = try sessions.checkIn()
        try habits.reload()
        XCTAssertEqual(try habits.archive(1), .running)
        XCTAssertEqual(habits.archived.count, 0)
    }

    func testArchivingTheSelectedHabitMovesTheSelectionToTheFirstLeft() throws {
        let (habits, _) = try makeStores()
        try threeHabits(habits)
        XCTAssertEqual(try habits.archive(1), .archived)
        XCTAssertEqual(habits.selected?.id, 2)
        XCTAssertEqual(habits.archived.map(\.id), [1])

        XCTAssertEqual(try habits.archive(3), .archived)
        XCTAssertEqual(habits.selected?.id, 2)
        XCTAssertFalse(try habits.select(3), "an archived habit cannot be selected")
    }

    func testRestoreIsRefusedWhenItsNameWasTakenSince() throws {
        let (habits, _) = try makeStores()
        try threeHabits(habits)
        XCTAssertEqual(try habits.archive(3), .archived)
        XCTAssertEqual(try habits.create(name: "guitar"), .saved)
        XCTAssertEqual(try habits.restore(3), .rejected(.duplicate))

        XCTAssertEqual(try habits.rename(4, to: "Bass"), .saved)
        XCTAssertEqual(try habits.restore(3), .saved)
        XCTAssertEqual(habits.active.map(\.id), [1, 2, 3, 4])
    }

    func testCheckInWritesToTheShownHabitAndTodayListsOnlyItsSessions() throws {
        let (habits, sessions) = try makeStores()
        try threeHabits(habits)
        _ = try sessions.checkIn()
        try sessions.checkOut()

        try sessions.show(habitId: 2)
        XCTAssertEqual(sessions.todaySessions.count, 0)
        XCTAssertEqual(try sessions.checkIn(), .opened)
        XCTAssertEqual(sessions.openSession?.habitId, 2)
        XCTAssertEqual(sessions.todaySessions.map(\.habitId), [2])

        try sessions.show(habitId: 1)
        XCTAssertEqual(try sessions.checkIn(), .otherHabitOpen)
        XCTAssertEqual(sessions.todaySessions.map(\.habitId), [1])
    }

    func testTheSelectionSurvivesReopeningTheRecord() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let path = folder.appendingPathComponent("record.sqlite").path

        let first = HabitStore(database: try RecordDatabase.open(path: path, nowMs: 0))
        try first.reload()
        _ = try first.create(name: "Reading")
        XCTAssertTrue(try first.select(2))

        let second = HabitStore(database: try RecordDatabase.open(path: path, nowMs: 0))
        try second.reload()
        XCTAssertEqual(second.selected?.id, 2)
    }
}
