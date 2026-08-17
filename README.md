# CheckIn

A personal Android tracker built around **showing up**. It records your working time through
authenticated **check-in / check-out** intervals and shows the record building up over time. There
is no target and no grade: a day counts because you turned up for it, not because its hours cleared
a bar.

## What it does

- **Check in / check out** from the first tab. Every check-in *and* check-out is gated by an
  on-device face check (ML Kit, offline), with **device biometric** as a fallback after repeated
  face-detection failures. Captured frames are transient — verified, then deleted immediately.
- **Net daily time** = the sum of your completed check-in/out intervals for the day (open intervals
  are excluded). Every day counts — 7 days a week, no weekend or holiday exemption.
- **A day counts if it has a session.** A 45-minute day on a bad week counts as showing up exactly
  as much as a nine-hour one, and streaks count consecutive days you turned up. Your hours are shown
  everywhere — on the calendar, in the charts, in the export — as a quantity, never as a verdict.
  Nothing is ever coloured red.
- **Today counts the moment you check out.** Your streak, averages and calendar update there and
  then, not at the next midnight. Until that first check-out the day simply isn't counted yet — it
  never shows up as a day you missed, so the numbers only ever move up as a day goes on.
- **Sessions are immutable** — no editing, deleting, or manual entry, by design.
- **A session reminder** every couple of hours while you're checked in. It only asks — ignoring it
  costs you nothing. A session you forget about closes itself at midnight, so a check-in left running
  overnight can't record a sixteen-hour day.
- **Check-in reminders** — on by default and at most one a day. Turn them off where you turn off any
  other notification: long-press one, or Android's notification settings for the app. Tapping one
  still runs the same face check.
- **Self-contained** — Room-only storage, no backend. Export your log to CSV via the share sheet.

## Tabs

| Tab | What it shows |
| --- | --- |
| **Check In** | Live timer and the check-in/out button, with today's sessions a tap away |
| **History** | Monthly calendar shaded by how long each day ran, plus the month's split and averages |
| **Reports** | Daily-hours and monthly charts, the all-time split, streaks, and CSV export |
| **Settings** | A shortcut into Android's notification settings — where every one of the app's notifications is switched on and off — and About (privacy policy, feedback, open-source licenses) |

## Requirements

- Android Studio (ships with the JetBrains JDK 21 the Gradle daemon needs)
- A device or emulator on **Android 14+** (min SDK 34; compile/target SDK 36)
- Grants for **Camera** (face verification), asked for at the first check-in, and **Notifications** (the live timer and reminders), asked for on first open

## Build & run

The Gradle wrapper is pinned to **Gradle 8.13**. Android Studio finds the required JDK automatically —
just open the project and Run. For **CLI builds**, point Gradle's toolchain detection at the JetBrains
JDK bundled with Android Studio:

```bash
export JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest -Dorg.gradle.java.installations.paths="$JBR"  # JVM unit tests
./gradlew :app:assembleDebug     -Dorg.gradle.java.installations.paths="$JBR"  # build debug APK
./gradlew :app:installDebug      -Dorg.gradle.java.installations.paths="$JBR"  # install on a device
```

Run a single test class:

```bash
./gradlew :app:testDebugUnitTest --tests "com.checkin.app.DeficitCalculatorTest"
```

## Static analysis

**ktlint** (formatting) and **detekt** (code smells) both gate CI. Style comes from `.editorconfig`;
detekt's rules are `config/detekt/detekt.yml` layered over its shipped defaults. There is no baseline
file — the tree is clean, and a new finding is meant to be fixed or suppressed at the site with a
reason.

```bash
./gradlew staticAnalysis   # what CI runs: ktlintCheck + detekt
./gradlew ktlintFormat     # auto-fix formatting
```

Run the same gate before each commit (once per clone):

```bash
git config core.hooksPath githooks
```

The hook is a no-op unless the commit stages Kotlin, and it refuses to commit a signing key or a
populated `keystore.properties`.

## Tech

Kotlin · Jetpack Compose (Material 3, a fixed indigo brand theme in light + dark, branded splash,
`WindowSizeClass`-adaptive) · Room (via KSP, reactive `Flow` queries) · a `specialUse` foreground
service for the live timer and presence reminder · CameraX + ML Kit face detection · BiometricPrompt
fallback. MVVM with a single reactive `UiState` per screen and lightweight manual DI (`AppContainer`).

See [`CLAUDE.md`](CLAUDE.md) for architecture details, conventions, and non-obvious behaviors.
