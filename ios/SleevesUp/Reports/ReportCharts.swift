import Shared
import SwiftUI

/// Reports' charts, drawn from the shared `ChartGeometry` as Android's are, so the degenerate cases
/// (empty, all zero, one point, a zero ceiling) render the same on both. A canvas is invisible to
/// VoiceOver, so each takes the sentence it is read as. Time runs from the leading edge, so each
/// mirrors under a right-to-left language.

/// Daily hours as a line over a fading fill, with an optional dashed reference line (the window's
/// own mean, never a target).
struct LineChartView: View {

    let values: [Float]
    let reference: Float?
    let description: String

    private let stroke: CGFloat = 2

    var body: some View {
        Canvas { context, size in
            guard !values.isEmpty else { return }
            let ceiling = ChartGeometry.shared.niceMaxY(rawMax: max(values.max() ?? 0, reference ?? 0))
            // The stroke is centred on the path, so a value on the floor would lose half its width
            // to the edge and an all-zero window would read as a blank chart. Inset by a full stroke.
            let plotHeight = max(size.height - stroke, 0)
            let plotTop = stroke / 2
            let points = ChartGeometry.shared.linePoints(
                values: values.map { KotlinFloat(value: $0) },
                width: Float(size.width),
                height: Float(plotHeight),
                maxY: ceiling
            ).map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y) + plotTop) }

            if let reference, reference > 0 {
                let y = plotTop + plotHeight - CGFloat(min(max(reference / ceiling, 0), 1)) * plotHeight
                var line = Path()
                line.move(to: CGPoint(x: 0, y: y))
                line.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(line, with: .color(Palette.outline), style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
            }
            // A single reading has no segment to stroke, so it is a dot.
            if points.count == 1 {
                let r = stroke * 1.5
                context.fill(
                    Path(ellipseIn: CGRect(x: points[0].x - r, y: points[0].y - r, width: 2 * r, height: 2 * r)),
                    with: .color(Palette.ink)
                )
                return
            }
            var line = Path()
            line.addLines(points)
            var fill = line
            fill.addLine(to: CGPoint(x: points[points.count - 1].x, y: plotTop + plotHeight))
            fill.addLine(to: CGPoint(x: points[0].x, y: plotTop + plotHeight))
            fill.closeSubpath()
            context.fill(
                fill,
                with: .linearGradient(
                    Gradient(colors: [Palette.ink.opacity(0.25), .clear]),
                    startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)
                )
            )
            context.stroke(line, with: .color(Palette.ink), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
        }
        .flipsForRightToLeftLayoutDirection(true)
        .accessibilityElement()
        .accessibilityLabel(description)
    }
}

/// Hours by month. The baseline rule is always drawn, so an all-zero record reads as a row of zeros
/// rather than a failed chart; a zero-height bar is skipped rather than drawn as a sliver.
struct BarChartView: View {

    let values: [Float]
    let description: String

    var body: some View {
        Canvas { context, size in
            guard !values.isEmpty else { return }
            let ceiling = ChartGeometry.shared.niceMaxY(rawMax: values.max() ?? 0)
            let rule: CGFloat = 1
            let plotHeight = max(size.height - rule, 0)
            context.fill(Path(CGRect(x: 0, y: plotHeight, width: size.width, height: rule)), with: .color(Palette.outline))
            let bars = ChartGeometry.shared.barRects(
                values: values.map { KotlinFloat(value: $0) },
                width: Float(size.width),
                height: Float(plotHeight),
                maxY: ceiling,
                gapRatio: 0.3
            )
            for bar in bars where bar.bottom - bar.top > 0 {
                let rect = CGRect(
                    x: CGFloat(bar.left), y: CGFloat(bar.top),
                    width: CGFloat(bar.right - bar.left), height: CGFloat(bar.bottom - bar.top)
                )
                context.fill(Path(roundedRect: rect, cornerRadius: 4), with: .color(Palette.ink))
            }
        }
        .flipsForRightToLeftLayoutDirection(true)
        .accessibilityElement()
        .accessibilityLabel(description)
    }
}

/// A ring split by `values`, in colours handed in rather than derived from the values: any mapping
/// from a magnitude to a strength would rank the slices. Nothing recorded still draws a ring.
struct DonutChartView: View {

    let values: [Int]
    let colors: [Color]
    let emptyColor: Color
    let description: String

    /// Grows with Dynamic Type from 112 to at most 140 points, as Android's ring grows with font scale.
    @ScaledMetric private var scaled: CGFloat = 112
    private let stroke: CGFloat = 12

    var body: some View {
        let side = min(max(scaled, 112), 140)
        Canvas { context, size in
            let diameter = min(size.width, size.height) - stroke
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let segments = ChartGeometry.shared.donutSegments(
                values: values.map { KotlinFloat(value: Float($0)) },
                startAngle: ChartGeometry.shared.TOP_OF_CIRCLE
            )
            func arc(from start: Float, sweep: Float) -> Path {
                var path = Path()
                path.addArc(
                    center: center, radius: diameter / 2,
                    startAngle: .degrees(Double(start)), endAngle: .degrees(Double(start + sweep)),
                    clockwise: false
                )
                return path
            }
            if segments.isEmpty {
                context.stroke(arc(from: 0, sweep: 360), with: .color(emptyColor), lineWidth: stroke)
                return
            }
            for segment in segments {
                context.stroke(
                    arc(from: segment.startAngle, sweep: segment.sweepAngle),
                    with: .color(colors[Int(segment.index) % colors.count]),
                    lineWidth: stroke
                )
            }
        }
        .frame(width: side, height: side)
        .accessibilityElement()
        .accessibilityLabel(description)
    }
}
