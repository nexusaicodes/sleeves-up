import Foundation

/// Where the record lives: the app group's container, which the widget extension can read, so the
/// home-screen widget states what the record holds. A record from a build that kept it in the app's
/// own Application Support is moved there once, before its first open.
enum RecordLocation {

    /// The app group the app and its widget extension share. Permanent once uploaded to Apple.
    static let appGroup = "group.com.nexusai.checkin.app"

    static let fileName = "record.sqlite"

    /// SQLite's files beside a database: a rollback journal left by an interrupted write, or a
    /// write-ahead log and its index. Each belongs to the database and moves with it.
    static let sidecarSuffixes = ["-journal", "-wal", "-shm"]

    /// The folder holding the record: Application Support inside the app group's container, or the
    /// app's own when the build carries no app group entitlement (the record still opens, and the
    /// widget then finds nothing).
    static func folder(fileManager: FileManager = .default) throws -> URL {
        let folder: URL
        if let group = fileManager.containerURL(forSecurityApplicationGroupIdentifier: appGroup) {
            folder = group.appendingPathComponent("Library/Application Support", isDirectory: true)
        } else {
            folder = try legacyFolder(fileManager: fileManager)
        }
        try fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    /// The app's own Application Support, where builds before the app group kept the record.
    static func legacyFolder(fileManager: FileManager = .default) throws -> URL {
        try fileManager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
    }

    /// Moves the database `fileName` and its sidecars from `source` to `destination`, unless
    /// `destination` already holds the database, in which case nothing moves and the old copy stays
    /// where it is. The sidecars move first and the database last, so a move cut short is finished
    /// by the next open: until the database itself has moved, the destination does not count as
    /// holding it.
    static func moveIfNeeded(fileName: String, from source: URL, to destination: URL, fileManager: FileManager = .default) throws {
        guard source.standardizedFileURL != destination.standardizedFileURL else { return }
        let target = destination.appendingPathComponent(fileName)
        let origin = source.appendingPathComponent(fileName)
        guard !fileManager.fileExists(atPath: target.path), fileManager.fileExists(atPath: origin.path) else { return }
        for suffix in sidecarSuffixes {
            let from = source.appendingPathComponent(fileName + suffix)
            guard fileManager.fileExists(atPath: from.path) else { continue }
            let to = destination.appendingPathComponent(fileName + suffix)
            // Left by a move cut short, whose source copy is the one now being moved.
            if fileManager.fileExists(atPath: to.path) { try fileManager.removeItem(at: to) }
            try fileManager.moveItem(at: from, to: to)
        }
        try fileManager.moveItem(at: origin, to: target)
    }
}
