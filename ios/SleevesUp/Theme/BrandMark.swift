import Shared
import SwiftUI

/// The brand mark, drawn rather than loaded so its cells can be addressed one by one: the same
/// geometry, settle and wave as Android's `BrandGrid`, read from the shared `BrandGridMotion`. Where a
/// still mark is enough (the widget, the Live Activity, the launch screen) the generated `Mark`
/// image is drawn instead.
///
/// - `still`: the mark at rest in `color`.
/// - `settle`: the cells land in reading order once, the last with an overshoot, then rest. The
///   check-out celebration's one motion.
/// - `pulse`: the open session's state. Running, a wave of brightness travels the seven cells, each
///   tracing the identical curve a beat apart, so no cell's place means anything; at rest every cell
///   sits at the track colour. With Reduce Motion, running is every cell at full colour, still
///   distinct from rest.
struct BrandMark: View {

    enum Mode: Equatable {
        case still
        case settle
        case pulse(running: Bool)
    }

    var mode: Mode = .still
    var color: Color = Palette.ink

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start = Date()
    /// Set once a settle has landed, so its timeline stops rather than redrawing a mark at rest.
    @State private var settled = false

    private static let motion = BrandGridMotion.shared
    private static let cells = motion.FILLED_CELLS
    private static let settledMs = Double(motion.SETTLED_MS)

    var body: some View {
        Group {
            if animating {
                TimelineView(.animation) { context in
                    grid(at: context.date.timeIntervalSince(start) * 1000)
                }
            } else {
                grid(at: Self.settledMs)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
        .task(id: mode) {
            start = Date()
            settled = false
            guard mode == .settle else { return }
            try? await Task.sleep(for: .milliseconds(Int(Self.settledMs) + 50))
            settled = true
        }
    }

    /// Only a settle still under way or a running pulse redraws; the rest state is one draw.
    private var animating: Bool {
        guard !reduceMotion else { return false }
        switch mode {
        case .still: return false
        case .settle: return !settled
        case .pulse(let running): return running
        }
    }

    private func grid(at elapsedMs: Double) -> some View {
        Canvas { canvas, size in
            let side = CGFloat(Self.motion.CELLS_A_SIDE)
            let pitch = min(size.width, size.height) / side
            let cell = pitch * CGFloat(Self.motion.FILL_FRAC)
            let inset = (pitch - cell) / 2
            let origin = CGPoint(x: (size.width - pitch * side) / 2, y: (size.height - pitch * side) / 2)
            let minScale = CGFloat(Self.motion.MIN_SCALE)

            for (index, position) in Self.cells.enumerated() {
                guard let paint = paint(index, elapsedMs) else { continue }
                let scaled = cell * (minScale + (1 - minScale) * paint.scale)
                let slack = (cell - scaled) / 2
                let rect = CGRect(
                    x: origin.x + CGFloat(position.col) * pitch + inset + slack,
                    y: origin.y + CGFloat(position.row) * pitch + inset + slack,
                    width: scaled, height: scaled
                )
                var layer = canvas
                layer.opacity = paint.opacity
                layer.fill(
                    Path(roundedRect: rect, cornerRadius: scaled * CGFloat(Self.motion.CORNER_FRAC)),
                    with: .color(color)
                )
            }
        }
    }

    /// One cell this frame: its opacity, and its scale on 0..1 from `MIN_SCALE` to full. Nil leaves
    /// it undrawn.
    private func paint(_ index: Int, _ elapsedMs: Double) -> (opacity: Double, scale: CGFloat)? {
        switch mode {
        case .still:
            return (1, 1)
        case .settle:
            let progress = Double(Self.motion.cellProgress(index: Int32(index), elapsed: Float(elapsedMs)))
            guard progress > 0 else { return nil }
            // The last cell in reading order is the entry just added, so it alone overshoots.
            let eased = index == Self.cells.count - 1 ? Ease.outBack(progress) : Ease.outCubic(progress)
            return (Ease.outCubic(progress), CGFloat(eased))
        case .pulse(let running):
            guard running else { return (Palette.trackAlpha, 1) }
            guard !reduceMotion else { return (1, 1) }
            let waveMs = Double(Self.motion.WAVE_MS)
            let phase = elapsedMs.truncatingRemainder(dividingBy: waveMs) / waveMs
            let level = Double(Self.motion.waveLevel(index: Int32(index), phase: Float(phase)))
            return (Palette.trackAlpha + (1 - Palette.trackAlpha) * level, 1)
        }
    }
}

/// Compose's `EaseOutCubic` and `EaseOutBack` as functions of progress, so the settle lands as
/// Android's does.
private enum Ease {
    static func outCubic(_ t: Double) -> Double { bezier(t, 0.33, 1.0, 0.68, 1.0) }
    static func outBack(_ t: Double) -> Double { bezier(t, 0.34, 1.56, 0.64, 1.0) }

    /// A CSS-style cubic Bézier easing: solve x(s) = t by Newton's method, return y(s).
    private static func bezier(_ t: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        func coord(_ s: Double, _ a: Double, _ b: Double) -> Double {
            3 * (1 - s) * (1 - s) * s * a + 3 * (1 - s) * s * s * b + s * s * s
        }
        func slope(_ s: Double, _ a: Double, _ b: Double) -> Double {
            3 * (1 - s) * (1 - s) * a + 6 * (1 - s) * s * (b - a) + 3 * s * s * (1 - b)
        }
        var s = t
        for _ in 0..<8 {
            let dx = slope(s, x1, x2)
            guard abs(dx) > 1e-6 else { break }
            s -= (coord(s, x1, x2) - t) / dx
            s = min(max(s, 0), 1)
        }
        return coord(s, y1, y2)
    }
}
