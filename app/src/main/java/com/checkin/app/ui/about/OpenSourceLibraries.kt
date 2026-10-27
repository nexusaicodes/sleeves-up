package com.checkin.app.ui.about

import androidx.annotation.StringRes
import com.checkin.app.R

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
     * The bundled typefaces. Bundled text rather than a link for the same reason as Apache-2.0:
     * the OFL requires its notice to travel with the font files, and those ship inside the APK.
     */
    OFL_1_1(
        displayName = "SIL Open Font License 1.1",
        url = "https://scripts.sil.org/OFL",
        bundled = true,
    ),

    /** Play In-App Updates and its core-common. Proprietary; linked, since nothing obliges a copy. */
    PLAY_CORE_TOS(
        displayName = "Play Core Software Development Kit Terms of Service",
        url = "https://developer.android.com/guide/playcore/license",
        bundled = false,
    ),

    /** The two Play services artifacts In-App Updates depends on. Proprietary; linked. */
    ANDROID_SDK_LICENSE(
        displayName = "Android Software Development Kit License",
        url = "https://developer.android.com/studio/terms",
        bundled = false,
    ),
}

/**
 * One entry on the Licenses screen. [coordinates] is a concrete Maven coordinate where the entry is
 * a single artifact and a `group.*` wildcard where it stands for a whole family.
 *
 * Names, copyrights and licence names are proper nouns and legal text, so they stay as written in
 * every language; only [note] is prose a reader is meant to understand, and it is a string resource.
 */
data class OpenSourceLibrary(
    val name: String,
    val coordinates: String,
    val copyright: String,
    val licenses: List<LibraryLicense>,
    @StringRes val note: Int? = null,
)

/**
 * Everything redistributed inside the app, grouped by project rather than by artifact.
 *
 * The release runtime classpath resolves to 54 group ids, which collapse into the projects below.
 * Grouping is only sound because the license is uniform within each group — where it isn't, the
 * group carries both, as CameraX does: its camera-core POM declares BSD alongside Apache-2.0 for the
 * libyuv it embeds.
 *
 * **This list is the resolved classpath, not the dependency block.** Guava's ListenableFuture,
 * AutoValue's annotations and JSpecify are all redistributed in the APK without
 * appearing in `app/build.gradle.kts`, so a list built from that file alone would omit them.
 *
 * Every entry is Apache-2.0, the OFL, or the BSD that rides along with CameraX, except the in-app
 * update stack: Play In-App Updates (Play Core terms) and the two Play services artifacts it needs,
 * basement and tasks (Android SDK License). Presence detection runs on the camera hardware, so no
 * ML library is on the classpath, and no other Play services artifact; `OpenSourceLibrariesTest`
 * fails if either returns. The type keeps its name though not every entry is open source.
 *
 * Keeping this by hand means it can drift when a dependency is added, so drift detection is
 * automated while the wording stays human. Two checks cover it: `OpenSourceLibrariesTest` guards the
 * shape of the list but cannot see the Gradle graph, and **`./gradlew :app:verifyLicenseCoverage`**
 * (defined in `app/build.gradle.kts`, hooked to `check`, run in CI) resolves the release runtime
 * classpath and fails naming any group id no entry's `coordinates` covers. It checks `res/font/`
 * the same way, since a typeface is redistributed like a dependency but has no Maven coordinate.
 *
 * So a new dependency does not need anyone to remember: the build says which group is unattributed.
 * To write the entry, list the graph with
 * `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and group by Maven group id.
 */
val OPEN_SOURCE_LIBRARIES: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary(
        name = "AndroidX (Jetpack)",
        coordinates = "androidx.*",
        copyright = "Copyright © The Android Open Source Project",
        licenses = listOf(LibraryLicense.APACHE_2_0),
        note = R.string.licenses_note_androidx,
    ),
    OpenSourceLibrary(
        name = "CameraX",
        coordinates = "androidx.camera:*",
        copyright = "Copyright © The Android Open Source Project",
        licenses = listOf(LibraryLicense.APACHE_2_0, LibraryLicense.BSD),
        note = R.string.licenses_note_camerax,
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
        name = "Play In-App Updates",
        coordinates = "com.google.android.play:*",
        copyright = "Copyright © Google LLC",
        licenses = listOf(LibraryLicense.PLAY_CORE_TOS),
        note = R.string.licenses_note_play,
    ),
    OpenSourceLibrary(
        name = "Google Play services",
        coordinates = "com.google.android.gms:play-services-basement,play-services-tasks",
        copyright = "Copyright © Google LLC",
        licenses = listOf(LibraryLicense.ANDROID_SDK_LICENSE),
        note = R.string.licenses_note_play_services,
    ),
    // The bundled typefaces. Not Maven artifacts, so their `coordinates` names the resource
    // instead — `verifyLicenseCoverage` matches these against res/font/ rather than the classpath.
    // Each copyright is the string embedded in the shipped .ttf's own name table, not a guess.
    OpenSourceLibrary(
        name = "Outfit",
        coordinates = "font:outfit_*",
        copyright = "Copyright © 2021 The Outfit Project Authors",
        licenses = listOf(LibraryLicense.OFL_1_1),
        note = R.string.licenses_note_outfit,
    ),
    OpenSourceLibrary(
        name = "Manrope",
        coordinates = "font:manrope_*",
        copyright = "Copyright © 2019 The Manrope Project Authors",
        licenses = listOf(LibraryLicense.OFL_1_1),
        note = R.string.licenses_note_manrope,
    ),
    OpenSourceLibrary(
        name = "IBM Plex Sans Arabic",
        coordinates = "font:plex_arabic_*",
        copyright = "Copyright © 2019 IBM Corp.",
        licenses = listOf(LibraryLicense.OFL_1_1),
        note = R.string.licenses_note_plex_arabic,
    ),
)
