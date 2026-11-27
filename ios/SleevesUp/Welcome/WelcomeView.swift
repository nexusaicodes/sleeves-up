import Shared
import SwiftUI

/// One page of the welcome tour, Android's `WelcomePages` copy in the app's language.
struct WelcomePage: Identifiable {
    let id: Int
    let symbol: String
    let title: String
    let body: String
    /// Below the body: how to place the widget, which iOS gives an app no way to do for the user.
    var note: String?

    static var widget: WelcomePage {
        WelcomePage(
            id: 2,
            symbol: "square.grid.2x2",
            title: String(app: "On your home screen"),
            body: String(app: "Add the widget to check in or out without opening the app first. The same presence check runs."),
            note: String(app: "Touch and hold an empty part of your Home Screen, tap Edit, then Add Widget.")
        )
    }

    /// The quote, the thesis, the widget, habits. The tour names nothing; the naming step follows.
    static var all: [WelcomePage] {
        [
            WelcomePage(
                id: 0,
                symbol: "quote.opening",
                title: String(app: "Make it a habit"),
                body: String(app: "“We are what we repeatedly do. Excellence, then, is not an act, but a habit.” — Will Durant, on Aristotle")
            ),
            WelcomePage(
                id: 1,
                symbol: "face.smiling",
                title: String(app: "Showing up is what counts"),
                body: String(app: "Check in with a glance at the camera, and check out the same way. Your time is logged, but showing up is what counts.")
            ),
            widget,
            WelcomePage(
                id: 3,
                symbol: "square.stack",
                title: String(app: "One habit at a time"),
                body: String(app: "Start with one habit you want to build, and add more later — each keeps its own record.")
            ),
        ]
    }
}

/// The first-run welcome tour: four pages, Skip and Next, then Get started on the last. A finish
/// and a skip differ only in the notification ask that follows (`FirstRun.asksNotificationsAfter`).
struct WelcomeView: View {

    let onExit: (FirstRun.Exit) -> Void

    @State private var page = 0

    private var isLast: Bool { page == WelcomePage.all.count - 1 }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if !isLast {
                    Button("Skip") { onExit(.skipped) }
                }
            }
            .frame(height: 44)
            .padding(.horizontal)
            TabView(selection: $page) {
                ForEach(WelcomePage.all) { item in
                    WelcomePageView(page: item).tag(item.id)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))
            Button(isLast ? LocalizedStringKey("Get started") : LocalizedStringKey("Next")) {
                if isLast { onExit(.finished) } else { withAnimation { page += 1 } }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding()
            .accessibilityIdentifier("welcomeAction")
        }
    }
}

struct WelcomePageView: View {
    let page: WelcomePage

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: page.symbol)
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text(page.title)
                .font(.title.weight(.semibold))
                .multilineTextAlignment(.center)
            Text(page.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            if let note = page.note {
                Text(note)
                    .font(.footnote)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Spacer()
        }
        .padding(.horizontal, 32)
    }
}

/// The widget page alone, for an install that finished the tour before it held this page, once
/// `FirstRun.widgetPageOwed` says so. Done, or swiping it away, marks it seen.
struct WidgetPageView: View {

    let onDone: () -> Void

    var body: some View {
        VStack {
            WelcomePageView(page: WelcomePage.widget)
            Button("Done", action: onDone)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding()
        }
    }
}
