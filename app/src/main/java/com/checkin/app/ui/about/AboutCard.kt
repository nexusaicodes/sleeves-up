package com.checkin.app.ui.about

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.text.Text
import com.checkin.app.BuildConfig
import com.checkin.app.R
import com.checkin.app.ui.components.ActionRow
import com.checkin.app.ui.components.AppIcons
import com.checkin.app.ui.components.SectionCard
import com.checkin.app.ui.components.SectionDivider

/**
 * The five meta links, then app identity as a footer.
 *
 * A card rather than its own screen: it is seven rows, and a dedicated destination for that would add
 * a tap without adding anything to read. Only what a row opens onto — the privacy explainer and the
 * licence list, which is longer than the whole of Settings — earns a route of its own.
 *
 * [showMessage] is supplied by the host rather than launched from a scope in here: this card is a
 * `LazyColumn` item, so a scope remembered locally dies the moment the card scrolls out of view —
 * taking the fallback snackbar with it, exactly when the user needs to read it.
 */
@Composable
fun AboutCard(
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Built once: none of these inputs can change while the process is alive.
    val draft = remember {
        Feedback.draft(
            app = AppBuild(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            device = DeviceBuild(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
            ),
        )
    }

    val noBrowser = stringResource(R.string.about_no_browser)
    val noEmailApp = stringResource(R.string.about_no_email_app, Feedback.ADDRESS)
    val noHandler = stringResource(R.string.about_no_handler)
    // Each row's own label doubles as its clipboard label, so the clip names itself in the reader's
    // language.
    val privacyPolicyLabel = stringResource(R.string.about_privacy_policy)
    val feedbackLabel = stringResource(R.string.about_feedback)
    val rateLabel = stringResource(R.string.about_rate)

    SectionCard(title = stringResource(R.string.about_section), modifier = modifier) {
        ActionRow(
            label = stringResource(R.string.about_privacy_on_device),
            icon = AppIcons.ChevronEnd,
            contentDescription = null,
            onClick = onOpenPrivacy,
        )
        ActionRow(
            label = privacyPolicyLabel,
            icon = AppIcons.OpenElsewhere,
            contentDescription = stringResource(R.string.about_open_link),
        ) {
            if (!ExternalLinks.openUrl(context, ExternalLinks.PRIVACY_POLICY_URL)) {
                val copied = ExternalLinks.copyToClipboard(
                    context,
                    label = privacyPolicyLabel,
                    text = ExternalLinks.PRIVACY_POLICY_URL,
                )
                showMessage(if (copied) noBrowser else noHandler)
            }
        }
        ActionRow(
            label = feedbackLabel,
            icon = AppIcons.OpenElsewhere,
            contentDescription = stringResource(R.string.about_open_link),
        ) {
            if (!ExternalLinks.sendFeedback(context, draft)) {
                val copied =
                    ExternalLinks.copyToClipboard(context, label = feedbackLabel, text = Feedback.ADDRESS)
                showMessage(if (copied) noEmailApp else noHandler)
            }
        }
        ActionRow(
            label = rateLabel,
            icon = AppIcons.OpenElsewhere,
            contentDescription = stringResource(R.string.about_open_link),
        ) {
            // Both the Play app and the web listing are missing on some emulators; the listing URL
            // is already the fallback inside openPlayListing, so a failure here means neither.
            if (!ExternalLinks.openPlayListing(context)) {
                val copied = ExternalLinks.copyToClipboard(
                    context,
                    label = rateLabel,
                    text = ExternalLinks.playListingUrl(context),
                )
                showMessage(if (copied) noBrowser else noHandler)
            }
        }
        ActionRow(
            label = stringResource(R.string.about_licenses),
            icon = AppIcons.ChevronEnd,
            contentDescription = null,
            onClick = onOpenLicenses,
        )

        SectionDivider()
        Identity()
    }
}

/**
 * Who built this and which build it is, as a centred footer below the links.
 *
 * Last and centred because it is the only thing on the card that cannot be acted on: the links are
 * why someone opens About, while the version is reference material looked up when support asks for
 * it. Centring is what stops it reading as another row — the five above are left-aligned and
 * tappable, and identity is neither. Muted to `bodySmall` because it is the least useful line on the
 * card.
 */
@Composable
private fun Identity() {
    Text(
        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        style = SparkTheme.typography.caption,
        color = SparkTheme.colors.onBackgroundVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.about_developer),
        style = SparkTheme.typography.caption,
        color = SparkTheme.colors.onBackgroundVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
