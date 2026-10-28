# Moving the UI to Spark: design plan and coverage gaps

**Scope.** This is about design only: what changes on screen, what maps onto Spark cleanly, and what needs its own design work. The toolchain upgrade is a separate prerequisite, done on `build/kotlin2-spark-ready`; section 9 has what it settles and what a trial build with Spark found.

**Reference.** [leboncoin/spark-android](https://github.com/leboncoin/spark-android) **4.2.1**, MIT licence. Its catalog app (`com.adevinta.spark.catalog`) was built from that tag and installed on the Nothing Phone for browsing. Component names below are Spark's public composables at that tag, and the colour findings in 2.3 come from reading its source there.

**Colours.** [nexusaicodes/mudda-design](https://github.com/nexusaicodes/mudda-design) **0.2.0** (`96d7813`): its light and dark schemes, `tokens/semantic/color.light.json` and `color.dark.json`. Only the colours are taken. Mudda's type (Amiri and Plex), its 3px corner and its no-shadow rule are not; Spark keeps its own type, shapes, elevation and motion.

---

## 1. The short answer

The routine Material 3 controls map across almost one for one: buttons, dialogs, chips, the radio button, the text field, the menu, the snackbar, the top bar and bottom bar, dividers and cards. That is roughly a third of the UI and the least distinctive third.

The rest falls into four groups.

| Group | What's in it | Why |
|---|---|---|
| **A. Foundations** | Typography, colour roles, the start/stop action colours, the recorded-day colour | Spark's theme is built differently from this app's: one font family, intents in place of semantic roles. Colour is settled in 2.2: mudda's palette in Spark's slots. |
| **B. Brand and data surfaces with no Spark equivalent** | The breathing gauge, the brand grid and the celebration; charts; the History calendar; the session ledger rows | Spark has nothing like them, and the components that look close (Gauge, Meter, Progress, Rating) break the app's no-grading rules |
| **C. Missing pieces** | The time picker, the pager dots, full-screen messages and empty states, the habit switcher, the sessions pill | No Spark component; these stay ours and are restyled in Spark's tokens |
| **D. Surfaces Spark cannot reach** | The home-screen widget, notifications, launcher and splash icons, system dialogs, Play assets | Not Compose, or not the app's to draw |

So the real question is not whether Spark covers the app. It is **whose visual identity the app takes on.** Spark styles the third of the UI that carries no identity, and the screens that define the app (Check In, History, Reports, the celebration) stay hand-built in any version of this plan.

---

## 2. Foundations: decide these before touching any screen

`SparkTheme` wraps `MaterialTheme`, mapping its own tokens onto M3's (`asMaterial3Colors`, `asMaterial3Typography`, `asMaterial3Shapes`). **Every M3 component that isn't converted still picks up Spark's colours, type and shapes.** That is what makes a gradual migration possible: switch the theme first and every screen moves together, then swap components one at a time.

### 2.1 Typography: needs dedicated thought

| | This app today (`ui/theme/Type.kt`) | Spark |
|---|---|---|
| Families | **Two**, split by reading size: Outfit for display, headline and title; Manrope for body and label | **One** (`SparkFontFamily`), stamped onto every style (`display1.copy(fontFamily = fontFamily.default)` and so on) |
| Default face | — | Nunito Sans |
| Arabic | IBM Plex Sans Arabic **replaces both** under `ar` (`typographyFor(locale)`), because Compose does no per-glyph fallback | Nunito Sans has no Arabic glyphs, so Arabic would fall back to the system face |
| Scale | M3: display, headline, title, body and label, each S/M/L | `display1–3`, `headline1–2`, `subhead`, `body1–2`, `caption`, `small`, `callout` |
| Ticking figures | `tabularFigures()` is load-bearing: Outfit's `1` is half the width of its `0`, so without it the gauge lurches every second | No tabular setting in the tokens |

Decisions:
- **Keep two families or accept one?** Spark's API wants one. Keeping two means building our own `SparkTypography` with a family per style and **not** passing `fontFamily`, which would overwrite them. That works, but it fights the system you are adopting.
- **Arabic must stay Plex**, chosen by locale at the theme root, as today. Pass `sparkFontFamily(plexArabic)` under `ar`.
- **Map the two scales.** Spark's scale is coarser (no S/M/L per role). Every `MaterialTheme.typography.X` call site needs a decided equivalent, and the two places that step a style down at large font scales (the gauge readout's `displayMedium` → `headlineMedium`) need the same step in Spark's names.
- **Tabular figures stay a per-call-site `TextStyle`**, whatever the face.

### 2.2 Colour: mudda's light and dark schemes in Spark's slots

**Decision.** The app takes mudda's two schemes: navy ink on cream paper in light, cream ink on night-navy paper in dark, brass for marks, and vermilion as the one accent. They go in through `lightSparkColors(…)` and `darkSparkColors(…)`. Nothing else about Spark changes. Spark's defaults would ship Leboncoin's orange, and today's indigo is an M3 tonal palette with no system behind it.

Mudda's roles at 0.2.0, for reference:

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

#### Spark slots

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
| `success` | `main`'s values; container `mainContainer` | Mudda's adapter maps success to ink. The app uses no success intent (section 4). |
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
| `backgroundVariant` / `onBackgroundVariant` | `#e9dcc0` / `#46536e` | `#15233f` / `#a7b0c2` | paper-200 / ink-muted. The bridge makes these M3's `surfaceVariant`/`onSurfaceVariant`, which here are `SectionCard`'s container and the app's secondary text (31 call sites). So the cards get mudda's raised paper and the secondary text gets its quieter ink. Spark's default `onBackgroundVariant` is the full text colour. |
| `surfaceInverse` / `onSurfaceInverse` | `#1b2a4a` / `#f1e6ce` | `#efe4cb` / `#0e1830` | ink / paper: the snackbar |
| `surfaceDark` / `onSurfaceDark` | `#1b2a4a` / `#f1e6ce` | `#15233f` / `#efe4cb` | ink / paper (light); paper-200 / ink (dark) |
| `surfaceTint` | as `surface` | `#4f7ebc` | Light: unused (2.3). Dark: derived so Spark's elevation steps land on mudda's night papers (2.3) |
| `outline` | `#717886` | `#6a7388` | Derived: ink-muted mixed into paper until it clears 3:1 on paper and paper-200, the WCAG minimum for a control's boundary |
| `outlineHigh` | `#1b2a4a` | `#efe4cb` | ink. Spark's default is its near-black text colour. |
| `scrim`, `dimContent1`–`5` | Spark's | Spark's | Unchanged |

Mudda's hairline (ink at 20%, brass at 22% in dark) has no Spark slot; Spark draws dividers in `outline`, which is heavier. `SectionDivider` already draws `outline` at 50% (`#b1afaa` on light paper against the hairline's `#c6c0b4`), so the app's own rules stay close to mudda's.

