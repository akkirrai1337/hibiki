package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        AnalyticsDonutPager(
            pages = pages,
            snapshot = snapshot,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.yummy_account_activity_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val dayWidth = (maxWidth - (ACTIVITY_CHART_DAY_GAP * (ACTIVITY_CHART_VISIBLE_DAYS - 1))) /
                    ACTIVITY_CHART_VISIBLE_DAYS
                ActivityBarChart(
                    days = snapshot.activityDays,
                    dayWidth = dayWidth,
                    listState = activityListState,
                    muted = !hasActivity,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AnalyticsDonutPager(
    pages: List<AnalyticsPage>,
    snapshot: LocalProfileSnapshot,
) {
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
        backIcon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
        forwardIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
    )
}

@Composable
private fun ActivityBarChart(
    days: List<ActivityDay>,
    dayWidth: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    org.akkirrai.hibiki.shared.profile.ProfileActivityBarChart(
        days = days.map { org.akkirrai.hibiki.shared.profile.ProfileActivityBarItem(it.dateLabel, it.episodeCount) },
        dayWidth = dayWidth,
        listState = listState,
        dayGap = ACTIVITY_CHART_DAY_GAP,
        minScaleEpisodes = ACTIVITY_CHART_MIN_SCALE_EPISODES,
        activeColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f) else Color(0xFFFF7A86),
        inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.54f),
        modifier = modifier,
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
