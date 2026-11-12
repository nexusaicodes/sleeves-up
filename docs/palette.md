# Palette

The app's colours: mudda's light and dark schemes in Spark's slots (`SparkLight`/`SparkDark` in `app/src/main/java/com/checkin/app/ui/theme/Color.kt`), the app tokens beside them, and the contrast of every pair that carries text. `Color.kt` is the source; this file is the reasoning and the contrast table, and changes with it.

The app takes mudda's two schemes: navy ink on cream paper in light, cream ink on night-navy paper in dark, brass for marks, and vermilion as the one accent. They go in through `lightSparkColors(…)` and `darkSparkColors(…)`. Nothing else about Spark changes; Spark's defaults would ship Leboncoin's orange.

Mudda's roles at 0.2.0:

| Role | Light | Dark | Mudda's use |
|---|---|---|---|
| `paper` | `#f1e6ce` | `#0e1830` | The page |
| `paper-200` | `#e9dcc0` | `#15233f` | A raised surface |
| `paper-300` | `#dfd0b0` | `#1c2c4d` | A higher or pressed surface |
| `ink` | `#1b2a4a` | `#efe4cb` | Text, primary fills |
| `ink-muted` | `#46536e` | `#a7b0c2` | Quieter text, the primary button's hover |
| `brass` | `#8c6a2e` | `#c9a56a` | Large labels and marks: under 4.5:1 on light paper, so never small text |
| `brass-fill` | `#7f5f28` | `#c9a56a` | Brass surfaces under paper-coloured text |
| `vermilion` | `#b3341c` | `#e5583b` | The one accent: focus, errors, what needs attention. Nothing decorative |

**How the roles map.** Spark's intents follow the mapping mudda already uses for daisyUI (its `engine.css`): primary → ink, secondary → brass-fill, accent and error → vermilion, neutral and info → ink-muted, success → ink, warning → brass-fill. Some Spark slots have no mudda colour: containers, variants, the outline and the dark elevation tint. Those are derived from mudda's own colours, and the **From** column says how. Mudda is the source; nothing gates the copy. Mudda-design plans an Android output (`values` and `values-night` resources, in its `docs/how-changes-flow.md`); once it exists, the mudda columns come from there and only the derived values stay in the app.

### Spark slots

Brand intents:

