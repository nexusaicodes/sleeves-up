import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin shared by the Android app and a future iOS app. The Kotlin Gradle plugin comes from
// the root script's buildscript classpath, so no version is stated here.
//
// The Android app consumes the jvm() target. There is deliberately no Android target, so this
// module needs no Android plugin: nothing in it touches an Android API.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        // Matches the app's compileOptions, so the classes it consumes are no newer than it targets.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // Each iOS target builds a static framework named Shared, which the Xcode app in ios/ links through
    // the embedAndSignAppleFrameworkForXcode task its build phase runs.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: ReportScope and MonthSteps take and return its types.
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
