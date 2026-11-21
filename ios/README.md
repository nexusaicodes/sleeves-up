# iOS

There is no iOS app here yet. This folder is where it will go.

What exists today is the shared Kotlin Multiplatform module in [`../shared`](../shared). It builds
for `iosArm64` (devices) and `iosSimulatorArm64` (Apple-silicon simulators) as well as for the JVM
that the Android app uses. It holds six pure-core files with no platform code: `ChartGeometry`,
`PresenceCheckRules`, `FaceDetectSupport`, `ReportScope`, `MonthSteps` and `TimeUnits`.

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
