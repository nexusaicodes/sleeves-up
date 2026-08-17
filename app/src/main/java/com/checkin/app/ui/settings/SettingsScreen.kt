package com.checkin.app.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.BuildConfig
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeCatalog
import com.checkin.app.notify.log.EngagementEvent
import com.checkin.app.ui.about.AboutCard
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    // Screen-scoped, not item-scoped: the About card that posts these messages is a lazy item, and a
    // scope remembered inside it is cancelled the moment the card scrolls away.
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The app's only notification surface, and first because a blocked state makes silent
        // no-ops of the timer, the session reminder and every nudge.
        item { NotificationsCard() }

        item { AboutCard(onOpenLicenses = onOpenLicenses, showMessage = showMessage) }

        // Debug-only. The diagnostics card reads state, the harness drives it, so state comes first.
        if (BuildConfig.DEBUG) {
            item { DiagnosticsCard(viewModel, showMessage) }
            item { NudgeHarnessCard(viewModel) }
        }
    }
}

@Composable
private fun NudgeHarnessCard(viewModel: SettingsViewModel) {
    SectionCard(title = "Nudge Harness (debug)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.debugRunPass() }) {
                Text("Run pass")
            }
            OutlinedButton(onClick = { viewModel.debugClearLog() }) {
                Text("Clear log")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // One button per variant, not per nudge: this install always buckets to the same wording, so
        // every other variant would otherwise be impossible to see on this device.
        Nudge.entries.forEach { nudge ->
            NudgeCatalog.variants(nudge).forEachIndexed { variant, _ ->
                OutlinedButton(
                    onClick = { viewModel.debugSend(nudge, variant) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Force send: ${nudge.name} v$variant")
                }
            }
        }
    }
}

/**
 * Debug-only diagnostics: the live state first, then what was recorded getting there.
 *
 * It shipped in release once, on the argument that these failures are silent and a user hitting one
 * has nothing else to report. The loop never closed though — rows read `DEGRADED PRESENCE_CHECK`,
 * and `Feedback.draft` carries only the app and device build, so the evidence never travelled.
 * Debug-only, it can be as blunt as it needs to be.
 *
 * The state block is what earns the card: every failure in `service/` looks normal from the UI, so
 * [DebugSnapshot.warnings] names them outright.
 */
@Composable
private fun DiagnosticsCard(viewModel: SettingsViewModel, showMessage: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    var snapshot by remember { mutableStateOf<DebugSnapshot?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Resume and the button are the only refreshes: nothing the snapshot reads is reactive, so
    // checking in or out leaves it stale until one of the two. Hence an explicit Refresh.
    val refresh: () -> Unit = { scope.launch { snapshot = viewModel.readSnapshot(context.channelStates()) } }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    SectionCard(title = "Diagnostics (debug)") {
        HelpText("Live session, service and alarm state, then what was recorded getting there.")

        snapshot?.let { DiagnosticsState(it) }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = refresh) {
                Text("Refresh")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val report = diagnosticsReport(snapshot, viewModel.readLog())
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, report)))
                        showMessage("Diagnostics copied")
                    }
                },
            ) {
                Text("Copy report")
            }
        }

        OutlinedButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide recent activity" else "Show recent activity")
        }
        // The collection lives inside the branch, not above it. `recentEvents` is
        // `WhileSubscribed`, so leaving it collected here would keep a Room query live on every
        // visit to Settings to feed a list that is collapsed by default and rarely opened.
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            DiagnosticsEvents(viewModel)
        }
    }
}

/**
 * Warnings first in the error colour, then the facts behind them. Warnings lead because they are the
 * answer; the lines below show the working, and usually there are no warnings at all.
 */
@Composable
private fun DiagnosticsState(snapshot: DebugSnapshot) {
    snapshot.warnings().forEach { warning ->
        Text(
            text = "! $warning",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    // Monospace so the labelled columns line up; these are read as a block, not as prose.
    Text(
        text = snapshot.lines().joinToString("\n"),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DiagnosticsEvents(viewModel: SettingsViewModel) {
    val events by viewModel.recentEvents.collectAsStateWithLifecycle()

    if (events.isEmpty()) {
        Text(
            text = "Nothing recorded yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    events.forEach { event ->
        Text(
            text = eventLine(event),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One log row. Carries `source` because it separates the subsystems sharing this table, and the two
 * queries that drive behaviour — the nudge daily cap and conversion attribution — both scope on it.
 * A wrong source is a bug in exactly those rules, and invisible unless the column is printed.
 */
private fun eventLine(event: EngagementEvent): String = buildString {
    append(eventTimeFormat.format(Instant.ofEpochMilli(event.at)))
    append("  ")
    append(event.source.take(SOURCE_WIDTH).padEnd(SOURCE_WIDTH))
    append("  ")
    append(event.event)
    append("  ")
    append(event.key)
    if (event.variant != 0) append("  v${event.variant}")
}

/** The clipboard payload: state, warnings, then the log — everything needed to describe a failure. */
private fun diagnosticsReport(snapshot: DebugSnapshot?, events: List<EngagementEvent>): String = buildString {
    appendLine("CheckIn diagnostics ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("${Build.MANUFACTURER} ${Build.MODEL}  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine()
    appendLine(snapshot?.asText() ?: "snapshot unavailable")
    appendLine("--- log (${events.size}) ---")
    events.forEach { appendLine(eventLine(it)) }
}

/**
 * Reads each channel's three switches off the platform — all three channels, unlike
 * [NotificationsCard], which checks only the timer because muting the others is a preference, not a
 * fault. That is about warning a *user*; a muted channel is the ordinary explanation here.
 */
private fun Context.channelStates(): List<ChannelState> {
    val manager = NotificationManagerCompat.from(this)
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val appEnabled = manager.areNotificationsEnabled()
    return listOf(NotificationChannels.TIMER, NotificationChannels.REMINDER, NotificationChannels.ENGAGEMENT)
        .map { id ->
            ChannelState(
                id = id,
                permissionGranted = granted,
                appEnabled = appEnabled,
                importance = manager.getNotificationChannelCompat(id)?.importance,
            )
        }
}

/** Secondary copy inside a card, explaining what the card is for or what its button will do. */
@Composable
internal fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val eventTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US).withZone(ZoneId.systemDefault())

/** Width of the source column, so rows from different subsystems stay aligned. Fits "PRESENCE". */
private const val SOURCE_WIDTH = 8

/** The clipboard entry's label — what a clipboard manager shows in place of the payload. */
private const val CLIP_LABEL = "CheckIn diagnostics"
