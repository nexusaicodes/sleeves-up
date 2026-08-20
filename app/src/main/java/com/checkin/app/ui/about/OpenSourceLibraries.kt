package com.checkin.app.ui.about

/**
 * A license one of the bundled libraries is distributed under.
 *
 * [bundled] marks the ones whose full text ships in `res/raw`, so the Licenses screen can reproduce
 * them with no network — everything else links out. Apache-2.0 section 4 asks for the license text
 * itself, not merely its name, and this app has no way to fetch it at runtime.
 */
enum class LibraryLicense(val displayName: String, val url: String, val bundled: Boolean) {
    APACHE_2_0(
        displayName = "Apache License 2.0",
        url = "https://www.apache.org/licenses/LICENSE-2.0",
        bundled = true,
    ),
    BSD(
        displayName = "BSD License",
        url = "https://chromium.googlesource.com/libyuv/libyuv/+/refs/heads/main/README.chromium",
        bundled = false,
    ),

    /**
     * The two bundled typefaces. Bundled text rather than a link for the same reason as Apache-2.0:
     * the OFL requires its notice to travel with the font files, and those ship inside the APK.
     */
    OFL_1_1(
        displayName = "SIL Open Font License 1.1",
        url = "https://scripts.sil.org/OFL",
        bundled = true,
    ),
}

/**
 * One entry on the Licenses screen. [coordinates] is a concrete Maven coordinate where the entry is
 * a single artifact and a `group.*` wildcard where it stands for a whole family.
 */
data class OpenSourceLibrary(
    val name: String,
    val coordinates: String,
    val copyright: String,
    val licenses: List<LibraryLicense>,
    val note: String? = null,
)

/**
 * Everything redistributed inside the app, grouped by project rather than by artifact.
 *
 * The release runtime classpath resolves to 52 group ids, which collapse into the projects below.
 * Grouping is only sound because the license is uniform within each group — where it isn't, the
 * group carries both, as CameraX does: its camera-core POM declares BSD alongside Apache-2.0 for the
 * libyuv it embeds.
 *
 * **This list is the resolved classpath, not the dependency block.** Guava's ListenableFuture,
 * AutoValue's annotations, JSpecify and javax.inject are all redistributed in the APK without
 * appearing in `app/build.gradle.kts`, so a list built from that file alone would omit them.
 *
 * Every entry is now Apache-2.0, the OFL, or the BSD that rides along with CameraX — no proprietary
 * terms remain. That is a consequence of presence detection being done by the camera hardware rather
 * than by a library: the ML Kit stack took Play services, ODML, datatransport and the Firebase
 * encoders off the classpath with it, and those carried the only non-open-source terms the app ever
 * redistributed.
 *
 * Keeping this by hand means it can drift when a dependency is added. `OpenSourceLibrariesTest`
 * guards the shape of the list, but it cannot see the Gradle graph — regenerate the group list with
 * `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and re-check this file when
 * `app/build.gradle.kts` changes.
 */
val OPEN_SOURCE_LIBRARIES: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary(
        name = "AndroidX (Jetpack)",
        coordinates = "androidx.*",
        copyright = "Copyright © The Android Open Source Project",
        licenses = listOf(LibraryLicense.APACHE_2_0),
        note = "Core, Compose, Material 3, Lifecycle, Navigation, Room, WorkManager, Biometric " +
            "and their dependencies.",
    ),
    OpenSourceLibrary(
        name = "CameraX",
        coordinates = "androidx.camera:*",
        copyright = "Copyright © The Android Open Source Project",
        licenses = listOf(LibraryLicense.APACHE_2_0, LibraryLicense.BSD),
        note = "camera-core embeds libyuv, which carries its own BSD license.",
    ),
    OpenSourceLibrary(
        name = "Kotlin Standard Library",
        coordinates = "org.jetbrains.kotlin:*",
        copyright = "Copyright © JetBrains s.r.o. and Kotlin Programming Language contributors",
        licenses = listOf(LibraryLicense.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "kotlinx.coroutines",
        coordinates = "org.jetbrains.kotlinx:*",
        copyright = "Copyright © JetBrains s.r.o.",
        licenses = listOf(LibraryLicense.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "Guava ListenableFuture",
        coordinates = "com.google.guava:listenablefuture:1.0",
        copyright = "Copyright © The Guava Authors",
        licenses = listOf(LibraryLicense.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "AutoValue Annotations",
        coordinates = "com.google.auto.value:auto-value-annotations:1.6.3",
        copyright = "Copyright © Google LLC",
        licenses = listOf(LibraryLicense.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "JetBrains Java Annotations",
        coordinates = "org.jetbrains:annotations",
        copyright = "Copyright © JetBrains s.r.o.",
        licenses = listOf(LibraryLicense.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "JSpecify",
        coordinates = "org.jspecify:jspecify",
        copyright = "Copyright © The JSpecify Authors",
        licenses = listOf(LibraryLicense.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "javax.inject",
        coordinates = "javax.inject:javax.inject:1",
        copyright = "Copyright © The JSR-330 Expert Group",
        licenses = listOf(LibraryLicense.APACHE_2_0),
    ),
    // The two bundled typefaces. Not Maven artifacts, so their `coordinates` names the resource
    // instead — `verifyLicenseCoverage` matches these against res/font/ rather than the classpath.
    // Each copyright is the string embedded in the shipped .ttf's own name table, not a guess.
    OpenSourceLibrary(
        name = "Outfit",
        coordinates = "font:outfit_*",
        copyright = "Copyright © 2021 The Outfit Project Authors",
        licenses = listOf(LibraryLicense.OFL_1_1),
        note = "Display, headline and title sizes.",
    ),
    OpenSourceLibrary(
        name = "Manrope",
        coordinates = "font:manrope_*",
        copyright = "Copyright © 2019 The Manrope Project Authors",
        licenses = listOf(LibraryLicense.OFL_1_1),
        note = "Body and label sizes.",
    ),
)
