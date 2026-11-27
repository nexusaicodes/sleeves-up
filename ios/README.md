# iOS

The iOS app (`SleevesUp/`), a SwiftUI app on the shared Kotlin code in [`../shared`](../shared),
which builds for `iosArm64` (devices) and `iosSimulatorArm64` (Apple-silicon simulators) as well as
for the JVM the Android app uses. CLAUDE.md's Key Source Paths lists the shared files.

What it does today: the welcome tour and the naming step for the seed habit; habits, created, renamed, archived,
restored and switched between, with one open session across all of them locking the switcher; the
shown habit's check-in and check-out, each through the presence gate, the open session counting up,
its sessions today, and the close of a forgotten session at its own midnight; and History, the
shown habit's month calendar and the sessions of a tapped day; and Reports, every figure and chart
for one month or all time, exported as CSV through the share sheet; and the open session's "Still
checked in?" reminders, whose Check Out action opens the gate; and each habit's daily reminder,
planned ahead, timed and switched per habit, and replanned by background refresh while the app is
not opened; and the open session's Live Activity on the Lock Screen and in the Dynamic Island,
whose Check Out opens the gate; and the home-screen widget, which opens the gate or the habit
picker. Every tab re-reads at midnight while the app is open.

- **`Record/`**: the record, a [GRDB](https://github.com/groue/GRDB.swift) SQLite database with the
  Android app's `sessions` and `app_state` tables and `habits` columns (less Android's `channel_id`),
  and the same SQL. It lives in the app group `group.com.nexusai.checkin.app`'s container
  (`RecordLocation`), where the widget extension can read it; a record an earlier build kept in the
  app's own Application Support is moved there, with its journal, before its first open, and a
  record already in the group is never replaced. `SessionStore` makes every write through the shared `SessionRules`, so a session
  is refused, closed and measured as on Android; `HabitStore` makes every habit write through the
  shared `HabitRules` and `HabitWrites`; `SessionZones` keeps the open session's check-in zone.
  `HistoryStore` reads a month through `DailyAggregates` (Android's daily aggregate query, as the
  shared `DailyAggregate`) and draws each day in the shared `CalendarMonth`'s states. `ReportsStore`
  resolves the scope to one window (`ReportScope.resolve`) and takes every figure from the shared
  `ReportFigures`, reading the window's days and session starts over the identical range. Its
  export writes that window through the shared `csvLines` under `exportFileName`, byte for byte the
  file Android writes, and writes nothing for a window with no completed day.
- **`Gate/`**: the presence gate. `FaceWatcher` runs the front camera with an
  `AVCaptureMetadataOutput` for faces only, so the app receives face bounds and never a frame.
  `PresenceGateView` applies the shared `PresenceCheckRules`, falls back to `DeviceUnlock`
  (`LocalAuthentication`), and with no passcode set says where to set one (iOS rule 6).
- **`Notify/`**: notifications. `SessionReminders` schedules the open session's reminders two-hourly
  from check-in up to its own midnight (the shared `SessionSchedule.remindersAhead`), only the first
  with a sound, in fixed slots that every read of the record re-syncs, so any close clears them.
  `NotificationTaps` and the `AppDelegate` turn a tap on one, or its Check Out action, into the gate
  on Check-In, recorded as `ClosedBy.REMINDER_NOTIFICATION`. Notifications are asked for once, after
  a finished welcome tour and the naming step; a skipped tour asks at the first check-in instead. `HabitReminders` plans each active habit's reminders with
  the shared `NudgePlan` (iOS rule 3) and replaces the plan whenever what it reads changes, on
  open, on return and at midnight, so a check-in removes that day's reminder. `NudgeLedger`
  (`nudges.sqlite`, apart from the record, as Android's `nudges.db`) keeps the plan and what was
  shown, for the wording rotation. `NudgeCatalog` is Android's English copy under the same frozen
  keys, and `ReminderSettings` each habit's minute and on/off by uuid. A reminder's tap shows its
  habit and opens the gate, as the shared `TapRouting.forHabit` decides. `BackgroundRefresh` asks
  the system to run app code every six hours or so, closing a forgotten session and replanning, so
  a user who stays away keeps the two-week plan (the identifier is in `SleevesUpInfo.plist`, merged
  into the generated Info.plist). iOS Settings' link to the app's notification settings opens the
  Habits screen.
- **`Common/`**: compiled into the app and the widget extension: `RecordLocation`,
  `SessionActivityAttributes` and `WidgetFace`.
- **`Live/`**: the open session's Live Activity (iOS rule 4) and the widget's redraw.
  `SessionActivity` starts it at check-in, renames it with the habit and ends it at any close,
  re-synced on every read of the record as the reminders are; it starts once per session per
  install, so a dismissed one, or one ended by iOS's eight-hour limit, stays gone, while an update,
  which ends the old install's activities, starts it again. `SessionActivityAttributes` (compiled
  into the app and the extension) carries the habit's name and the session's span, never a figure.
  **`SleevesUpWidgets/`**, the widget extension, draws it: the habit, a clock the system counts up
  from check-in and stops at the session's midnight, and Check Out, a `sleevesup://session/check-out`
  link that opens the gate, recorded as `ClosedBy.TIMER_NOTIFICATION` (the Live Activity is iOS's
  timer notification). The scheme is registered in `SleevesUpInfo.plist`; whoever opens a
  `sleevesup://` link gets only the gate or the picker.
  The extension also holds **the home-screen widget** (iOS rule 5), which states `WidgetFace` and
  never a figure: "Check in to <name>" for one active habit, "Check In" for several, "Check out of
  <name>" while a session is open, with the mark at full colour. It reads the record read-only from
  the app group's container and holds one entry; the app asks it to redraw (`WidgetRedraw`)
  whenever the open session or the active habits' names change, and after background refresh.
  Its tap, `sleevesup://widget/tap`, is routed in the app by the shared `TapRouting.forWidget`
  from the rows at the tap: the gate for the open session's check-out (recorded as
  `ClosedBy.WIDGET`), the gate for the one habit, or `HabitPickerView` ("Check in to…", names
  only), whose pick is routed as that habit's reminder would be. The widget's kind,
  `CheckInWidget`, is frozen once one is placed.
