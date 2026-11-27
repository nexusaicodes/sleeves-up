import Foundation

/// The locale every screen, widget and notification formats in: the language iOS resolved for the
/// app (English or Arabic, from `Localizable.xcstrings`) in the device's region, with Latin digits
/// whatever the language, as Android's `nu-latn` extension gives. The region and its preferences
/// (week start, 12- or 24-hour clock) stay the device's.
enum AppLocale {

    static var current: Locale {
        var components = Locale.Components(locale: .current)
        if let language = Bundle.main.preferredLocalizations.first, language != "Base" {
            // The region lives in the language's components, so it is carried across.
            let region = components.languageComponents.region
            components.languageComponents = Locale.Language.Components(identifier: language)
            components.languageComponents.region = region
        }
        components.numberingSystem = "latn"
        return Locale(components: components)
    }

    /// A clock time ("10:00", "2:05 PM") as the device writes one, in Latin digits.
    static func clock(_ date: Date) -> String {
        date.formatted(Date.FormatStyle(date: .omitted, time: .shortened, locale: current))
    }
}

extension String {
    /// `value` in the app's language, its interpolated numbers in Latin digits. The parameter's
    /// type is what lets the compiler extract a literal passed here into the string catalog.
    init(app value: String.LocalizationValue) {
        self.init(localized: value, locale: AppLocale.current)
    }
}
