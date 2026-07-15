package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
    val activityScrollState = rememberScrollState()
    LaunchedEffect(snapshot.activityDays) {
        activityScrollState.scrollTo(activityScrollState.maxValue)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(activityScrollState),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ActivityBarChart(
                        days = snapshot.activityDays,
                        dayWidth = dayWidth,
                        muted = !hasActivity,
                    )
                }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.yummy_account_segment_stats),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageArrowButton(
                    enabled = currentPage > 0,
                    onClick = { currentPage -= 1 },
                    isBack = true,
                    size = 32.dp,
                )
                Text(
                    text = "${currentPage + 1}/${pages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PageArrowButton(
                    enabled = currentPage < pages.lastIndex,
                    onClick = { currentPage += 1 },
                    isBack = false,
                    size = 32.dp,
                )
            }
        }
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 0.28f else 0.12f),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = if (isBack) {
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight
                },
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
                },
            )
        }
    }
}

@Composable
private fun LegendGrid(
    items: List<AnalyticsSegment>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val safeColumns = columns.coerceAtLeast(1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.chunked(safeColumns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { item ->
                    LegendItem(
                        item = item,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((safeColumns - row.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    item: AnalyticsSegment,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(item.color),
        )
        Text(
            text = item.label,
            modifier = Modifier.widthIn(max = 132.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.valueLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SegmentDonut(
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

    Row(
        modifier = Modifier
            .width((dayWidth * days.size) + (ACTIVITY_CHART_DAY_GAP * (days.size - 1).coerceAtLeast(0)))
            .height(142.dp),
        horizontalArrangement = Arrangement.spacedBy(ACTIVITY_CHART_DAY_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
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
            title = "Время просмотра по спискам",
            centerPrimary = snapshot.genreSegments.sumOf(DistributionSegment::count).toString(),
            centerSecondary = "всего",
            segments = snapshot.genreSegments.map { segment ->
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