- **`CheckIn/`**: the Check-In screen, and `SessionIntervalRow`, a session as Check-In and History
  both draw it.
- **`History/`**: the History tab: a month stepper that stops at the current month, the calendar
  (shaded for a completed session whatever it held, faded only outside the record, on the device's
  week start), and the tapped day's sessions.
- **`Reports/`**: the Reports tab: the Month / All time choice over a month stepper, an empty state
  for a scope holding no counted day, and six cards (The Numbers, Days Split, Daily Hours, Hours by
  Month under all time, Start Times, Sessions Per Day). `ReportCharts` draws the line, bar and ring
  charts from the shared `ChartGeometry`, as Android's are drawn. The last card exports the
  screen's window and opens the share sheet.
- **`Welcome/`**: first run, ordered by the shared `FirstRun.step`: the four-page welcome tour
  (Android's copy; the widget page says how to place the widget, since iOS gives an app no way to
  add one), then the naming step, then the notification ask over the app. `SeenPrompts` keeps
  Android's `welcome_seen`, `notifications_asked` and `widget_page_seen`. An install that finished
  the tour before it held the widget page meets that page once, alone, when `FirstRun.widgetPageOwed`
  says so (two days holding a session of the shown habit, no widget placed), decided as the app comes
  to the front; a start that finds the widget placed ends the question.
- **`Habits/`**: the Habits screen (tap a row to show it; swipe or long-press to edit or archive),
  the name editor with each habit's reminder (`ReminderFields`: on or off, iOS only, and the time,
  05:00 to 21:00), the first-run naming step, which sets the time too, and `habitSwitcher`, the top
  bar every tab carries. Rows state each habit's reminder time and nothing else.

Copy is English only for now, taken from the Android app's strings.

## Building and running the app

