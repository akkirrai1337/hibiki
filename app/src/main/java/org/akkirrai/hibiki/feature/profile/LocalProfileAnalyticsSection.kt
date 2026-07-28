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

@Composable
internal fun AnalyticsCard(
    snapshot: LocalProfileSnapshot,
) {
    val hasActivity = snapshot.activeDaysCount > 0
    val pages = remember(
        snapshot.libraryStatusSegments,
        snapshot.genreSegments,
        snapshot.watchTimeLabel,
        snapshot.libraryTotal,
    ) {
        buildAnalyticsPages(snapshot)
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
                pages = pages.map { page ->
                    org.akkirrai.hibiki.shared.profile.ProfileAnalyticsPage(
                        centerPrimary = page.centerPrimary,
                        centerSecondary = page.centerSecondary,
                        segments = page.segments.map { segment ->
                            org.akkirrai.hibiki.shared.profile.ProfileAnalyticsSegment(
                                label = segment.label,
                                valueLabel = segment.valueLabel,
                                weight = segment.weight,
                                color = segment.color,
                            )
                        },
                    )
                },
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

private fun buildAnalyticsPages(snapshot: LocalProfileSnapshot): List<AnalyticsPage> {
    return listOf(
        AnalyticsPage(
            title = "Время просмотра",
            centerPrimary = snapshot.libraryTotal.toString(),
            centerSecondary = "всего",
            segments = snapshot.libraryStatusSegments.map { segment ->
                AnalyticsSegment(
                    label = segment.label,
                    valueLabel = segment.count.toString(),
                    weight = segment.count.toFloat(),
                    color = segment.color,
                )
            },
            legendColumns = 2,
        ),
        AnalyticsPage(
            title = "Жанры",
            centerPrimary = snapshot.genreSegments.sumOf(DistributionSegment::count).toString(),
            centerSecondary = "жанров",
            segments = snapshot.genreSegments.map { segment ->
                AnalyticsSegment(
                    label = segment.label,
                    valueLabel = segment.count.toString(),
                    weight = segment.count.toFloat(),
                    color = segment.color,
                )
            },
            legendColumns = 3,
        ),
    )
}

private data class AnalyticsPage(
    val title: String,
    val centerPrimary: String,
    val centerSecondary: String,
    val segments: List<AnalyticsSegment>,
    val legendColumns: Int,
)

private data class AnalyticsSegment(
    val label: String,
    val valueLabel: String,
    val weight: Float,
    val color: Color,
)

private const val ACTIVITY_CHART_MIN_SCALE_EPISODES = 8
private val ACTIVITY_CHART_DAY_GAP = 4.dp
private const val ACTIVITY_CHART_VISIBLE_DAYS = 7
