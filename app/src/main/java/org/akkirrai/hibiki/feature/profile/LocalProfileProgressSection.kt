package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.profile.ProfileRules

/**
 * Level, streak and achievements - the part of the profile that has something to say before any
 * data exists, which is the whole reason it is here. An empty profile used to be three stat tiles
 * reading zero.
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

@Composable
internal fun LevelCard(level: ProfileRules.LevelProgress, streak: ProfileRules.StreakInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.local_profile_level_badge, level.level),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(Modifier.weight(1f))
                Text(
                    stringResource(R.string.local_profile_level_xp, level.xpIntoLevel, level.xpForLevel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Drawn rather than a LinearProgressIndicator: the track needs the same rounded ends as
            // the rest of this screen's bars, which the material one does not give.
            val fraction = if (level.xpForLevel > 0) {
                (level.xpIntoLevel.toFloat() / level.xpForLevel).coerceIn(0f, 1f)
            } else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                )
            }

            // Only once there is a run to show. A "0 day streak" is a reproach, not information.
            if (streak.current > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streak.atRisk) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF7043),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.local_profile_streak_days, streak.current),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (streak.best > streak.current) {
                        Text(
                            stringResource(R.string.local_profile_streak_best, streak.best),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (streak.atRisk) {
                        Text(
                            stringResource(R.string.local_profile_streak_at_risk),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AchievementsCard(achievements: List<ProfileRules.Achievement>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.local_profile_achievements_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
            val fraction = if (achievement.target > 0) {
                (achievement.current / achievement.target).toFloat().coerceIn(0f, 1f)
            } else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
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
