package com.checkin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins what Android's backup carries, read from the shipped rule files: an exclusion added or dropped
 * there changes what a restored install holds, and nothing else would notice.
 *
 * The record, its habits and the reminder times keyed on them travel together; the prompt flags
 * (a cloud restore carries no permission grants) and the live session's render mirror and alarm
 * instants (true only on the device that armed them) do not. Gradle runs unit tests from the module
 * directory, so the paths are relative to `app/`.
 */
class ShippedBackupRulesTest {

    private val extraction = parse("data_extraction_rules.xml")
    private val legacy = parse("backup_rules.xml")

    private val cloudExcluded = excludes(extraction.single("cloud-backup"))

    @Test
    fun `the record and its write-ahead log travel with a cloud backup`() {
        // A single <include> turns the section into an allowlist and drops everything it omits.
        assertEquals(0, extraction.single("cloud-backup").getElementsByTagName("include").length)
        listOf("_app", "_app-wal", "_app-shm", "nudges.db").forEach {
            assertTrue("$it is excluded", "database:$it" !in cloudExcluded)
        }
    }

    @Test
    fun `reminder times travel with the habits they are keyed on`() {
        assertTrue("database:_app" !in cloudExcluded)
        assertTrue("sharedpref:reminder_prefs.xml" !in cloudExcluded)
    }

    /** Without it a session restored open closes at the restoring device's midnight, not its own. */
    @Test
    fun `the open session's check-in zone travels with the record`() {
        assertTrue("sharedpref:session_zone_prefs.xml" !in cloudExcluded)
    }

    @Test
    fun `prompt flags and the live session mirror stay off a cloud backup`() {
        assertEquals(
            setOf("sharedpref:prompt_prefs.xml", "sharedpref:checkin_timer_prefs.xml"),
            cloudExcluded,
        )
    }

    @Test
    fun `device transfer carries everything`() {
        val transfer = extraction.single("device-transfer")
        assertEquals(emptySet<String>(), excludes(transfer))
        assertEquals(0, transfer.getElementsByTagName("include").length)
    }

    @Test
    fun `the pre-31 rules state the same exclusions`() {
        assertEquals(cloudExcluded, excludes(legacy))
        assertEquals(0, legacy.getElementsByTagName("include").length)
    }

    private fun parse(name: String): Element = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/xml/$name")).documentElement

    private fun Element.single(tag: String): Element {
        val nodes = getElementsByTagName(tag)
        assertEquals("one <$tag>", 1, nodes.length)
        return nodes.item(0) as Element
    }

    /** Every `<exclude>` under [this] as `domain:path`. */
    private fun excludes(parent: Element): Set<String> {
        val nodes = parent.getElementsByTagName("exclude")
        return (0 until nodes.length).mapTo(mutableSetOf()) { i ->
            val node = nodes.item(i) as Element
            "${node.getAttribute("domain")}:${node.getAttribute("path")}"
        }
    }
}
