package com.checkin.app

import com.checkin.app.util.TimePatterns
import com.checkin.app.util.displayLocale
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [TimePatterns] built from a resource directory's `strings.xml`, the way the theme builds them on a
 * device: a test pinned to a hand-copied pattern keeps passing while the shipped one is edited.
 * Gradle runs unit tests from the module directory, so the path is relative to `app/`.
 */
object ShippedPatterns {

    /** [preferred] stands for the device's language list, which supplies the region. */
    fun read(dir: String, preferred: List<Locale>): TimePatterns {
        val strings = strings(dir)
        fun get(name: String) = checkNotNull(strings[name]) { "$name missing from $dir/strings.xml" }
        return TimePatterns(
            locale = displayLocale(preferred, get("language_code")),
            hoursMinutes = get("time_hours_minutes"),
            minutesSeconds = get("time_minutes_seconds"),
            clock = get("time_clock_pattern"),
            dateWithYear = get("date_with_year_pattern"),
            dateWithWeekday = get("date_with_weekday_pattern"),
            axisDay = get("date_axis_day_pattern"),
            axisMonth = get("date_axis_month_pattern"),
            axisMonthWithYear = get("date_axis_month_year_pattern"),
            monthYear = get("date_month_year_pattern"),
        )
    }

    /** Every `<string>` in [dir], unescaped as aapt would for the escapes these patterns use. */
    fun strings(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to unescape(node.textContent)
        }
    }

    private fun unescape(raw: String): String {
        val quoted = raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')
        val body = if (quoted) raw.substring(1, raw.length - 1) else raw.trim()
        return body.replace("\\'", "'").replace("\\\"", "\"")
    }
}
