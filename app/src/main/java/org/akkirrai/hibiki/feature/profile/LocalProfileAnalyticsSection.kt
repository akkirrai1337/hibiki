package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        org.akkirrai.hibiki.shared.profile.ProfileAnalyticsPagerHeader(
            title = stringResource(R.string.yummy_account_segment_stats),
            currentPage = currentPage,
            pageCount = pages.size,
            backIcon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            forwardIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            onPrevious = { currentPage -= 1 },
            onNext = { currentPage += 1 },
        )
        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "AnalyticsPage",
        ) { pageIndex ->
            val displayedPage = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendGrid(
                    items = displayedPage.segments,
                    modifier = Modifier.weight(1f),
                    columns = 1,
                )
                SegmentDonut(
                    segments = displayedPage.segments,
                    centerPrimary = displayedPage.centerPrimary,
                    centerSecondary = displayedPage.centerSecondary,
                    modifier = Modifier.size(152.dp),
                    muted = displayedPage.segments.all { it.weight <= 0f },
                )
            }
                if (pageIndex == 0) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.yummy_account_stat_episodes_title)}: ${snapshot.totalEpisodes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${stringResource(R.string.yummy_account_stat_watch_short)}: ${snapshot.watchTimeLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageArrowButton(
    enabled: Boolean,
    onClick: () -> Unit,
    isBack: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    org.akkirrai.hibiki.shared.profile.ProfilePageArrowButton(
        icon = if (isBack) Icons.AutoMirrored.Outlined.KeyboardArrowLeft
        else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        size = size,
    )
}

@Composable
private fun LegendGrid(
    items: List<AnalyticsSegment>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    org.akkirrai.hibiki.shared.profile.ProfileLegendGrid(
        items = items.map { item ->
            org.akkirrai.hibiki.shared.profile.ProfileLegendGridItem(item.label, item.valueLabel, item.color)
        },
        columns = columns,
        modifier = modifier,
    )
}

@Composable
private fun LegendItem(
    item: AnalyticsSegment,
    modifier: Modifier = Modifier,
) {
    org.akkirrai.hibiki.shared.profile.ProfileLegendItem(
        label = item.label,
        valueLabel = item.valueLabel,
        color = item.color,
        modifier = modifier,
    )
}

@Composable
private fun SegmentDonut(
    segments: List<AnalyticsSegment>,
    centerPrimary: String,
    centerSecondary: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    org.akkirrai.hibiki.shared.profile.ProfileSegmentDonut(
        segments = segments.map { segment ->
            org.akkirrai.hibiki.shared.profile.ProfileDonutSegment(segment.weight, segment.color)
        },
        centerPrimary = centerPrimary,
        centerSecondary = centerSecondary,
        modifier = modifier,
        muted = muted,
    )
}

@Composable
private fun SegmentDonutLegacy(
    segments: List<AnalyticsSegment>,
    centerPrimary: String,
    centerSecondary: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val trackColor = if (muted) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            val strokeWidth = 18.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            var startAngle = -90f
            val safeTotal = segments.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            segments.filter { it.weight > 0f }.forEach { segment ->
                val sweep = segment.weight / safeTotal * 360f
                drawArc(
                    color = if (muted) segment.color.copy(alpha = 0.4f) else segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = centerSecondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
        }
    }
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

@Composable
private fun ActivityBarChartLegacy(
    days: List<ActivityDay>,
    dayWidth: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val maxEpisodes = days.maxOfOrNull(ActivityDay::episodeCount)
        ?.coerceAtLeast(ACTIVITY_CHART_MIN_SCALE_EPISODES)
        ?: ACTIVITY_CHART_MIN_SCALE_EPISODES
    val activeColor = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
    } else {
        Color(0xFFFF7A86)
    }
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.54f)

    LazyRow(
        state = listState,
        modifier = modifier.height(142.dp),
        horizontalArrangement = Arrangement.spacedBy(ACTIVITY_CHART_DAY_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(
            items = days,
            key = ActivityDay::dateLabel,
        ) { day ->
            val barHeight = if (day.episodeCount > 0) {
                (18 + (66 * day.episodeCount / maxEpisodes)).dp
            } else {
                10.dp
            }
            Column(
                modifier = Modifier.width(dayWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .height(114.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = day.episodeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (day.episodeCount > 0) activeColor else inactiveColor),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = day.dateLabel,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
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