#### App tokens beside Spark's

| Token | Light | Dark | Notes |
|---|---|---|---|
| `dayColor()` | `#8c6a2e` | `#c9a56a` | brass, mudda's colour for marks. At `RECORDED_DAY_ALPHA` a cell reads `#cebb96` / `#4f4944` (see the alpha note below). |
| `startActionColors()` | `#7f5f28` / `#f1e6ce` | `#c9a56a` / `#0e1830` | brass-fill. Still the recorded day's hue, in the deeper brass mudda keeps for surfaces under text. In dark the two brasses are one value. |
| `stopActionColors()` | `#1b2a4a` / `#f1e6ce` | `#efe4cb` / `#0e1830` | ink |
| `descriptiveHues` | `#1b2a4a`, `#8c6a2e`, `#2c6a70` | `#efe4cb`, `#c9a56a`, `#79b2b0` | ink, brass and teal. Teal is the one colour not in mudda. |

**Why brass and ink for the action.**
- **No red is left in the app.** In mudda, red (vermilion) means errors and attention, so a red Check Out would say what `ButtonIntent.Danger` says, the reading the action-colour rule forbids. "Nothing renders red" now holds for the whole app, not only the calendar.
- **The start half stays the recorded-day hue.** Brass marks the day and brass-fill carries the label, which is how mudda splits the two.
- **The stop half equals Spark's `main` and mudda's primary button.** Keep it as an app token anyway: start and stop stay one pair in one file, and the animated change between them keeps working. Don't reach for `ButtonIntent.Main`.
- **They are further apart than today's pair.** brass-fill against ink is 2.42:1 in light, and brass against cream ink is 1.83:1 in dark. Today's green against red is 1.10:1 and 1.48:1. In dark the hue gap (gold against cream) is the smaller one, so judge the dark change on a device; the label and the icon change with it.

