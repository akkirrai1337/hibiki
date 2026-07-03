package org.akkirrai.hibiki.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.yummyFavoriteListColor

@Composable
internal fun AnalyticsCard(
    snapshot: YummyProfileSnapshot,
) {
    val hasActivity = snapshot.activeDaysCount > 0
    val favoriteLabel = stringResource(R.string.library_category_favorite)
    val statusSegments = remember(snapshot.distributionSegments, snapshot.favoriteCount, favoriteLabel) {
        snapshot.distributionSegments + DistributionSegment(
            label = favoriteLabel,
            count = snapshot.favoriteCount,
            color = yummyFavoriteListColor(),
        )
    }
    val durationSegments = remember(
        snapshot.durationSegments,
        snapshot.favoriteHoursLabel,
        snapshot.favoriteDurationSeconds,
        favoriteLabel,
    ) {
        snapshot.durationSegments + DurationSegment(
            label = favoriteLabel,
            hoursLabel = snapshot.favoriteHoursLabel,
            value = snapshot.favoriteDurationSeconds,
            color = yummyFavoriteListColor(),
        )
    }
    val pages = remember(
        durationSegments,
        snapshot.siteWatchSegments,
        snapshot.siteWatchTimeLabel,
        snapshot.watchTimeLabel,
        snapshot.ratingSegments,
        snapshot.ratingAverageLabel,
        snapshot.ratedTitlesCount,
        snapshot.genreSegments,
        snapshot.genreTrackedTitlesCount,
    ) {
        buildAnalyticsPages(
            snapshot = snapshot,
            durationSegments = durationSegments,
        )
    }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(pages.size) {
        currentPage = currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }
    val page = pages[currentPage]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnalyticsDonutPager(
                page = page,
                canGoBack = currentPage > 0,
                canGoForward = currentPage < pages.lastIndex,
                onBack = { if (currentPage > 0) currentPage -= 1 },
                onForward = { if (currentPage < pages.lastIndex) currentPage += 1 },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StreakBlock(
                    value = snapshot.streakDays.toString(),
                    modifier = Modifier.width(88.dp),
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.yummy_account_activity_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ActivityHeatmap(
                                days = snapshot.activityDays,
                                rows = 7,
                                columns = 18,
                                cellSize = 6,
                                gap = 2,
                                muted = !hasActivity,
                            )
                        }
                    }
                }
            }
            StatusTilesGrid(segments = statusSegments)
        }
    }
}

