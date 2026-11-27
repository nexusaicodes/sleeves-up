import SwiftUI

/// What a gated check-out just completed: the closed session, its own day's completed sessions for
/// its habit, and the habit's name. Read for the closed session's `date_key`, so a session closed
/// after midnight reports the day it belongs to.
struct CheckOutCompleted: Identifiable, Equatable {
    let sessionMs: Int64
    let dayTotalMs: Int64
    let daySessionCount: Int
    let habitName: String?
    let raisedAt: Date
    var id: Date { raisedAt }

    /// A celebration left behind (Home, then a return much later) is closed rather than greeting a
    /// later visit, sooner than the gate's ten minutes since it reacts to a moment.
    static let expiry: TimeInterval = 2 * 60
}

/// The brand mark's cells arriving in reading order, Android's `BrandGrid` settle: each cell takes
/// `cellMs` to arrive and starts `staggerMs` after the one before.
enum MarkSettle {
    static let cellsASide = 3
    /// Row and column of the two cells the mark leaves out.
    static let emptyCells: Set<[Int]> = [[1, 2], [2, 0]]
    static let cells: [(row: Int, col: Int)] = (0..<cellsASide).flatMap { row in
        (0..<cellsASide).map { (row: row, col: $0) }
    }.filter { !emptyCells.contains([$0.row, $0.col]) }

    static let cellMs = 260.0
    static let staggerMs = 70.0
    static var settledMs: Double { Double(cells.count - 1) * staggerMs + cellMs }

    /// How far the cell at `index` has arrived `elapsedMs` into the settle, from 0 to 1.
    static func progress(index: Int, elapsedMs: Double) -> Double {
        min(max((elapsedMs - Double(index) * staggerMs) / cellMs, 0), 1)
    }
}

/// The check-out celebration: the mark settling once, the habit, "You showed up", the session's
/// length and the day so far. Identical at twenty minutes and at nine hours, since anything that
/// warms with the duration is a target in disguise. Nothing retires it on a timer: a tap closes it.
struct CheckOutCelebrationView: View {

    let completed: CheckOutCompleted
    let onDismiss: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    private var dayText: String {
        String(app: "\(ReportText.sessions(completed.daySessionCount)) · \(SessionText.short(completed.dayTotalMs)) today")
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(spacing: 12) {
                SettlingMark(animated: !reduceMotion).frame(width: 96, height: 96)
                if let name = completed.habitName {
                    Text(name).font(.subheadline.weight(.medium)).foregroundStyle(.secondary)
                }
                Text("You showed up").font(.title2.weight(.semibold))
                Text(SessionText.short(completed.sessionMs))
                    .font(.system(size: 40, weight: .bold).monospacedDigit())
                    .foregroundStyle(.tint)
                Text(dayText).font(.subheadline).foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.center)
            .padding(32)
            VStack {
                Spacer()
                Text("Tap to close").font(.caption.weight(.medium)).foregroundStyle(.secondary).padding(.bottom, 48)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onDismiss)
        // One announcement for the whole surface rather than four fragments, and a tap closes it.
        .accessibilityRepresentation {
            Button(
                [completed.habitName, String(app: "You showed up"), SessionText.short(completed.sessionMs), dayText]
                    .compactMap { $0 }.joined(separator: ". "),
                action: onDismiss
            )
            .accessibilityIdentifier("celebration")
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, Date().timeIntervalSince(completed.raisedAt) > CheckOutCompleted.expiry {
                onDismiss()
            }
        }
    }
}

/// The mark's seven cells landing in reading order once, then resting.
private struct SettlingMark: View {
    let animated: Bool
    @State private var start = Date()

    var body: some View {
        TimelineView(.animation(paused: !animated)) { context in
            let elapsed = animated ? context.date.timeIntervalSince(start) * 1000 : MarkSettle.settledMs
            Canvas { canvas, size in
                let pitch = size.width / CGFloat(MarkSettle.cellsASide)
                for (index, cell) in MarkSettle.cells.enumerated() {
                    let progress = MarkSettle.progress(index: index, elapsedMs: elapsed)
                    guard progress > 0 else { continue }
                    let side = pitch * 0.88 * (0.6 + 0.4 * progress)
                    let centre = CGPoint(x: (CGFloat(cell.col) + 0.5) * pitch, y: (CGFloat(cell.row) + 0.5) * pitch)
                    let rect = CGRect(x: centre.x - side / 2, y: centre.y - side / 2, width: side, height: side)
                    canvas.opacity = progress
                    canvas.fill(Path(roundedRect: rect, cornerRadius: side * 0.22), with: .foreground)
                }
            }
        }
        .accessibilityHidden(true)
    }
}
