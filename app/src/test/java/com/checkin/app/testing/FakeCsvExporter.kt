package com.checkin.app.testing

import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.ExportResult

class FakeCsvExporter(var result: ExportResult = ExportResult.Success) : CsvExporter {
    var lastRange: Pair<String, String>? = null
    var lastHabitName: String? = null
    var lastSummaries: Map<String, DailyAggregate> = emptyMap()
    override suspend fun export(
        habitName: String?,
        startKey: String,
        endKey: String,
        summaries: Map<String, DailyAggregate>,
    ): ExportResult {
        lastRange = startKey to endKey
        lastHabitName = habitName
        lastSummaries = summaries
        return result
    }
}
