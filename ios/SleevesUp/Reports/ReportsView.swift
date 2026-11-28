import Shared
import SwiftUI

/// The Reports tab: a Month / All time choice over a month stepper, then every figure and chart for
/// the one window that scope resolves to. A scope holding no counted day is an empty state, never a
/// set of zeros. Every number is a quantity with no direction; only days shown up is the thesis.
struct ReportsView: View {

    let reports: ReportsStore
    let habits: HabitStore

    @State private var failed = false
    @State private var sharing: SharedFile?
    @State private var notice: String?

    /// A written export, for the share sheet.
    private struct SharedFile: Identifiable {
        let url: URL
        var id: URL { url }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    scopeSelector
                    if failed { Text(HistoryText.readFailed).font(AppFont.bodySmall).foregroundStyle(Palette.inkMuted) }
                    if reports.loaded {
                        if let figures = reports.figures {
                            cards(figures)
                        } else {
                            emptyScope
                        }
                    }
                }
                .padding()
            }
            .background(Palette.paper.ignoresSafeArea())
            .habitSwitcher(habits)
            .onAppear { act { try reports.reload() } }
            .sheet(item: $sharing) { file in ShareSheet(items: [file.url]) }
            .safeAreaInset(edge: .bottom) {
                if let notice {
                    Toast(text: notice)
                        .task(id: notice) {
                            try? await Task.sleep(for: .seconds(4))
                            self.notice = nil
                        }
                }
            }
        }
    }

    /// The only control on the screen. The stepper keeps its slot under all time rather than
    /// vanishing, so the row does not reflow with the choice.
    private var scopeSelector: some View {
        VStack(spacing: 8) {
            Picker("Scope", selection: Binding(
                get: { reports.isAllTime },
                set: { allTime in act { try allTime ? reports.selectAllTime() : reports.selectMonth() } }
            )) {
                Text("Month").tag(false)
                Text("All time").tag(true)
            }
            .pickerStyle(.segmented)
            HStack {
                Button { act { try reports.previousMonth() } } label: { MonthArrow(systemName: "chevron.left") }
                    .accessibilityLabel("Previous month")
                    .disabled(reports.isAllTime)
                Spacer()
                Text(ReportText.scopeLabel(reports.scope)).font(AppFont.titleMedium).foregroundStyle(Palette.ink)
                Spacer()
                Button { act { try reports.nextMonth() } } label: { MonthArrow(systemName: "chevron.right") }
                    .accessibilityLabel("Next month")
                    .disabled(!reports.hasNextMonth)
            }
        }
    }

    private var emptyScope: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Nothing recorded here").font(AppFont.titleMedium).foregroundStyle(Palette.ink)
            Text(ReportText.emptyMessage(reports.scope)).foregroundStyle(Palette.inkMuted)
        }
        .padding(.top, 24)
    }

    @ViewBuilder
    private func cards(_ figures: ReportFigures) -> some View {
        NumbersCard(figures: figures, trackingStart: reports.isAllTime ? reports.trackingStart : nil)
        SplitCard(figures: figures)
        DailyHoursCard(figures: figures)
        // Two bars or more: one bar states nothing the total above has not already stated.
        if figures.monthlySeries.count > 1 { MonthlyHoursCard(figures: figures) }
        StartTimesCard(figures: figures)
        SessionsPerDayCard(figures: figures)
        // Last, so it never competes with the figures, and only with a window to write: offering it
        // on an empty scope would offer a no-op.
        ReportCard(title: "Export Data") {
            Button("Export \(ReportText.scopeInline(reports.scope))", action: export)
                .buttonStyle(.tertiary)
        }
    }

    /// Writes the screen's window as CSV and opens the share sheet over it.
    private func export() {
        do {
            switch try reports.export(habitName: habits.selected?.name) {
            case .file(let url):
                sharing = SharedFile(url: url)
                notice = String(app: "CSV ready to share")
            case .nothing:
                notice = String(app: "No completed days to export yet")
            }
        } catch {
            notice = String(app: "Export failed: \(error.localizedDescription)")
        }
    }

    private func act(_ read: () throws -> Void) {
        do {
            try read()
            failed = false
        } catch {
            failed = true
        }
    }
}