**Why teal is the third chart hue.** The splits need three hues of equal weight. Mudda has two, ink and brass; its third colour, vermilion, is reserved for attention. Ink with ink-muted would be one hue at two strengths, a ramp. Under Spark's bridge `tertiary` is `support`, so today's `descriptiveHues()` (primary, tertiary, secondary) would collapse to two colours anyway (2.3). The three must be app tokens. The teal has the following properties:
- **Its weight is close to brass's** on the chart card (paper-200): 4.54:1 against brass's 3.67:1 in light, and 6.55:1 against 6.75:1 in dark.
- **It survives colour blindness.** It sits across the blue–yellow axis from brass, which survives red–green colour blindness. In simulation it stays at ΔE ≥ 38 from brass under protanopia, deuteranopia and tritanopia, in both themes.
- **Ink is the heaviest of the three** (10.47:1 on paper-200 in light). The categories have no order, but if the ink segment reads as the important one on a device, ink-muted (`#46536e` / `#a7b0c2`, 5.68:1 / 7.17:1) is the fallback. With ink itself out of the trio, that is no longer one hue at two strengths.

**The recorded-day alpha.** At today's `RECORDED_DAY_ALPHA` (0.35), a brass cell stands off the page less than the green does: 1.52:1 against 1.63:1 in light, and 1.99:1 against 2.17:1 in dark. 0.40 restores that separation (1.63:1 and 2.23:1), and the day number on the cell stays at 7.04:1 and 6.24:1. It is still one fixed constant, so the binary rule is untouched.

#### Contrast, every pair that carries text or must be seen

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

**There is one miss: vermilion as small text on paper-200 in dark (4.31:1).** The one red text today, the presence check's error line, sits on the page, not on a card, so nothing hits it. Keep error text off `backgroundVariant` surfaces. If one ever needs it, lift the dark error to `#e66649` (vermilion moved 10% toward cream: 4.73:1 there, 5.34:1 under its night label) rather than move mudda's vermilion.

### 2.3 What Spark's source does with colour

Read at the 4.2.1 tag. These hold whatever the palette; the palette above already accounts for them.

1. **The M3 bridge folds slots together.** `asMaterial3Colors` sets `tertiary` to `support`, all five `surfaceContainer…` roles and `surfaceBright`/`surfaceDim` to `surface`, `outlineVariant` to `outlineHigh`, and `inversePrimary` to `mainVariant`. Consequences for M3 components the app keeps or hasn't swapped yet:
   - **`descriptiveHues()` loses a hue**, as above.
   - **The M3 `TimePicker` loses its dial.** Its dialog (`surfaceContainerHigh`) and its clock dial (`surfaceContainerHighest`) become the same colour, so the dial's disc vanishes into the dialog. `ReminderTimePicker` must pass `TimePickerDefaults.colors(clockDialColor = …, timeSelectorUnselectedContainerColor = …)` explicitly; `backgroundVariant` (paper-200) is the obvious value.
   - **A bare `HorizontalDivider` draws in ink**, since it uses `outlineVariant`, which is now the strongest line in the app. `SectionDivider`'s KDoc already forbids a bare divider; the reason now holds in light too.
   - **M3 menus, dialogs and the M3 `NavigationBar` all draw on `surface`**, which is paper. The `NavigationBar`'s indicator is `secondaryContainer`, which is `supportContainer` (a brass tint).
