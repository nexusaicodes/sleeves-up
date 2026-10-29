package com.checkin.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.adevinta.spark.ExperimentalSparkApi
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Ghost
import com.adevinta.spark.components.dialog.AlertDialog
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.components.toggles.RadioButtonLabelled
import com.checkin.app.R
import com.checkin.app.platform.LanguageSettings
import com.checkin.app.platform.choose
import com.checkin.app.ui.components.ActionRow
import com.checkin.app.ui.theme.LocalAppTypography
import com.checkin.app.ui.theme.typographyFor
import com.checkin.app.util.AppLanguage
import java.util.Locale

/**
 * The App language row and its picker: System default, English, العربية — and nothing else.
 *
 * Android's own App language screen lists every locale the device's languages, keyboards and SIM
 * suggest in a supported language, region variants and duplicates included. This offers the three
 * choices that actually differ, but writes the same per-app setting through
 * [com.checkin.app.platform.LanguageSettings], so the system screen and this one always agree.
 * Choosing applies at once: the platform recreates the activity and
 * `CheckInApplication.onConfigurationChanged` rewrites the notification text.
 */
@Composable
internal fun LanguageRow(settings: LanguageSettings) {
    // Null until the first resume reads it: one binder call per visit, not one per composition too.
    var stored by remember(settings) { mutableStateOf<List<Locale>?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    // A choice that leaves the configuration unchanged (English, on an English device) recreates
    // nothing, and the system screen can change the choice while this one is paused.
    LifecycleResumeEffect(settings) {
        stored = settings.stored()
        onPauseOrDispose { }
    }

    val current = stored?.let(AppLanguage::of)
    ActionRow(
        label = stringResource(R.string.language_app),
        // No chevron: a tap opens the picker in place rather than leading to another screen.
        icon = null,
        contentDescription = null,
        value = when {
            stored == null -> null
            current != null -> current.label()
            // A locale stored some other way, in a language the app does not ship, named in itself.
            else -> stored?.firstOrNull()?.let { it.getDisplayName(it).replaceFirstChar { c -> c.titlecase(it) } }
        },
        onClickLabel = stringResource(R.string.language_change),
        onClick = { choosing = true },
    )

    if (choosing) {
        LanguageDialog(
            current = current,
            onChoose = { choice ->
                choosing = false
                settings.choose(choice)
                stored = listOfNotNull(choice.locale)
            },
            onDismiss = { choosing = false },
        )
    }
}

/** [current] is null when the stored locale is none of the choices, so no option is selected. */
@OptIn(ExperimentalSparkApi::class)
@Composable
private fun LanguageDialog(current: AppLanguage?, onChoose: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_app), style = LocalAppTypography.current.headlineSmall) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    // The whole row is the radio's target, at the app's 48dp minimum.
                    RadioButtonLabelled(
                        selected = language == current,
                        onClick = { onChoose(language) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = language.label(), style = language.labelStyle())
                    }
                }
            }
        },
        // Choosing a row is the confirmation, so the dialog's one button only dismisses it.
        confirmButton = {},
        dismissButton = { Button.Ghost(onClick = onDismiss, text = stringResource(R.string.action_cancel)) },
    )
}

@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system_default
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.ARABIC -> R.string.language_arabic
    },
)

/**
 * Each language's name in that language's own face: Compose does no per-glyph fallback, so العربية
 * set in the English type would fall to a system face instead of the bundled Plex Arabic. System
 * default is read in the screen's language, so it keeps the screen's type.
 */
@Composable
private fun AppLanguage.labelStyle(): TextStyle {
    val own = remember(locale) { locale?.let { typographyFor(it).bodyLarge } }
    return own ?: SparkTheme.typography.body1
}
