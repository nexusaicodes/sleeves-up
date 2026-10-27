package com.checkin.app

import com.checkin.app.data.local.ClosedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The stored strings are frozen: they are CSV cells, and the DAO's aggregate queries spell each one
 * out literally. A renamed value would orphan every row already written under the old one, and a
 * value the SQL does not count would drop its sessions from the CSV's ending columns.
 */
class ClosedByTest {

    @Test
    fun `the stored values are frozen`() {
        assertEquals(
            listOf("in_app", "timer_notification", "reminder_notification", "day_boundary"),
            ClosedBy.entries.map { it.storedValue },
        )
    }

    @Test
    fun `every value round-trips through its stored string`() {
        ClosedBy.entries.forEach { assertEquals(it, ClosedBy.fromStored(it.storedValue)) }
    }

    @Test
    fun `an open session and an unknown string read as no ending`() {
        assertNull(ClosedBy.fromStored(null))
        assertNull(ClosedBy.fromStored("widget"))
    }

    /** Both aggregate queries count every ending, so each value appears in the DAO once per query. */
    @Test
    fun `the DAO's aggregates count every ending and nothing else`() {
        val dao = File("src/main/java/com/checkin/app/data/local/CheckInSessionDao.kt").readText()
        val counted = Regex("""closed_by = '([a-z_]+)'""").findAll(dao).map { it.groupValues[1] }.toList()
        val queries = AGGREGATE_QUERIES

        assertEquals(
            ClosedBy.entries.map { it.storedValue }.sorted(),
            counted.distinct().sorted(),
        )
        ClosedBy.entries.forEach { value ->
            assertEquals(value.name, queries, counted.count { it == value.storedValue })
        }
    }

    private companion object {
        /** `getDailyAggregates` and its flow twin. */
        const val AGGREGATE_QUERIES = 2
    }
}
