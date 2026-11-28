import GRDB
import SwiftUI
import WidgetKit

/// The home-screen widget: a door to the gate, never a display of the record. It reads the record
/// read-only from the app group's container, and the app asks for a redraw whenever what it reads
/// changes, so it holds one entry and never wakes on its own. A stale face costs nothing, since the
/// app routes the tap from the rows at the tap.
struct CheckInWidget: Widget {

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetFace.kind, provider: FaceProvider()) { entry in
            FaceView(face: entry.face)
                .containerBackground(Palette.paper, for: .widget)
                .widgetURL(WidgetFace.tap)
                .environment(\.locale, AppLocale.current)
        }
        .configurationDisplayName(Text(verbatim: "Sleeves Up"))
        .description("Check in from your home screen")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct FaceEntry: TimelineEntry {
    let date: Date
    let face: WidgetFace
}

struct FaceProvider: TimelineProvider {

    func placeholder(in context: Context) -> FaceEntry { FaceEntry(date: .now, face: .checkIn) }

    func getSnapshot(in context: Context, completion: @escaping (FaceEntry) -> Void) {
        completion(FaceEntry(date: .now, face: context.isPreview ? .checkIn : Self.readFace()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FaceEntry>) -> Void) {
        completion(Timeline(entries: [FaceEntry(date: .now, face: Self.readFace())], policy: .never))
    }

    /// The face from the record, or "Check In" when it cannot be read (never opened, or mid-write):
    /// a tap still opens the app, which decides.
    static func readFace() -> WidgetFace {
        guard let folder = try? RecordLocation.folder() else { return .checkIn }
        let path = folder.appendingPathComponent(RecordLocation.fileName).path
        guard FileManager.default.fileExists(atPath: path) else { return .checkIn }
        var configuration = Configuration()
        configuration.readonly = true
        do {
            let queue = try DatabaseQueue(path: path, configuration: configuration)
            return try queue.read { db in
                let names = try String?.fetchAll(db, sql: "SELECT name FROM habits WHERE archived = 0 ORDER BY id ASC")
                let open = try Row.fetchOne(db, sql: """
                    SELECT habits.name AS name FROM sessions JOIN habits ON habits.id = sessions.habit_id
                    WHERE sessions.stopped_at IS NULL LIMIT 1
                    """)
                return WidgetFace.of(activeNames: names, openSession: open.map { $0["name"] as String? })
            }
        } catch {
            return .checkIn
        }
    }
}

private struct FaceView: View {
    let face: WidgetFace

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // The mark at rest is the ink at 40%, at full colour while a session is open.
            Image("Mark")
                .renderingMode(.template)
                .resizable()
                .frame(width: 32, height: 32)
                .foregroundStyle(Palette.ink.opacity(face.sessionOpen ? 1 : 0.4))
                .accessibilityHidden(true)
            Spacer(minLength: 0)
            Text(face.text)
                .font(.headline)
                .foregroundStyle(Palette.ink)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}