@Composable
private fun AnalyticsDonutPager(
    page: AnalyticsPage,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageArrowButton(
                enabled = canGoBack,
                onClick = onBack,
                isBack = true,
            )
            LegendGrid(
                items = page.segments,
                modifier = Modifier.weight(1f),
                columns = page.legendColumns,
            )
            PageArrowButton(
                enabled = canGoForward,
                onClick = onForward,
                isBack = false,
            )
            SegmentDonut(
                segments = page.segments,
                centerPrimary = page.centerPrimary,
                centerSecondary = page.centerSecondary,
                modifier = Modifier.size(132.dp),
                muted = page.segments.all { it.weight <= 0f },
            )
        }
        page.supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageArrowButton(
    enabled: Boolean,
    onClick: () -> Unit,
    isBack: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 0.22f else 0.12f),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(34.dp),
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
            modifier = Modifier.weight(1f),
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
            color = if (item.weight > 0f) AccountWarmAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun StreakBlock(
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFF6670),
        )
        Text(
            text = stringResource(R.string.yummy_account_site_days_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusTilesGrid(
    segments: List<DistributionSegment>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { segment ->
                    StatusTile(
                        modifier = Modifier.weight(1f),
                        segment = segment,
                    )
                }
                repeat(2 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusTile(
    segment: DistributionSegment,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(segment.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segment.color),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = segment.count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
        Canvas(modifier = Modifier.fillMaxSize()) {
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
private fun ActivityHeatmap(
    days: List<ActivityDay>,
    rows: Int = 7,
    columns: Int = 20,
    cellSize: Int = 14,
    gap: Int = 2,
    muted: Boolean = false,
) {
    val emptyColor = if (muted) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.18f)
    } else {
        Color(0xFF343B49)
    }
    val activeColor = if (muted) {
        Color(0xFF687487)
    } else {
        Color(0xFFFF7A86)
    }
    val paddedDays = buildList<ActivityDay?> {
        addAll(days.takeLast(rows * columns))
        repeat((rows * columns) - size) { add(null) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        repeat(rows) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                repeat(columns) { columnIndex ->
                    val itemIndex = columnIndex * rows + rowIndex
                    val day = paddedDays.getOrNull(itemIndex)
                    Box(
                        modifier = Modifier
                            .size(cellSize.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    day == null || day.intensity <= 0 -> emptyColor
                                    else -> day.color(base = activeColor, muted = muted)
                                },
                            ),
                    )
                }
            }
        }
    }
}

private fun ActivityDay.color(
    base: Color,
    muted: Boolean = false,
): Color {
    if (muted) {
        return base.copy(alpha = 0.3f)
    }
    return when (intensity) {
        0 -> base.copy(alpha = 0.2f)
        1 -> base.copy(alpha = 0.4f)
        2 -> base.copy(alpha = 0.62f)
        3 -> base.copy(alpha = 0.82f)
        else -> Color(0xFFFFA15F)
    }
}

private fun buildAnalyticsPages(
    snapshot: YummyProfileSnapshot,
    durationSegments: List<DurationSegment>,
): List<AnalyticsPage> {
    return listOf(
        AnalyticsPage(
            title = "Время просмотра на сайте",
            centerPrimary = snapshot.siteWatchTimeLabel,
            centerSecondary = "всего",
            segments = snapshot.siteWatchSegments.map { segment ->
                AnalyticsSegment(
                    label = segment.label,
                    valueLabel = segment.hoursLabel,
                    weight = segment.value.toFloat(),
                    color = segment.color,
                )
            },
            legendColumns = 2,
            supportingText = if (snapshot.siteWatchSegments.all { it.value <= 0L }) {
                "Разбивка строится из server watch sums Yummy. Если API не вернул типизированные bucket'ы, круг останется пустым."
            } else {
                null
            },
        ),
        AnalyticsPage(
            title = "Продолжительность по спискам",
            centerPrimary = snapshot.watchTimeLabel,
            centerSecondary = "всего",
            segments = durationSegments.map { segment ->
                AnalyticsSegment(
                    label = segment.label,
                    valueLabel = segment.hoursLabel,
                    weight = segment.value.toFloat(),
                    color = segment.color,
                )
            },
            legendColumns = 2,
        ),
        AnalyticsPage(
            title = "Время продолжительности эпизодов",
            centerPrimary = "0 ч",
            centerSecondary = "runtime",
            segments = snapshot.siteWatchSegments.map { segment ->
                AnalyticsSegment(
                    label = segment.label,
                    valueLabel = "0 ч",
                    weight = 0f,
                    color = segment.color,
                )
            },
            legendColumns = 2,
            supportingText = "Для этой страницы нужны duration/runtime эпизодов по тайтлам из списка. Текущий Yummy API и локальный кэш Hibiki такую длительность пока не дают, поэтому страница пока placeholder.",
        ),
        AnalyticsPage(
            title = "Поставленные оценки",
            centerPrimary = snapshot.ratedTitlesCount.toString(),
            centerSecondary = "avg ${snapshot.ratingAverageLabel}",
            segments = snapshot.ratingSegments.map { segment ->
                AnalyticsSegment(
                    label = segment.label,
                    valueLabel = segment.count.toString(),
                    weight = segment.count.toFloat(),
                    color = segment.color,
                )
            },
            legendColumns = 5,
            supportingText = if (snapshot.ratedTitlesCount == 0) {
                "Оценки читаются из Yummy API, но в текущем API-слое нет подтверждённой записи рейтинга обратно."
            } else {
                "Оценки уже парсятся из Yummy API. Запись рейтинга обратно пока не подтверждена."
            },
        ),
        AnalyticsPage(
            title = "Жанры",
            centerPrimary = snapshot.genreSegments.sumOf(DistributionSegment::count).toString(),
            centerSecondary = "тегов",
            segments = snapshot.genreSegments.map { segment ->
                AnalyticsSegment(
                    label = segment.label,
                    valueLabel = segment.count.toString(),
                    weight = segment.count.toFloat(),
                    color = segment.color,
                )
            },
            legendColumns = 3,
            supportingText = if (snapshot.genreTrackedTitlesCount == 0) {
                "Жанры доступны только для тайтлов, чьи метаданные уже были сохранены локально в Hibiki."
            } else {
                "Жанры собираются из локального кэша метаданных Hibiki и могут быть неполными."
            },
        ),
    )
}

private data class AnalyticsPage(
    val title: String,
    val centerPrimary: String,
    val centerSecondary: String,
    val segments: List<AnalyticsSegment>,
    val legendColumns: Int,
    val supportingText: String? = null,
)

private data class AnalyticsSegment(
    val label: String,
    val valueLabel: String,
    val weight: Float,
    val color: Color,
)
