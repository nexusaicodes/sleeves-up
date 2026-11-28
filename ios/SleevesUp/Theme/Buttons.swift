import SwiftUI

/// The app's buttons, Spark's rebranded set: `primary` is ink (one per screen), `secondary` brass,
/// `tertiary` outlined and `ghost` text alone. Each keeps a 48-point target whatever it looks like.
/// None is ever red: an action is coloured by what it does, never by an outcome.
struct AppButtonStyle: ButtonStyle {

    enum Kind { case primary, secondary, tertiary, ghost }

    var kind: Kind = .primary
    var height: CGFloat = 56
    var font: Font = AppFont.titleMedium

    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(font)
            .lineLimit(2)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 24)
            .frame(minHeight: kind == .ghost ? 48 : height)
            .foregroundStyle(labelColor)
            .background(fill, in: Capsule())
            .overlay {
                if kind == .tertiary { Capsule().strokeBorder(Palette.outline, lineWidth: 1) }
            }
            .contentShape(Capsule())
            .opacity(isEnabled ? (configuration.isPressed ? 0.8 : 1) : 0.38)
    }

    private var fill: Color {
        switch kind {
        case .primary: Palette.ink
        case .secondary: Palette.brassFill
        case .tertiary, .ghost: .clear
        }
    }

    private var labelColor: Color {
        switch kind {
        case .primary, .secondary: Palette.paper
        case .tertiary, .ghost: Palette.ink
        }
    }
}

extension ButtonStyle where Self == AppButtonStyle {
    static var primary: AppButtonStyle { AppButtonStyle(kind: .primary) }
    static var secondary: AppButtonStyle { AppButtonStyle(kind: .secondary) }
    static var tertiary: AppButtonStyle { AppButtonStyle(kind: .tertiary) }
    static var ghost: AppButtonStyle { AppButtonStyle(kind: .ghost, font: AppFont.labelLarge) }
}

/// A transient notice drawn over a screen: Android's snackbar, ink under paper.
struct Toast: View {
    let text: String

    var body: some View {
        Text(text)
            .font(AppFont.bodyMedium)
            .foregroundStyle(Palette.paper)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Palette.ink, in: RoundedRectangle(cornerRadius: 12))
            .padding()
    }
}