| Slot (`on…` after the slash) | Light | Dark | From |
|---|---|---|---|
| `main` / `onMain` | `#1b2a4a` / `#f1e6ce` | `#efe4cb` / `#0e1830` | ink / paper |
| `mainContainer` / `onMainContainer` | `#dfd0b0` / `#1b2a4a` | `#1c2c4d` / `#efe4cb` | paper-300 / ink |
| `mainVariant` / `onMainVariant` | `#46536e` / `#f1e6ce` | `#a7b0c2` / `#0e1830` | ink-muted (mudda's primary hover) / paper |
| `support` / `onSupport` | `#7f5f28` / `#f1e6ce` | `#c9a56a` / `#0e1830` | brass-fill / paper |
| `supportContainer` / `onSupportContainer` | `#dfd0b3` / `#1b2a4a` | `#2c2f39` / `#efe4cb` | Derived: brass-fill at 16% over paper / ink |
| `supportVariant` / `onSupportVariant` | `#665230` / `#f1e6ce` | `#d6bc8f` / `#0e1830` | Derived: brass-fill moved 25% toward ink (light) or white (dark) / paper |
| `accent` / `onAccent` | `#b3341c` / `#f1e6ce` | `#e5583b` / `#0e1830` | vermilion / paper |
| `accentContainer` / `onAccentContainer` | `#e8cdb5` / `#1b2a4a` | `#2c2132` / `#efe4cb` | Derived: vermilion at 14% over paper / ink |
| `accentVariant` / `onAccentVariant` | `#8d3228` / `#f1e6ce` | `#ea7962` / `#0e1830` | Derived: vermilion moved 25% toward ink (light) or 20% toward white (dark) / paper |

Status and utility intents (each with its `on…` colour paper, its container as named, and the container's `on…` colour ink):

| Slot | Takes | From |
|---|---|---|
| `success` | `main`'s values; container `mainContainer` | Mudda's adapter maps success to ink. The app uses no success intent (CLAUDE.md's Spark entry). |
| `alert` | `support`'s values; container `supportContainer` | Mudda's warning |
| `error` | `accent`'s values; container `accentContainer` | Mudda's error |
| `info`, `neutral` | ink-muted (`#46536e` / `#a7b0c2`); container paper-300 | Mudda's info and neutral |
| `focus` | vermilion; container paper-300 | Mudda's focus ring |
| `ai`, `business`, `service`, `new` | the first three take `neutral`'s values, `new` takes `main`'s | Unused by the app. Set anyway, so that no Leboncoin default ships |

Surfaces and lines:

| Slot | Light | Dark | From |
|---|---|---|---|
| `background` / `onBackground` | `#f1e6ce` / `#1b2a4a` | `#0e1830` / `#efe4cb` | paper / ink |
| `surface` / `onSurface` | as `background` | as `background` | Spark's own default keeps surface equal to background and raises a surface by elevation. Kept. |
| `backgroundVariant` / `onBackgroundVariant` | `#e9dcc0` / `#46536e` | `#15233f` / `#a7b0c2` | paper-200 / ink-muted. The bridge makes these M3's `surfaceVariant`/`onSurfaceVariant`, which here are `SectionCard`'s container and the app's secondary text. So the cards get mudda's raised paper and the secondary text gets its quieter ink. Spark's default `onBackgroundVariant` is the full text colour. |
| `surfaceInverse` / `onSurfaceInverse` | `#1b2a4a` / `#f1e6ce` | `#efe4cb` / `#0e1830` | ink / paper: the snackbar |
| `surfaceDark` / `onSurfaceDark` | `#1b2a4a` / `#f1e6ce` | `#15233f` / `#efe4cb` | ink / paper (light); paper-200 / ink (dark) |
| `surfaceTint` | as `surface` | `#4f7ebc` | Light: unused. Dark: derived so Spark's elevation steps land on mudda's night papers (see Elevation below) |
| `outline` | `#717886` | `#6a7388` | Derived: ink-muted mixed into paper until it clears 3:1 on paper and paper-200, the WCAG minimum for a control's boundary |
| `outlineHigh` | `#1b2a4a` | `#efe4cb` | ink. Spark's default is its near-black text colour. |
| `scrim`, `dimContent1`–`5` | Spark's | Spark's | Unchanged |

Mudda's hairline (ink at 20%, brass at 22% in dark) has no Spark slot; Spark draws dividers in `outline`, which is heavier. `SectionDivider` already draws `outline` at 50% (`#b1afaa` on light paper against the hairline's `#c6c0b4`), so the app's own rules stay close to mudda's.

### App tokens beside Spark's

| Token | Light | Dark | Notes |
|---|---|---|---|
| `dayColor()` | `#8c6a2e` | `#c9a56a` | brass, mudda's colour for marks, drawn on the calendar at `RECORDED_DAY_ALPHA` (0.40). |
| `startActionColors()` | `#7f5f28` / `#f1e6ce` | `#c9a56a` / `#0e1830` | brass-fill. Still the recorded day's hue, in the deeper brass mudda keeps for surfaces under text. In dark the two brasses are one value. |
| `stopActionColors()` | `#1b2a4a` / `#f1e6ce` | `#efe4cb` / `#0e1830` | ink |
| `descriptiveHues` | `#1b2a4a`, `#8c6a2e`, `#2c6a70` | `#efe4cb`, `#c9a56a`, `#79b2b0` | ink, brass and teal. Teal is the one colour not in mudda. |

**Why brass and ink for the action.**
- **No red in the app.** In mudda, red (vermilion) means errors and attention, so a red Check Out would say what `ButtonIntent.Danger` says, the reading the action-colour rule forbids.
- **The start half stays the recorded-day hue.** Brass marks the day and brass-fill carries the label, which is how mudda splits the two.
- **The stop half equals Spark's `main` and mudda's primary button.** Keep it as an app token anyway: start and stop stay one pair in one file, and the animated change between them keeps working. Don't reach for `ButtonIntent.Main`.
- **The two halves stand apart.** brass-fill against ink is 2.42:1 in light, and brass against cream ink is 1.83:1 in dark, where the hue gap (gold against cream) does more of the work; the label and the icon change with the colour.

**Why teal is the third chart hue.** The splits need three hues of equal weight. Mudda has two, ink and brass; its third colour, vermilion, is reserved for attention. Ink with ink-muted would be one hue at two strengths, a ramp. Under Spark's bridge `tertiary` is `support`, so hues read from the scheme's primary, tertiary and secondary would collapse to two colours (see below). The three must be app tokens. The teal has the following properties:
- **Its weight is close to brass's** on the chart card (paper-200): 4.54:1 against brass's 3.67:1 in light, and 6.55:1 against 6.75:1 in dark.
- **It survives colour blindness.** It sits across the blue–yellow axis from brass, which survives red–green colour blindness. In simulation it stays at ΔE ≥ 38 from brass under protanopia, deuteranopia and tritanopia, in both themes.
- **Ink is the heaviest of the three** (10.47:1 on paper-200 in light). The categories have no order, but if the ink segment reads as the important one on a device, ink-muted (`#46536e` / `#a7b0c2`, 5.68:1 / 7.17:1) is the fallback. With ink itself out of the trio, that is no longer one hue at two strengths.

**The recorded-day alpha is 0.40.** A brass cell then stands off the page at 1.63:1 in light and 2.23:1 in dark (0.35 gave only 1.52:1 and 1.99:1), and the day number on the cell stays at 7.04:1 and 6.24:1. It is one fixed constant, so the binary rule is untouched.

### Contrast, every pair that carries text or must be seen

| Pair | Light | Dark | Needs |
|---|---|---|---|
| ink on paper / paper-200 / paper-300 | 11.48 / 10.47 / 9.34 | 13.95 / 12.37 / 10.96 | 4.5 |
| ink-muted on paper / paper-200 / paper-300 | 6.23 / 5.68 / 5.06 | 8.08 / 7.17 / 6.35 | 4.5 |
| paper on ink (`main`, Check Out) | 11.48 | 13.95 | 4.5 |
| paper on brass-fill (`support`, Check In) | 4.75 | 7.61 | 4.5 |
| paper on vermilion (`accent`, `error`) | 4.95 | 4.86 | 4.5 |
| paper on ink-muted (`mainVariant`, `neutral`) | 6.23 | 8.08 | 4.5 |
| paper on `supportVariant` / `accentVariant` | 6.02 / 6.47 | 9.60 / 6.22 | 4.5 |
| ink on `supportContainer` / `accentContainer` | 9.35 / 9.37 | 10.57 / 12.13 | 4.5 |
| vermilion text on paper / paper-200 | 4.95 / 4.52 | 4.86 / **4.31** | 4.5 |
| `outline` on paper / paper-200 | 3.58 / 3.27 | 3.71 / 3.29 | 3 |
| Unfocused text-field border (`outlineHigh` at 56%) on paper | 3.34 | 5.12 | 3 |
| Day number on a recorded cell, alpha 0.35 / 0.40 | 7.57 / 7.04 | 7.02 / 6.24 | 4.5 |
| Chart hues on paper-200: ink / brass / teal | 10.47 / 3.67 / 4.54 | 12.37 / 6.75 / 6.55 | 3 |

**There is one miss: vermilion as small text on paper-200 in dark (4.31:1).** The one red text, the presence check's error line, sits on the page, not on a card, so nothing hits it. Keep error text off `backgroundVariant` surfaces. If one ever needs it, lift the dark error to `#e66649` (vermilion moved 10% toward cream: 4.73:1 there, 5.34:1 under its night label) rather than move mudda's vermilion.

## What Spark's source does with colour

Read at the 4.2.1 tag. These hold whatever the palette; the tables above already account for them.

1. **The M3 bridge folds slots together.** `asMaterial3Colors` sets `tertiary` to `support`, all five `surfaceContainer…` roles and `surfaceBright`/`surfaceDim` to `surface`, `outlineVariant` to `outlineHigh`, and `inversePrimary` to `mainVariant`. Consequences for anything that reads M3 roles:
   - **Scheme-derived chart hues lose one**, which is why `descriptiveHues` are app tokens.
   - **The M3 `TimePicker` loses its dial.** Its dialog (`surfaceContainerHigh`) and its clock dial (`surfaceContainerHighest`) become the same colour, so the dial's disc vanishes into the dialog. `ReminderTimePicker` therefore passes `TimePickerDefaults.colors(clockDialColor = …, timeSelectorUnselectedContainerColor = …)` explicitly.
   - **A bare `HorizontalDivider` draws in ink**, since it uses `outlineVariant`, the strongest line in the app. `SectionDivider` exists for that reason.
   - **M3 menus and dialogs draw on `surface`**, which is paper.
2. **Selection is `support` throughout Spark**: `RadioButton` (default `IntentColors.Support`), the chips (`ChipIntent.Support`), `SegmentedControl` (a `supportContainer` fill with a `support` border), the tabs' support intent, and the rail and drawer indicators. So the Reports scope selector, the language radio and the lit tab all draw in brass. That is mudda's own secondary, and it makes brass mean "recorded", "check in" and "selected": fine as marks, but it is why the calendar's selected day is a 2dp ink ring rather than a fill.
3. **Buttons and fields match mudda's actions without configuration.** `ButtonFilled` defaults to `ButtonIntent.Main`, an ink fill, which is mudda's primary button. Outlined borders are `outline`. A `TextField`'s border is `outlineHigh` (ink) when focused and `outlineHigh` at `dim2` (56%) when not. Keyboard focus uses the `focus` intent, so the ring is vermilion, as in mudda.
4. **Elevation behaves differently by theme.** `surfaceColorAtElevation` returns `surface` unchanged when its luminance is 0.5 or more, and light paper's is 0.79. So in light only shadows raise a surface. In dark it overlays `surfaceTint` at (4.5 · ln(elevation + 1) + 2)%. Spark's white tint would turn night navy grey (`#283146` at 6dp). `#4f7ebc` lands 6dp exactly on mudda's paper-200 (`#15233f`), 1dp on `#111d37`, 3dp on `#13203c` and 12dp on `#172643`, so raised surfaces walk up mudda's own night steps.
5. **`accent` is read by `ButtonBoost`, the highlighted `Card`, `Popover`, `SegmentedGauge` and accent `Tag`s.** The app uses none of them, and vermilion stays for attention only.
6. **Spark also ships `lightHighContrastSparkColors` and `darkHighContrastSparkColors`.** The app offers no high-contrast scheme, so these are out of scope.