2. **Selection is `support` throughout Spark**: `RadioButton` (default `IntentColors.Support`), the chips (`ChipIntent.Support`), `SegmentedControl` (a `supportContainer` fill with a `support` border), the tabs' support intent, and the rail and drawer indicators. So the Reports scope selector, the language radio and the lit tab all draw in brass. That is mudda's own secondary, and it makes brass mean "recorded", "check in" and "selected": fine as marks. It does break the calendar's selected cell, though; see 3.2.
3. **Buttons and fields match mudda's actions without configuration.** `ButtonFilled` defaults to `ButtonIntent.Main`, an ink fill, which is mudda's primary button. Outlined borders are `outline`. A `TextField`'s border is `outlineHigh` (ink) when focused and `outlineHigh` at `dim2` (56%) when not. Keyboard focus uses the `focus` intent, so the ring is vermilion, as in mudda.
4. **Elevation behaves differently by theme.** `surfaceColorAtElevation` returns `surface` unchanged when its luminance is 0.5 or more, and light paper's is 0.79. So in light only shadows raise a surface. In dark it overlays `surfaceTint` at (4.5 · ln(elevation + 1) + 2)%. Spark's white tint would turn night navy grey (`#283146` at 6dp). `#4f7ebc` lands 6dp exactly on mudda's paper-200 (`#15233f`), 1dp on `#111d37`, 3dp on `#13203c` and 12dp on `#172643`, so raised surfaces walk up mudda's own night steps.
5. **`accent` is read by `ButtonBoost`, the highlighted `Card`, `Popover`, `SegmentedGauge` and accent `Tag`s.** None is in this plan; keep it that way, since vermilion here is for attention only.
6. **Spark also ships `lightHighContrastSparkColors` and `darkHighContrastSparkColors`.** The app offers no high-contrast scheme, so these are out of scope.

### 2.4 Shape

Spark's scale (`extraSmall` 4dp → `small` 8 → `medium` 12 → `large` 16 → `extraLarge` 28, plus `full`) is M3-shaped and maps directly. The one constraint is the **brand mark**: `BrandGrid`'s `CORNER_FRAC` (0.22) and `FILL_FRAC` (0.88) come from `play-store-assets/generate_icons.py` and must **not** take a Spark shape token.

---

## 3. Component by component

Legend:
- **Direct**: swap the composable.
- **Configure**: swap, but a setting or slot needs deciding.
- **Ours**: keep our component and restyle it in Spark's tokens.
- **Design**: needs dedicated design thought.
- **Avoid**: a Spark component that looks like a fit but breaks a product rule.

### 3.1 Check In tab

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Check In / Check Out button | M3 `Button`, 64dp (`ACTION_HEIGHT`), colours animated between start and stop so it reads as one control changing state | `ButtonFilled` at 44 or 56dp, colour from an intent | **Design.** Height, the start/stop colours (brass-fill to start, ink to stop; 2.2) and the animated change don't fit an intent-based button. Either a custom `ButtonColors` (if Spark exposes it) or keep ours. The 64dp is inside a hand-kept fit budget (`FIXED_CONTENT_HEIGHT`), so any height change moves the budget. |
| Breathing gauge (`BrandGridGauge`) | The brand lattice, a wave passing over seven cells; it says only whether a session is open | `SegmentedGauge`, `Meter`, `Progressbar`, `CircularProgressIndicator` | **Ours, and Avoid the Spark lookalikes.** Every Spark gauge, meter and progress bar fills toward a goal, which is the deleted daily target in visual form. Its colour needs no work: it reads `primary`, which becomes ink, the same colour as the Check Out button showing beneath it while it breathes. |
| Readout (`0m 1s`) | `displayMedium`, tabular figures, `maxLines = 1`, steps down at large font scales | type token | **Configure:** pick the Spark style and keep `tnum` and the step-down. |
| Sessions pill (`TodaySessions`) | `Surface` pill, 48dp touch floor, expands a ledger | `Chip` / `Tag` | **Design.** A chip reads as a filter and a tag as a label; this is a disclosure. Probably ours, in Spark shapes. |
| Session ledger (`SessionIntervalRow`) | Two columns: clock range and duration, with the duration column scaling with font scale and capped | `ListItem` | **Ours.** `ListItem`'s slots don't express the column rule that keeps "2h 15m" from rendering as "2h". |
| Check-out celebration (`CheckOutCelebration`, `BrandGrid(settle = true)`) | Full screen, the seven cells landing in order; identical at 20 minutes and at 9 hours | none | **Ours.** Restyle the type only. |

