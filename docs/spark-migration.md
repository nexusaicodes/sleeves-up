# Moving the UI to Spark: design plan and coverage gaps

**Scope.** This is about design only: what changes on screen, what maps onto Spark cleanly, and what needs its own design work. The toolchain upgrade is a separate prerequisite and is not covered here (Spark 4.x needs Kotlin 2.x; this app is on 1.9.20).

**Reference.** [leboncoin/spark-android](https://github.com/leboncoin/spark-android) **4.2.1**, MIT licence. Its catalog app (`com.adevinta.spark.catalog`) was built from that tag and installed on the Nothing Phone for browsing. Component names below are Spark's public composables at that tag.

---

## 1. The short answer

The routine Material 3 controls map across almost one for one: buttons, dialogs, chips, the radio button, the text field, the menu, the snackbar, the top bar and bottom bar, dividers and cards. That is roughly a third of the UI and the least distinctive third.

The rest falls into four groups.

| Group | What's in it | Why |
|---|---|---|
| **A. Foundations** | Typography, colour roles, the start/stop action colours, the recorded-day colour | Spark's theme is built differently from this app's: one font family, intents in place of semantic roles |
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

### 2.2 Colour: needs dedicated thought

Spark's colours are **intents**: `main`, `support`, `accent`, `success`, `alert`, `error`, `info`, `neutral`, `ai`, each with `on…`, `…Container` and `…Variant`. Buttons, tags and chips take an intent (`ButtonIntent.Main … Danger …`). This app's colours are a fixed indigo M3 scheme (`ui/theme/Theme.kt`) plus four hand-tuned roles in `ui/theme/Color.kt`.

| App colour | Meaning (from CLAUDE.md) | Nearest Spark token | Conflict |
|---|---|---|---|
| Indigo `primary` | Brand | `main` (+ `support`/`accent`) | Map the scheme through `lightSparkColors`/`darkSparkColors`. Decide what `support` and `accent` are, since the app has only one brand hue. |
| `startActionColors()` (green) | The **start** half of a start/stop control; the same hue as a recorded calendar day | `success` / `ButtonIntent.Success` | "Success" is an outcome word. The app colours **the action, never the outcome**. Reusing `success` for "check in" ties the button to a meaning the app avoids. |
| `stopActionColors()` (red) | The **stop** half; explicitly **not** `colorScheme.error`, and not a verdict | `error` / `ButtonIntent.Danger` | Spark's red means *danger or destructive*. Check Out is neither. Using `Danger` says checking out is a risky act. |
| `dayColor()` at `RECORDED_DAY_ALPHA` | A day with a session; one fixed shade, **never a ramp, and nothing ever red** | none | Has to remain an app-level token. |
| `descriptiveHues` | Three hues **of equal weight** for start-time and sessions-per-day splits, never one hue at three strengths | none (Spark has no data-viz palette) | Has to remain an app-level token. |

Decisions:
- **Map indigo onto `main`** and decide `support` and `accent`. Using Spark's default palette (Leboncoin orange) would make this a rebrand, not a migration.
- **Keep the start, stop, day and chart colours as app tokens** that sit beside Spark's rather than inside its intents. That costs the action button the ability to be a plain `ButtonFilled(intent = …)`; see 3.1.
- **Re-check contrast in both themes.** Today every action pair clears WCAG AA; Spark's container and variant tones are new pairs to check.

### 2.3 Shape

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
| Check In / Check Out button | M3 `Button`, 64dp (`ACTION_HEIGHT`), colours animated between start and stop so it reads as one control changing state | `ButtonFilled` at 44 or 56dp, colour from an intent | **Design.** Height, the start/stop colours (2.2) and the animated change don't fit an intent-based button. Either a custom `ButtonColors` (if Spark exposes it) or keep ours. The 64dp is inside a hand-kept fit budget (`FIXED_CONTENT_HEIGHT`), so any height change moves the budget. |
| Breathing gauge (`BrandGridGauge`) | The brand lattice, a wave passing over seven cells; it says only whether a session is open | `SegmentedGauge`, `Meter`, `Progressbar`, `CircularProgressIndicator` | **Ours, and Avoid the Spark lookalikes.** Every Spark gauge, meter and progress bar fills toward a goal, which is the deleted daily target in visual form. |
| Readout (`0m 1s`) | `displayMedium`, tabular figures, `maxLines = 1`, steps down at large font scales | type token | **Configure:** pick the Spark style and keep `tnum` and the step-down. |
| Sessions pill (`TodaySessions`) | `Surface` pill, 48dp touch floor, expands a ledger | `Chip` / `Tag` | **Design.** A chip reads as a filter and a tag as a label; this is a disclosure. Probably ours, in Spark shapes. |
| Session ledger (`SessionIntervalRow`) | Two columns: clock range and duration, with the duration column scaling with font scale and capped | `ListItem` | **Ours.** `ListItem`'s slots don't express the column rule that keeps "2h 15m" from rendering as "2h". |
| Check-out celebration (`CheckOutCelebration`, `BrandGrid(settle = true)`) | Full screen, the seven cells landing in order; identical at 20 minutes and at 9 hours | none | **Ours.** Restyle the type only. |

### 3.2 History tab

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Calendar (`CalendarGrid`) | Binary: a day has a session or it doesn't. Cell height derived from the viewport, 48–80dp. Fades only days outside the record. | none | **Ours.** A pure token restyle. Keep `dayColor()` as its own token (2.2). |
| Month selector | Icon buttons and a title | `IconButtonGhost` plus a type token | **Direct.** |
| Selected-day slot (header, rows, empty state) | `DayHeader`, `SessionIntervalRow`, `EmptyState` | `ListItem`, none | **Ours.** `DETAIL_SLOT_HEIGHT` is sized to the tallest state and must be re-measured if the empty state's type changes. |

### 3.3 Reports tab

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Scope selector (month / all time) | M3 `FilterChip` and a month stepper | `ChipSelectable`, or `SegmentedControl` | **Configure.** A two-option segmented control may read better than chips. Decide which. |
| Cards (`ChartCard`, `SectionCard`) | M3 `Card` and our layout | `Card` / `OutlinedCard` | **Direct** for the container; the content inside stays ours. |
| Line, bar and donut charts | Hand-rolled `Canvas` over pure `ChartGeometry`; mirrored under RTL; an all-zero window draws as zeros | none | **Ours.** Restyle the strokes, axes and legend type in Spark tokens. The hues stay app tokens (2.2). |
| `StatsRow`, `LegendRow`, `AxisLabel` | Plain rows | none | **Ours**, with Spark type. |
| Averages | Deliberately plain rows, never arcs | `Meter`, `SegmentedGauge`, `RatingDisplay` | **Avoid.** An arc or meter has only the user's own best to fill against. |

### 3.4 Habits, Settings and About

| Element | Today | Spark | Verdict |
|---|---|---|---|
| Habit switcher (`HabitSwitcher`) | The top bar's end slot: the habit name in `titleLarge` plus a swap mark (or a lock), which opens the Habits screen | `TopAppBar` actions | **Design.** Spark's top bar accepts it as a slot, but it is the one place a title is a control, and the locked state needs a Spark-styled treatment. |
| Habit rows (`HabitNameRow`) | Name plus an optional reminder time; 48dp floor; **names only** | `ListItem` | **Configure.** `ListItem` can host it. Never add a trailing count or `Badge`. |
| Row overflow (Edit / Archive) | M3 `DropdownMenu` | `DropdownMenu` / `DropdownMenuItem` | **Direct.** |
| Add / edit habit dialog (`HabitEditor`) | M3 `AlertDialog`, `OutlinedTextField`, `FilledTonalButton` | `AlertDialog` or `ModalScaffold`, `TextField`, `ButtonTinted` | **Direct.** |
| Reminder time (`ReminderTimePicker`) | M3 `TimePicker` in a dialog; Set is disabled outside 05:00–21:00 rather than clamping | **no time picker in Spark** | **Ours.** Keep the M3 `TimePicker`; under `SparkTheme` it takes Spark's colours. Check it doesn't look foreign beside Spark's dialog. |
| Language row (`LanguagePicker`) | M3 `RadioButton` list in a dialog | `RadioButtonLabelled` | **Direct.** |
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
| App frame (`AppNavScaffold`, `BottomNavigationBar`) | M3 `Scaffold`, `TopAppBar`, `NavigationBar` | `Scaffold`, `TopAppBar`, `NavigationBar` | **Direct.** Check the selected-tab indicator's colour against the brand. |

---

## 4. Spark components the app must not use

These are well made, and each one breaks a rule in CLAUDE.md's "Product rules" section:

| Spark component | Rule it breaks |
|---|---|
| `SegmentedGauge`, `Meter`, `Progressbar`, `CircularProgressIndicator` / `LinearProgressIndicator` (determinate), `ProgressTrackerRow`/`Column` | **Hours have no direction.** A fill is a target, and a tracker is a completion bar. (An *indeterminate* `Spinner` for loading is fine.) |
| `RatingDisplay`, `RatingInput`, `RatingStar` | Grades a day or a habit. |
| `Badge` with a count on a habit | **Nothing compares habits.** A count per row ranks them. |
| `ButtonIntent.Danger` on Check Out, `ButtonIntent.Success` on Check In | The action is coloured, never the outcome (2.2). |
| `Stepper` | A numeric stepper invites a target ("how many minutes?"). Nothing in the app takes a number. |

Worth writing into CLAUDE.md as a rule if the migration goes ahead, so a later "use the Spark gauge here" gets refused on sight.

---

## 5. Out of Spark's reach

These are not Compose, so Spark cannot style them. They need matching by hand, or they will look like a different app beside the new UI.

| Surface | Where | What to match |
|---|---|---|
| Home-screen widget | `res/layout/widget_check_in.xml`, `widget_*` colours, `WidgetLabel` style | Colours, label type (system sans, by design), corner radius (the system's) |
| Notifications | `NotificationFactory`, `ic_stat_*` | The accent colour only; the system draws the rest |
| Launcher icon, splash, Play icon | `generate_icons.py`, `ic_launcher_foreground.xml`, `ic_splash_icon.xml` | The mark stays; the background colour follows the brand decision in 2.2 |
| System dialogs | Permission prompts, `BiometricPrompt`, Play's update dialog | Nothing; these are the system's |
| Store listing | `play-store-assets/` screenshots and feature graphic | Re-shoot after the UI changes |

---

## 6. Plan

Each phase ends with something installable, so it can be judged on the phone before going further.

0. **Foundations (decisions, no code):** settle 2.1 (type), 2.2 (colour roles and the four app tokens) and 2.3. Output: a token sheet, mapping every `MaterialTheme.typography.*` and `colorScheme.*` call site to its Spark name.
1. **Theme only.** Wrap the app in `SparkTheme` with the mapped tokens and the right family per locale; change no component. Thanks to the M3 bridge, every screen moves together. Judge it in light and dark, in English and Arabic.
2. **Direct swaps.** Dialogs, menus, chips and the segmented control, the text field, the radio buttons, the snackbar, top bar and bottom bar, cards, the gate and tour buttons. These are low risk, and each is a visible step.
3. **Our components in Spark tokens.**
   - `FullScreenMessage` and `EmptyState` first: they carry the most screens.
   - Then the session ledger, the calendar, the charts, the page dots and the sessions pill.
4. **Dedicated design, one item at a time:**
   - the Check-In button (height, start and stop colours, the animated change);
   - the habit switcher;
   - the face-check overlay's contrast;
   - the gauge, readout and celebration typography.
5. **Off-Compose surfaces (section 5):** the widget, notification accent, icons and splash, then the store screenshots.
6. **Review pass:**
   - every screen under Arabic, in dark, at font scale 2.0, with animations off and on a small (≤ 560dp-tall) viewport;
   - the Check-In screen's fit budget (`FIXED_CONTENT_HEIGHT`, `TEXT_CONTENT_HEIGHT`) and History's `DETAIL_SLOT_HEIGHT`, re-measured;
   - contrast checks on every new colour pair.

---

## 7. Open decisions

| # | Decision | Options | Notes |
|---|---|---|---|
| 1 | Brand | Keep indigo through Spark's tokens, or take Spark's default palette | Spark's default is a rebrand, not a migration. |
| 2 | Type | One family (Spark's way), or keep the Outfit and Manrope split | Arabic stays Plex either way. |
| 3 | Action colours | App tokens beside Spark, or remap to `success`/`danger` | The latter reverses a documented product rule. |
| 4 | Check-In button | A custom button, or a Spark button with custom colours | Depends on what `ButtonFilled` lets you override. |
| 5 | Reports scope | `ChipSelectable` or `SegmentedControl` | Both are direct swaps. |
| 6 | Where Spark stops | Spark for the controls only, with brand and data surfaces staying ours; or try to "Sparkify" everything | This plan assumes the first. |

## 8. Still to verify on a device

- Spark under `ar`: does every component mirror correctly, and do its icons that should mirror (back, chevrons) actually do so?
- Whether Spark's motion honours "Remove animations". The app's own motion checks `animationsEnabled`; Spark's component animations have to be checked separately.
- `TextField` with a large centred single-line style, for the naming step.
- The M3 `TimePicker` dialog next to Spark's `AlertDialog`: does it look like the same app?
