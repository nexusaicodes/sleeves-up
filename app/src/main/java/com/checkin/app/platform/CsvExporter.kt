package com.checkin.app.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.checkin.app.R
import com.checkin.app.data.local.DailyAggregate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Outcome of a CSV export — a typed result so no user-facing strings live in the ViewModel. */
sealed interface ExportResult {
    data object Success : ExportResult

    /** The requested range holds no completed day. Not a failure — there is simply nothing to write. */
    data object Nothing : ExportResult
    data class Failure(val message: String?) : ExportResult
}

/** Writes the sessions CSV and hands it to the system share sheet. */
interface CsvExporter {
    suspend fun export(startKey: String, endKey: String, summaries: Map<String, DailyAggregate>): ExportResult
}

class DefaultCsvExporter(private val context: Context) : CsvExporter {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Every failure mode here — file I/O, a FileProvider misconfiguration, no app able to receive
    // the share — is reported to the user the same way, as a Failure carrying the message. Catching
    // each separately would produce identical branches.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun export(
        startKey: String,
        endKey: String,
        summaries: Map<String, DailyAggregate>,
    ): ExportResult = try {
        // Blocking file I/O runs off the main thread; the share Intent stays on Main.
        val csvFile = withContext(Dispatchers.IO) { writeCsv(startKey, endKey, summaries) }
        share(csvFile)
        ExportResult.Success
    } catch (e: Exception) {
        ExportResult.Failure(e.message)
    }

    /** Writes one row per day across the range, gap-filling days with no sessions as zeros. */
    private fun writeCsv(startKey: String, endKey: String, summaries: Map<String, DailyAggregate>): File {
        val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(exportDir, "checkin_${startKey}_$endKey.csv")

        FileWriter(file).use { writer ->
            writer.write(csvHeader())

            var current = LocalDate.parse(startKey, dateFormatter)
            val end = LocalDate.parse(endKey, dateFormatter)
            while (!current.isAfter(end)) {
                val key = current.format(dateFormatter)
                writer.write(csvRow(key, summaries[key]))
                current = current.plusDays(1)
            }
        }
        return file
    }

    private fun share(csvFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.export_chooser_title))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}
