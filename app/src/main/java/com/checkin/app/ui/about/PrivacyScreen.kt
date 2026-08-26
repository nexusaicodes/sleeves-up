package com.checkin.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.SectionCard

/**
 * The four claims the listing leads with, stated in the app's own words.
 *
 * It exists because the strongest thing this app can say is a negative, and a negative is only worth
 * anything if the reader can check it. Android's own App info screen is where it is checkable, so
 * this screen's job is to make the claim plainly and then say where to go and verify it — not to be
 * the evidence itself.
 *
 * Deliberately **not** a screenshot of Android's permission list: that is not this app's UI, it
 * varies by OEM and version, and Play is inconsistent about accepting system chrome in a listing.
 *
 * Every line here is subject to the same re-verification rule as the listing copy: "no internet
 * permission" holds because nothing on the release classpath declares `INTERNET`, which a new
 * dependency can silently take away. See the merged-manifest entry in `CLAUDE.md` before a release.
 */
@Composable
fun PrivacyScreen(innerPadding: PaddingValues) {
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
            SectionCard(title = stringResource(R.string.privacy_cannot_title)) {
                ClaimRow(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.privacy_no_internet_title),
                    body = stringResource(R.string.privacy_no_internet_body),
                )
                ClaimRow(
                    icon = Icons.Default.NoAccounts,
                    title = stringResource(R.string.privacy_no_account_title),
                    body = stringResource(R.string.privacy_no_account_body),
                )
                ClaimRow(
                    icon = Icons.Default.NoPhotography,
                    title = stringResource(R.string.privacy_no_photo_title),
                    body = stringResource(R.string.privacy_no_photo_body),
                )
                ClaimRow(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.privacy_no_analytics_title),
                    body = stringResource(R.string.privacy_no_analytics_body),
                    last = true,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.privacy_check_title)) {
                Text(
                    text = stringResource(R.string.privacy_check_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One claim: a mark, what the app does not do, and the mechanism that makes it so.
 *
 * The icon is decorative and the whole row is read as a single node — announced as separate ones a
 * claim arrives as a fragment ("no internet permission") divorced from the sentence that makes it
 * checkable, which is the half worth hearing.
 */
@Composable
private fun ClaimRow(icon: ImageVector, title: String, body: String, last: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$title. $body" },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (!last) Spacer(modifier = Modifier.height(20.dp))
}
