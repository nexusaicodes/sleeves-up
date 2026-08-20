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
            // An empty note would render as a stray gap rather than as nothing.
            assertFalse(library.name, library.note?.isBlank() == true)
        }
    }

    @Test
    fun `coordinates are unique so no project is listed twice`() {
        val coordinates = OPEN_SOURCE_LIBRARIES.map { it.coordinates }
        assertEquals(coordinates.size, coordinates.toSet().size)
    }

    /**
     * Presence detection moved to the camera HAL, which is the platform rather than a dependency, so
     * the whole ML Kit stack left the classpath with it — and with that stack went the only two
     * non-open-source licences the app ever redistributed. A re-add would bring both back.
     */
    @Test
    fun `nothing on the list ships under proprietary terms`() {
        listOf(
            "com.google.mlkit",
            "com.google.android.gms",
            "com.google.android.odml",
            "com.google.android.datatransport",
            "com.google.firebase",
        ).forEach { group ->
            assertFalse(group, OPEN_SOURCE_LIBRARIES.any { it.coordinates.startsWith(group) })
        }
    }

    @Test
    fun `camerax declares the bsd component alongside apache`() {
        val cameraX = OPEN_SOURCE_LIBRARIES.single { it.coordinates.startsWith("androidx.camera") }
        assertTrue(cameraX.licenses.containsAll(listOf(LibraryLicense.APACHE_2_0, LibraryLicense.BSD)))
    }

    /**
     * Both of these ship a copy because their terms require it: Apache-2.0 section 4(a) for the code,
     * and the OFL for the two bundled typefaces. Everything else links out.
     */
    @Test
    fun `exactly the licenses obliging a shipped copy are bundled`() {
        val bundled = LibraryLicense.entries.filter { it.bundled }.toSet()
        assertEquals(setOf(LibraryLicense.APACHE_2_0, LibraryLicense.OFL_1_1), bundled)
    }

    @Test
    fun `the bundled fonts are attributed under the OFL and nothing else claims it`() {
        val underOfl = OPEN_SOURCE_LIBRARIES.filter { LibraryLicense.OFL_1_1 in it.licenses }
        assertEquals(listOf("Outfit", "Manrope"), underOfl.map { it.name })
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