### 3.2 History tab

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Calendar (`CalendarGrid`) | Binary: a day has a session or it doesn't. Cell height derived from the viewport, 48–80dp. Fades only days outside the record. | none | **Ours, and one Design item.** A recorded day becomes brass (`dayColor()`, an app token; 2.2), ideally at alpha 0.40. The selected cell needs a change. Today it fills with `primaryContainer`, which becomes paper-300, only 1.33:1 from a recorded cell in light: tan on tan, so a tapped empty day looks recorded. Draw the selection as a 2dp ink ring over the cell's own state instead (7.04:1 on a recorded cell, 11.48:1 on paper). That also stops the selection hiding whether the selected day was recorded, which the fill does today. Today's number and dot follow `primary` into ink. |
| Month selector | Icon buttons and a title | `IconButtonGhost` plus a type token | **Direct.** |
| Selected-day slot (header, rows, empty state) | `DayHeader`, `SessionIntervalRow`, `EmptyState` | `ListItem`, none | **Ours.** `DETAIL_SLOT_HEIGHT` is sized to the tallest state and must be re-measured if the empty state's type changes. |

### 3.3 Reports tab

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Scope selector (month / all time) | M3 `FilterChip` and a month stepper | `ChipSelectable`, or `SegmentedControl` | **Configure.** A two-option segmented control may read better than chips. Decide which. Either way the selected option draws in brass (`support`; 2.3). |
| Cards (`ChartCard`, `SectionCard`) | M3 `Card` and our layout | `Card` / `OutlinedCard` | **Direct** for the container; the content inside stays ours. `SectionCard`'s container is `surfaceVariant`, which becomes paper-200, mudda's raised paper. Don't use Spark's highlighted card: its border is `accent`, which is vermilion. |
| Line, bar and donut charts | Hand-rolled `Canvas` over pure `ChartGeometry`; mirrored under RTL; an all-zero window draws as zeros | none | **Ours.** Restyle the strokes, axes and legend type in Spark tokens. The three split hues become ink, brass and teal app tokens (2.2); the missed-day, baseline and reference strokes keep `outline`. |
| `StatsRow`, `LegendRow`, `AxisLabel` | Plain rows | none | **Ours**, with Spark type. |
| Averages | Deliberately plain rows, never arcs | `Meter`, `SegmentedGauge`, `RatingDisplay` | **Avoid.** An arc or meter has only the user's own best to fill against. |

### 3.4 Habits, Settings and About

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Habit switcher (`HabitSwitcher`) | The top bar's end slot: the habit name in `titleLarge` plus a swap mark (or a lock), which opens the Habits screen | `TopAppBar` actions | **Design.** Spark's top bar accepts it as a slot, but it is the one place a title is a control, and the locked state needs a Spark-styled treatment. |
| Habit rows (`HabitNameRow`) | Name plus an optional reminder time; 48dp floor; **names only** | `ListItem` | **Configure.** `ListItem` can host it. Never add a trailing count or `Badge`. |
| Row overflow (Edit / Archive) | M3 `DropdownMenu` | `DropdownMenu` / `DropdownMenuItem` | **Direct.** |
| Add / edit habit dialog (`HabitEditor`) | M3 `AlertDialog`, `OutlinedTextField`, `FilledTonalButton` | `AlertDialog` or `ModalScaffold`, `TextField`, `ButtonTinted` | **Direct.** |
| Reminder time (`ReminderTimePicker`) | M3 `TimePicker` in a dialog; Set is disabled outside 05:00–21:00 rather than clamping | **no time picker in Spark** | **Ours, with colours passed in.** Keep the M3 `TimePicker`. Under Spark's bridge its dial takes the same colour as its dialog and disappears (2.3), so pass `clockDialColor` and `timeSelectorUnselectedContainerColor` as paper-200 (`backgroundVariant`). Check it doesn't look foreign beside Spark's dialog. |
| Language row (`LanguagePicker`) | M3 `RadioButton` list in a dialog | `RadioButtonLabelled` | **Direct.** The selected radio is brass (`support`). |
| Settings and About lists (`ActionRow`, `AboutCard`) | Our 48dp rows in cards | `ListItem`, `Card` | **Configure:** `ActionRow` can wrap `ListItem` and keep its 48dp guarantee. |
| Privacy and Licences screens | Text in cards; licences as one `LazyColumn` item per paragraph | `Card`, type tokens, `TextLink` | **Direct.** |