/// A titled card on raised paper, as Android's `ChartCard` and `SectionCard` are.
private struct ReportCard<Content: View>: View {

    let title: LocalizedStringKey
    var subtitle: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(AppFont.cardTitle).foregroundStyle(Palette.ink)
                if let subtitle { Text(subtitle).font(AppFont.bodySmall).foregroundStyle(Palette.inkMuted) }
            }
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.paper200, in: RoundedRectangle(cornerRadius: 16))
    }
}

private struct StatRow: View {
    let label: LocalizedStringKey
    let value: String

    var body: some View {
        HStack {
            Text(label).foregroundStyle(Palette.inkMuted)
            Spacer()
            Text(value).monospacedDigit().foregroundStyle(Palette.ink)
        }
        .font(AppFont.bodyMedium)
        .accessibilityElement(children: .combine)
    }
}

/// Totals and averages, stated and left alone. The averages are rows, never rings: the only
/// denominator to fill a ring against is the user's own best.
private struct NumbersCard: View {
    let figures: ReportFigures
    let trackingStart: Kotlinx_datetimeLocalDate?

    var body: some View {
        ReportCard(title: "The Numbers") {
            if let trackingStart { StatRow(label: "Tracking since", value: ReportText.dateWithYear(trackingStart)) }
            StatRow(label: "Days tracked", value: ReportText.days(figures.totalDays))
            StatRow(label: "Showed up", value: ReportText.days(figures.showedUpDays))
            StatRow(label: "Missed", value: ReportText.days(figures.missedDays))
            Divider().overlay(Palette.outline.opacity(0.5))
            StatRow(label: "Total hours", value: SessionText.short(figures.totalWorkedMs))
            StatRow(label: "Total sessions", value: ReportText.sessions(figures.totalSessions))
            StatRow(label: "Hours per tracked day", value: SessionText.short(figures.avgDailyMs))
            StatRow(label: "Sessions per day showed up", value: ReportText.average(figures.avgSessionsPerDay))
        }
    }
}

private struct SplitCard: View {
    let figures: ReportFigures

    var body: some View {
        ReportCard(title: "Days Split") {
            HStack(spacing: 20) {
                DonutChartView(
                    values: [Int(figures.showedUpDays), Int(figures.missedDays)],
                    colors: [Palette.dayColor, Palette.outline],
                    emptyColor: Palette.outline,
                    description: ReportText.daysSplit(figures)
                )
                Legend(colors: [Palette.dayColor, Palette.outline], labels: [String(app: "Showed up"), String(app: "Missed")],
                       values: [Int(figures.showedUpDays), Int(figures.missedDays)])
            }
        }
    }
}

private struct DailyHoursCard: View {
    let figures: ReportFigures

    var body: some View {
        let hours = figures.dailySeries.map { Float($0.workedMs) / Float(ReportText.msPerHour) }
        let average = hours.isEmpty ? 0 : hours.reduce(0, +) / Float(hours.count)
        let days = figures.dailySeries.count
        ReportCard(title: "Daily Hours", subtitle: String(app: "\(days) days · dashed line = average")) {
            LineChartView(values: hours, reference: average, description: String(app: "Daily hours over \(days) days."))
                .frame(height: 140)
            HStack {
                Text(figures.dailySeries.first.map { ReportText.axisDay($0.date) } ?? "")
                Spacer()
                Text(figures.dailySeries.last.map { ReportText.axisDay($0.date) } ?? "")
            }
            .font(AppFont.labelSmall)
            .foregroundStyle(Palette.inkMuted)
            .accessibilityHidden(true)
        }
    }
}

