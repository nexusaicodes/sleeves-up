import LocalAuthentication

/// The gate's fallback: the device's own unlock, Face ID or Touch ID with the passcode behind it,
/// as Android's `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
enum DeviceUnlock {

    /// Whether the device can unlock at all. False when no passcode is set, the case where the
    /// fallback becomes instructions for setting one (iOS rule 6).
    static func isAvailable() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    /// Asks for the device's unlock, and returns whether it was given.
    static func ask(reason: String) async -> Bool {
        do {
            return try await LAContext().evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)
        } catch {
            return false
        }
    }
}
