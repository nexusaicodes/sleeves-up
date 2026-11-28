import SwiftUI

/// The app's type, Android's `Type.kt` split: **Outfit** for display, headline and title sizes and
/// **Manrope** for body and label sizes, since their x-heights (0.460 and 0.540 em) make each wrong at
/// the other's size. Under Arabic, **IBM Plex Sans Arabic** replaces both, as SwiftUI does no
/// per-glyph fallback between custom faces. Sizes are Material 3's, and each scales with Dynamic Type
/// from the system style it is read beside.
///
/// The faces are Android's own files, bundled by `project.yml` and registered in `UIAppFonts`.
/// Ticking figures take `.monospacedDigit()`: Outfit's `1` is half the width of its `0`.
enum AppFont {

    static let displayMedium = display(45, .regular, relativeTo: .largeTitle)
    static let displaySmall = display(36, .regular, relativeTo: .largeTitle)
    static let headlineMedium = display(28, .regular, relativeTo: .title)
    static let headlineSmall = display(24, .regular, relativeTo: .title)
    static let titleLarge = display(22, .regular, relativeTo: .title2)
    static let titleMedium = display(16, .medium, relativeTo: .headline)

    /// The gauge's readout and the celebration's figure: display and headline sizes in bold, as
    /// Android's `SessionTimer` sets them.
    static let readout = display(45, .bold, relativeTo: .largeTitle)
    static let readoutCompact = display(28, .bold, relativeTo: .title)
    /// The Check-In action's label.
    static let actionLabel = display(16, .bold, relativeTo: .headline)
    /// A screen's own heading (the welcome tour, the naming step, the celebration).
    static let heading = display(28, .semibold, relativeTo: .title)
    /// A card's or a month's title.
    static let cardTitle = display(16, .semibold, relativeTo: .headline)

    static let bodyLarge = text(16, .regular, relativeTo: .body)
    static let bodyMedium = text(14, .regular, relativeTo: .subheadline)
    static let bodySmall = text(12, .regular, relativeTo: .caption)
    static let labelLarge = text(14, .medium, relativeTo: .callout)
    static let labelSmall = text(11, .medium, relativeTo: .caption2)

    /// Body text at Spark's highlight weight, the app's emphasis in running text.
    static let bodyLargeEmphasis = text(16, .medium, relativeTo: .body)
    static let bodyMediumEmphasis = text(14, .medium, relativeTo: .subheadline)
    /// The calendar's today number: in this palette today and other days share ink, so weight is
    /// half of what marks it.
    static let bodyMediumBold = text(14, .bold, relativeTo: .subheadline)

    enum Weight { case regular, medium, semibold, bold }

    /// The UIKit face for a bar title, which takes a `UIFont` rather than a `Font`.
    static func uiFont(display: Bool, size: CGFloat, weight: Weight, textStyle: UIFont.TextStyle) -> UIFont {
        let name = postScriptName(display ? displayFamily : textFamily, weight)
        let base = UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
        return UIFontMetrics(forTextStyle: textStyle).scaledFont(for: base)
    }

    private static func display(_ size: CGFloat, _ weight: Weight, relativeTo style: Font.TextStyle) -> Font {
        .custom(postScriptName(displayFamily, weight), size: size, relativeTo: style)
    }

    private static func text(_ size: CGFloat, _ weight: Weight, relativeTo style: Font.TextStyle) -> Font {
        .custom(postScriptName(textFamily, weight), size: size, relativeTo: style)
    }

    private static var arabic: Bool { AppLocale.current.language.languageCode == .arabic }
    private static var displayFamily: String { arabic ? "IBMPlexSansArabic" : "Outfit" }
    private static var textFamily: String { arabic ? "IBMPlexSansArabic" : "Manrope" }

    private static func postScriptName(_ family: String, _ weight: Weight) -> String {
        switch weight {
        case .regular: "\(family)-Regular"
        case .medium: "\(family)-Medium"
        case .semibold: "\(family)-SemiBold"
        case .bold: "\(family)-Bold"
        }
    }
}
