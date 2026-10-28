package com.checkin.app.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps every placed home-screen widget showing the current [WidgetFace], driven off the tables so no
 * check-in, check-out, archive or day-boundary close has to remember the widget.
 *
 * [faces] is the one description of the face, used by both paths: the collector [start] runs for as
 * long as a widget is placed, and the one-off [redraw] (the provider's `onUpdate`, a language change).
 * Every draw goes through one lock and skips a face identical to the last one drawn, so:
 * - a redraw cannot land a face read before a change after the collector has drawn that change — its
 *   read and its draw happen under the lock, and the collector's newer face waits behind it;
 * - an unchanged face costs no `updateAppWidget`, which would restart the names' cycle.
 *
 * Never throws except to cancel: its callers launch on the application scope, which has no exception
 * handler. A failed read is retried by the collector; a failed draw leaves the face already shown.
 */
class WidgetSync(
    private val widgets: HomeWidgets,
    private val faces: () -> Flow<WidgetFace>,
    private val scope: CoroutineScope,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {
    private val lock = Mutex()
    private var drawn: WidgetFace? = null
    private var collector: Job? = null

    /**
     * Starts following the tables, unless already following them. With no widget placed the collector
     * ends at once — most installs never place one, and two Room observers for the life of every
     * process would be paid for nothing. The provider's `onUpdate`, which fires on placement, starts
     * it again.
     */
    fun start() {
        synchronized(this) {
            if (collector?.isActive == true) return
            collector = scope.launch {
                if (!placed()) return@launch
                faces()
                    // A transient read failure must not end the sync for the life of the process: in
                    // one a broadcast created, no activity start would ever redraw the widget.
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) return@retryWhen false
                        delay(retryDelayMs)
                        true
                    }
                    .collect { face -> drawGuarded(face, force = false) }
            }
        }
    }

    /** Stops following the tables: the last widget was removed. */
    fun stop() {
        synchronized(this) {
            collector?.cancel()
            collector = null
        }
    }

    /**
     * Draws once from a fresh read, even a face identical to the last drawn: its callers are the
     * system asking for a widget's views (placement, a launcher restart) and a language change, where
     * the face is the same and the words are not. Does nothing while no widget is placed.
     */
    suspend fun redraw() {
        if (!placed()) return
        drawGuarded(face = null, force = true)
    }

    /**
     * Draws [face], or a freshly read one when null, under [lock]. The read sits inside the lock so a
     * face read before a change cannot land after the collector has drawn that change; a collector face
     * that waited behind a redraw is followed by Room's emission for any write since. A throwing read
     * or draw leaves [drawn] unchanged, so the next attempt is not skipped as a duplicate.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun drawGuarded(face: WidgetFace?, force: Boolean) {
        try {
            lock.withLock {
                val next = face ?: faces().first()
                if ((force || next != drawn) && widgets.show(next)) drawn = next
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The face already shown stands; the next table change or start draws it again.
        }
    }

    // Unknown counts as placed: following the tables for nothing is cheaper than a widget left stale.
    private fun placed(): Boolean = runCatching { widgets.placed() }.getOrDefault(true)

    companion object {
        /** How long the collector waits before re-subscribing after a failed read. */
        const val RETRY_DELAY_MS = 30_000L
    }
}
