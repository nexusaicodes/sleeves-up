# iOS

The iOS app's beginnings: a SwiftUI app shell (`SleevesUp/`) that links the shared Kotlin code and
shows a few values it computes. It has no product features yet.

What exists today is the shared Kotlin Multiplatform module in [`../shared`](../shared). It builds
for `iosArm64` (devices) and `iosSimulatorArm64` (Apple-silicon simulators) as well as for the JVM
that the Android app uses. It holds six pure-core files with no platform code: `ChartGeometry`,
`PresenceCheckRules`, `FaceDetectSupport`, `ReportScope`, `MonthSteps` and `TimeUnits`.

## Building and running the app

Needs a Mac with Xcode, an iOS simulator runtime (`xcodebuild -downloadPlatform iOS`) and
[XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`). The Xcode project is
generated from `project.yml` and is not checked in:

```bash
cd ios
xcodegen generate          # writes SleevesUp.xcodeproj
open SleevesUp.xcodeproj   # then Run, or build from the command line:
xcodebuild -project SleevesUp.xcodeproj -scheme SleevesUp \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

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

CI runs on Linux and does not build these targets, so run them before merging a change to
`shared/`.
