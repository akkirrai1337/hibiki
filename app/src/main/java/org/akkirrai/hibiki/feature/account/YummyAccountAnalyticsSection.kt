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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.core.design.yummyFavoriteListColor

@Composable
internal fun AnalyticsCard(
    snapshot: YummyProfileSnapshot,
) {
    val hasActivity = snapshot.activeDaysCount > 0
    val statusSegments = remember(snapshot.distributionSegments, snapshot.favoriteCount) {
        snapshot.distributionSegments + DistributionSegment(
            label = "Любимое",
            count = snapshot.favoriteCount,
            color = yummyFavoriteListColor(),
        )
    }
    val durationSegments = remember(
        snapshot.durationSegments,
        snapshot.favoriteHoursLabel,
        snapshot.favoriteDurationSeconds,
    ) {
        snapshot.durationSegments + DurationSegment(
            label = "Любимое",
            hoursLabel = snapshot.favoriteHoursLabel,
            value = snapshot.favoriteDurationSeconds,
            color = yummyFavoriteListColor(),
        )
    }
    val hasDurationData = durationSegments.any { it.value > 0L }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Время по спискам",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DistributionDonut(
                    segments = durationSegments,
                    centerLabel = snapshot.watchTimeLabel,
                    modifier = Modifier.size(132.dp),
                    muted = !hasDurationData,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    durationSegments.forEach { segment ->
                        DurationLegendRow(segment = segment, muted = !hasDurationData)
                    }
                }
            }
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
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.05f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
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
            StatusTilesGrid(segments = statusSegments)
        }
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
            text = "дней на сайте",
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
private fun DistributionDonut(
    segments: List<DurationSegment>,
    centerLabel: String,
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
            val safeTotal = segments.sumOf(DurationSegment::value).coerceAtLeast(1L)
            segments.filter { it.value > 0L }.forEach { segment ->
                val sweep = segment.value / safeTotal.toFloat() * 360f
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
                text = centerLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = "всего",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun DurationLegendRow(
    segment: DurationSegment,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(segment.color),
        )
        Text(
            text = segment.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = segment.hoursLabel,
            modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
            } else {
                AccountWarmAccent
            },
        )
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
    val emptyColor = Color(0xFF252A33).copy(alpha = 0.78f)
    val activeColor = Color(0xFF8E96A3)
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
        return base.copy(alpha = 0.24f)
    }
    return when (intensity) {
        0 -> base.copy(alpha = 0.12f)
        1 -> base.copy(alpha = 0.28f)
        2 -> base.copy(alpha = 0.48f)
        3 -> base.copy(alpha = 0.7f)
        else -> base
    }
}