Needs a Mac with Xcode, an iOS simulator runtime (`xcodebuild -downloadPlatform iOS`) and
[XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`). The Xcode project is
generated from `project.yml` and is not checked in:

```bash
cd ios
xcodegen generate          # writes SleevesUp.xcodeproj
open SleevesUp.xcodeproj   # then Run, or build and test from the command line:
xcodebuild -project SleevesUp.xcodeproj -scheme SleevesUp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

`SleevesUpTests` runs `SessionStore`, `HabitStore` and `HistoryStore` against an in-memory record:
the record's move into the app group's container, one open session, a check-out that closes once, the midnight close in the check-in's zone, the
selection locked to a running habit, archive refusals and where an archive moves the selection, a
restore held to the name rule, a day shading at check-out and per habit, the month stepper and
week start, Reports' window ending at yesterday until today is checked out of, per habit, and its
Month / All time steps, the exported file's name and bytes, the session reminders' instants,
sound and clearing, the Live Activity's start, rename, end and once-per-session-per-install rule, the widget's faces, the first-run prompts, and the habit reminders' plan, replan after a check-in and settled wording,
against a fake notification center, and each habit's reminder settings. `NudgeCatalogTests` reads Android's `NudgeCatalog.kt` and
`strings.xml` from the repository and fails if the iOS copy drifts from them. `SleevesUpUITests` drives the real UI, finishing the tour and naming the seed
habit when they are owed. Five tests run every time: the welcome tour paging through to the app, the gate offering device unlock with **Not now** writing nothing,
creating a habit, switching to it and archiving it, History stepping back a month and showing a
tapped day, and Reports switching between Month and All time and opening the share sheet over an
export. A full check-in and
check-out through simulated Face ID runs only when the host answers each Face ID prompt, so it is
opt-in:

```bash
xcrun simctl spawn booted notifyutil -s com.apple.BiometricKit.enrollmentChanged 1
xcrun simctl spawn booted notifyutil -p com.apple.BiometricKit.enrollmentChanged   # enrol Face ID
TEST_RUNNER_SIM_FACE_ID_HOST=1 xcodebuild -project SleevesUp.xcodeproj -scheme SleevesUp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test \
  -only-testing:SleevesUpUITests/GateUITests/testCheckInAndOutThroughDeviceUnlock &
# while it runs, answer each prompt with a match:
while sleep 2; do xcrun simctl spawn booted notifyutil -p com.apple.BiometricKit_Sim.pearl.match; done
```

The UI tests run against the app's own record on the simulator, so they skip themselves if an
earlier run left a session open. If the simulator refuses to launch
the test host ("Application failed preflight checks"), boot a different simulator and use that.

**On the simulator** there is no front camera, so the gate asks for no camera permission and
offers device unlock at once. The
simulator reports device unlock as available whether or not Face ID is enrolled (checked with
`LAContext.canEvaluatePolicy` on Xcode 26.3), so the no-passcode instructions can't be reached
there. The face path and the no-passcode path both need a real device.

**On a device**, sign with your own team on the command line, so nothing about signing is
committed. With a free (personal) team the install stops launching after 7 days, and the first
launch needs the developer trusted under Settings, General, VPN & Device Management. Developer Mode
must be on. `xcodebuild` cannot register an app group for a team that has none, so once per team
open the project in Xcode, choose the team under Signing & Capabilities for both the SleevesUp and
SleevesUpWidgets targets, and let the App Groups capability register `group.com.nexusai.checkin.app`
(tick it if unticked); after that the command line signs with it. The widget extension is a second
App ID, which a free team's limit of ten new App IDs a week counts.

```bash
cd ios
xcodebuild -project SleevesUp.xcodeproj -scheme SleevesUp -destination 'id=<device id>' \
  -allowProvisioningUpdates DEVELOPMENT_TEAM=<team id> CODE_SIGN_STYLE=Automatic build
xcrun devicectl device install app --device <device> <derived data>/Build/Products/Debug-iphoneos/SleevesUp.app
```

`xcodebuild -showdestinations` lists the device id. The face path was checked this way on an
iPhone 15 (iOS 27.2.1, Xcode 26.3): the camera prompt, a face passing with nothing to press, the
10-second device-unlock offer with the camera covered, and **Not now** writing nothing. The
no-passcode instructions have not been seen, since showing them means removing the device's passcode.

GRDB comes in through Swift Package Manager, pinned to an exact version in `project.yml`. It is MIT
licensed; the iOS app has no licences screen yet, and needs one listing it before release.

Each build first runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` (a build phase in
`project.yml`), so the Swift code always links the current Kotlin. That phase needs Android
Studio's bundled JetBrains Runtime, the same JDK the Gradle commands use.

Swift sees the shared code through the `Shared` framework. A Kotlin `object` is reached through
`.shared` (`PresenceCheckRules.shared`), a top-level function through its file's `…Kt` class
(`ReportScopeKt.resolve`), and kotlinx-datetime types carry a prefix (`Kotlinx_datetimeLocalDate`).

The bundle ID is `com.nexusai.checkin.app`, matching Android's `applicationId`, and becomes permanent
at the first upload to Apple. The minimum iOS version is 17.0 (Live Activities need 16.1, interactive
widgets 17) and can change in any release.

## Building the shared code for iOS

Needs a Mac with Xcode. From the repo root:

```bash
export JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:linkDebugTestIosSimulatorArm64 -Dorg.gradle.java.installations.paths="$JBR"
./gradlew :shared:iosSimulatorArm64Test          -Dorg.gradle.java.installations.paths="$JBR"
```

The first command compiles the shared code and its tests for the simulator and links the test
binary. The second also runs it, which needs an iOS simulator runtime: install one with
`xcodebuild -downloadPlatform iOS`. The first Kotlin/Native build downloads its toolchain into
`~/.konan`.

The product decisions where iOS can't do what the Android app relies on are in
[`../docs/ios-decisions.md`](../docs/ios-decisions.md), and stated as rules in CLAUDE.md's "iOS
platform rules".

CI runs on Linux and builds neither these targets nor the iOS app, so run the shared iOS tests
before merging a change to `shared/`, and the app's build and tests before merging a change to `ios/`.
