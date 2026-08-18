package com.checkin.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The license list, as its own destination because it is longer than the whole of Settings.
 *
 * Licences flagged [LibraryLicense.bundled] have their text shipped rather than linked. Every link
 * here hands a URL to the browser via [ExternalLinks], which needs a browser and a connection — so
 * on an offline device a link alone would leave unreadable the licences the app is obliged to
 * reproduce in full: Apache-2.0 for the code, the OFL for the two typefaces.
 */
@Composable
fun LicensesScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.about_no_browser)
    val noHandler = stringResource(R.string.about_no_handler)

    val bundledLicenses = remember { LibraryLicense.entries.filter { it.bundled } }
    // Both hoisted above the lazy list so each paragraph can stay a real lazy item — Apache-2.0 alone
    // splits into 33 of them, and composing them all on the frame the toggle flips is long enough to see.
    // Expansion is saveable (a rotation mid-licence would lose the reader's place); the text is not,
    // since holding 11 KB per licence in the state bundle costs more than reading the file again.
    var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val paragraphs = remember { mutableStateMapOf<String, List<String>>() }

    // Read on expand, not at screen entry, and off the main thread: the common visit opens neither.
    LaunchedEffect(expanded) {
        expanded.forEach { name ->
            if (paragraphs[name] == null) {
                val license = LibraryLicense.valueOf(name)
                paragraphs[name] = withContext(Dispatchers.IO) {
                    context.resources
                        .openRawResource(license.rawTextRes())
                        .bufferedReader()
                        .use { it.readText() }
                        .split(PARAGRAPH_BREAK)
                }
            }
        }
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
        item {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(OPEN_SOURCE_LIBRARIES) { library ->
            SectionCard(title = library.name) {
                Text(
                    text = library.coordinates,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = library.copyright,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                library.note?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                // Kept wide: the two CameraX licences sit side by side, and adjacent tap targets
                // this small are easy to confuse for one another.
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    library.licenses.forEach { license ->
                        Text(
                            text = license.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                // A bare clickable Text is only as tall as its glyphs. The role makes
                                // TalkBack announce a link rather than read it as prose, and the
                                // padding brings the target to the 48dp minimum.
                                .clickable(role = Role.Button) {
                                    if (!ExternalLinks.openUrl(context, license.url)) {
                                        val copied = ExternalLinks.copyToClipboard(
                                            context,
                                            label = license.displayName,
                                            text = license.url,
                                        )
                                        val message = if (copied) noBrowser else noHandler
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    }
                                }
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }

        // Driven by the `bundled` flag rather than a hardcoded Apache block, so a third bundled
        // licence becomes an enum entry and a raw file, not another copy of this section.
        bundledLicenses.forEach { license ->
            val open = license.name in expanded
            item(key = "toggle-${license.name}") {
                OutlinedButton(
                    onClick = {
                        expanded = if (open) expanded - license.name else expanded + license.name
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (open) R.string.licenses_hide_full_text else R.string.licenses_show_full_text,
                            license.displayName,
                        ),
                    )
                }
            }
            if (open) {
                items(paragraphs[license.name].orEmpty()) { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/**
 * The raw resource holding a licence's full text. Kept here rather than on [LibraryLicense] so the
 * enum stays plain Kotlin with no generated-resource references, and remains unit-testable as such.
 */
private fun LibraryLicense.rawTextRes(): Int = when (this) {
    LibraryLicense.APACHE_2_0 -> R.raw.apache_2_0
    LibraryLicense.OFL_1_1 -> R.raw.ofl_1_1
    // The rest link out; `bundled` is false for them and this is never reached.
    else -> error("$name has no bundled text")
}

/** Paragraphs in the licence text are separated by a blank line. */
private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
