package com.checkin.app.data.local

import androidx.room.TypeConverter

/**
 * Stores [ClosedBy] as its frozen [ClosedBy.storedValue] rather than as an ordinal.
 *
 * An ordinal would reshuffle the whole table the moment a value is inserted into the enum, silently
 * relabelling every historical row, and the string is the value the CSV prints anyway — one
 * spelling, written once, read by the export and by anything a future migration would match on.
 *
 * An unrecognised string reads back as null (a hand-edited or corrupt row) rather than throwing, on
 * the same principle as everything else here: a session's hours must survive a value nobody can
 * parse.
 */
class ClosedByConverter {
    @TypeConverter
    fun toStored(value: ClosedBy?): String? = value?.storedValue

    @TypeConverter
    fun fromStored(value: String?): ClosedBy? = ClosedBy.fromStored(value)
}
