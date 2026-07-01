package org.akkirrai.hibiki.feature.account

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyProfileWatchSum
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserList
import org.akkirrai.animeresolver.metadata.YummyUserListWatchStat
import org.akkirrai.hibiki.core.design.YUMMY_FAVORITE_LIST_ID
import org.akkirrai.hibiki.core.design.yummyListColor
import org.akkirrai.hibiki.core.design.yummyListLabel

internal fun buildProfileSnapshot(
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
    listWatchStats: List<YummyUserListWatchStat>,
): YummyProfileSnapshot {
    val history = profile.watches?.history.orEmpty()
    val watchSums = profile.watches?.sum.orEmpty()
    val activityCounts = history
        .filter { (it.duration ?: 0L) > 0L || (it.episodeCount ?: 0) > 0 }
        .mapNotNull { it.date?.let(::epochToLocalDate) }
        .groupingBy { it }
        .eachCount()

    val today = LocalDate.now()
    val activityDays = (0 until HEATMAP_DAYS)
        .map { today.minusDays((HEATMAP_DAYS - 1 - it).toLong()) }
        .map { date ->
            ActivityDay(
                intensity = (activityCounts[date] ?: 0).coerceAtMost(4),
            )
        }

    val streakDays = profile.daysOnline ?: 0

    val totalDuration = listWatchStats.sumOf { it.seconds }
        .takeIf { it > 0L }
        ?: watchSums.sumOf { it.spentTime ?: 0L }
            .takeIf { it > 0L }
        ?: history.sumOf { it.duration ?: 0L }

    val recentItems = libraryItems
        .asSequence()
        .filter { !it.title.isBlank() && it.addedAt != null }
        .sortedByDescending { it.addedAt }
        .take(6)
        .map { item ->
            RecentLibraryItem(
                title = item.title,
                posterUrl = item.posterUrl,
                ratingLabel = item.yummyRating?.let(::formatYummyRating),
                statusLabel = item.list.yummyListLabel(),
                dateLabel = item.addedAt?.let(::formatEpochDateShort).orEmpty(),
                color = item.list.yummyListColor(),
            )
        }
        .toList()

    return YummyProfileSnapshot(
        watchTimeLabel = formatDurationLabel(totalDuration),
        streakDays = streakDays,
        streakLabel = streakDays.toString(),
        activeDaysCount = activityCounts.size,
        totalEpisodes = history.sumOf { it.episodeCount ?: 0 },
        favoriteCount = profile.counts.favorite.takeIf { profile.counts.hasAny }
            ?: libraryItems.count(YummyUserAnimeListItem::isFavorite),
        favoriteHoursLabel = formatDurationLabel(listWatchStats.sumForRawListId(YUMMY_FAVORITE_LIST_ID)),
        favoriteDurationSeconds = listWatchStats.sumForRawListId(YUMMY_FAVORITE_LIST_ID),
        libraryTotal = libraryItems.size,
        distributionSegments = buildDistributionSegments(profile, libraryItems),
        durationSegments = buildDurationSegments(watchSums, listWatchStats),
        activityDays = activityDays,
        recentLibraryItems = recentItems,
        onlineDaysLabel = profile.daysOnline?.toString() ?: "—",
    )
}

internal fun normalizeYummyAssetUrl(rawUrl: String?): String? {
    val value = rawUrl?.trim()?.takeIf(String::isNotBlank) ?: return null
    return when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$YUMMY_WEB_BASE$value"
        else -> "$YUMMY_WEB_BASE/$value"
    }
}

private fun buildDistributionSegments(
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
): List<DistributionSegment> {
    if (profile.counts.hasAny) {
        return listOf(
            DistributionSegment(YummyUserList.Watching.yummyListLabel(), profile.counts.watching, YummyUserList.Watching.yummyListColor()),
            DistributionSegment(YummyUserList.Planned.yummyListLabel(), profile.counts.planned, YummyUserList.Planned.yummyListColor()),
            DistributionSegment(YummyUserList.Completed.yummyListLabel(), profile.counts.completed, YummyUserList.Completed.yummyListColor()),
            DistributionSegment(YummyUserList.Dropped.yummyListLabel(), profile.counts.dropped, YummyUserList.Dropped.yummyListColor()),
            DistributionSegment(YummyUserList.OnHold.yummyListLabel(), profile.counts.onHold, YummyUserList.OnHold.yummyListColor()),
        )
    }

    val counts = libraryItems.groupingBy { it.list }.eachCount()
    return listOf(
        DistributionSegment(YummyUserList.Watching.yummyListLabel(), counts[YummyUserList.Watching] ?: 0, YummyUserList.Watching.yummyListColor()),
        DistributionSegment(YummyUserList.Planned.yummyListLabel(), counts[YummyUserList.Planned] ?: 0, YummyUserList.Planned.yummyListColor()),
        DistributionSegment(YummyUserList.Completed.yummyListLabel(), counts[YummyUserList.Completed] ?: 0, YummyUserList.Completed.yummyListColor()),
        DistributionSegment(YummyUserList.Dropped.yummyListLabel(), counts[YummyUserList.Dropped] ?: 0, YummyUserList.Dropped.yummyListColor()),
        DistributionSegment(YummyUserList.OnHold.yummyListLabel(), counts[YummyUserList.OnHold] ?: 0, YummyUserList.OnHold.yummyListColor()),
    )
}

