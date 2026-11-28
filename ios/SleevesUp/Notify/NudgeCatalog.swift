import Foundation
import Shared

/// Habit reminders' copy, the Android app's strings under the same frozen keys (`NudgeCatalog.kt`,
/// `strings.xml`). Each English line is also its key in `Localizable.xcstrings`, whose Arabic is
/// Android's `values-ar`; `NudgeCatalogTests` reads all three and fails on any drift. A title holds
/// `%1$s` for the habit's name in either language, with a twin for a habit still owed one.
enum NudgeCatalog {

    struct Title {
        let key: String
        let named: String
        let unnamed: String
    }

    struct Body {
        let key: String
        let text: String
    }

    static let titles: [String: [Title]] = [
        "not_started": [
            Title(key: "not_started.t1", named: "%1$s: ready when you are", unnamed: "Ready when you are"),
            Title(key: "not_started.t2", named: "%1$s: the first session is the whole step", unnamed: "The first session is the whole step"),
            Title(key: "not_started.t3", named: "%1$s: start with a short one", unnamed: "Start with a short one"),
            Title(key: "not_started.t4", named: "%1$s: today could be the first day", unnamed: "Today could be the first day"),
        ],
        "steady": [
            Title(key: "steady.t1", named: "%1$s: make a start", unnamed: "Make a start"),
            Title(key: "steady.t2", named: "%1$s: worth showing up for", unnamed: "Worth showing up for"),
            Title(key: "steady.t3", named: "%1$s: time to show up", unnamed: "Time to show up"),
            Title(key: "steady.t4", named: "%1$s: a good moment to start", unnamed: "A good moment to start"),
        ],
        "returning": [
            Title(key: "returning.t1", named: "%1$s: today starts fresh", unnamed: "Today starts fresh"),
            Title(key: "returning.t2", named: "%1$s: one session makes the day", unnamed: "One session makes the day"),
            Title(key: "returning.t3", named: "%1$s: a small start counts", unnamed: "A small start counts"),
            Title(key: "returning.t4", named: "%1$s: today can count", unnamed: "Today can count"),
        ],
    ]

    static let bodies: [String: [Body]] = [
        "not_started": [
            Body(key: "not_started.b1", text: "Check in for a few minutes. That's all a first day needs."),
            Body(key: "not_started.b2", text: "Nothing left to set up. One check-in starts the record."),
            Body(key: "not_started.b3", text: "A short session counts exactly as much as a long one. Start with whichever fits."),
            Body(key: "not_started.b4", text: "The hardest session is the first. Make it a small one."),
            Body(key: "not_started.b5", text: "Nothing to prepare. Check in, and you've begun."),
            Body(key: "not_started.b6", text: "Ten minutes is a real start."),
            Body(key: "not_started.b7", text: "You don't need a plan for today, just a start."),
            Body(key: "not_started.b8", text: "Pick the smallest version of this you can do, and do that."),
            Body(key: "not_started.b9", text: "Every record begins with one day. This one is open."),
            Body(key: "not_started.b10", text: "Start before it feels ready. Showing up is what counts."),
            Body(key: "not_started.b11", text: "One session today gives the calendar its first mark."),
            Body(key: "not_started.b12", text: "However long it turns out to be, today can count."),
        ],
        "steady": [
            Body(key: "steady.b1", text: "Whenever you start, it counts — a short day is still a day shown up for."),
            Body(key: "steady.b2", text: "Any length counts. Start where you are."),
            Body(key: "steady.b3", text: "Today doesn't need to be big. It just needs a start."),
            Body(key: "steady.b4", text: "Show up for a few minutes and see where it goes."),
            Body(key: "steady.b5", text: "A quick session now is a whole day shown up for."),
            Body(key: "steady.b6", text: "You know the drill: check in, then begin."),
            Body(key: "steady.b7", text: "The day still has room for a session."),
            Body(key: "steady.b8", text: "Short and done counts as much as long and done."),
            Body(key: "steady.b9", text: "Start small. You can always keep going."),
            Body(key: "steady.b10", text: "Showing up is the whole job today."),
            Body(key: "steady.b11", text: "No need to feel ready. Starting is enough."),
            Body(key: "steady.b12", text: "Check in, even briefly, and today counts."),
        ],
        "returning": [
            Body(key: "returning.b1", text: "Every day starts fresh. A short session today counts in full."),
            Body(key: "returning.b2", text: "Ten minutes counts as much as any day ever did."),
            Body(key: "returning.b3", text: "Nothing is owed. Just today, at whatever length suits it."),
            Body(key: "returning.b4", text: "The smallest session still counts. Start with that."),
            Body(key: "returning.b5", text: "Today is its own day. Check in for a few minutes."),
            Body(key: "returning.b6", text: "No ground to make up — the record only asks about today."),
            Body(key: "returning.b7", text: "Five minutes is a whole day shown up for."),
            Body(key: "returning.b8", text: "Make it as small as you like. Starting is what counts."),
            Body(key: "returning.b9", text: "Today can count, whatever shape it takes."),
            Body(key: "returning.b10", text: "Easy does it. One short session is enough."),
            Body(key: "returning.b11", text: "There's no perfect moment. Now works."),
            Body(key: "returning.b12", text: "A short check-in today is a full day in the record."),
        ],
    ]
}

extension NudgeCatalog {

    /// A wording key as the shared planner reads it.
    final class Key: NSObject, NudgeVariant {
        let key: String
        init(_ key: String) { self.key = key }
    }

    /// `voice`'s pools of keys, for `NudgePlan`.
    static func keys(_ voice: NudgeVoice.Voice) -> VoiceKeys {
        let pool = voice.name.lowercased()
        return VoiceKeys(
            titles: (titles[pool] ?? []).map { Key($0.key) },
            bodies: (bodies[pool] ?? []).map { Key($0.key) }
        )
    }

    /// The title under `key` for a habit named `habitName`, or its unnamed twin while it has none,
    /// in `bundle`'s language.
    static func title(_ key: String, habitName: String?, in bundle: Bundle = .main) -> String? {
        guard let title = titles.values.joined().first(where: { $0.key == key }) else { return nil }
        guard let habitName else { return translated(title.unnamed, in: bundle) }
        return translated(title.named, in: bundle).replacingOccurrences(of: "%1$s", with: habitName)
    }

    static func body(_ key: String, in bundle: Bundle = .main) -> String? {
        bodies.values.joined().first { $0.key == key }.map { translated($0.text, in: bundle) }
    }

    /// `english`'s translation in `bundle`'s language, looked up by the English line itself, which
    /// is its key in the string catalog. The habit's name goes in after the lookup, so a name holding
    /// a `%` cannot be read as a format.
    private static func translated(_ english: String, in bundle: Bundle) -> String {
        bundle.localizedString(forKey: english, value: english, table: nil)
    }
}
