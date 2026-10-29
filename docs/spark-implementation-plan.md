# Moving the UI to Spark: implementation plan

**What this is.** The build-and-code half of the Spark migration: what lands in which PR, in which order, touching which files, gated by what. The design half (colour, components, what Spark must never be used for) is `docs/spark-migration.md`; this plan cites its sections as *design 2.2* and so on and does not repeat them.

**Grounded in, as of 2026-10-29:**
- Spark **4.2.1** source, read at the tag (`decbaa6`). Paths below are relative to that tree: `spark-core/src/commonMain/kotlin/com/adevinta/spark/` is **C**, `spark/src/main/kotlin/com/adevinta/spark/components/` is **S**.
- A throwaway trial adoption on `v4.0` (`d9378a7`), run through every repo gate (section 2).
- A file-by-file inventory of the app's Material 3 surface (section 5).

**Version.** This is v4.0 work. `v4.0` is open and already holds the toolchain Spark needs (#35) and the merged-manifest gate (#36). Every PR below branches off `v4.0` and targets it. None targets `main`.

---

## 1. Decisions taken for this plan

These were open, or turned out to be forced by Spark's source. Each has a best guess so the plan can be concrete. Section 9 has their status: most are now confirmed.

| # | Decision | Taken | Why |
|---|---|---|---|
| D1 | Typography under Spark's one-family theme | **Spark's family is Manrope** (Plex Arabic under `ar`). **Outfit lives on in an app-owned `AppTypography`** for the 15 display, headline and title sites. | `SparkTheme` always calls `typography.updateFontFamily(fontFamily)` (C`SparkTheme.kt:136`, C`tokens/Type.kt:155-167`), so a per-style family cannot survive inside Spark's typography. Spark's controls draw body and label text (button labels use `callout`, fields use `body1`), which is Manrope's size range. Spark's `Text` takes an explicit `style` (C`components/text/Text.kt:199-216`), so an Outfit style passed in is respected. |
| D2 | Nunito Sans | **Never shipped.** Always pass `fontFamily`, `tools:discard` the `res/font` copy, and strip the `assets/composeResources` copy at build time. | Nunito ships twice (924 KB of the 1.04 MB Spark adds), and the app never draws in it. The discard is safe only if no `SparkTheme` falls back to the default, so every `SparkTheme` goes through one app wrapper (D7). |
| D3 | Spark's lint (`MaterialComposableHasSparkReplacement`, ERROR, ships inside the AAR) | **Stays an error throughout** (confirmed). Legitimate M3 leftovers take a site-level `@SuppressLint` with a reason. | Adding the dependency fails `lintDebug` with 152 errors before a line of UI changes. With the rule never relaxed, the dependency and every flagged replacement have to land together, so PRs 1-4 below are commit groups of **one** PR, `ui/spark-adoption` (section 3). |
| D4 | Manifest entries Spark brings | **Remove all four** with `tools:node="remove"`. | FileKit's provider and initializer, Compose Resources' `AndroidContextProvider` and gms `ModuleDependencies` (the photo-picker backport request) all run or register unasked, and the app uses none of them. The trial confirmed the merged manifest then matches the baseline exactly. CLAUDE.md's rule is to remove what runs unasked. |
| D5 | Icons | **Spark's `LeboncoinIcons`** (confirmed), with every directional icon wrapped in `mirroredInRtl()`. The few with no Spark equivalent keep their Material vector, drawn through Spark's `Icon` (section 5.6). | None of Spark's 571 drawables is `autoMirrored`, and its `UpNavigationIcon` hard-codes a left arrow (S`navigation/UpNavigationIcon.kt:49`). `SparkIcon.mirroredInRtl()` (`ui/components/MirroredIcon.kt`, done on `ui/spark-adoption`, `MirroredIconTest`) flips any `SparkIcon` under a right-to-left layout, in every Spark component, since they all draw through `rememberSparkIconPainter`. `Tab.icon` becomes a `SparkIcon`. `ScreenTest` reads no icon, and `LeboncoinIcons.X` is a plain `R.drawable` reference on the JVM. |
| D6 | Buttons API | **The rebranded `Button.*` family with `useRebrandedButtons = true`** (confirmed). Mapping: M3 `Button` → `Button.Primary`, `FilledTonalButton` → `Button.Secondary`, `OutlinedButton` → `Button.Tertiary`, `TextButton` → `Button.Ghost`. Never `Button.Success`, `Button.Danger`, `Button.Boost` or `Button.Ai`. | It is where Spark is heading, and README recommends the flag. Read at the tag, its colours sit on design 2.2's slots with no re-mapping: `Primary` is `main`/`onMain`, ink (S`buttons/ButtonPrimary.kt:78-79`); `Secondary` is `support`/`onSupport`, brass-fill (S`buttons/ButtonSecondary.kt:77-78`); `Tertiary` is transparent with an `onSurface` label and outline (S`buttons/ButtonTertiary.kt:77-81`); `Ghost` is text. `Success` and `Danger` are outlined in `success` and `error`, which design 4 already bans for the action. Spark's guidance is one `Primary` per view. |
| D7 | Theme entry point | **One `CheckInAppTheme` builds the only `SparkTheme`; nested themes go through `SparkColorScope`.** | A nested `SparkTheme` re-defaults `fontFamily` to Nunito and `sparkFeatureFlag` to its default (C`SparkTheme.kt:105-108`). With Nunito discarded (D2), a bare nested `SparkTheme` crashes with `Resources.NotFoundException`. A source-reading test fails on a `SparkTheme(` call anywhere else (PR 8.2, added in PR 2). |
| D8 | The Check-In start/stop button | **A Spark `Button.Primary` at 64dp inside `SparkColorScope(main = animated container, onMain = animated content)`.** The shape becomes Spark's pill (confirmed). | Spark buttons accept no colours, and `BaseSparkButton(colors =)` is `@InternalSparkApi` (S`buttons/Button.kt:67-86`). `Button.Primary` reads `SparkTheme.colors.main`/`onMain` (S`buttons/ButtonPrimary.kt:78-79`), so a scoped copy of the colours recolours it. `Modifier.height(64.dp)` wins over Spark's `defaultMinSize` (S`buttons/Button.kt:89-91`), so the fit budget is unchanged. The shape is fixed to `shapes.full` (S`buttons/ButtonTokens.kt:38-41`). |
| D9 | Large centred name field (`HabitFields` in `HabitEditor.kt`) | **Stays an app component** on `BasicTextField`, restyled in Spark tokens. The ordinary dialog field becomes Spark's `TextField`. | Spark's `TextField` has no `textStyle` or `textAlign`; input is fixed to `body1` (S`textfields/SparkTextField.kt:149,254,359`). |
| D10 | Reminder time picker | **Stays M3 `TimePicker`, inside a local `MaterialTheme` carrying the app's M3 typography and explicit dial colours.** | Spark has no time picker. Spark's M3 bridge maps `titleMedium` to `display2` (32sp bold) and folds every `surfaceContainer*` role into `surface`, so the picker would render oversized text on an invisible dial. |
| D11 | Reports scope selector | **`SegmentedControl.Horizontal`** with two segments. The month stepper stays icon buttons. | Two exclusive options are a segmented control, not filters. The segment count is within Spark's 2-5 (S`segmentedcontrol/SegmentedControlTokens.kt:47-56`). Its 52dp minimum height has to be checked against the Reports layout. |
| D12 | Snackbars | **Spark's own `SnackbarHostState`** in `LocalSnackbarHostState`. | Spark's `SnackbarHost` takes its own state class (S`snackbars/SnackbarHost.kt:89`), and its lint flags M3's. It reuses M3's `SnackbarDuration` and `SnackbarResult`, so `AppUpdateOffer`'s logic is unchanged. |
| D13 | Licence entries | **Seven grouped entries** (section 3.4), plus a new bundled `LibraryLicense.MIT` whose text ships in `res/raw/mit.txt`. | 18 group ids are uncovered. Spark and FileKit are MIT, which obliges shipping the notice. Everything else is Apache-2.0. |
| D14 | Order | **One adoption PR (plumbing, theme, primitives, controls, as ordered commit groups), then our components, design items, off-Compose surfaces, hardening.** Each PR leaves `v4.0` green and installable. | The same order as design 6. D3 makes the first four inseparable: no subset of them passes lint. |

---

## 2. What the trial build measured

Run on `v4.0` at `d9378a7`, adding `spark-bom`, `spark` and `spark-icons` 4.2.1 and wrapping the theme root in `SparkTheme`. Nothing was committed. The full diff is kept outside the repo.

| Gate | Result | Fix in this plan |
|---|---|---|
| Resolved classpath | 155 → 241 artifacts. **No existing version moves**; the `constraints {}` block holds. | none needed |
| `staticAnalysis` | pass | — |
| `testDebugUnitTest` | pass (468) | — |
| `verifyMergedManifest` | **fail**: `initializer io.github.vinceglb.filekit.initializer.FileKitInitializer`, `provider io.github.vinceglb.filekit.dialogs.fileprovider.FileKitFileProvider`, `provider org.jetbrains.compose.resources.AndroidContextProvider`, `service com.google.android.gms.metadata.ModuleDependencies` (it carries `photopicker_activity:0:required`) | D4: four `tools:node="remove"` entries. Two of them trip lint `MissingClass`, so each takes a scoped `tools:ignore="MissingClass"`. **Verified**: the APK manifest then matches the baseline. |
| `verifyLicenseCoverage` | **fail**: 18 group ids (section 3.4) | D13 |
| `lintDebug` | **fail**: 152 × `MaterialComposableHasSparkReplacement` (Text 83, Icon 23, TextButton 11, IconButton 7, Button 4, OutlinedButton 4, AlertDialog 3, and singles). No other new issue. | D3 |
| Networking in the dex | none (`java/net`, `javax/net`, OkHttp, Cronet, datatransport, Google Fonts). `ui-text-google-fonts` is on the classpath as a direct Spark dependency, and R8 strips all of it. | Re-check on every Spark bump. |
| Permissions | unchanged | — |
| Release APK | 5,350,848 → 6,388,878 B (**+1.04 MB**). Nunito accounts for 924 KB: 7 TTFs in `res/font` (591 KB compressed) and 6 in `assets/composeResources/com.adevinta.spark.core/font/` (333 KB compressed). | D2. **Verified**: with both copies stripped the APK is 5,461,451 B, **+110 KB** over baseline, with exactly the baseline's 12 TTFs. |

Things that did **not** work, so the plan does not use them:
- `androidResources.ignoreAssetsPatterns`: not an input to `mergeReleaseAssets`.
- `packaging.resources.excludes`: it applies to Java resources, not assets.
- Passing our own family alone: R8 still sees `nunitoFontFamily()`'s `R.font` references, and assets are never shrunk.

---

## 3. PR sequence

**PRs 1-4 are one PR.** Spark's lint stays an error (D3), so no build that has the dependency passes `lintDebug` until every flagged composable is replaced. They land together from `ui/spark-adoption` (already started: the dependency and `mirroredInRtl`, `3a8828c`). Each "PR" below is a commit group on that branch, in this order, so the history still bisects. CI is red on the branch until group 4 is in, which is expected; open the PR only then. PR 5 onward are separate PRs.

Each PR is a branch off `v4.0`, named as shown, and targets `v4.0`. All CI gates must pass, every PR ends with an installable build, and every PR passes CLAUDE.md §5's docs-sync and self-review. "Device check" means the Pixel and the Nothing Phone, light and dark, English and Arabic, unless narrowed.

### PR 1 — group 1 of `ui/spark-adoption`: Spark on the classpath, nothing drawn with it

No visible change, by design. It keeps every gate cost apart from every design question in the history.

1. **`app/build.gradle.kts` dependencies**: `implementation(platform("com.adevinta.spark:spark-bom:4.2.1"))`, `implementation("com.adevinta.spark:spark")`, `implementation("com.adevinta.spark:spark-icons")`. Keep the existing Compose BOM; Spark's own BOM resolves to the same 2026.09.00.
2. **Lint** (D3): no change. Spark's rule stays an error, which is why groups 1-4 share one PR. Done alongside step 1: `SparkIcon.mirroredInRtl()` and `MirroredIconTest` (`3a8828c`).
3. **Manifest** (D4), in `app/src/main/AndroidManifest.xml`:
   - Inside the existing `androidx.startup.InitializationProvider` `<provider>`: `<meta-data android:name="io.github.vinceglb.filekit.initializer.FileKitInitializer" tools:node="remove" />`.
   - `<provider android:name="io.github.vinceglb.filekit.dialogs.fileprovider.FileKitFileProvider" tools:node="remove" />`.
   - `<provider android:name="org.jetbrains.compose.resources.AndroidContextProvider" tools:node="remove" tools:ignore="MissingClass" />`.
   - `<service android:name="com.google.android.gms.metadata.ModuleDependencies" tools:node="remove" tools:ignore="MissingClass" />`.
   - Each gets a one-line comment in the style of the existing emoji2 removal: what it does, and that the app calls nothing that needs it.
   - **No change to `merged-manifest-allowlist.txt`**: the merged manifest is the baseline's.
   - **Known risk:** with `AndroidContextProvider` gone, any Compose Resources read (`Res.*`, `SparkIcon.Resource`) throws `MissingResourceException`. Spark's Android code reads none (it uses `R.font` and `R.drawable`), and section 6 forbids the app from adding one.
4. **Nunito out of the APK** (D2):
   - `app/src/main/res/raw/keep.xml`: `<resources xmlns:tools="http://schemas.android.com/tools" tools:discard="@font/nunito_sans_*" />`. Lint may need `tools:ignore` on it; the trial saw no new lint issue.
   - The assets copy: an `androidComponents.onVariants` transform of `SingleArtifact.ASSETS` (AGP Variant API) that copies the merged assets minus `composeResources/com.adevinta.spark.core/font/**`. Not the trial's `doLast` on `mergeReleaseAssets`, which breaks up-to-date checks and configuration cache. If the transform proves awkward under AGP 9.4, a `doLast` with a declared output is the fallback; say so in the PR.
   - With PR 1 nothing calls `SparkTheme`, so discarding Nunito cannot crash yet. From PR 2 on it depends on D7's wrapper.
5. **Fonts gate**: extend `verifyLicenseCoverage`'s font check from `src/main/res/font` to also scan the **merged release assets** for `ttf`, `otf` and `ttc`, so a font a library slips into `assets/` is caught as `res/font` is today. With step 4 it finds nothing new. Pin it by temporarily disabling step 4 locally and confirming the gate fails, then note that in the PR.
6. **Licences** (D13), in `ui/about/OpenSourceLibraries.kt`:
   - **New enum value** `LibraryLicense.MIT(displayName = "MIT License", url = "https://opensource.org/license/mit", bundled = true)`, plus `res/raw/mit.txt` holding Spark's `LICENSE` as shipped at the tag (copyright Adevinta) followed by FileKit's (copyright Vincent Guillebaud; read from its repo at 0.16.0).
   - **`LicensesScreen`'s raw-resource mapping** gains `MIT → R.raw.mit`.
   - **New entries**, each with `coordinates` matching the group ids:

     | Entry | `coordinates` | Licence | Covers |
     |---|---|---|---|
     | Spark | `com.adevinta.spark` | MIT | spark, spark-core, spark-icons, spark-annotation, spark-bom |
     | Compose Multiplatform | `org.jetbrains.compose.*` | Apache-2.0 | animation, annotation-internal, collection-internal, components, foundation, material, material3, material3.adaptive, runtime, ui |
     | JetBrains AndroidX redirects | `org.jetbrains.androidx.*` | Apache-2.0 | lifecycle, savedstate, window |
     | Coil | `io.coil-kt.coil3` | Apache-2.0 | |
     | Okio | `com.squareup.okio` | Apache-2.0 | |
     | Accompanist | `com.google.accompanist` | Apache-2.0 | drawablepainter |
     | FileKit | `io.github.vinceglb` | MIT | |

     Check whether `androidx.constraintlayout`, `androidx.documentfile` and the Compose additions already fall under the existing `androidx.*` entry; the trial's uncovered list suggests they do. Also check `org.jetbrains.kotlinx` (collections-immutable, kotlinx-io), which the trial did not list, presumably because an existing `org.jetbrains.kotlinx` entry covers it; confirm.
   - **`OpenSourceLibrariesTest`**: the bundled set becomes `{APACHE_2_0, OFL_1_1, MIT}` (:76). The ML Kit, odml, datatransport and Firebase ban stays. The proprietary pin is unchanged.
   - Each entry's `note` is a string resource with its `values-ar` twin.
7. **Verify**: every CI step. Plus `assembleRelease` size (target ≤ +120 KB over the pre-PR build), a dex grep for networking classes, `aapt2 dump xmltree` of the APK manifest against the pre-PR build, and `unzip -l` to show only the 12 baseline TTFs.
8. **Docs** (same PR):
   - CLAUDE.md's networking entry: the four removals and why, beside the emoji2 paragraph.
   - The licence entry: MIT bundled, and the font gate now covering assets.
   - The Build Commands paragraph: Spark's BOM, and the `constraints {}` sentence now true rather than prospective.
   - `docs/spark-migration.md` section 9: point here.

**Device check**: install over the current v4.0 build. Nothing should look different. Open Licences and read the MIT text.

**Done** on `plan/spark-migration`. What it found, and where it departs from the steps above:
- **`ModuleDependencies` is FileKit's**, not a gms library's: the merger report names `filekit-dialogs-android`. Its entry carries no class anywhere. Lint also flags `AndroidContextProvider` as `MissingClass` although `components-resources-android` carries it; both keep the scoped ignore.
- **The assets transform works** under AGP 9.4.1 (`SingleArtifact.ASSETS` is `Transformable`): `StripSparkFontAssets` in `app/build.gradle.kts`, applied to every variant. No `doLast` fallback.
- **The font gate fails on any typeface in the release assets**, rather than matching asset fonts against `font:` entries as it does `res/font`. Every font the app draws is a `res/font` resource, so a typeface in assets is never one to attribute, only one to strip. Pinned as planned: with the exclude pointed elsewhere the task failed naming the six Nunito assets; restored, it passes.
- **Not covered by the gate**: a font a library ships in its own `res/font`. Nunito's is discarded by `keep.xml`, but deleting that file would ship it again with no gate failing (the gate reads only the app's `res/font`). Closing it means scanning the release APK's resource table; left for PR 8.
- **Licences**: 71 group ids, 20 entries. `androidx.constraintlayout`, `androidx.documentfile` and the Compose additions fall under `androidx.*`; `kotlinx-collections-immutable` and `kotlinx-io` under `org.jetbrains.kotlinx:*`. Okio comes through Coil and Accompanist's drawablepainter through Spark directly. Every POM was read: Spark and FileKit MIT, the rest Apache-2.0. `mit.txt` holds both projects' `LICENSE` files as shipped (Spark at 4.2.1, copyright 2023 Adevinta; FileKit at tag `0.16.0`, copyright 2026 Vincent Guillebaud), and `OpenSourceLibrariesTest` pins MIT to exactly those two entries.
- **Release APK**: 5,417,123 B, **+66 KB** over the 5,350,848 B baseline (the trial's +110 KB also called `SparkTheme`, so expect the figure to rise once PR 2 does). `unzip -l` shows exactly the app's 12 TTFs, byte for byte; the dex holds no `java/net`, `javax/net`, OkHttp, Cronet or datatransport string; none of the four entries is in the APK's manifest.
- **`lintDebug`** fails only on 152 × `MaterialComposableHasSparkReplacement`, as D3 expects. Its other findings (`GradleDependency`, `OldTargetApi` and one hint) predate Spark.
- The `aapt2` diff against a pre-PR build was not run (that needs a second checkout); `verifyMergedManifest` passing against an unchanged allowlist shows the same thing for every entry kind it reads.

### PR 2 — group 2 of `ui/spark-adoption`: SparkTheme at the root, colours and type in

The whole app changes colour and body face in one step (design 6 phase 1), through the M3 bridge. No component is swapped yet.

1. **`ui/theme/Color.kt`**:
   - Replace the private green/red values with design 2.2's tokens: `dayColor()` brass, `startActionColors()` brass-fill/paper, `stopActionColors()` ink/paper, all with light and dark values.
   - Add `descriptiveHues()`, moved here from `ReportsScreen.kt:518`, returning the fixed ink, brass and teal per theme. It no longer reads `colorScheme` roles, which Spark's bridge folds together (design 2.3.1).
   - Add `SparkLight = lightSparkColors(...)` and `SparkDark = darkSparkColors(...)`, every slot set from design 2.2's tables, `surfaceTint = #4f7ebc` in dark. Set every intent, used or not, so no Leboncoin default ships.
2. **`ui/theme/Type.kt`**:
   - `sparkFamilyFor(locale): FontFamily`, returning Manrope or Plex Arabic.
   - `sparkTypographyFor(locale): SparkTypography`, built with `sparkTypography(...)` at M3-equivalent metrics so the fit budgets hold: `body1` 16/24, `body2` 14/20, `caption` 12/16, `small` 11/16 (M3 `labelSmall`, not Spark's 10/14, to keep `WEEKDAY_HEADER_HEIGHT`), `callout` 14/20 medium for button labels. Keep Spark's display and headline defaults, since our display text uses `AppTypography`.
   - `AppTypography`, an `@Immutable` data class: `display` (`displayMedium`, `displaySmall`), `headline` (`headlineMedium`, `headlineSmall`) and `title` (`titleLarge`, `titleMedium`). Built from today's `typographyFor(locale)` values (Outfit, or Plex under `ar`), and provided as `LocalAppTypography` beside `LocalTimePatterns`.
   - **Keep `typographyFor(locale)` as the full M3 `Typography`.** It is now the M3 fallback for D10's time picker and for `LanguagePicker`'s per-language name (`LanguagePicker.kt:138`).
   - `tabularFigures()` is unchanged.
3. **`ui/theme/Theme.kt`, `CheckInAppTheme`**:
   ```kotlin
   SparkTheme(
       colors = if (darkTheme) SparkDark else SparkLight,
       typography = sparkTypographyFor(locale),
       fontFamily = sparkFontFamily(fontFamily = sparkFamilyFor(locale)),
       sparkFeatureFlag = AppSparkFlags,  // useRebrandedButtons = true, isContainingActivityEdgeToEdge = true
   ) { CompositionLocalProvider(LocalTimePatterns provides patterns, LocalAppTypography provides appType, content = content) }
   ```
   - Delete `BrandLight` and `BrandDark` (the M3 schemes); their replacement is the bridge.
   - Keep the system-bar `SideEffect`. Its light/dark decision now reads paper's luminance.
   - Add `@Composable fun SparkColorScope(transform: SparkColors.() -> SparkColors, content)` (D7). It re-calls `SparkTheme` with `SparkTheme.colors.transform()` and the **same** typography, font family and flags, read from app-held values rather than re-defaulted. It is the only other `SparkTheme` call in the app, and `SparkThemeCallSitesTest` (PR 8.2) lands in this PR to keep it so.
4. **Bridge fallout, fixed in this PR** so nothing regresses visibly between PRs:
   - `ReminderTimePicker` (D10): wrap the `TimePicker` dialog in `MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surfaceContainerHigh = backgroundVariant, surfaceContainerHighest = backgroundVariant…), typography = typographyFor(locale))` and pass `TimePickerDefaults.colors(clockDialColor = …, timeSelectorUnselectedContainerColor = …)` (design 2.3.1).
   - Every `MaterialTheme.typography.titleMedium`, `titleLarge`, `headline*` and `display*` site (15, listed in section 5.2) moves to `LocalAppTypography.current`. **This is mandatory, not polish**: the bridge maps `titleMedium` to 32sp bold, so `SectionCard`, `EmptyState`, `FullScreenMessage`, `HistoryScreen`'s day header and the Check-In button label would jump in size.
   - The 42 body and label sites stay on `MaterialTheme.typography.*` for now. With the metrics in step 2, the bridge gives them equivalent sizes (`bodyMedium` → `body2`, `bodySmall` → `caption`, `bodyLarge` → `body1`, `labelLarge` → `body2` highlight, `labelMedium` → `caption`, `labelSmall` → `small`). PR 3 renames them.
   - The default text style changes from M3 `bodyLarge` (16sp) to Spark's `ProvideTextStyle(body2)` (14sp, C`SparkTheme.kt:154-158`). 27 `Text` calls set no style (section 5.3). Most sit inside a button or dialog slot that supplies its own; check the rest on screen and give each an explicit style where 14sp is wrong.
   - `CalendarGrid`'s selected-cell fill (`primaryContainer`, now paper-300, 1.33:1 against a recorded cell) moves to design 3.2's 2dp ink ring in **this** PR, since it is broken from the moment the theme lands.
   - `RECORDED_DAY_ALPHA` 0.35 → 0.40 (design decision 7).
5. **Previews**: the nine `CheckInAppTheme` previews keep working. Spark's preview theme (`PreviewTheme`) is `@InternalSparkApi`, so don't use it.
6. **Verify**:
   - CI.
   - Screenshots of every tab, before and after, light and dark, English and Arabic, kept in the PR description.
   - Contrast-check every pair in design 2.2's table on device (any colour-picker app).
   - The Check-In fit budget at font scale 1.0, 1.5 and 2.0, and on a ≤560dp viewport (emulator `CheckIn_API31` at 2.0).
   - `DETAIL_SLOT_HEIGHT` still holds the empty state.
7. **Docs**:
   - CLAUDE.md's Conventions typography paragraph: Spark's family is Manrope or Plex; Outfit lives in `AppTypography`; why the split survives; `typographyFor` is the M3 fallback.
   - Colour: the fixed indigo scheme becomes mudda's palette in Spark's slots; `descriptiveHues` lives in `Color.kt`.
   - The day-hue entry: brass.
   - The action-colour entry: brass-fill and ink, "no red in the app".
   - The calendar entry: selection is a ring.
   - The `ui/theme/` key-paths line.

**Device check**: everything, as this is the biggest visual step. Look specifically for the time picker dial, bars raised on cream with no tonal step, and the dark-mode Check In → Check Out change.

**Done** on `plan/spark-migration`. What it found, and where it departs from the steps above:
- **One mapping for both themes.** `Color.kt` holds mudda's roles and the derived values as a private `Palette` per theme, and one `Palette.slots(base)` sets every Spark slot through `SparkColors.copy` over `lightSparkColors()`/`darkSparkColors()`, so the role-to-slot rule is written once. `surfaceTint` is a palette field (paper in light, `#4f7ebc` in dark). The function is 73 lines, one per slot, and carries a site-level `@Suppress("LongMethod")` with that reason. `dayColor`, the action pairs and `descriptiveHues` still pick light or dark from `isSystemInDarkTheme()`, as they did.
- **`sparkTypographyFor` takes M3's styles whole**, letter spacing included, rather than restating sizes: `body1` = `bodyLarge`, `body2` = `bodyMedium`, `caption` = `bodySmall`, `small` = `labelSmall`, `callout` = `labelLarge`, `highlightWeight` Medium. So `small` is **Medium**, not Spark's Normal: every `small` site is a label that is Medium today. The bridge maps M3's `labelMedium` to `caption` at Normal weight, so its three explicit sites and the M3 `NavigationBar`'s labels lose Medium until PR 3 (`caption.highlight`) and PR 4 (Spark's bar).
- **`ReminderTimePicker`'s local theme overrides type only.** The dialog stays on the page (`surfaceContainerHigh` is `surface` under the bridge), and the dial and unselected time fields take `surfaceVariant` (paper-200) through `TimePickerDefaults.colors`. Raising the dialog to paper-200 as step 4 suggested would put the out-of-range line, vermilion, on paper-200, which is 4.31:1 in dark (design 2.2), and the dial would then need a third surface. The selected time field is `primaryContainer` (paper-300) against the unselected paper-200, a small step: a device check.
- **Three sites beyond the 15 moved to `AppTypography`**: the `TopAppBar` title (`titleLarge`) and the language and habit-editor dialog titles (`headlineSmall`). They set no style, so the M3 slot supplied one from the bridge: `titleLarge` → `headline1` (20sp bold Manrope) and `headlineSmall` → `display3` (24sp bold). The time picker's title is fixed by its local theme.
- **The 27 unstyled `Text` sites** (5.3) all sit in an M3 slot (button, chip, menu item, field label, navigation label, dialog, top bar) that provides its own style, so Spark's `body2` default reaches none of them. Apart from the three titles above and `labelMedium`, the bridge gives each slot its old size and weight (`labelLarge` → `body2` at the highlight weight, Medium).
- **`SparkColorScope`** reads the colours, shapes, typography and flags from the enclosing Spark theme and the family from a private `LocalAppSparkFontFamily` that `CheckInAppTheme` provides, since Spark exposes no reader for the family. Nothing calls it yet; PR 4 does (D8). `SparkThemeCallSitesTest` asserts that `SparkTheme(` appears in `ui/theme/Theme.kt` only, and exactly twice there.
- **The selection ring is a conditional `border`** over the cell's own fill, in `onSurface` (ink through the bridge), so a selected day's number keeps its normal colour. Today's number and dot still read `primary`, now ink.
- `SectionDivider`'s KDoc now gives the bridge's reason for never using a bare `HorizontalDivider` (`outlineVariant` is ink).
- **Gates**: everything passes except `lintDebug`, which still fails on exactly **152** × `MaterialComposableHasSparkReplacement`; theme work swaps no component. The other lint findings are the ones listed under PR 1, plus an `AutoboxingStateCreation` hint in `CheckInScreen.kt` that this PR did not touch.
- **Release APK**: 5,482,659 B, **+132 KB** over the 5,350,848 B baseline (PR 1: +66 KB). That is 22 KB over the trial's +110 KB, which wrapped a `SparkTheme` without the palette and typography code. `unzip -l` still shows exactly the app's 12 TTFs.
- **Not done here**: screenshots, the on-device contrast check and the fit budget at font scales 1.0 to 2.0 (step 6). They need a device and are left to the device pass.

### PR 3 — group 3 of `ui/spark-adoption`: Text, Icon, Surface and theme reads

Mechanical, and lint-driven: it clears most of the 152 errors.

1. `androidx.compose.material3.Text` → `com.adevinta.spark.components.text.Text` (83 sites, 30 files). The signature is the same; `style`, `color`, `maxLines` and `overflow` carry over.
2. `androidx.compose.material3.Icon` → `com.adevinta.spark.components.icons.Icon` (23 sites), and every `Icons.*` to its `LeboncoinIcons` equivalent from section 5.6: `.mirroredInRtl()` on the directional ones, and the gaps keep their Material vector through Spark `Icon`'s `ImageVector` overload (C`components/icons/Icon.kt:64`). `Tab.icon` (`Screen.kt`) becomes `SparkIcon`.
3. `Surface` (CheckIn pill `CheckInScreen.kt:341`, `MainActivity.kt` root) → `com.adevinta.spark.components.surface.Surface`. Keep the pill's 48dp touch floor.
4. Theme reads:
   - `MaterialTheme.colorScheme.X` → `SparkTheme.colors.Y` (71 sites, map in section 5.4).
   - `MaterialTheme.typography.X` body and label → `SparkTheme.typography.Y` (42 sites, map in section 5.2).
   - `MaterialTheme.shapes.extraLarge` → `SparkTheme.shapes.extraLarge`.
   - What remains of `MaterialTheme` is the time picker's local theme only.
5. Run `lintDebug` and record the remaining `MaterialComposableHasSparkReplacement` count in the PR (expected: the ~40 controls PR 4 swaps).
6. **Verify**: screenshots should match PR 2's pixel for pixel, apart from Spark `Text`'s defaults (`LocalContentColor` is `onSurface`). Any visible difference is a bug in this PR.
7. **Docs**: CLAUDE.md's "One description of…" and the components entries that name M3 types.

**Done** on `plan/spark-migration`. What it found, and where it departs from the steps above:
- **Every theme read is `SparkTheme`'s.** Colours follow 5.4 and type 5.2 exactly, and `MaterialTheme` is now read nowhere but `ReminderTimePicker`, where the local `MaterialTheme(typography = …)` stays (D10). Even inside it, the dial colour and the range line's colours read `SparkTheme.colors`, which are what the bridge hands the picker anyway.
- **Not pixel-identical to PR 2 in one place, on purpose**: `labelMedium` → `caption.highlight` gives History's day summary and the celebration's dismiss hint Medium weight again, where under PR 2 the bridge drew them at Normal. The third `labelMedium` site, Hours by Month's figures, sets `SemiBold` itself and does not move. `labelLarge` → `body2.highlight` matches the bridge exactly, because the bridge also builds `labelLarge` as `body2` at the highlight weight. Spark's `Text`, `Icon` and `Surface` resolve colour, style and content colour the way M3's do (`LocalContentColor`, `LocalTextStyle`, `contentColorFor`), so nothing else should move apart from the icon glyphs.
- **Icon-taking parameters became `SparkIcon`**: `ActionRow`, `EmptyState` (the vector overload; the `Painter` one stays for the brand mark), `MessageIcon`, `GateMessageScreen`, `HabitExample` and Privacy's `ClaimRow`, as well as `Tab.icon`. A gap therefore travels as `SparkIcon.Vector(Icons.X)` rather than through `Icon`'s `ImageVector` overload. It draws the same (`rememberSparkIconPainter` → `rememberVectorPainter`), and one parameter type serves both kinds of icon.
- **The mirrored icons are in one new file, `ui/components/AppIcons.kt`**: `Back`, `ChevronStart`, `ChevronEnd`, `Walking`, `Book` and `OpenElsewhere`, each `mirroredInRtl()` once. They are shared because the end chevron alone is used on four screens.
- **Deviations from 5.6:**
  - **`OpenInNew` → `ShareExternal` is mirrored.** The table says no, but Material's icon is `Icons.AutoMirrored.Filled.OpenInNew`, which is the table's own rule for Mirror. Spark's glyph is the same picture, an arrow leaving a box towards the top end corner, so it flips with the reading direction as Material's did.
  - **`Import`/`Export` (check in and out) are not mirrored.** Their arrows are vertical, into and out of a tray, and the glyphs are symmetric about the vertical axis, so a flip changes nothing. Mirroring them would only list a symmetric icon as directional. `MirroredIcon`'s KDoc no longer gives check in and out as its examples.
  - `Import`/`Export` are taken over `Disconnect` (open item 2), as the stage instructions said. The Check-In button itself is still M3 (PR 6).
- **`ic_stat_check_out` and the button no longer match.** The notification action's icon is still Material's `Logout` glyph, while the button now draws Spark's `Export`. Redrawing the drawable from Spark's `spark_icons_lbc_export` belongs to PR 7 (off-Compose surfaces). CLAUDE.md's notification-icons entry states the gap.
- **The pill's `Surface`** is Spark's clickable one. Its own touch floor is 44dp (`minimumTouchTargetSize`) where M3's was 48dp, so the `heightIn(min = 48.dp)` already on it is now what holds the 48dp rule, not a duplicate.
- **Gates**: everything passes except `lintDebug`, which fails on exactly **44** × `MaterialComposableHasSparkReplacement`, down from 152. They are all PR 4's controls plus two left for later: `Button`×4 (`CheckInScreen`, which is PR 6, plus `FullScreenMessage`, `GateMessageScreen`, `ReportsScreen`), `TextButton`×11, `OutlinedButton`×4, `FilledTonalButton`, `IconButton`×7, `AlertDialog`×3, `DropdownMenu` and `DropdownMenuItem`×2, `FilterChip`, `RadioButton`, `OutlinedTextField`, `Card`, `HorizontalDivider`, `Scaffold`, `TopAppBar`, `SnackbarHost`, `NavigationBar`, `NavigationBarItem`, and `ReminderTimePicker`'s `MaterialTheme`, which is D10's and takes a site-level `@SuppressLint` in PR 4. The large name field's `TextField` is not flagged. The other lint findings are unchanged from PR 2.
- **Device checks still open**: the icon glyphs against Material's at the same size (above all the `*Criteria` icons' weight, which 5.6 asks for), `ShareExternal` reading as "opens elsewhere", and the Arabic pass on every mirrored icon.

### PR 4 — group 4 of `ui/spark-adoption`: the direct swaps

One commit per group, so a regression can be bisected to a control. The file and line map is section 5.1.

| Group | Today → Spark | Notes |
|---|---|---|
| Buttons | `Button`/`FilledTonalButton`/`OutlinedButton`/`TextButton` → `Button.Primary`/`Button.Secondary`/`Button.Tertiary`/`Button.Ghost` (D6) | `FullScreenMessage`, `GateMessageScreen`, `WelcomeScreen`, `LicensesScreen`, `PresenceCheckScreen`, `HabitEditor`, `HabitPickerScreen`, `HabitsScreen`, `LanguagePicker`, `ReminderTimePicker`'s dialog buttons, `HabitSwitcher` (see PR 6), `ReportsScreen` export. `size = ButtonSize.Large` where today's minimum height is 56dp (`FullScreenMessage`'s `ACTION_MIN_HEIGHT`). One `Primary` per screen; a second filled action is `Secondary`. **Never `isLoading`** (it draws a spinner, S`buttons/Button.kt:100-111`). Not the Check-In button: PR 6. |
| Icon buttons | `IconButton` (7) → `IconButtonGhost(icon = LeboncoinIcons.X, contentDescription = ...)`, chevrons `.mirroredInRtl()` | The minimum touch target becomes 44dp (C`tools/modifiers/TouchTarget.kt:49`) against M3's 48. Wrap in `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` to keep the app's 48dp rule, and keep `MONTH_SELECTOR_HEIGHT` at 48. Spark wraps every icon button in a tooltip showing `contentDescription`: check it doesn't fire on a long press over the month arrows. |
| Dialogs | `AlertDialog` (3: `HabitEditor`, `LanguagePicker`, `ReminderTimePicker`) → Spark `AlertDialog` (`@ExperimentalSparkApi`) | `ReminderTimePicker` keeps its local `MaterialTheme` around the `TimePicker` only; the dialog frame becomes Spark's. Titles take an `AppTypography.headline` `Text`. |
| Menus | `DropdownMenu`/`DropdownMenuItem` (`HabitsScreen`) → Spark's | The item is the scoped `DropdownMenuItemColumnScope.DropdownMenuItem` (S`menu/DropdownMenu.kt:461`), not the deprecated unscoped one. |
| Selection | `FilterChip` (`ReportsScreen`) → `SegmentedControl.Horizontal` (D11); `RadioButton` (`LanguagePicker`) → `RadioButtonLabelled` | The segmented control's 52dp minimum height moves the Reports scroll content. Nothing on Reports has a fit budget, but check the first card is still in view on a small viewport. |
| Text fields | `OutlinedTextField` → Spark `TextField`; the large centred `TextField` stays ours (D9), restyled | `counter = TextFieldCharacterCounter(...)` if the editor shows a length limit today. Spark's `keyboardOptions` defaults to sentence capitalisation, as the app does now. |
| Frame | `Scaffold`/`TopAppBar`/`NavigationBar`/`NavigationBarItem`/`SnackbarHost` → Spark's | `TopAppBar` and `NavigationBar` are `@ExperimentalSparkApi`; `@OptIn` at the call site, replacing today's `ExperimentalMaterial3Api` one. `NavigationBarItem.icon` takes a `SparkIcon`: `tab.icon`. Spark lint's `UnusedSparkScaffoldPaddingParameter` and `SparkScaffoldBarWithoutInsets` must pass. The back arrow is `LeboncoinIcons.ArrowLeft.mirroredInRtl()` in an `IconButtonGhost`, never Spark's `UpNavigationIcon`, which cannot mirror (D5). |
| Snackbar | `SnackbarHostState` (M3) → Spark's (D12) | `LocalSnackbarHostState`'s type changes; `AppUpdateOffer` and every `showSnackbar` caller recompile against it. Spark's `showSnackbar` signature is a superset. |
| Cards and dividers | `Card` (`SectionCard`) → `Card.Flat(colors = SparkTheme.colors.backgroundVariant)`; `HorizontalDivider` → Spark's, `intent = Outline` at the existing 50% | Never `Card.Highlight*`: its border is `accent`, vermilion (design 4). |

**Verify**:
- CI, and `lintDebug`'s `MaterialComposableHasSparkReplacement` count should be the expected residue (the Check-In button, the large name field's `BasicTextField` if flagged, the time picker).
- **TalkBack pass** on every swapped control: Spark's `RadioButtonLabelled`, `SegmentedControl` (`role`) and `NavigationBarItem` must announce selection.
- Device check under Arabic: chevrons and the back arrow point the right way, and the segmented control orders right to left.

**Docs**: CLAUDE.md wherever an M3 component is named (the nav scaffold entry, the habit switcher entry, the Settings entry), and the 48dp rule now saying Spark's 44dp floor is raised to 48.

**Done** on `plan/spark-migration`, one commit per group below. What it found, and where it departs from the table above:
- **Buttons.** Every site follows D6. One `Primary` per screen holds: `FullScreenAction` (tour, naming step, widget page), the gate stop's action, and Reports' export; everything else is `Secondary`, `Tertiary` or `Ghost`. `FullScreenAction` is `ButtonSize.Large`, so its `ACTION_MIN_HEIGHT` and `ACTION_CORNER` are gone (the shape is Spark's pill); it keeps its `AppTypography.titleMedium` label. Button icons go through the `icon` parameter, which Spark draws with no description, as the label already names the action. `HabitSwitcher` is a `Button.Ghost` around its existing row (PR 6.2 still redesigns it): a ghost takes the slot's content colour, which in the top bar's actions is `onBackgroundVariant`, so its mark is tinted `onSurface` to stay beside the name as it was. The Check-In button keeps M3 under a site-level `@SuppressLint` whose comment names PR 6.1.
- **Icon buttons** go through one new app component, `ui/components/AppIconButton.kt`, rather than a `Modifier.sizeIn` at each site. `IconButtonGhost` applies its `modifier` to an outer `Box` with the button pinned to its top-start corner, so a 48dp minimum there would leave a 44dp target off-centre. `AppIconButton` centres the ghost button in a `Box` of at least 48dp, into which the platform widens the 44dp target at the input layer. It takes `contentDescription` as required and an `intent`: `Main` (ink) everywhere except the Habits row's overflow, which is `Neutral` (the muted ink it was tinted before). `MONTH_SELECTOR_HEIGHT` stays 48dp.
- **Dialogs.** The three are Spark's `AlertDialog`, a thin wrapper over M3's with the same slots and defaults, so nothing moves visually. `ReminderTimePicker`'s title now passes `AppTypography.headlineSmall` explicitly instead of relying on the local `MaterialTheme`'s, so all three dialog titles read one style. The local `MaterialTheme` wraps the whole dialog still, and `ReminderTimePicker` carries the site-level `@SuppressLint` D10 asks for.
- **Menus.** The Habits row's overflow is Spark's `DropdownMenu`; inside its `DropdownMenuItemColumnScope` the import `com.adevinta.spark.components.menu.DropdownMenuItem` resolves to the scoped item, not the deprecated unscoped one (the build reports no deprecation). The item's slots match M3's, so only the imports change.
- **Selection.** Reports' scope is `SegmentedControl.Horizontal` with **Month** and **All time** (a new `scope_month` string, with its Arabic twin), on its own row above the month stepper, which keeps its slot and disables under all time as before. The chip's toggle no longer fits two segments, so `ReportsViewModel.toggleAllTime` is gone: All time calls `selectAllTime`, and `selectMonth` is now a no-op on a month already (reading the view-model's scope, so the race the toggle guarded against stays closed). `ReportsViewModelTest`'s toggle test became two: All time then Month back to back lands on the current month, and Month on a stepped-to month keeps it. `LanguagePicker`'s rows are `RadioButtonLabelled` with the 48dp floor kept on the row; Spark spaces the radio and label 8dp apart where the hand-built row used 16dp.
- **Text fields.** The dialog's field is Spark's `TextField` (`label`, and `FormFieldStatus.Error` with the problem as `stateMessage`). No `counter`: the editor shows no length limit, and `TOO_LONG` is a stated problem rather than a cap. The naming step's large centred field is now the app's own `TitleNameField` on `BasicTextField` (D9): `AppTypography.headlineSmall` centred, an underline drawn in `outline` (1dp), `main` while focused or `error` with a problem (2dp), the problem in `caption`/`error` beneath, and the crossfading placeholder as before. It was M3's `TextField`, which lint did not flag, so the move is D9's restyle rather than a lint fix, and no suppression is needed for it.
- **Frame and snackbar, one commit.** Spark's `SnackbarHost` takes only Spark's `SnackbarHostState`, so the snackbar group cannot land apart from the scaffold's; `LocalSnackbarHostState` changed type in the same commit, and every `showSnackbar` caller compiled unchanged. Two opt-ins differ from 5.5: `AppTopBar` needs `ExperimentalMaterial3Api` beside `ExperimentalSparkApi`, because Spark's `TopAppBar` takes M3's experimental `TopAppBarColors` and scroll behaviour; and `BottomNavigationBar` needs `ExperimentalSparkApi`, since Spark's `NavigationBar` is experimental (its `NavigationBarItem` is not). The nav item draws `tab.icon` with no description, the label naming the tab. Snackbars now take Spark's default `Info` intent, with its icon; whether that icon belongs on an update offer or a locked-switcher note is a device check for PR 5.
- **Cards and dividers.** `SectionCard` is `Card.Flat(colors = backgroundVariant, contentPadding = 16dp)`, its own inner padded `Column` gone since Spark's card pads a `Column` itself. `SectionDivider` is Spark's `HorizontalDivider(intent = Outline)` at the same half strength through `Modifier.alpha(0.5f)`, since the Spark divider takes an intent, not a colour.
- **Gates**: all pass, `lintDebug` included, with **0** `MaterialComposableHasSparkReplacement` findings. Two site-level suppressions remain in `src/main`, each with its reason: `CheckInScreen`'s `CheckInOutButton` (PR 6.1) and `ReminderTimePicker`'s local `MaterialTheme` (D10). The large name field needs none: it is on `BasicTextField`. M3 is now imported only for those two, for `Typography` in `Type.kt` (the source `AppTypography` is cut from), for `SnackbarDuration`/`SnackbarResult` (Spark reuses them) and for the opt-in markers.
- **Device checks still open**: TalkBack on `RadioButtonLabelled` (Spark builds it on `triStateToggleable` with `Role.RadioButton`, where the old row used `selectable`, so check it still announces "selected"), on the segmented control and on the nav items; the icon buttons' long-press tooltips, above all on the month arrows; the 48dp targets of `AppIconButton` (Spark lays out 44dp, the platform widens at input); Reports' first card still in view under the 52dp segmented control on a ≤560dp viewport; `Button.Secondary`'s solid brass for the editor's time (section 9 item 2); `TitleNameField`'s underline and error against M3's; the default `Info` snackbar icon; chevrons, back arrow and segment order under Arabic.

### PR 5 — `ui/spark-own-components`: our components in Spark tokens

Design 6 phase 3. These are app composables, restyled only:
- `FullScreenMessage` and `EmptyState` first.
- Then `SessionIntervalRow`, `CalendarGrid` (beyond PR 2's ring), the charts (`LineChart`, `BarChart`, `DonutChart`: strokes `SparkTheme.colors.outline`, the hues from `descriptiveHues()`, axis type `caption`), `ChartCard`, `StatsRow`, `LegendRow`, `PageDots`, `ActionRow`, `AboutCard` and `HabitNameRow`.
- `ActionRow` keeps its own 48dp layout. Don't rebuild it on Spark's `ListItem` (experimental, and it adds slots the rows don't use).
- **Re-measure**: `DETAIL_SLOT_HEIGHT` (the empty state's type changes), `DonutChartDefaults.size()`, and `DURATION_COLUMN`/`DURATION_COLUMN_MAX` (render "12h 59m" at font scale 2.0 in Manrope caption with `tnum`).
- **No logic changes.** `ChartGeometryTest` and `BrandGridTest` must pass untouched.

**Done** on `plan/spark-migration`, one commit. What it found, and where it departs from the list above:
- **Most of the list was already in Spark tokens.** PR 3 moved every colour and type read to `SparkTheme` and `LocalAppTypography`, and PR 4 the controls, so `FullScreenMessage`, `MessageIcon`, `EmptyState` and `HabitNameRow` needed nothing, and the charts' strokes already drew in `outline` and the splits in `descriptiveHues()`. What was left was M3-era literals and stale reasoning: `CircleShape` and an 8dp `RoundedCornerShape` (now `shapes.full` and `shapes.small`, the same geometry), two 0.38 alphas (M3's disabled alpha; now Spark's `dim3`, 0.40, on `ActionRow`'s disabled row and the calendar's days outside the record), explicit `SemiBold`/`Bold` on body and caption figures (now `.highlight`, the Medium weight: `StatsRow`'s value, `LegendRow`'s count, the ledger's duration and Hours by Month's figures), and comments that reasoned from M3's `outlineVariant` (`PageDots`, the split card) or named `bodySmall` (`AboutCard`).
- **Axis labels are `caption`** (12/16 Normal), where they were `small` (11/16 Medium). The line height is the same, so no chart card moves; "Sep '26" grows from 37.2 to 40.6dp, inside the 48dp a sixth of the card gives each bar.
- **The calendar's today number stays Bold**, not `.highlight`. With `main` and `onSurface` both ink in this palette, weight and the dot are all that mark today, and Medium is too close to the Normal beside it.
- **Measured, not estimated.** Each constant below was re-derived by shaping the strings with HarfBuzz over the shipped `res/font` files (tabular figures where the row uses them). It found two real defects:
  - `DURATION_COLUMN` (56dp) was narrower than Arabic's widest duration: "12 س 59 د" is 57.0dp in Plex Arabic SemiBold at 12sp (56.8 at Medium), so a two-digit-hours session wrapped to "12 س 59" at the default scale, a wrong number. And the 88dp cap held "12h 59m" (50.1dp) only up to a font scale of 1.76; at 2.0 it showed "12h". Now **60dp** (57.0 plus 3) to **120dp** (twice that, since caption's 12sp scales linearly to 2.0, Android's largest). At 2.0 on a 360dp phone the range keeps 192dp of the 320dp row and ellipsizes, which is the order of sacrifice the KDoc states.
  - `LegendRow` measured its label before its count, so beside a grown ring a long label could take the whole row and leave the count no width. The label is now `weight(1f, fill = false)` and wraps, as `StatsRow`'s already did. That is layout, not logic: what the row says is unchanged.
- **`DonutChartDefaults`**: `BaseSize` stays **112dp**, now derived: the hole's inscribed square must hold the widest caption figure, 57.0dp, which needs 24 + 57.0 / 0.707 = 104.6dp; 112 leaves a 62.2dp square. `MaxSize` goes from 148 to **140dp**: Reports' card content is 288dp on a 360dp phone, the row spends 20dp between ring and legend, and the widest legend line is 103dp of text (Arabic's "أيام دون حضور" and a three-digit count in Plex body2) plus 24dp of swatch and gaps, which leaves 141. Ring and legend both grow with the font, so they outgrow the row together from a scale of 1.13 in Arabic (1.17 in English) whatever the cap; from there the legend wraps its labels rather than lose a count, and the cap, reached at 1.25, bounds how much of the row the ring takes.
- **`DETAIL_SLOT_HEIGHT` stays 180dp.** The empty state's type did not change (Outfit `titleMedium` 24dp line, Manrope `body2` 20dp lines), so the arithmetic gives 176dp as before: 24 + 48 + 12 + 24 + 4 + 2 × 20 + 24. The longest message is 284dp against 280dp of room on a 360dp phone, so two lines, and still two in a 320dp phone's 232dp; Arabic's is 197dp, one line. The sessions state is 140dp. The KDoc now carries the figures. `TEXT_CONTENT_HEIGHT` (110dp) still covers the growth: at 2.0 the empty state's text grows by at most 104dp under linear scaling, and less under Android 14's non-linear curve.
- **Gates**: all pass, with 0 `MaterialComposableHasSparkReplacement` findings; `ChartGeometryTest` and `BrandGridTest` are untouched.
- **Device checks**: Arabic at font scale 1.0 with a session of ten hours or more (the duration whole, on Check-In and History); font scale 2.0 in both languages (duration whole, range ellipsized); Reports' splits at 1.0, 1.3 and 2.0 in Arabic (counts whole, labels wrapping beside a 140dp ring); the axis labels at caption size; `dim3` on Settings' locked Habits row and on the calendar's future days, in both themes; the weight of the `.highlight` figures beside the Outfit titles.

### PR 6 — one branch per design item

Each is small, design-led, and judged on device before the next. Design 6 phase 4, with Spark's constraints now known:

1. **`ui/check-in-button`**: D8. `SparkColorScope({ copy(main = container, onMain = content) })` around a `Button.Primary(size = ButtonSize.Large, modifier = Modifier.fillMaxWidth().height(ACTION_HEIGHT))`. `container` and `content` come from `animateColorAsState` over start and stop. Check on device whether Spark animates the change again inside the button; if the two animations stack visibly, set the outer spec to `snap()`. Keep the label in `AppTypography.title` Bold. Icons: the section 5.6 pick for check in and check out, `.mirroredInRtl()`. `FIXED_CONTENT_HEIGHT` is unchanged (still 64dp); re-check it.
2. **`ui/habit-switcher`**: the top bar's end slot. A `Button.Ghost` with the name in `AppTypography.title` and the swap or lock icon. Locked state: `enabled = true` (so it can snackbar) with the lock icon and `onSurface` at `dim1`.
3. **`ui/presence-scrim`**: a `SparkTheme.colors.scrim`-based container behind `PresenceCheckScreen`'s hint and fallback, so white text over a bright preview reads at 4.5:1. Measure it over a white wall.
4. **`ui/gauge-type`**: the readout (`AppTypography.display`/`headline` with `tnum`, step-down unchanged), the celebration's type, and `BrandGrid`'s colours (`primary` → `SparkTheme.colors.main`; the geometry never takes a Spark shape token, design 2.4).

**Done** on `plan/spark-migration`, one commit per item. What each found:
- **6.1, the Check-In button.** `Button.Primary(size = ButtonSize.Large)` inside `SparkColorScope { copy(main = container, onMain = content) }`, with `Modifier.height(ACTION_HEIGHT)` (64dp) winning over the Large size's 56dp `defaultMinSize`; the shape is Spark's pill. The `@SuppressLint` and its comment are gone, and M3's `Button`/`ButtonDefaults` are no longer imported anywhere in `src/main`.
  - **The two animations cannot stack**, so the outer spec stays the default spring rather than `snap()`. Read in M3 1.4.0's bytecode (the version Spark resolves): `ButtonColors.containerColor(enabled)` returns a plain colour and `ButtonKt` calls no `animateColor*`, so the button draws each frame's scoped `main` as given. The device check is now only that the change looks as before.
  - **The icon stays 28dp, in the content row** rather than Spark's `icon` slot, which draws 16dp (S`buttons/Button.kt:228`) and would be lost on a 64dp button. Spark's content `Row` is top-aligned, so icon and label each take `align(CenterVertically)`. `Import`/`Export` stay unmirrored (PR 3: symmetric about the vertical axis).
  - The label keeps `AppTypography.titleMedium` Bold, one line, ellipsized. `FIXED_CONTENT_HEIGHT` is unchanged: the button is still 64dp tall.
- **6.2, the habit switcher.** Still the `Button.Ghost` PR 4 made it, now without the extra `Row` (Spark's content slot is a `RowScope` already; name and mark each take `align(CenterVertically)`, since that row is top-aligned). The name stays `AppTypography.titleLarge` (the plan's "`title`" is that field: `AppTypography` has no `title`), with the swap mark or `LockOutline`. **Locked**, both take `onSurface` at `dim1`: 5.2:1 on paper in light and 7.7:1 in dark, measured from the palette, so the dimmed name still clears AA. The button stays enabled, because the tap is what snackbars why. The colours are passed to the `Text` and `Icon` rather than provided as `LocalContentColor`, which would have meant importing M3 here for that one local; the ghost's ripple therefore keeps the top bar's muted content colour.
- **6.3, the presence scrim.** `PresenceCheckScreen`'s hint, error, success and fallback button sit in one rounded panel (`shapes.large`, 16dp in from the edges, 48dp off the bottom as before), drawn by a private `OverPreview`: `scrim` (black) at `SCRIM_ALPHA` **0.88** with the **dark scheme** inside it (`SparkColorScope { SparkDark }`), in both themes. The preview can be a white wall in either theme, so light-on-dark is the only pairing with something fixed to stand on, and the dark scheme already holds every colour the panel draws.
  - **Measured over pure white** (black at 0.88 over white is #1F1F1F): vermilion error text 4.57:1, muted hint (ink-muted) 7.59:1, cream text and marks 13.1:1, the Tertiary button's outline 3.49:1. The vermilion is what sets the alpha: 0.75 would have held the hint at 4.77:1 but the error at 2.87:1, and the outline needs 0.84 for 3:1.
  - **Deviation: the dismiss button is in a scrim too**, a `shapes.full` circle around its 48dp target. The plan names only the hint and fallback, but the dark scheme's cream cross on a white wall is 1.2:1 and the light scheme's ink one vanishes over a dark room, so the screen's only exit had the same defect.
  - Inside the panel the per-text 24dp side padding and the 16/8dp spacers became the panel's 16dp padding and a 12dp `spacedBy`, so a panel ending in text has no trailing gap.
- **6.4, the gauge's type and colours.** Nothing left to move: PR 3 had already put the readout on `AppTypography.displayMedium`/`headlineMedium` with `tabularFigures()` (the step-down unchanged), the celebration on `AppTypography` and Spark's `body2`/`caption`, and `BrandGrid`, `BrandGridGauge` and the readout on `SparkTheme.colors.main` (the readout through the theme's ink content colour). The geometry takes no shape token; `BrandGridTest` is untouched.
  - **The budget re-check found a 6.1 defect, fixed in this commit.** `FIXED_CONTENT_HEIGHT` (236dp) holds: date row 20 (`body2`) + collapsed pill 48 (its floor; 8 + 24 + 8 inside) + spacer 12 + button 64 + readout 56 (`displayMedium`'s 52dp line) + gap 12 = 212dp, so 24dp of breathing room, as before. `TEXT_CONTENT_HEIGHT` (116dp) holds: date 20 + pill label 20 + readout 56 = 96dp of scaling text, so it errs 20dp high, as intended. But the button's *inside* had changed: `ButtonSize.Large` pads 18dp top and bottom (S`buttons/ButtonSize.kt:43-46`) where M3's button padded 8, so the label had 28dp instead of 48, and `titleMedium`'s 24dp line clipped from a font scale of about 1.17. The size has no padding parameter, and `Medium` (12dp) would only move the threshold to about 1.67. The label is now `wrapContentHeight(unbounded = true)`: measured at its own height and centred, it draws over the padding up to the full 64dp, where the button's shape clips it. That is more room than M3 gave (48dp).
- **Gates**: all pass, `lintDebug` included, with **0** `MaterialComposableHasSparkReplacement` findings. One site-level suppression remains in `src/main`: `ReminderTimePicker`'s local `MaterialTheme` (D10). M3 is now imported only for it, `Typography` in `Type.kt`, `SnackbarDuration`/`SnackbarResult`, History's window size class and the opt-in markers.
- **Device checks**: the Check-In button's brass-to-ink change (one smooth transition, no double animation), its pill shape, 28dp icon and label at font scale 1.0, 1.3 and 2.0 in both languages (label whole, centred, not clipped); the switcher locked and unlocked in both themes (the dimmed name beside the lock, the snackbar on tap); the face check over a white wall and a dark room in both themes, with each message (looking, looking slow, unsupported, an error) and both fallback buttons, and the dismiss circle at top start.

### PR 7 — `ui/spark-off-compose`: surfaces Spark cannot reach

Design 5's table, executed:
- `values/colors.xml` and `values-night/colors.xml`: `brand_window_background`, `widget_background`, `widget_text`, `widget_mark_rest`, `widget_mark_running` and `ic_launcher_background`, to the listed values.
- `play-store-assets/generate_icons.py`: the foreground fill `#FFFFFF` → `#F1E6CE` and the background `#3F51B5` → `#1B2A4A`. Run it and paste the printed `pathData` and fills back (never hand-edit). Regenerate the 512px Play icon. `verify()` must still pass.
- Status-bar icons stay white (alpha only).
- **Device check**: the launcher icon (themed and unthemed), the splash in both themes, the widget on a light and a dark wallpaper, and no off-white flash at cold start.

**Done** on `plan/spark-migration`. What it found:
- **Colours**, from design 5 and the palette in `ui/theme/Color.kt`: `brand_window_background` and `widget_background` paper (`#F1E6CE` / `#0E1830`), `widget_text` and `widget_mark_running` ink (`#1B2A4A` / `#EFE4CB`), `widget_mark_rest` ink at 40% (`#661B2A4A` / `#66EFE4CB`), `ic_launcher_background` ink `#1B2A4A` (one value, no night override, as before). Measured: widget text 11.5:1 (light) and 14.0:1 (dark); the launcher's paper mark on ink 11.5:1. The resting mark is 2.25:1 in light and 3.25:1 in dark, decorative (`importantForAccessibility="no"`, the label carries the meaning), keeping the gauge's convention.
- **`generate_icons.py` now prints the fills and the tile colour** beside each `pathData` (`INK`/`PAPER`, replacing `BRAND_INDIGO`/`WHITE`), so "paste the printed values back" covers colour too; it printed no fill before. Run: `verify()` passes (29.34 of 33). The printed paths were compared with the shipped ones by script and are byte-identical, so only `ic_launcher_foreground.xml`'s `fillColor` changed. `ic_stat_checkin.xml` stays white (printed as such). The 512px Play icon is regenerated, paper on ink.
- **`ic_stat_check_out` is redrawn from Spark's `spark_icons_lbc_export`** (PR 3's carried gap): the path verbatim with its `evenOdd` fill type, filled white, and no longer `autoMirrored`, since the glyph is symmetric about its vertical axis (as in-app). Its MIT notice is already on the Licences screen (PR 1).
- Comments naming indigo in `ic_launcher_background.xml` and `themes.xml` now say ink.
- **Not done, left for the store re-shoot**: the feature graphic (`generate_feature_graphic.py`) still draws its deep-indigo gradient, and `play-store-assets/README.md` describes it against the old launcher indigo. Both go with the screenshots, which need a device.
- **Gates**: all pass.
- **Device checks**: the launcher icon themed and unthemed, the splash in both themes, the widget on a light and a dark wallpaper (both faces, at rest and running), the check-out action icon in the timer and reminder notifications, and no off-white flash at cold start.

### PR 8 — `ui/spark-hardening`: lock it in

1. **Audit the lint suppressions** (D3). Every `@SuppressLint("MaterialComposableHasSparkReplacement")` left in `src/main` carries its reason, and each still has no Spark equivalent: the time picker and the large name field (D9), and nothing else unless the PR says why.
2. **detekt `ForbiddenImport`** in `config/detekt/detekt.yml`, carrying design 4 as a build failure rather than a review rule:
   ```yaml
   style:
     ForbiddenImport:
       active: true
       imports:
         - value: 'com.adevinta.spark.components.progress.*'
           reason: 'Hours have no direction: no gauge, meter, progress or tracker. Includes Spinner.'
         - value: 'com.adevinta.spark.components.progressbar.*'
           reason: 'As above.'
         - value: 'com.adevinta.spark.components.gauge.*'
           reason: 'As above.'
         - value: 'com.adevinta.spark.components.meter.*'
           reason: 'As above.'
         - value: 'com.adevinta.spark.components.rating.*'
           reason: 'Grades a day or a habit.'
         - value: 'com.adevinta.spark.components.badge.*'
           reason: 'A count per habit ranks habits.'
         - value: 'com.adevinta.spark.components.stepper.*'
           reason: 'Nothing in the app takes a number; a stepper invites a target.'
         - value: 'com.adevinta.spark.components.slider.*'
           reason: 'As stepper.'
         - value: 'com.adevinta.spark.components.fileupload.*'
           reason: "FileKit's provider and initializer are removed from the manifest."
         - value: 'com.adevinta.spark.components.image.*'
           reason: 'Coil-backed; the app loads no images.'
         - value: 'org.jetbrains.compose.resources.*'
           reason: 'AndroidContextProvider is removed; a Compose Resources read throws.'
   ```
   The package names match the tag's `S/` listing (`progress`, `progressbar`, `gauge`, `meter`, `rating`, `badge`, `stepper`, `slider`, `fileupload`, `image`); re-check them on any Spark bump.

   **The `SparkTheme` call cannot be banned the same way.** The object and the composable share a name, so banning the import would ban every `SparkTheme.colors` read. detekt's `ForbiddenMethodCall` needs type resolution, which this repo's detekt runs without. So D7's rule becomes a JVM test, `SparkThemeCallSitesTest`, which reads `src/main/java` (as `ClosedByTest` already reads `CheckInSessionDao.kt`) and asserts `SparkTheme(` appears only in `ui/theme/Theme.kt`. Add it in **PR 2**, the moment the discard in PR 1 makes a stray call a crash.
3. **`isLoading`** cannot be banned by import. A CLAUDE.md line, plus a review item.
4. **CLAUDE.md**:
   - A new "Spark" gotcha entry: design 4's forbidden list (pointing at the detekt rule), the one-family typography and `AppTypography`, `SparkColorScope` as the only nested theme, the four manifest removals, Nunito discarded and why that makes D7 load-bearing, D5's icon rule, the time picker's local `MaterialTheme`.
   - Update Architecture (`Jetpack Compose + Spark + Room…`) and the key-paths `ui/theme/` line.
5. **`play-store-assets/`**: re-shoot every screenshot and the feature graphic. Update LISTING.md if anything describes the look.
6. **The design 6 phase 6 review pass**: Arabic, dark, font scale 2.0, animations off, a ≤560dp viewport, every budget re-measured, and design 2.2's contrast table re-run on device.

When PR 8 lands, `v4.0` is releasable from the migration's side.

---

## 4. Branch and CI mechanics

- `ui/spark-adoption` (PRs 1-4) merges first, as one PR. Each later PR branches off `v4.0` after its predecessor merges: serial, not stacked, so a PR never targets another feature branch.
- PR 6's four items may run in parallel once PR 5 is in, since they touch disjoint files (`CheckInScreen.kt`, `HabitSwitcher.kt`, `PresenceCheckScreen.kt`, `TimerGauge.kt`/`CheckOutCelebration.kt`/`BrandGrid.kt`).
- CI already runs on `v[0-9]*` pushes and PRs. Nothing in `.github/workflows/ci.yml` changes.
- `VERSION_CODE`/`VERSION_NAME`: unchanged by these PRs. Set them when v4.0 is cut.

---

## 5. Call-site maps

From the inventory of `app/src/main/java/com/checkin/app/` on `v4.0`.

### 5.1 M3 components per file (PR 3 and PR 4)

| File | M3 today | Becomes |
|---|---|---|
| `MainActivity.kt` | `MaterialTheme` read, `Surface` | `SparkTheme.colors.background`, Spark `Surface` |
| `ui/AppUpdateOffer.kt` | `SnackbarDuration`, `SnackbarResult` | unchanged (Spark reuses them) |
| `ui/about/AboutCard.kt` | `Text`×2 | Spark `Text` |
| `ui/about/LicensesScreen.kt` | `Text`×7, `OutlinedButton` | Spark `Text`, `Button.Tertiary` |
| `ui/about/PrivacyScreen.kt` | `Text`×3, `Icon` | Spark |
| `ui/checkin/CheckInScreen.kt` | `Text`×3, `Icon`×2, `Button`+`buttonColors`, `Surface` pill | PR 6.1 for the button; Spark `Surface` pill |
| `ui/checkin/CheckOutCelebration.kt` | `Text`×5 | Spark `Text`, `AppTypography` |
| `ui/checkin/TimerGauge.kt` | `Text` | Spark `Text`, `AppTypography.display`/`headline` |
| `ui/components/ActionRow.kt` | `Text`×2, `Icon` | Spark |
| `ui/components/ChartCard.kt` | `Text`×5 | Spark |
| `ui/components/EmptyState.kt` | `Text`×2, `Icon` | Spark |
| `ui/components/FullScreenMessage.kt` | `Text`×3, `Icon`×2, `Button` | Spark, `Button.Primary(size = Large)` |
| `ui/components/LocalSnackbarHostState.kt` | `SnackbarHostState` | Spark's |
| `ui/components/ReminderTimePicker.kt` | `AlertDialog`, `TimePicker`, `TextButton`×2, `Text`×4 | Spark `AlertDialog`, `Button.Ghost`; **M3 `TimePicker` kept** (D10) |
| `ui/components/SectionCard.kt` | `Card`, `HorizontalDivider`, `Text`×2 | `Card.Flat`, Spark `HorizontalDivider` |
| `ui/components/SessionIntervalRow.kt` | `Text`×2 | Spark |
| `ui/habits/HabitEditor.kt` | `AlertDialog`, `FilledTonalButton`, `TextButton`×2, `OutlinedTextField`, `TextField`, `Icon`, `Text`×7 | Spark `AlertDialog`, `Button.Secondary`, `Button.Ghost`, Spark `TextField`; large field ours (D9) |
| `ui/habits/HabitNameRow.kt` | `Text`×2 | Spark |
| `ui/habits/HabitPickerScreen.kt` | `TextButton`, `Text`×2 | `Button.Ghost` |
| `ui/habits/HabitsScreen.kt` | `DropdownMenu`, `DropdownMenuItem`×2, `IconButton`, `TextButton`, `Icon`×4, `Text`×4 | Spark menu, `IconButtonGhost`, `Button.Ghost` |
| `ui/history/CalendarGrid.kt` | `Text`×2 | Spark |
| `ui/history/HistoryScreen.kt` | `IconButton`×2, `Icon`×2, `Text`×3; window-size-class | `IconButtonGhost`; window-size-class unchanged (`material3-window-size-class` stays) |
| `ui/navigation/AppNavScaffold.kt` | `Scaffold`, `TopAppBar`, `SnackbarHost`, `IconButton`, `Icon`, `Text` | Spark frame |
| `ui/navigation/BottomNavigationBar.kt` | `NavigationBar`, `NavigationBarItem`, `Icon`, `Text` | Spark |
| `ui/navigation/HabitSwitcher.kt` | `TextButton`, `Icon`, `Text` | PR 6.2 |
| `ui/presence/GateMessageScreen.kt` | `Button`, `TextButton`, `Text`×2 | `Button.Primary`, `Button.Ghost` |
| `ui/presence/PresenceCheckScreen.kt` | `OutlinedButton`×2, `IconButton`, `Icon`×3, `Text`×6 | `Button.Tertiary`, `IconButtonGhost`; scrim PR 6.3 |
| `ui/reports/ReportsScreen.kt` | `Button`, `FilterChip`, `IconButton`×2, `Icon`×3, `Text`×4 | `Button.Primary`, `SegmentedControl`, `IconButtonGhost` |
| `ui/settings/LanguagePicker.kt` | `AlertDialog`, `RadioButton`, `TextButton`, `Text`×3 | Spark `AlertDialog`, `RadioButtonLabelled`, `Button.Ghost` |
| `ui/theme/Theme.kt`, `Type.kt` | `MaterialTheme`, color schemes, `Typography` | PR 2 |
| `ui/welcome/WelcomeScreen.kt` | `OutlinedButton`, `TextButton`×2, `Text`×4 | `Button.Tertiary`, `Button.Ghost` |

Nothing in `ui/components/charts/`, the ViewModels or the pure files imports M3, so none of them changes.

### 5.2 Typography (57 sites)

| M3 style today | Sites | Becomes | Size today → after |
|---|---|---|---|
| `bodyMedium` | 19 | `SparkTheme.typography.body2` | 14/20 → 14/20 |
| `bodySmall` | 12 | `caption` | 12/16 → 12/16 |
| `bodyLarge` | 2 | `body1` | 16/24 → 16/24 |
| `labelLarge` | 3 | `body2.highlight` (`highlightWeight` set to Medium in `sparkTypographyFor`) | 14/20 medium → same |
| `labelMedium` | 3 | `caption.highlight` | 12/16 medium → same |
| `labelSmall` | 3 | `small` (tuned to 11/16) | 11/16 → 11/16 |
| `titleMedium` | 5 | `LocalAppTypography.current.titleMedium` | unchanged (Outfit) |
| `titleLarge` | 3 | `…titleLarge` | unchanged |
| `headlineSmall` | 4 | `…headlineSmall` | unchanged |
| `headlineMedium` | 1 | `…headlineMedium` | unchanged |
| `displayMedium` | 1 | `…displayMedium` | unchanged |
| `displaySmall` | 1 | `…displaySmall` | unchanged |

`labelMedium` is wanted at medium weight and `labelLarge` too, but `highlightWeight` is one value. Set it to Medium, since the app has no bold body text outside the two inline `fontWeight = Bold` sites (`TimerGauge`, the Check-In button), which keep their explicit weight.

### 5.3 Text with no explicit style (27 sites)

These are governed by Spark's default style (`body2`, 14sp) instead of M3's (`bodyLarge`, 16sp):
- `LanguagePicker:94,118`
- `HabitsScreen:216,224,244`
- `HabitEditor:78,92,153,212,228,229`
- `HabitPickerScreen:75`
- `AppNavScaffold:122`
- `BottomNavigationBar:18`
- `LicensesScreen:163`
- `WelcomeScreen:102,167,204`
- `ReminderTimePicker:58,80,84`
- `PresenceCheckScreen:368,388`
- `GateMessageScreen:40,43`
- `ReportsScreen:199,551`

Most sit in button, menu or dialog slots, which supply their own style. Check each one on screen in PR 2.

### 5.4 Colour roles (73 sites)

| M3 role | Sites | `SparkTheme.colors.` |
|---|---|---|
| `onSurfaceVariant` | 31 (+1 via `with`) | `onBackgroundVariant` (ink-muted, design 2.2) |
| `primary` | 19 | `main` |
| `outline` | 6 | `outline` |
| `onSurface` | 4 | `onSurface` |
| `background` | 3 | `background` |
| `surfaceVariant` | 2 | `backgroundVariant` |
| `error` | 1 (+1 via `with`) | `error` |
| `onBackground` | 1 | `onBackground` |
| `primaryContainer`/`onPrimaryContainer` | 1/1 (`CalendarGrid`) | gone: replaced by the selection ring (PR 2) |
| `tertiary`/`secondary` | 1/1 (`descriptiveHues`) | gone: fixed app tokens (PR 2) |

Watch `primary` → `main`, which turns indigo into ink. That is correct per design 2.2, but it is where "everything went dark" will be noticed: `BrandGrid`'s default colour, the calendar's today marker, the welcome page dots, the chart line and the licence links.

### 5.5 Opt-ins after the migration

- `@OptIn(ExperimentalSparkApi::class)`: `AppNavScaffold` (`TopAppBar`), `BottomNavigationBar` (`NavigationBar`), `HabitEditor`, `LanguagePicker`, `ReminderTimePicker` (`AlertDialog`).
- `@OptIn(ExperimentalMaterial3Api::class)`: `ReminderTimePicker`, and `AppNavScaffold`'s top bar (Spark's `TopAppBar` takes M3's experimental colour and scroll types).
- `ExperimentalMaterial3WindowSizeClassApi`: `HistoryScreen`, unchanged.
- `@InternalSparkApi` (an ERROR-level opt-in): never used.

### 5.6 Icons: Material → Spark (PR 3)

**Mirror** marks the icons that are `Icons.AutoMirrored` today, which take `.mirroredInRtl()`. Hold each mirrored icon in a top-level `val`, as `MirroredIcon.kt`'s KDoc says. "Gap" means Spark has no equivalent, so the Material vector stays, drawn through Spark's `Icon` (confirmed), and `material-icons-extended` stays a dependency for them. Spark's Outline cut is used throughout, for one weight.

| Material today | Where | Spark | Mirror |
|---|---|---|---|
| `AutoMirrored.Filled.ArrowBack` | `AppNavScaffold` | `ArrowLeft` | yes |
| `AutoMirrored.Filled.KeyboardArrowLeft`/`Right` | `HistoryScreen`, `ReportsScreen` month steppers | `ChevronVerticalLeft`/`Right` | yes |
| `AutoMirrored.Filled.KeyboardArrowRight` | `AboutCard`, `SettingsScreen` rows | `ChevronVerticalRight` | yes |
| `AutoMirrored.Filled.Login`/`Logout` | `CheckInScreen` button | `Import`/`Export`, or `Disconnect` (open item 2) | yes |
| `AutoMirrored.Filled.DirectionsRun` | `HabitExample` | `ManWalkingOutline` | yes |
| `AutoMirrored.Filled.MenuBook` | `HabitExample` | `Book` | yes |
| `Default.ExpandLess`/`ExpandMore` | `CheckInScreen` pill | `ChevronHorizontalUp`/`Down` | no |
| `OpenInNew` | `AboutCard` | `ShareExternal` (check the glyph reads as "opens elsewhere") | no |
| `Default.Schedule`, `Outlined.Schedule` | `Screen` (Check In tab), `HabitEditor` | `ClockOutline` | no |
| `Default.CalendarMonth` | `Screen` (History tab) | `CalendarTextOutline` | no |
| `Default.Event` | `HistoryScreen` | `CalendarDotOutline` | no |
| `Default.Assessment`, `Default.Insights` | `Screen` (Reports tab), `ReportsScreen`, `EmptyState` preview | `GraphOutline` | no |
| `Default.Settings` | `Screen` (Settings tab) | `GearOutline` | no |
| `Default.Lock` | `HabitSwitcher`, `SettingsScreen`, `PresenceCheckScreen` | `LockOutline` | no |
| `Default.Add` | `HabitsScreen` | `Plus` | no |
| `Default.Check` | `HabitsScreen` | `Check` | no |
| `Default.MoreVert` | `HabitsScreen` | `ThreeDotsVertical` | no |
| `Outlined.Edit` | `HabitsScreen` | `PenOutline` | no |
| `Default.Archive` | `HabitsScreen` | `BoxOutline` | no |
| `Default.Close` | `PresenceCheckScreen` | `Cross` | no |
| `Default.FileDownload` | `ReportsScreen` export | `DownloadFileOutline` | no |
| `Rounded.PhotoCamera` | `PresenceGate` | `CameraOutline` | no |
| `Default.Block` | `PrivacyScreen` | `BannedOutline` | no |
| `Filled.MusicNote`, `Filled.FitnessCenter`, `Filled.SelfImprovement`, `Filled.Brush` | `HabitExample` | `Music`, `DumbbellCriteria`, `LotusFlowerCriteria`, `PaintPalette` | no |
| `Default.SwapHoriz` | `HabitSwitcher` | **gap**: Material kept | — |
| `Default.Fingerprint` | `PresenceCheckScreen` device unlock | **gap**: Material kept | — |
| `Rounded.Face` | `CameraDisclosureScreen`, `WelcomeScreen` | **gap**: Material kept. Spark's faces (`HappyFace`, `NeutralFace`, `SadFace`) carry an expression, which grades: never use them. | — |
| `Rounded.Widgets` | `WelcomeScreen` widget page | **gap**: Material kept | — |
| `Default.CloudOff`, `NoAccounts`, `NoPhotography` | `PrivacyScreen` | **gap**: Material kept (Spark has no struck-through glyphs) | — |

Spark's `*Criteria` icons are drawn for Leboncoin's listing filters. Check that the ones above sit at the same weight as the rest before taking them, and treat any that don't as gaps, keeping the Material icon.

---

## 6. Guards this plan adds, and the invariants behind them

| Invariant | Guard | PR |
|---|---|---|
| No Nunito in the APK | `tools:discard` + assets transform; `verifyLicenseCoverage` scans merged assets for fonts | 1 |
| No `SparkTheme` falls back to its default family (else crash, since Nunito is discarded) | `SparkColorScope` is the only nested theme; `SparkThemeCallSitesTest` fails on a `SparkTheme(` call outside `Theme.kt` | 2 |
| No manifest entry runs unasked | `verifyMergedManifest` (exists) and the four removals | 1 |
| No Compose Resources read (its context provider is removed) | detekt bans `org.jetbrains.compose.resources.*` | 8 |
| Every shipped group id has a licence entry, MIT text bundled | `verifyLicenseCoverage` (exists), `OpenSourceLibrariesTest` | 1 |
| No gauge, meter, progress, rating, badge, stepper or slider | detekt `ForbiddenImport` | 8 |
| No raw M3 where Spark has a replacement | Spark's own lint, back at ERROR | 8 |
| Arrows mirror under RTL | `mirroredInRtl()` on every directional icon (section 5.6 marks them), with `MirroredIconTest` pinning the flip. Nothing can tell a directional icon from a symmetric one, so the Arabic device pass in section 8 is the check. | 1, 3 |
| No networking class | the dex grep, by hand, on every Spark bump (CLAUDE.md lists it) | 1 |

---

## 7. Risks

| Risk | Likelihood | Effect | Mitigation |
|---|---|---|---|
| A Spark component reads Compose Resources at runtime | Low (none does at 4.2.1 on Android) | `MissingResourceException` crash | The detekt ban covers our code; for Spark's, re-read `Res.` usages under `spark/src/main` on every bump. The fallback is to keep the provider and allowlist it. |
| A Spark bump changes `sparkFontFamily`'s fallback or adds a font | Medium | Crash (discarded resource) or a silently bigger APK | The font gate (PR 1) fails the build on a new font. CLAUDE.md's bump checklist. |
| Spark's motion ignores "Remove animations" | High (no reduced-motion code found) | Button colour, ripple and snackbar motion with animations off | Unverified whether Compose's animation clock scales `animate*AsState` by the system setting. Check on device in PR 4 before relying on it. If Spark's infinite animations (`pulse`, placeholder shimmer) are ever used, gate them on `animationsEnabled`. Neither is in this plan. |
| Spark's tooltip on every icon button | Medium | A long press shows text over the calendar | Check in PR 4. A `contentDescription` is required anyway, so there is no fix beyond accepting it. |
| The 44dp icon-button floor | Certain | Smaller targets than the app's 48dp rule | Raise to 48 with `sizeIn` (PR 4). |
| The pill-shaped 64dp Check-In button | Certain (D8) | A visible change of shape | A design call, flagged in section 9. |
| The M3 bridge's oversized `titleMedium` reaching an unmigrated M3 component | Certain for the time picker, possible elsewhere | 32sp text | Every non-Spark M3 component sits inside a local `MaterialTheme(typography = typographyFor(locale))`. After PR 4 the time picker is the only one. |
| Spark `@ExperimentalSparkApi` components change between minors | Medium | Compile breaks on bump | Pin the Spark BOM. Bump deliberately, alone, in its own PR. |
| Bisecting a visual regression across PR 2 | Medium | PR 2 changes everything at once | Before and after screenshots of every tab in PR 2's description. PR 3 must be pixel-identical to PR 2. |

---

## 8. Device verification matrix (every PR from 2 on)

| Axis | Values |
|---|---|
| Device | Pixel (stock), Nothing Phone (the MediaTek face check), `CheckIn_API31` emulator (Android 12, 560dp-tall profile) |
| Theme | light, dark |
| Language | English, Arabic (RTL, Plex) |
| Font scale | 1.0, 1.5, 2.0 |
| Animations | on, off (developer options → all three scales off) |
| Paths | fresh install tour → naming → first check-in through the gate → check-out → celebration; History tap on a recorded, an empty and a future day; Reports month and all-time, export; Habits add, edit, archive, switch; Settings language switch; widget tap |

PR 1 needs only an install-over plus Licences. PR 3 needs only a screenshot diff against PR 2.

---

## 9. Decisions: status

**Confirmed on 2026-10-29:** D1 (Manrope as Spark's family, Outfit in `AppTypography`), D3 (Spark's lint stays an error), D4 (all four manifest removals), D5 (Spark's icons with `mirroredInRtl`), D6 (rebranded `Button.*`), the icon gaps (Material kept), D8 (pill-shaped Check-In button), D11 (segmented control).

**Still open, all small and settled on device during PR 3 and PR 6:**

1. **Check in and check out icons**: `Import`/`Export` (an arrow into or out of a tray) or `Disconnect` for check out. Neither is a door, which Material's `Login`/`Logout` are.
2. **`FilledTonalButton` → `Button.Secondary`**: today's tonal button is soft and `Secondary` is solid brass. `Button.Tertiary` (outlined) is the quieter fallback for `HabitEditor`'s time button.
