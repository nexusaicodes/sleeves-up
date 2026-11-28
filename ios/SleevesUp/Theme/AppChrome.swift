import SwiftUI
import UIKit

/// The system chrome in the app's colours and type. The bars stay UIKit's own (the tab bar, the
/// navigation bar and its back button, swipe-back), so the app behaves as iOS apps do; only their
/// paint changes: paper behind, ink for what is selected or titled, Outfit titles, Manrope labels.
/// UIKit's appearance proxies hold fonts rather than text styles, and reach only bars created after
/// they are set, so a text-size change while the app runs rebuilds the appearances and hands them to
/// every bar already on screen (`refreshLiveBars`).
enum AppChrome {

    /// The bars' paint at the current text size.
    private struct Appearances {
        let nav: UINavigationBarAppearance
        let tab: UITabBarAppearance
        let segmentSelected: [NSAttributedString.Key: Any]
        let segmentNormal: [NSAttributedString.Key: Any]
    }

    /// The text size the bars were last painted at.
    @MainActor private static var paintedCategory: UIContentSizeCategory?

    /// Set once, before the first bar is drawn.
    @MainActor
    static func install() {
        paintedCategory = UIApplication.shared.preferredContentSizeCategory
        let paint = appearances()
        UINavigationBar.appearance().standardAppearance = paint.nav
        UINavigationBar.appearance().scrollEdgeAppearance = paint.nav
        UINavigationBar.appearance().compactAppearance = paint.nav
        UITabBar.appearance().standardAppearance = paint.tab
        UITabBar.appearance().scrollEdgeAppearance = paint.tab

        // The welcome tour's page dots.
        UIPageControl.appearance().currentPageIndicatorTintColor = Palette.UI.ink
        UIPageControl.appearance().pageIndicatorTintColor = Palette.UI.ink.withAlphaComponent(0.25)

        UISegmentedControl.appearance().selectedSegmentTintColor = Palette.UI.ink
        UISegmentedControl.appearance().backgroundColor = Palette.UI.paper200
        UISegmentedControl.appearance().setTitleTextAttributes(paint.segmentSelected, for: .selected)
        UISegmentedControl.appearance().setTitleTextAttributes(paint.segmentNormal, for: .normal)
    }

    /// After a text-size change: new bars take the proxies, bars already on screen are handed the
    /// rebuilt appearances directly.
    @MainActor
    static func refreshLiveBars() {
        // UIKit also posts the notification when nothing changed (at launch, for one); re-painting
        // live bars then is wasted work at best.
        let category = UIApplication.shared.preferredContentSizeCategory
        guard category != paintedCategory else { return }
        install()
        let paint = appearances()
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
        for window in windows { apply(paint, to: window) }
    }

    @MainActor
    private static func apply(_ paint: Appearances, to view: UIView) {
        if let bar = view as? UINavigationBar {
            bar.standardAppearance = paint.nav
            bar.scrollEdgeAppearance = paint.nav
            bar.compactAppearance = paint.nav
        } else if let bar = view as? UITabBar {
            bar.standardAppearance = paint.tab
            bar.scrollEdgeAppearance = paint.tab
        } else if let control = view as? UISegmentedControl {
            control.setTitleTextAttributes(paint.segmentSelected, for: .selected)
            control.setTitleTextAttributes(paint.segmentNormal, for: .normal)
        }
        for child in view.subviews { apply(paint, to: child) }
    }

    @MainActor
    private static func appearances() -> Appearances {
        let hairline = Palette.UI.ink.withAlphaComponent(0.2)

        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = Palette.UI.paper
        nav.shadowColor = hairline
        nav.titleTextAttributes = [
            .foregroundColor: Palette.UI.ink,
            .font: AppFont.uiFont(display: true, size: 17, weight: .semibold, textStyle: .headline),
        ]
        nav.largeTitleTextAttributes = [
            .foregroundColor: Palette.UI.ink,
            .font: AppFont.uiFont(display: true, size: 32, weight: .semibold, textStyle: .largeTitle),
        ]
        let button = UIBarButtonItemAppearance()
        button.normal.titleTextAttributes = [
            .font: AppFont.uiFont(display: false, size: 17, weight: .medium, textStyle: .body),
        ]
        nav.buttonAppearance = button
        nav.backButtonAppearance = button

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = Palette.UI.paper
        tab.shadowColor = hairline
        let label = AppFont.uiFont(display: false, size: 10, weight: .medium, textStyle: .caption2)
        for item in [tab.stackedLayoutAppearance, tab.inlineLayoutAppearance, tab.compactInlineLayoutAppearance] {
            item.normal.iconColor = Palette.UI.inkMuted
            item.normal.titleTextAttributes = [.foregroundColor: Palette.UI.inkMuted, .font: label]
            item.selected.iconColor = Palette.UI.ink
            item.selected.titleTextAttributes = [.foregroundColor: Palette.UI.ink, .font: label]
        }

        let segment = AppFont.uiFont(display: false, size: 13, weight: .medium, textStyle: .footnote)
        return Appearances(
            nav: nav,
            tab: tab,
            segmentSelected: [.foregroundColor: Palette.UI.paper, .font: segment],
            segmentNormal: [.foregroundColor: Palette.UI.ink, .font: segment]
        )
    }
}

extension View {
    /// A screen on the app's paper: a `List` or `Form` drops its grouped grey for it, and its rows sit
    /// on the raised paper (`rowOnPaper()`).
    func screenBackground() -> some View {
        scrollContentBackground(.hidden)
            .background(Palette.paper.ignoresSafeArea())
    }

    /// A grouped row on raised paper, as Android's cards are.
    func rowOnPaper() -> some View {
        listRowBackground(Palette.paper200)
    }
}
