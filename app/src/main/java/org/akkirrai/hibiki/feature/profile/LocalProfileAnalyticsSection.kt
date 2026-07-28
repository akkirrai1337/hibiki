package org.akkirrai.hibiki.feature.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.profile.AppProfileAnalyticsContent
import org.akkirrai.hibiki.shared.profile.LocalProfileSnapshot

@Composable
internal fun AnalyticsCard(
    snapshot: LocalProfileSnapshot,
) {
    AppProfileAnalyticsContent(
        snapshot = snapshot,
        watchTimeTitle = stringResource(R.string.local_profile_analytics_watch_title),
        totalLabel = stringResource(R.string.local_profile_analytics_total_label),
        genresTitle = stringResource(R.string.local_profile_analytics_genres_title),
        genresLabel = stringResource(R.string.local_profile_analytics_genres_label),
        analyticsTitle = stringResource(R.string.yummy_account_segment_stats),
        episodeStat = "${stringResource(R.string.yummy_account_stat_episodes_title)}: ${snapshot.totalEpisodes}",
        watchStat = "${stringResource(R.string.yummy_account_stat_watch_short)}: ${snapshot.watchTimeLabel}",
        activityTitle = stringResource(R.string.yummy_account_activity_title),
        activeColor = if (snapshot.activeDaysCount > 0) {
            Color(0xFFFF7A86)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
        },
        inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.54f),
    )
}