### 3.5 First run, the gate, and full-screen surfaces

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Tour pager and page dots | `HorizontalPager`, our `PageDots` with one spoken position | **no pager indicator** | **Ours.** Restyle the dots in Spark tokens. |
| Full-screen messages (`FullScreenMessage`, `MessageIcon`, `FullScreenAction`) | Shared by the tour, the gate stops, the widget page | none | **Ours, and important:** it is the shared layout the tour and the gate must not drift from. Restyle it once and every surface follows. |
| Empty states (`EmptyState`) | Icon, title and body | none | **Ours.** |
| Naming step (`NameHabitScreen`) | A large single-line field and the cycling example | `TextField` | **Configure:** check that Spark's field supports the large, centred, underline look, and its behaviour in RTL. |
| Camera disclosure and gate stops (`CameraDisclosureScreen`, `GateMessageScreen`) | Full-screen messages with a **Not now** exit | `ButtonFilled` / `ButtonGhost` | **Direct** for the buttons. |
| Face check (`PresenceCheckScreen`) | Camera preview behind hint text and a fallback button | none | **Design.** White text over a bright preview is already hard to read (seen on the Pixel). Spark doesn't solve this; it needs a scrim or container behind the text. |
| Habit picker (`HabitPickerScreen`) | The Habits list with everything but choosing removed | `ListItem`, `ButtonGhost` | **Direct**, via `HabitNameRow`. |
| Update offer (`AppUpdateOffer`) | Play's own dialog, then our Restart snackbar | `Snackbar` | **Direct** for the snackbar. Play's dialog is Play's. |
| App frame (`AppNavScaffold`, `BottomNavigationBar`) | M3 `Scaffold`, `TopAppBar`, `NavigationBar` | `Scaffold`, `TopAppBar`, `NavigationBar` | **Direct.** The selected-tab indicator is `supportContainer` (a brass tint, `#dfd0b3` / `#2c2f39`) with an ink icon. Bars sit on paper, raised by Spark's shadow in light and by the tint in dark (2.3). |

---

## 4. Spark components the app must not use

These are well made, and each one breaks a rule in CLAUDE.md's "Product rules" section:

| Spark component | Rule it breaks |
|---|---|
| `SegmentedGauge`, `Meter`, `Progressbar`, `CircularProgressIndicator` / `LinearProgressIndicator` (determinate), `ProgressTrackerRow`/`Column` | **Hours have no direction.** A fill is a target, and a tracker is a completion bar. (An *indeterminate* `Spinner` for loading is fine.) |
| `RatingDisplay`, `RatingInput`, `RatingStar` | Grades a day or a habit. |
| `Badge` with a count on a habit | **Nothing compares habits.** A count per row ranks them. |
| `ButtonIntent.Danger` on Check Out, `ButtonIntent.Success` on Check In | The action is coloured, never the outcome (2.2). Under mudda's palette `Danger` is vermilion, mudda's error colour, and `Success` is ink. |
| `Stepper` | A numeric stepper invites a target ("how many minutes?"). Nothing in the app takes a number. |

Worth writing into CLAUDE.md as a rule if the migration goes ahead, so a later "use the Spark gauge here" gets refused on sight.

One more, for the palette rather than a product rule: `ButtonBoost`, the highlighted `Card`, `Popover` and accent `Tag`s draw in `accent`, which is vermilion here. Vermilion is for attention, never decoration.

---

## 5. Out of Spark's reach

These are not Compose, so Spark cannot style them. They need matching by hand, or they will look like a different app beside the new UI. The values come from 2.2.

