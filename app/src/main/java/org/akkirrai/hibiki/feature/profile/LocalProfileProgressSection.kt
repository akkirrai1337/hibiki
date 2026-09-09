package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.profile.ProfileRules

/**
 * Level, streak and achievements.
 *
 * Level does not get a card of its own: it is drawn as a ring around the avatar with the number on
 * its edge, because a standalone progress bar three sections down reads as something bolted on
 * rather than as a property of the profile. Achievements are a horizontal strip of one line, with
 * the full list behind a sheet - seven full-width rows were most of the page's height for seven
 * numbers.
 *
 * The numbers all come from [ProfileRules], which is checked against the same vectors as the
 * desktop implementation, so a given history reads the same on both. This file is only the
 * presentation: icons and strings live here precisely because the rules must not know about them.
 */

/** Which icon stands for a family's current tier. Presentation, deliberately kept out of the rules
 * - the desktop picks its own from the same tier ids, and neither has to agree with the other. */
private fun tierIcon(tierId: String): ImageVector = when (tierId) {
    "first_title" -> Icons.Filled.Flag
    "collector_10", "collector_25", "collector_50" -> Icons.Filled.MenuBook
    "finisher", "marathoner_10", "marathoner_50" -> Icons.Filled.CheckCircle
    "streak_7", "streak_14", "streak_30" -> Icons.Filled.LocalFireDepartment
    "watch_24h", "watch_100h", "watch_500h" -> Icons.Filled.Schedule
    "episodes_50", "episodes_100", "episodes_300" -> Icons.Filled.Tv
    else -> Icons.Filled.Explore
}

/** The tier a family card is currently showing - the one in progress, or the last once maxed. */
private fun activeTierId(achievement: ProfileRules.Achievement): String {
    val tiers = ProfileRules.FAMILY_TIERS.getValue(achievement.id)
    return tiers[if (achievement.level >= tiers.size) tiers.size - 1 else achievement.level].id
}

/** Whole numbers read as whole numbers; only watched hours are ever fractional. */
private fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

private fun ProfileRules.LevelProgress.fraction(): Float =
    if (xpForLevel > 0) (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f) else 0f

private fun ProfileRules.Achievement.fraction(): Float =
    if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f

/**
 * The avatar's XP ring, plus the level number sitting on its lower edge.
 *
 * [content] is the avatar itself, drawn inside the ring. The ring is a plain arc rather than a
 * CircularProgressIndicator so its track, cap and thickness match the strip's tiles below.
 */
@Composable
internal fun AvatarLevelRing(
    level: ProfileRules.LevelProgress,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f * alpha)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val fraction = level.fraction()
    Box(modifier = modifier.size(86.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(86.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val diameter = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2 + 0f,
                inset,
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = fill,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
        // On the ring rather than beside it: the number is a property of the ring, and putting it
        // anywhere else means the ring has to be explained.
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = level.level.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
    }
}

/** The one line under the name: how far into the level, and the streak when there is one. */
@Composable
internal fun LevelSummaryLine(level: ProfileRules.LevelProgress, streak: ProfileRules.StreakInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.local_profile_xp_inline, level.xpIntoLevel, level.xpForLevel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Only once there is a run to show. A "0 day streak" is a reproach, not information.
        if (streak.current > 0) {
            Surface(
                shape = CircleShape,
                color = if (streak.atRisk) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                } else {
                    StreakFlame.copy(alpha = 0.16f)
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streak.atRisk) MaterialTheme.colorScheme.onSurfaceVariant else StreakFlame,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = streak.current.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Every family in one scrollable line: an icon inside its own progress ring, the tier's name, and
 * how far along it is. Enough to see at a glance that something is close; the sheet is for reading
 * the actual numbers.
 */
@Composable
internal fun AchievementsStrip(
    achievements: List<ProfileRules.Achievement>,
    edgePadding: Dp,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = edgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.local_profile_achievements_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(Modifier.weight(1f))
            Text(
                stringResource(R.string.local_profile_achievements_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShapeSmall)
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        // The inset lives in the content padding rather than on the row, so the first and last
        // tiles sit where the headings do while the ones in between still scroll under the edges.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = edgePadding),
        ) {
            items(achievements, key = { it.id }) { AchievementTile(it, onClick = onSeeAll) }
        }
    }
}

@Composable
private fun AchievementTile(achievement: ProfileRules.Achievement, onClick: () -> Unit) {
    val done = achievement.unlocked
    val ringColor = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val fraction = achievement.fraction()
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                )
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction > 0f) {
                    drawArc(
                        ringColor, -90f, 360f * fraction, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Icon(
                tierIcon(activeTierId(achievement)),
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            achievementTitle(activeTierId(achievement)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(28.dp),
        )
        Text(
            "${formatAmount(achievement.current)}/${formatAmount(achievement.target)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The full list, on demand. Same rows the strip summarises, with room for the tier count. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AchievementsSheet(
    achievements: List<ProfileRules.Achievement>,
    streak: ProfileRules.StreakInfo,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.local_profile_achievements_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // The best run belongs here rather than in the header chip, which only has room for
            // the run that is still going.
            if (streak.best > 0) {
                Text(
                    stringResource(R.string.local_profile_streak_best, streak.best),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            achievements.forEach { AchievementRow(it) }
        }
    }
}

@Composable
private fun AchievementRow(achievement: ProfileRules.Achievement) {
    val tierId = activeTierId(achievement)
    val done = achievement.unlocked
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tierIcon(tierId),
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    achievementTitle(tierId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // A family with one tier is not "level 1 of 1" - it is just done or not.
                if (achievement.maxLevel > 1) {
                    Text(
                        "  " + stringResource(
                            R.string.local_profile_achievement_level,
                            achievement.level,
                            achievement.maxLevel,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.weight(1f))
                Text(
                    "${formatAmount(achievement.current)} / ${formatAmount(achievement.target)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(achievement.fraction())
                        .height(5.dp)
                        .background(
                            if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
    }
}

/** Tier ids are shared with the desktop, so their titles are looked up by the same id rather than
 * carried around in the rules. */
@Composable
private fun achievementTitle(tierId: String): String = stringResource(
    when (tierId) {
        "first_title" -> R.string.local_profile_achievement_first_title
        "collector_10" -> R.string.local_profile_achievement_collector_10
        "collector_25" -> R.string.local_profile_achievement_collector_25
        "collector_50" -> R.string.local_profile_achievement_collector_50
        "finisher" -> R.string.local_profile_achievement_finisher
        "marathoner_10" -> R.string.local_profile_achievement_marathoner_10
        "marathoner_50" -> R.string.local_profile_achievement_marathoner_50
        "streak_7" -> R.string.local_profile_achievement_streak_7
        "streak_14" -> R.string.local_profile_achievement_streak_14
        "streak_30" -> R.string.local_profile_achievement_streak_30
        "watch_24h" -> R.string.local_profile_achievement_watch_24h
        "watch_100h" -> R.string.local_profile_achievement_watch_100h
        "watch_500h" -> R.string.local_profile_achievement_watch_500h
        "episodes_50" -> R.string.local_profile_achievement_episodes_50
        "episodes_100" -> R.string.local_profile_achievement_episodes_100
        "episodes_300" -> R.string.local_profile_achievement_episodes_300
        "genre_explorer" -> R.string.local_profile_achievement_genre_5
        "genre_explorer_10" -> R.string.local_profile_achievement_genre_10
        else -> R.string.local_profile_achievement_genre_15
    },
)

private val StreakFlame = Color(0xFFFF7043)
private val CircleShapeSmall = RoundedCornerShape(8.dp)
