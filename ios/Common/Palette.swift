import SwiftUI
import UIKit

/// The app's colours: mudda's light and dark schemes (navy ink on cream paper, cream ink on night
/// paper, brass for marks, vermilion as the one accent), the same values Android's `Color.kt` holds.
/// `docs/palette.md` has the reasoning and the contrast of every pair that carries text. Each colour
/// follows the system appearance on its own, so no view picks a scheme.
///
/// There is no red action, no success or danger colour and no ramp: vermilion means an error or
/// something needing attention, never decoration, and nothing here has a strength that grades.
enum Palette {
    /// The page.
    static let paper = Color(uiColor: UI.paper)
    /// A raised surface: cards, the sessions pill, grouped rows.
    static let paper200 = Color(uiColor: UI.paper200)
    /// A higher or pressed surface.
    static let paper300 = Color(uiColor: UI.paper300)
    /// Text and primary fills.
    static let ink = Color(uiColor: UI.ink)
    /// Quieter text.
    static let inkMuted = Color(uiColor: UI.inkMuted)
    /// Large labels and marks; under 4.5:1 on light paper, so never small text.
    static let brass = Color(uiColor: UI.brass)
    /// Brass surfaces under paper-coloured text.
    static let brassFill = Color(uiColor: UI.brassFill)
    /// The one accent: errors and what needs attention. Nothing decorative.
    static let vermilion = Color(uiColor: UI.vermilion)
    /// A control's boundary, clearing 3:1 on paper and paper-200.
    static let outline = Color(uiColor: UI.outline)
    /// The third chart hue, the one colour not in mudda.
    static let teal = Color(uiColor: UI.teal)

    /// The night scheme whatever the appearance, for what sits over the camera preview.
    static let nightPaper = Color(hex: 0x0E1830)
    static let nightInk = Color(hex: 0xEFE4CB)
    static let nightVermilion = Color(hex: 0xE5583B)

    // --- app tokens ------------------------------------------------------------------------------

    /// A recorded day on the calendar: brass at one fixed strength, never scaled by hours.
    static let dayColor = brass
    static let recordedDayAlpha = 0.40

    /// The Check-In action is coloured by what it does, never by an outcome: brass-fill to check in,
    /// ink to check out, each under paper.
    static let startAction = (fill: brassFill, label: paper)
    static let stopAction = (fill: ink, label: paper)

    /// Three hues of equal weight for the start-time and sessions-per-day splits; never one hue at
    /// three strengths, which would rank them.
    static let descriptiveHues = [ink, brass, teal]

    /// The open session's mark at rest: the mark's own colour, faint.
    static let trackAlpha = 0.15

    /// The same colours as `UIColor`s, for what UIKit draws (the bars' appearance).
    enum UI {
        static let paper = dynamic(light: 0xF1E6CE, dark: 0x0E1830)
        static let paper200 = dynamic(light: 0xE9DCC0, dark: 0x15233F)
        static let paper300 = dynamic(light: 0xDFD0B0, dark: 0x1C2C4D)
        static let ink = dynamic(light: 0x1B2A4A, dark: 0xEFE4CB)
        static let inkMuted = dynamic(light: 0x46536E, dark: 0xA7B0C2)
        static let brass = dynamic(light: 0x8C6A2E, dark: 0xC9A56A)
        static let brassFill = dynamic(light: 0x7F5F28, dark: 0xC9A56A)
        static let vermilion = dynamic(light: 0xB3341C, dark: 0xE5583B)
        static let outline = dynamic(light: 0x717886, dark: 0x6A7388)
        static let teal = dynamic(light: 0x2C6A70, dark: 0x79B2B0)

        private static func dynamic(light: UInt32, dark: UInt32) -> UIColor {
            UIColor { traits in UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light) }
        }
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
