package com.checkin.app

import com.checkin.app.ui.about.LibraryLicense
import com.checkin.app.ui.about.OPEN_SOURCE_LIBRARIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape of a hand-maintained list. These tests cannot see the Gradle graph, so they
 * cannot prove the list is complete — they only stop it degrading into blanks and duplicates.
 */
class OpenSourceLibrariesTest {

    @Test
    fun `every entry is fully populated`() {
        assertTrue(OPEN_SOURCE_LIBRARIES.isNotEmpty())
        OPEN_SOURCE_LIBRARIES.forEach { library ->
            assertTrue(library.name, library.name.isNotBlank())
            assertTrue(library.name, library.coordinates.isNotBlank())
            assertTrue(library.name, library.copyright.isNotBlank())
            assertTrue(library.name, library.licenses.isNotEmpty())
        }
    }

    @Test
    fun `coordinates are unique so no project is listed twice`() {
        val coordinates = OPEN_SOURCE_LIBRARIES.map { it.coordinates }
        assertEquals(coordinates.size, coordinates.toSet().size)
    }

    /**
     * Presence detection runs on the camera HAL, which is the platform rather than a dependency, so
     * no ML stack is on the classpath. Re-adding one would bring its proprietary terms and, with
     * datatransport, a network path.
     */
    @Test
    fun `no ml or telemetry stack is on the list`() {
        listOf(
            "com.google.mlkit",
            "com.google.android.odml",
            "com.google.android.datatransport",
            "com.google.firebase",
        ).forEach { group ->
            assertFalse(group, OPEN_SOURCE_LIBRARIES.any { it.coordinates.startsWith(group) })
        }
    }

    /**
     * The in-app update stack is the only proprietary code shipped, and it needs exactly two Play
     * services artifacts. `verifyLicenseCoverage` matches by group, so it would let a third gms
     * artifact through under this entry; this pins the entry to the two it names.
     */
    @Test
    fun `play services is limited to what in-app updates needs`() {
        val proprietary = setOf(LibraryLicense.PLAY_CORE_TOS, LibraryLicense.ANDROID_SDK_LICENSE)
        assertEquals(
            setOf("com.google.android.play:*", "com.google.android.gms:play-services-basement,play-services-tasks"),
            OPEN_SOURCE_LIBRARIES.filter { library -> library.licenses.any { it in proprietary } }
                .map { it.coordinates }.toSet(),
        )
    }

    @Test
    fun `camerax declares the bsd component alongside apache`() {
        val cameraX = OPEN_SOURCE_LIBRARIES.single { it.coordinates.startsWith("androidx.camera") }
        assertTrue(cameraX.licenses.containsAll(listOf(LibraryLicense.APACHE_2_0, LibraryLicense.BSD)))
    }

    /**
     * These ship a copy because their terms require it: Apache-2.0 section 4(a) and MIT's notice
     * clause for the code, and the OFL for the bundled typefaces. Everything else links out.
     */
    @Test
    fun `exactly the licenses obliging a shipped copy are bundled`() {
        val bundled = LibraryLicense.entries.filter { it.bundled }.toSet()
        assertEquals(setOf(LibraryLicense.APACHE_2_0, LibraryLicense.OFL_1_1, LibraryLicense.MIT), bundled)
    }

    /** res/raw/mit.txt holds exactly these two projects' notices, so a third MIT entry needs its own. */
    @Test
    fun `mit covers spark and filekit and nothing else`() {
        val underMit = OPEN_SOURCE_LIBRARIES.filter { LibraryLicense.MIT in it.licenses }
        assertEquals(listOf("Spark", "FileKit"), underMit.map { it.name })
    }

    @Test
    fun `the bundled fonts are attributed under the OFL and nothing else claims it`() {
        val underOfl = OPEN_SOURCE_LIBRARIES.filter { LibraryLicense.OFL_1_1 in it.licenses }
        assertEquals(listOf("Outfit", "Manrope", "IBM Plex Sans Arabic"), underOfl.map { it.name })
        // A font is a redistributed asset, not a Maven artifact; the coordinate names the resource
        // so verifyLicenseCoverage can match it against res/font/ rather than the classpath.
        underOfl.forEach { assertTrue(it.name, it.coordinates.startsWith("font:")) }
    }

    @Test
    fun `every license points somewhere a reader can verify it`() {
        LibraryLicense.entries.forEach {
            assertTrue(it.name, it.url.startsWith("https://"))
            assertTrue(it.name, it.displayName.isNotBlank())
        }
    }
}