private fun buildDurationSegments(
    watchSums: List<YummyProfileWatchSum>,
    listWatchStats: List<YummyUserListWatchStat>,
): List<DurationSegment> {
    if (listWatchStats.isNotEmpty()) {
        val values = listOf(
            Triple(YummyUserList.Watching.yummyListLabel(), listWatchStats.sumFor(YummyUserList.Watching), YummyUserList.Watching.yummyListColor()),
            Triple(YummyUserList.Planned.yummyListLabel(), listWatchStats.sumFor(YummyUserList.Planned), YummyUserList.Planned.yummyListColor()),
            Triple(YummyUserList.Completed.yummyListLabel(), listWatchStats.sumFor(YummyUserList.Completed), YummyUserList.Completed.yummyListColor()),
            Triple(YummyUserList.Dropped.yummyListLabel(), listWatchStats.sumFor(YummyUserList.Dropped), YummyUserList.Dropped.yummyListColor()),
            Triple(YummyUserList.OnHold.yummyListLabel(), listWatchStats.sumFor(YummyUserList.OnHold), YummyUserList.OnHold.yummyListColor()),
        )

        return values.map { (label, value, color) ->
            DurationSegment(
                label = label,
                hoursLabel = formatDurationLabel(value),
                value = value,
                color = color,
            )
        }
    }

    fun sumFor(vararg keys: String): Long {
        val normalizedKeys = keys.map { it.lowercase(Locale.ROOT) }
        return watchSums
            .filter { item ->
                val bucket = listOf(item.alias, item.shortName, item.name)
                    .filterNotNull()
                    .joinToString(" ")
                    .lowercase(Locale.ROOT)
                normalizedKeys.any(bucket::contains)
            }
            .sumOf { it.spentTime ?: 0L }
    }

    val values = listOf(
        Triple(YummyUserList.Watching.yummyListLabel(), sumFor("watch", "watching", "смотр"), YummyUserList.Watching.yummyListColor()),
        Triple(YummyUserList.Planned.yummyListLabel(), sumFor("plan", "planned", "план"), YummyUserList.Planned.yummyListColor()),
        Triple(YummyUserList.Completed.yummyListLabel(), sumFor("complete", "completed", "просмотр"), YummyUserList.Completed.yummyListColor()),
        Triple(YummyUserList.Dropped.yummyListLabel(), sumFor("drop", "dropped", "брош"), YummyUserList.Dropped.yummyListColor()),
        Triple(YummyUserList.OnHold.yummyListLabel(), sumFor("hold", "on hold", "отлож"), YummyUserList.OnHold.yummyListColor()),
    )

    return values.map { (label, value, color) ->
        DurationSegment(
            label = label,
            hoursLabel = formatDurationLabel(value),
            value = value,
            color = color,
        )
    }
}

private fun List<YummyUserListWatchStat>.sumFor(
    list: YummyUserList,
): Long {
    return filter { it.rawListId == list.value }.sumOf { it.seconds }
}

private fun List<YummyUserListWatchStat>.sumForRawListId(rawListId: Int): Long {
    return filter { it.rawListId == rawListId }.sumOf { it.seconds }
}

private fun formatDurationLabel(rawDuration: Long): String {
    if (rawDuration <= 0L) return localizedHourLabel("0")
    val secondsBasedHours = rawDuration / 3600.0
    val minutesBasedHours = rawDuration / 60.0
    val hours = when {
        rawDuration >= 10_000L -> secondsBasedHours
        rawDuration >= 180L -> minutesBasedHours
        else -> secondsBasedHours
    }
    return if (hours >= 10) {
        localizedHourLabel(hours.toInt().toString())
    } else {
        localizedHourLabel(String.format(Locale.US, "%.1f", hours))
    }
}

private fun epochToLocalDate(value: Long): LocalDate {
    val instant = if (value >= 1_000_000_000_000L) {
        Instant.ofEpochMilli(value)
    } else {
        Instant.ofEpochSecond(value)
    }
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun formatEpochDateShort(value: Long): String {
    val date = epochToLocalDate(value)
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    return when {
        daysAgo <= 0 -> "Сегодня"
        daysAgo == 1 -> "Вчера"
        daysAgo < 7 -> "${daysAgo}д"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
    }
}

internal fun formatEpochDateCompact(value: Long): String {
    return epochToLocalDate(value).format(DateTimeFormatter.ofPattern("dd.MM.yy", Locale("ru")))
}

private fun localizedHourLabel(value: String): String {
    return "$value ч"
}

private fun formatYummyRating(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

internal data class YummyProfileSnapshot(
    val watchTimeLabel: String,
    val streakDays: Int,
    val streakLabel: String,
    val activeDaysCount: Int,
    val totalEpisodes: Int,
    val favoriteCount: Int,
    val favoriteHoursLabel: String,
    val favoriteDurationSeconds: Long,
    val libraryTotal: Int,
    val distributionSegments: List<DistributionSegment>,
    val durationSegments: List<DurationSegment>,
    val activityDays: List<ActivityDay>,
    val recentLibraryItems: List<RecentLibraryItem>,
    val onlineDaysLabel: String,
)

internal data class DistributionSegment(
    val label: String,
    val count: Int,
    val color: Color,
)

internal data class DurationSegment(
    val label: String,
    val hoursLabel: String,
    val value: Long,
    val color: Color,
)

internal data class ActivityDay(
    val intensity: Int,
)

internal data class RecentLibraryItem(
    val title: String,
    val posterUrl: String?,
    val ratingLabel: String?,
    val statusLabel: String,
    val dateLabel: String,
    val color: Color,
)

private const val HEATMAP_DAYS = 140
private const val YUMMY_WEB_BASE = "https://ru.yummyani.me"
