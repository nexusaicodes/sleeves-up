import AVFoundation
import UIKit

/// The front camera, asked only whether a face is in view. It runs an `AVCaptureMetadataOutput`
/// for `.face` and nothing else: the app receives face bounds, never a frame, so no image exists
/// for it to keep or lose. The preview the user sees is drawn by the system's preview layer.
final class FaceWatcher: NSObject, AVCaptureMetadataOutputObjectsDelegate, @unchecked Sendable {

    /// The session the preview layer shows. Configured once; started and stopped off the main thread.
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "presence.camera")
    private let onFaces: @MainActor (Bool) -> Void

    /// `onFaces` is told, on the main actor, whether the latest metadata held at least one face.
    init(onFaces: @escaping @MainActor (Bool) -> Void) {
        self.onFaces = onFaces
    }

    /// Whether a front camera that reports faces could be set up. False on a device without one
    /// (and on the simulator), where the gate offers device unlock at once.
    func configure() -> Bool {
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: camera),
              session.canAddInput(input) else { return false }
        let output = AVCaptureMetadataOutput()
        session.beginConfiguration()
        session.addInput(input)
        guard session.canAddOutput(output) else {
            session.commitConfiguration()
            return false
        }
        session.addOutput(output)
        session.commitConfiguration()
        guard output.availableMetadataObjectTypes.contains(.face) else { return false }
        output.metadataObjectTypes = [.face]
        output.setMetadataObjectsDelegate(self, queue: .main)
        return true
    }

    func start() {
        queue.async { [session] in session.startRunning() }
    }

    func stop() {
        queue.async { [session] in session.stopRunning() }
    }

    nonisolated func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        let found = metadataObjects.contains { $0.type == .face }
        MainActor.assumeIsolated { onFaces(found) }
    }
}
