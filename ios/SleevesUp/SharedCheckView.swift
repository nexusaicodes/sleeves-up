import Shared
import SwiftUI

/// A placeholder screen proving the app links the shared Kotlin code: it shows values computed by
/// `ReportScope.resolve` and `PresenceCheckRules` for today. It is not product UI.
struct SharedCheckView: View {
    var body: some View {
        let today = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        let year = Int32(today.year!)
        let month = Int32(today.month!)
        let todayDate = Kotlinx_datetimeLocalDate(year: year, month: month, day: Int32(today.day!))
        let window = ReportScopeKt.resolve(
            ReportScopeMonth(month: Kotlinx_datetimeYearMonth(year: year, month: month)),
            trackingStart: Kotlinx_datetimeLocalDate(year: year, month: month, day: 1),
            countedThrough: todayDate
        )
        let waitMs = PresenceCheckRules.shared.remainingWaitMs(startedAtMs: 0, nowMs: 4_000)

        VStack(alignment: .leading, spacing: 12) {
            Text("Shared code check").font(.title2.bold())
            if let window {
                Text("This month's report window: \(window.start.description()) to \(window.end.description()), \(window.days) days")
            } else {
                Text("This month's report window: none")
            }
            Text("Device-unlock offer, 4 s into the gate: \(waitMs / 1000) s left")
        }
        .padding()
    }
}