/// Above this many bars there is no room for a total under each, and the first and last month label
/// the axis instead. VoiceOver hears every month either way.
private let monthLabelsUpTo = 6

private struct MonthlyHoursCard: View {
    let figures: ReportFigures

    var body: some View {
        let series = figures.monthlySeries
        let multiYear = Set(series.map { $0.month.year }).count > 1
        let label = { (point: MonthPoint) in ReportText.axisMonth(point.month, multiYear: multiYear) }
        let spoken = series.map { String(app: "\(label($0)) \(SessionText.short($0.workedMs))") }
            .joined(separator: ReportText.listSeparator)
        ReportCard(title: "Hours by Month") {
            BarChartView(
                values: series.map { Float($0.workedMs) / Float(ReportText.msPerHour) },
                description: String(app: "Hours by month: \(spoken)")
            )
            .frame(height: 120)
            Group {
                if series.count <= monthLabelsUpTo {
                    HStack(spacing: 0) {
                        ForEach(Array(series.enumerated()), id: \.offset) { _, point in
                            VStack(spacing: 2) {
                                Text(label(point))
                                Text(SessionText.short(point.workedMs)).monospacedDigit()
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                } else {
                    HStack {
                        Text(series.first.map(label) ?? "")
                        Spacer()
                        Text(series.last.map(label) ?? "")
                    }
                }
            }
            .font(AppFont.labelSmall)
            .foregroundStyle(Palette.inkMuted)
            .accessibilityHidden(true)
        }
    }
}

private struct StartTimesCard: View {
    let figures: ReportFigures

    var body: some View {
        let counts = [StartBucket.morning, .afternoon, .evening].map { Int(figures.startBuckets[$0]?.int32Value ?? 0) }
        let spoken = counts.map(ReportText.sessions)
        ReportCard(title: "Start Times") {
            SplitRow(
                values: counts,
                labels: [String(app: "Morning"), String(app: "Afternoon"), String(app: "Evening")],
                description: String(app: "Start times: \(spoken[0]) in the morning, \(spoken[1]) in the afternoon, \(spoken[2]) in the evening")
            )
        }
    }
}

private struct SessionsPerDayCard: View {
    let figures: ReportFigures

    var body: some View {
        let counts = [SessionBand.one, .two, .threePlus].map { Int(figures.sessionBands[$0]?.int32Value ?? 0) }
        let spoken = counts.map(ReportText.days)
        let average = ReportText.average(figures.avgSessionsPerDay)
        ReportCard(title: "Sessions Per Day") {
            SplitRow(
                values: counts,
                labels: [ReportText.sessions(1), ReportText.sessions(2), String(app: "3 or more")],
                description: String(app: "Sessions per day: \(spoken[0]) with one session, \(spoken[1]) with two, \(spoken[2]) with three or more. Average \(average).")
            )
        }
    }
}

/// A ring with a legend beside it, in three hues of equal weight: no slice is better than another,
/// so no slice is stronger.
private struct SplitRow: View {
    let values: [Int]
    let labels: [String]
    let description: String

    private let hues = Palette.descriptiveHues

    var body: some View {
        HStack(spacing: 20) {
            DonutChartView(values: values, colors: hues, emptyColor: Palette.outline, description: description)
            Legend(colors: hues, labels: labels, values: values)
        }
    }
}

private struct Legend: View {
    let colors: [Color]
    let labels: [String]
    let values: [Int]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(labels.indices, id: \.self) { index in
                HStack(spacing: 8) {
                    Circle().fill(colors[index]).frame(width: 10, height: 10)
                    Text(labels[index]).foregroundStyle(Palette.inkMuted)
                    Spacer(minLength: 8)
                    Text(verbatim: "\(values[index])").monospacedDigit().foregroundStyle(Palette.ink)
                }
                .font(AppFont.bodyMedium)
            }
        }
        .accessibilityHidden(true)
    }
}

/// The system share sheet over `items`.
private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
