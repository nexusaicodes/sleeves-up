import SwiftUI
import UIKit

/// The system chrome in the app's colours and type. The bars stay UIKit's own (the tab bar, the
/// navigation bar and its back button, swipe-back), so the app behaves as iOS apps do; only their
/// paint changes: paper behind, ink for what is selected or titled, Outfit titles, Manrope labels.
enum AppChrome {

    /// Set once, before the first bar is drawn.
    @MainActor
    static func install() {
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
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav

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
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab

        // The welcome tour's page dots.
        UIPageControl.appearance().currentPageIndicatorTintColor = Palette.UI.ink
        UIPageControl.appearance().pageIndicatorTintColor = Palette.UI.ink.withAlphaComponent(0.25)

        UISegmentedControl.appearance().selectedSegmentTintColor = Palette.UI.ink
        UISegmentedControl.appearance().backgroundColor = Palette.UI.paper200
        UISegmentedControl.appearance().setTitleTextAttributes(
            [.foregroundColor: Palette.UI.paper, .font: label.withSize(13)], for: .selected
        )
        UISegmentedControl.appearance().setTitleTextAttributes(
            [.foregroundColor: Palette.UI.ink, .font: label.withSize(13)], for: .normal
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
