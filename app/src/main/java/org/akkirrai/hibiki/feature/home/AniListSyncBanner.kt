package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.anilist.AniListSyncStatus

/**
 * Tells the user that AniList sync needs them: changes are waiting to be sent (and how many would delete an
 * entry there), or the sign-in ran out. Tapping it goes straight to what has to be confirmed.
 */
@Composable
internal fun AniListSyncBanner(
    pending: AniListSyncStatus.Pending?,
    needsSignIn: Boolean,
    onClick: () -> Unit,
) {
    val hasDeletions = pending != null && pending.removals > 0
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (hasDeletions) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (hasDeletions) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        pending != null && pending.removals > 0 ->
                            stringResource(R.string.anilist_banner_removals, pending.changes, pending.removals)
                        pending != null -> stringResource(R.string.anilist_banner_pending, pending.changes)
                        else -> stringResource(R.string.anilist_banner_sign_in)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        if (pending != null) R.string.anilist_banner_review else R.string.anilist_banner_sign_in_action,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