| Surface | Where | What to match |
|---|---|---|
| Home-screen widget | `res/layout/widget_check_in.xml`, `widget_*` colours, `WidgetLabel` style | `widget_background` paper (`#F1E6CE` / `#0E1830`), `widget_text` ink (`#1B2A4A` / `#EFE4CB`), `widget_mark_running` ink and `widget_mark_rest` ink at the same 40% (`#661B2A4A` / `#66EFE4CB`), keeping the gauge's convention. Label type stays the system sans and the corner radius stays the system's, by design. |
| Notifications | `NotificationFactory`, `ic_stat_*` | Nothing to change: no `setColor` is set today, so the system tints. The icons stay white (alpha only). |
| Launcher icon, splash, Play icon | `generate_icons.py`, `ic_launcher_foreground.xml`, `ic_splash_icon.xml`, `ic_launcher_background` | The mark's geometry stays. Its colours follow mudda's own icon (`mark.svg`, a paper mark on an ink tile): `ic_launcher_background` `#3F51B5` → `#1B2A4A`, and the foreground's fill `#FFFFFF` → `#F1E6CE`, printed by `generate_icons.py` and never hand-edited. The splash shares `ic_launcher_background`, so it follows. The themed (`<monochrome>`) icon ignores the fill. |
| Launch window | `brand_window_background` in `values` and `values-night` | `#FEFBFF` / `#1B1B1F` → paper, `#F1E6CE` / `#0E1830`, so there is no off-white flash before Compose draws |
| System dialogs | Permission prompts, `BiometricPrompt`, Play's update dialog | Nothing; these are the system's |
| Store listing | `play-store-assets/` screenshots and feature graphic | Re-shoot after the UI changes |

---

## 6. Plan

Each phase ends with something installable, so it can be judged on the phone before going further.

0. **Foundations (decisions, no code):** settle 2.1 (type) and 2.4 (shape). Colour is settled in 2.2, whose tables are the colour half of the token sheet. Output: the type half, mapping every `MaterialTheme.typography.*` call site to its Spark name, and every `colorScheme.*` call site checked against 2.2's bridge notes.
1. **Theme only.** Wrap the app in `SparkTheme` with 2.2's `lightSparkColors`/`darkSparkColors` and the right family per locale. Move the four app tokens (`dayColor`, start, stop and the split hues) to 2.2's values in the same step, since indigo-era greens beside mudda's paper would be judged as a bug. Change no component. Thanks to the M3 bridge, every screen moves together. Judge it in light and dark, in English and Arabic.
2. **Direct swaps.** Dialogs, menus, chips and the segmented control, the text field, the radio buttons, the snackbar, top bar and bottom bar, cards, the gate and tour buttons. These are low risk, and each is a visible step.
3. **Our components in Spark tokens.**
   - `FullScreenMessage` and `EmptyState` first: they carry the most screens.
   - Then the session ledger, the calendar, the charts, the page dots and the sessions pill.
   - Pass the `TimePicker`'s dial colours (2.3) in the same phase as the theme, or the dial is invisible from phase 1.
4. **Dedicated design, one item at a time:**
   - the Check-In button (height, and the animated change between brass-fill and ink);
   - the calendar's selected cell, as a ring rather than a fill (3.2);
   - the habit switcher;
   - the face-check overlay's contrast;
   - the gauge, readout and celebration typography.
5. **Off-Compose surfaces (section 5):** the widget, the launch window, the icons and splash (through `generate_icons.py`), then the store screenshots and feature graphic.
6. **Review pass:**
   - every screen under Arabic, in dark, at font scale 2.0, with animations off and on a small (≤ 560dp-tall) viewport;
   - the Check-In screen's fit budget (`FIXED_CONTENT_HEIGHT`, `TEXT_CONTENT_HEIGHT`) and History's `DETAIL_SLOT_HEIGHT`, re-measured;
   - contrast checks on every colour pair in 2.2's table, and any pair added since.

---

## 7. Open decisions

