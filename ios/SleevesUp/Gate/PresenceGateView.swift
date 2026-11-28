import AVFoundation
import LocalAuthentication
import Shared
import SwiftUI

/// The presence check every check-in and check-out passes through, with Android's rules from the
/// shared `PresenceCheckRules`: the first face the camera reports passes, with nothing to press;
/// device unlock is offered after `DEVICE_UNLOCK_OFFERED_AFTER_MS`, or at once when the camera
/// cannot look; and it is never a dead end. With no passcode set, iOS gives an app no way to open
/// passcode settings, so the fallback says where to set one (iOS rule 6). **Not now** always leaves
/// with nothing written.
struct PresenceGateView: View {

    let onPassed: () -> Void
    let onDismiss: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @State private var watcher: FaceWatcher?
    @State private var cameraUsable: Bool?
    @State private var facePresent = false
    @State private var startedAt = Date()
    @State private var searchTimedOut = false
    @State private var usingDeviceUnlock = false
    @State private var passed = false
    @State private var needsPasscode = false

    private var rules: PresenceCheckRules { PresenceCheckRules.shared }

    /// The scrim under everything drawn over the preview, dense enough that the vermilion error still
    /// reads 4.5:1 over a white wall.
    private static let scrimAlpha = 0.88

    var body: some View {
        ZStack {
            if let watcher, cameraUsable == true {
                CameraPreview(session: watcher.session).ignoresSafeArea()
            } else {
                Palette.nightPaper.ignoresSafeArea()
            }
            // What sits over the preview is in the night scheme on a scrim, whatever the appearance,
            // so it reads over a white wall as over a dark room. The preview itself stays visible.
            VStack(spacing: 20) {
                HStack {
                    Spacer()
                    Button("Not now", action: onDismiss)
                        .font(AppFont.labelLarge)
                        .padding(.horizontal, 16)
                        .frame(minHeight: 48)
                        .background(Palette.nightPaper.opacity(Self.scrimAlpha), in: Capsule())
                }
                Spacer()
                VStack(spacing: 16) {
                    if needsPasscode {
                        passcodeHelp
                    } else {
                        Text(message)
                            .font(AppFont.bodyLarge)
                            .foregroundStyle(cameraUsable == false ? Palette.nightVermilion : Palette.nightInk)
                            .multilineTextAlignment(.center)
                        if rules.offersFallback(passed: passed, usingDeviceUnlock: usingDeviceUnlock, searchTimedOut: searchTimedOut) {
                            Button(action: useDeviceUnlock) {
                                Text("Use device unlock").frame(maxWidth: .infinity)
                            }
                            .buttonStyle(NightPrimaryButtonStyle())
                        }
                    }
                }
                .padding(20)
                .frame(maxWidth: .infinity)
                .background(Palette.nightPaper.opacity(Self.scrimAlpha), in: RoundedRectangle(cornerRadius: 24))
            }
            .padding(16)
            .foregroundStyle(Palette.nightInk)
            .environment(\.colorScheme, .dark)
        }
        .task { await startLooking() }
        .task(id: cameraUsable) { await waitForFallback() }
        .onDisappear { watcher?.stop() }
        .onChange(of: facePresent) { _, present in
            guard rules.passes(facePresent: present, usingDeviceUnlock: usingDeviceUnlock), !passed else { return }
            pass()
        }
        .onChange(of: scenePhase) { _, phase in
            // Back from Settings: a passcode set there makes device unlock available.
            if phase == .active, needsPasscode, DeviceUnlock.isAvailable() { needsPasscode = false }
        }
    }

    private var message: String {
        if passed { return String(app: "You're here!") }
        if cameraUsable == false { return String(app: "This camera can't check for a face. Use device unlock instead.") }
        if searchTimedOut { return String(app: "Still looking. You can use device unlock instead.") }
        return String(app: "Looking for a face…")
    }

    private var passcodeHelp: some View {
        VStack(spacing: 12) {
            Text("Set a passcode to use device unlock").font(AppFont.titleMedium)
            Text("Open Settings, then \(passcodeSettingsName), and turn on a passcode. Then come back here.")
                .multilineTextAlignment(.center)
        }
    }

    /// The Settings entry for this device, which names its biometry.
    private var passcodeSettingsName: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .touchID: return String(app: "Touch ID & Passcode")
        case .faceID: return String(app: "Face ID & Passcode")
        default: return String(app: "Passcode")
        }
    }

    private func startLooking() async {
        startedAt = Date()
        guard FaceWatcher.hasFrontCamera else {
            cameraUsable = false
            searchTimedOut = true
            return
        }
        let granted: Bool
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: granted = true
        case .notDetermined: granted = await AVCaptureDevice.requestAccess(for: .video)
        default: granted = false
        }
        let faces = FaceWatcher { present in facePresent = present }
        guard granted, faces.configure() else {
            cameraUsable = false
            searchTimedOut = true
            return
        }
        watcher = faces
        cameraUsable = true
        faces.start()
    }

    /// Latches the fallback once the wait runs out, timed from the gate opening, not from the first
    /// camera result, so a camera that delivers nothing still reaches it.
    private func waitForFallback() async {
        guard cameraUsable == true else { return }
        let waitMs = rules.remainingWaitMs(startedAtMs: Clock.nowMs(startedAt), nowMs: Clock.nowMs(Date()))
        try? await Task.sleep(for: .milliseconds(waitMs))
        if !Task.isCancelled { searchTimedOut = true }
    }

    private func useDeviceUnlock() {
        guard DeviceUnlock.isAvailable() else {
            needsPasscode = true
            return
        }
        // Set before the prompt, so a face found behind it cannot pass the gate under the dialog.
        usingDeviceUnlock = true
        Task {
            let unlocked = await DeviceUnlock.ask(reason: String(app: "Use your device unlock to verify your presence"))
            usingDeviceUnlock = false
            if unlocked { pass() }
        }
    }

    private func pass() {
        guard !passed else { return }
        passed = true
        watcher?.stop()
        Task {
            try? await Task.sleep(for: .milliseconds(400))
            onPassed()
        }
    }
}

/// The camera's live preview, drawn by the system's preview layer.
private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

/// The gate's one filled button, in the night scheme: cream under night paper.
private struct NightPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(AppFont.titleMedium)
            .padding(.horizontal, 24)
            .frame(minHeight: 56)
            .foregroundStyle(Palette.nightPaper)
            .background(Palette.nightInk, in: Capsule())
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
