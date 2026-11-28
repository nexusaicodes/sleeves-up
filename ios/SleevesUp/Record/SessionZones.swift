import Foundation

/// The zone the open session was checked in under, by session id: the iOS counterpart of Android's
/// `SessionZones`. A forgotten session is closed at **its own** midnight, and the device's zone at
/// the close can be another one entirely, after travel or a restore onto a device set elsewhere.
/// Written at check-in and cleared at every close.
struct SessionZones {

    private let defaults: UserDefaults
    private static let sessionKey = "open_session_id"
    private static let zoneKey = "open_session_zone"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func record(sessionId: Int64, zone: TimeZone) {
        defaults.set(sessionId, forKey: Self.sessionKey)
        defaults.set(zone.identifier, forKey: Self.zoneKey)
    }

    /// The zone `sessionId` was checked in under, or nil when none was recorded for it.
    func zone(of sessionId: Int64) -> TimeZone? {
        guard defaults.object(forKey: Self.sessionKey) as? Int64 == sessionId,
              let identifier = defaults.string(forKey: Self.zoneKey) else { return nil }
        return TimeZone(identifier: identifier)
    }

    func clear() {
        defaults.removeObject(forKey: Self.sessionKey)
        defaults.removeObject(forKey: Self.zoneKey)
    }
}