| # | Decision | Options | Notes |
|---|---|---|---|
| 1 | Brand | **Decided:** mudda's light and dark schemes in Spark's slots (2.2) | Neither indigo nor Spark's default palette. |
| 2 | Type | One family (Spark's way), or keep the Outfit and Manrope split | Arabic stays Plex either way. |
| 3 | Action colours | **Decided:** app tokens beside Spark: brass-fill to start, ink to stop, brass for a recorded day | No red is left. Remapping to `success`/`danger` would reverse a documented product rule. |
| 4 | Check-In button | A custom button, or a Spark button with custom colours | Depends on what `ButtonFilled` lets you override. |
| 5 | Reports scope | `ChipSelectable` or `SegmentedControl` | Both are direct swaps. |
| 6 | Where Spark stops | Spark for the controls only, with brand and data surfaces staying ours; or try to "Sparkify" everything | This plan assumes the first. |
| 7 | Recorded-day alpha | Keep 0.35, or 0.40 | 0.40 restores today's separation from the page under brass (2.2). Either way it is one fixed constant. |
| 8 | Third chart hue in practice | Ink, brass and teal; or ink-muted, brass and teal | Teal is decided; swap ink for ink-muted only if the ink segment reads as the important one on a device (2.2). |
| 9 | Keeping in step with mudda | Copy the values by hand; or take mudda's Android output once it exists | By hand until then. Any mudda release that changes a colour means re-running 2.2's contrast table, since the derived values are computed from mudda's. |

## 8. Still to verify on a device

- Spark under `ar`: does every component mirror correctly, and do its icons that should mirror (back, chevrons) actually do so?
- Whether Spark's motion honours "Remove animations". The app's own motion checks `animationsEnabled`; Spark's component animations have to be checked separately.
- `TextField` with a large centred single-line style, for the naming step.
- The M3 `TimePicker` dialog next to Spark's `AlertDialog`: does it look like the same app, with its dial colours passed in?
- In dark, the Check In → Check Out change (brass to cream ink): does it read as a change of state at a glance?
- On cream paper under Spark's light shadows, do cards, bars and dialogs read as raised enough, with no tonal step to help?
- The brass recorded day beside the brass selected chip or radio: does brass still read as one meaning per screen?

## 9. Prerequisites verified

Spark 4.2.1 is built with Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.1, compileSdk 37 and Compose BOM 2026.09.00, minSdk 24. Branch `build/kotlin2-spark-ready` brings the app to that toolchain with no visible change: Gradle 9.8.2, AGP 9.4.1 with built-in Kotlin 2.4.21, KSP 2.3.12 and Room 2.8.5, compileSdk 37 (targetSdk stays 36, minSdk 31), the Compose BOM 2026.09.00 (material3 1.4.0), and floors for the transitive libraries Spark would otherwise raise.

A scratch branch then added `spark-bom`/`spark` 4.2.1 and wrapped the theme root in `SparkTheme`, to see what adoption costs before any design work.

| Check | Result |
|---|---|
| Builds | Yes, on the branch above. |
| Moves an existing version | Not any more. Spark raised nine transitive libraries (coroutines 1.11, serialization 1.11, appcompat 1.8, window 1.5.1 and smaller ones); the branch's `constraints {}` block now sets them, so adoption only adds artifacts. |
| Adds a permission | No. The merged `uses-permission` set is unchanged. |
| Adds manifest components | Yes, four: FileKit's `FileKitFileProvider` and its startup `FileKitInitializer`, Compose Resources' `AndroidContextProvider`, and gms's `ModuleDependencies` service with `photopicker_activity` metadata, which asks Play services to install the backported photo picker. The app uses no file picker; remove FileKit's entries and the photo-picker request with `tools:node="remove"`, or the Privacy screen's "one outside question" claim gains a second. |
| Networking classes in the dex | None (no `java.net`, OkHttp, Cronet, datatransport). Coil comes without a network fetcher. |
| Fonts | `spark-core` bundles six Nunito Sans TTFs (~690 KB) as Compose Resources assets, not in `res/font`. `verifyLicenseCoverage` checks only `res/font`, so it cannot see them: either extend it to `assets/composeResources`, or keep Nunito out of the APK if the app passes its own families (2.1). Bundled, Nunito needs no download; the dex holds no font-provider code for it (checked statically, not on a device). |
| Licence list | 14 new group ids need entries: `com.adevinta.spark` (MIT, which obliges a shipped notice: a new bundled `LibraryLicense` and its `res/raw` text), Coil 3, okio, accompanist, FileKit, and JetBrains' Compose Multiplatform and androidx redirect groups. |
| Size | Release APK 5.38 MB → 6.39 MB (+1.0 MB), before any Spark component is used. The fonts are 0.7 MB of it. |

**Found on the way, not Spark's:** v3.2 already ships emoji2's `EmojiCompatInitializer` (a transitive dependency), whose default config asks Play services' font provider for an emoji font. That is an outside request the Privacy screen does not name. Check it on a device and decide separately: disable the initializer, or name it.

