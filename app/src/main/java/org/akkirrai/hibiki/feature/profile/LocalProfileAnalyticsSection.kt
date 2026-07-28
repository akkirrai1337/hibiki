package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.profile.LocalProfileSnapshot
import org.akkirrai.hibiki.shared.profile.buildProfileAnalyticsPages

@Composable
internal fun AnalyticsCard(
    snapshot: LocalProfileSnapshot,
) {
    val hasActivity = snapshot.activeDaysCount > 0
    val watchTimeTitle = stringResource(R.string.local_profile_analytics_watch_title)
    val totalLabel = stringResource(R.string.local_profile_analytics_total_label)
    val genresTitle = stringResource(R.string.local_profile_analytics_genres_title)
    val genresLabel = stringResource(R.string.local_profile_analytics_genres_label)
    val pages = remember(
        snapshot.libraryStatusSegments,
        snapshot.genreSegments,
        snapshot.watchTimeLabel,
        snapshot.libraryTotal,
        watchTimeTitle,
        totalLabel,
        genresTitle,
        genresLabel,
    ) {
        buildProfileAnalyticsPages(
            snapshot = snapshot,
            watchTimeTitle = watchTimeTitle,
            totalLabel = totalLabel,
            genresTitle = genresTitle,
            genresLabel = genresLabel,
        )
    }
    val firstVisibleActivityDay = remember(snapshot.activityDays.size) {
        (snapshot.activityDays.size - ACTIVITY_CHART_VISIBLE_DAYS).coerceAtLeast(0)
    }
    val activityListState = rememberLazyListState(
        initialFirstVisibleItemIndex = firstVisibleActivityDay,
    )
    LaunchedEffect(snapshot.activityDays) {
        activityListState.scrollToItem(firstVisibleActivityDay)
    }
    org.akkirrai.hibiki.shared.profile.AppProfileAnalyticsCard(
        donutContent = {
            org.akkirrai.hibiki.shared.profile.AppProfileAnalyticsDonutPager(
                pages = pages,
                title = stringResource(R.string.yummy_account_segment_stats),
                episodeStat = "${stringResource(R.string.yummy_account_stat_episodes_title)}: ${snapshot.totalEpisodes}",
                watchStat = "${stringResource(R.string.yummy_account_stat_watch_short)}: ${snapshot.watchTimeLabel}",
            )
        },
        activityContent = {
            org.akkirrai.hibiki.shared.profile.AppProfileActivitySection(
                title = stringResource(R.string.yummy_account_activity_title),
                days = snapshot.activityDays.map { day ->
                    org.akkirrai.hibiki.shared.profile.ProfileActivityBarItem(day.dateLabel, day.episodeCount)
                },
                listState = activityListState,
                visibleDays = ACTIVITY_CHART_VISIBLE_DAYS,
                dayGap = ACTIVITY_CHART_DAY_GAP,
                minScaleEpisodes = ACTIVITY_CHART_MIN_SCALE_EPISODES,
                activeColor = if (hasActivity) Color(0xFFFF7A86) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
                inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.54f),
            )
        },
    )
}

private const val ACTIVITY_CHART_MIN_SCALE_EPISODES = 8
private val ACTIVITY_CHART_DAY_GAP = 4.dp
private const val ACTIVITY_CHART_VISIBLE_DAYS = 7
