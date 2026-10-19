package com.checkin.app.ui.settings

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.checkin.app.R
import com.checkin.app.ui.components.ActionRow
import com.checkin.app.util.AppLanguage
import java.util.Locale

/**
 * The App language row and its picker: System default, English, العربية — and nothing else.
 *
 * Android's own App language screen lists every locale the device's languages, keyboards and SIM
 * suggest in a supported language, region variants and duplicates included. This offers the three
 * choices that actually differ, but writes the same per-app setting through [LocaleManager], so the
 * system screen and this one always agree. Choosing applies at once: the platform recreates the
 * activity and `CheckInApplication.onConfigurationChanged` rewrites the notification text.
 */
@Composable
internal fun LanguageRow() {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService<LocaleManager>() }
    var current by remember(manager) { mutableStateOf(manager.appLanguage()) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    // A choice that leaves the configuration unchanged (English, on an English device) recreates
    // nothing, and the system screen can change the choice while this one is paused.
    LifecycleResumeEffect(manager) {
        current = manager.appLanguage()
        onPauseOrDispose { }
    }

    ActionRow(
        label = stringResource(R.string.language_app),
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        value = current.label(),
        enabled = manager != null,
        onClick = { choosing = true },
    )

    if (choosing && manager != null) {
        LanguageDialog(
            current = current,
            onChoose = { choice ->
                choosing = false
                if (choice != current) {
                    current = choice
                    manager.applyAppLanguage(choice)
                }
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun LanguageDialog(current: AppLanguage, onChoose: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_app)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = language == current,
                                role = Role.RadioButton,
                                onClick = { onChoose(language) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = language.label(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.language_cancel)) } },
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

private fun LocaleManager?.appLanguage(): AppLanguage = AppLanguage.of(this?.applicationLocales.orEmpty())

// The system locales, not the context's: once a choice is stored, the context reads that choice.
private fun LocaleManager.applyAppLanguage(choice: AppLanguage) {
    val locale = choice.localeFor(systemLocales.orEmpty())
    applicationLocales = if (locale == null) LocaleList.getEmptyLocaleList() else LocaleList(locale)
}

private fun LocaleList?.orEmpty(): List<Locale> = if (this == null) emptyList() else (0 until size()).map { get(it) }
