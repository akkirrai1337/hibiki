package org.akkirrai.hibiki.feature.account

import android.content.res.Resources
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
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.YUMMY_FAVORITE_LIST_ID
import org.akkirrai.hibiki.core.design.yummyListColor
import org.akkirrai.hibiki.core.design.yummyListLabel
import org.akkirrai.hibiki.core.model.Anime

internal fun buildProfileSnapshot(
    resources: Resources,
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
    listWatchStats: List<YummyUserListWatchStat>,
    cachedLibraryMetadata: List<Anime> = emptyList(),
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
                statusLabel = item.list.yummyListLabel(resources),
                dateLabel = item.addedAt?.let { formatEpochDateShort(resources, it) }.orEmpty(),
                color = item.list.yummyListColor(),
            )
        }
        .toList()

    val genreSegments = buildGenreSegments(cachedLibraryMetadata)
    val genreTrackedTitlesCount = cachedLibraryMetadata.count { it.genres.isNotEmpty() }
    val siteWatchSegments = buildSiteWatchSegments(resources, watchSums)
    val siteWatchTotal = siteWatchSegments.sumOf(DurationSegment::value)

    return YummyProfileSnapshot(
        watchTimeLabel = formatDurationLabel(resources, totalDuration),
        siteWatchTimeLabel = formatDurationLabel(resources, siteWatchTotal),
        streakDays = streakDays,
        streakLabel = streakDays.toString(),
        activeDaysCount = activityCounts.size,
        totalEpisodes = history.sumOf { it.episodeCount ?: 0 },
        favoriteCount = profile.counts.favorite.takeIf { profile.counts.hasAny }
            ?: libraryItems.count(YummyUserAnimeListItem::isFavorite),
        favoriteHoursLabel = formatDurationLabel(resources, listWatchStats.sumForRawListId(YUMMY_FAVORITE_LIST_ID)),
        favoriteDurationSeconds = listWatchStats.sumForRawListId(YUMMY_FAVORITE_LIST_ID),
        libraryTotal = libraryItems.size,
        distributionSegments = buildDistributionSegments(resources, profile, libraryItems),
        siteWatchSegments = siteWatchSegments,
        durationSegments = buildDurationSegments(resources, watchSums, listWatchStats),
        activityDays = activityDays,
        recentLibraryItems = recentItems,
        onlineDaysLabel = profile.daysOnline?.toString() ?: "-",
        genreSegments = genreSegments,
        genreTrackedTitlesCount = genreTrackedTitlesCount,
    )
}

internal fun normalizeYummyAssetUrl(rawUrl: String?): String? {
    val value = rawUrl
        ?.trim()
        ?.trim('"')
        ?.replace("\\/", "/")
        ?.takeIf(String::isNotBlank)
        ?: return null
    return when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$YUMMY_WEB_BASE$value"
        else -> "$YUMMY_WEB_BASE/$value"
    }
}

internal fun YummyProfile.resolvedAvatarUrl(): String? {
    return normalizeYummyAssetUrl(
        avatarUrl ?: avatars.full ?: avatars.big ?: avatars.small,
    )
}

private fun buildDistributionSegments(
    resources: Resources,
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
): List<DistributionSegment> {
    if (profile.counts.hasAny) {
        return listOf(
            DistributionSegment(YummyUserList.Watching.yummyListLabel(resources), profile.counts.watching, YummyUserList.Watching.yummyListColor()),
            DistributionSegment(YummyUserList.Planned.yummyListLabel(resources), profile.counts.planned, YummyUserList.Planned.yummyListColor()),
            DistributionSegment(YummyUserList.Completed.yummyListLabel(resources), profile.counts.completed, YummyUserList.Completed.yummyListColor()),
            DistributionSegment(YummyUserList.Dropped.yummyListLabel(resources), profile.counts.dropped, YummyUserList.Dropped.yummyListColor()),
            DistributionSegment(YummyUserList.OnHold.yummyListLabel(resources), profile.counts.onHold, YummyUserList.OnHold.yummyListColor()),
        )
    }

    val counts = libraryItems.groupingBy { it.list }.eachCount()
    return listOf(
        DistributionSegment(YummyUserList.Watching.yummyListLabel(resources), counts[YummyUserList.Watching] ?: 0, YummyUserList.Watching.yummyListColor()),
        DistributionSegment(YummyUserList.Planned.yummyListLabel(resources), counts[YummyUserList.Planned] ?: 0, YummyUserList.Planned.yummyListColor()),
        DistributionSegment(YummyUserList.Completed.yummyListLabel(resources), counts[YummyUserList.Completed] ?: 0, YummyUserList.Completed.yummyListColor()),
        DistributionSegment(YummyUserList.Dropped.yummyListLabel(resources), counts[YummyUserList.Dropped] ?: 0, YummyUserList.Dropped.yummyListColor()),
        DistributionSegment(YummyUserList.OnHold.yummyListLabel(resources), counts[YummyUserList.OnHold] ?: 0, YummyUserList.OnHold.yummyListColor()),
    )
}

private fun buildDurationSegments(
    resources: Resources,
    watchSums: List<YummyProfileWatchSum>,
    listWatchStats: List<YummyUserListWatchStat>,
): List<DurationSegment> {
    if (listWatchStats.isNotEmpty()) {
        val values = listOf(
            Triple(YummyUserList.Watching.yummyListLabel(resources), listWatchStats.sumFor(YummyUserList.Watching), YummyUserList.Watching.yummyListColor()),
            Triple(YummyUserList.Planned.yummyListLabel(resources), listWatchStats.sumFor(YummyUserList.Planned), YummyUserList.Planned.yummyListColor()),
            Triple(YummyUserList.Completed.yummyListLabel(resources), listWatchStats.sumFor(YummyUserList.Completed), YummyUserList.Completed.yummyListColor()),
            Triple(YummyUserList.Dropped.yummyListLabel(resources), listWatchStats.sumFor(YummyUserList.Dropped), YummyUserList.Dropped.yummyListColor()),
            Triple(YummyUserList.OnHold.yummyListLabel(resources), listWatchStats.sumFor(YummyUserList.OnHold), YummyUserList.OnHold.yummyListColor()),
        )

        return values.map { (label, value, color) ->
            DurationSegment(
                label = label,
                hoursLabel = formatDurationLabel(resources, value),
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
        Triple(YummyUserList.Watching.yummyListLabel(resources), sumFor("watch", "watching", "смотр"), YummyUserList.Watching.yummyListColor()),
        Triple(YummyUserList.Planned.yummyListLabel(resources), sumFor("plan", "planned", "план"), YummyUserList.Planned.yummyListColor()),
        Triple(YummyUserList.Completed.yummyListLabel(resources), sumFor("complete", "completed", "просмотр"), YummyUserList.Completed.yummyListColor()),
        Triple(YummyUserList.Dropped.yummyListLabel(resources), sumFor("drop", "dropped", "брош"), YummyUserList.Dropped.yummyListColor()),
        Triple(YummyUserList.OnHold.yummyListLabel(resources), sumFor("hold", "on hold", "отлож"), YummyUserList.OnHold.yummyListColor()),
    )

    return values.map { (label, value, color) ->
        DurationSegment(
            label = label,
            hoursLabel = formatDurationLabel(resources, value),
            value = value,
            color = color,
        )
    }
}

private fun buildSiteWatchSegments(
    resources: Resources,
    watchSums: List<YummyProfileWatchSum>,
): List<DurationSegment> {
    val totals = linkedMapOf(
        "Serials" to 0L,
        "S.Films" to 0L,
        "ONA" to 0L,
        "OVA" to 0L,
        "Movie" to 0L,
        "Special" to 0L,
    )

    watchSums.forEach { sum ->
        val bucket = sum.resolveSiteWatchBucket() ?: return@forEach
        totals[bucket] = totals.getValue(bucket) + (sum.spentTime ?: 0L)
    }

    val colors = mapOf(
        "Serials" to Color(0xFFA06AE0),
        "S.Films" to Color(0xFFF0C419),
        "ONA" to Color(0xFFFF9F1C),
        "OVA" to Color(0xFFFF646B),
        "Movie" to Color(0xFFC24ED3),
        "Special" to Color(0xFF737373),
    )

    val labels = mapOf(
        "Serials" to "TV-Сериалы",
        "S.Films" to "К.фильмы",
        "ONA" to "ONA",
        "OVA" to "OVA",
        "Movie" to resources.getString(R.string.details_type_movie),
        "Special" to resources.getString(R.string.details_type_special),
    )

    return totals.map { (bucket, value) ->
        DurationSegment(
            label = labels.getValue(bucket),
            hoursLabel = formatDurationLabelForUi(value),
            value = value,
            color = colors.getValue(bucket),
        )
    }
}

private fun buildGenreSegments(
    cachedLibraryMetadata: List<Anime>,
): List<DistributionSegment> {
    val counts = cachedLibraryMetadata
        .flatMap { anime -> anime.genres.map(String::trim).filter(String::isNotBlank).distinct() }
        .groupingBy { it }
        .eachCount()

    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(6)
        .mapIndexed { index, entry ->
            DistributionSegment(
                label = entry.key,
                count = entry.value,
                color = genrePalette[index % genrePalette.size],
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

private fun formatDurationLabel(resources: Resources, rawDuration: Long): String {
    if (rawDuration <= 0L) return localizedHourLabel(resources, "0")
    val secondsBasedHours = rawDuration / 3600.0
    val minutesBasedHours = rawDuration / 60.0
    val hours = when {
        rawDuration >= 10_000L -> secondsBasedHours
        rawDuration >= 180L -> minutesBasedHours
        else -> secondsBasedHours
    }
    return if (hours >= 10) {
        localizedHourLabel(resources, hours.toInt().toString())
    } else {
        localizedHourLabel(resources, String.format(Locale.US, "%.1f", hours))
    }
}

private fun formatDurationLabelForUi(rawDuration: Long): String {
    if (rawDuration <= 0L) return "0 ч"
    val secondsBasedHours = rawDuration / 3600.0
    val minutesBasedHours = rawDuration / 60.0
    val hours = when {
        rawDuration >= 10_000L -> secondsBasedHours
        rawDuration >= 180L -> minutesBasedHours
        else -> secondsBasedHours
    }
    return if (hours >= 10) {
        "${hours.toInt()} ч"
    } else {
        "${String.format(Locale.US, "%.1f", hours)} ч"
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

private fun formatEpochDateShort(resources: Resources, value: Long): String {
    val date = epochToLocalDate(value)
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    return when {
        daysAgo <= 0 -> resources.getString(R.string.yummy_account_date_today)
        daysAgo == 1 -> resources.getString(R.string.yummy_account_date_yesterday)
        daysAgo < 7 -> resources.getString(R.string.yummy_account_date_days_ago_short, daysAgo)
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

internal fun formatEpochDateCompact(value: Long): String {
    return epochToLocalDate(value).format(DateTimeFormatter.ofPattern("dd.MM.yy", Locale.getDefault()))
}

private fun localizedHourLabel(resources: Resources, value: String): String {
    return resources.getString(R.string.yummy_account_duration_hours_short, value)
}

private fun formatYummyRating(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

private fun YummyProfileWatchSum.resolveSiteWatchBucket(): String? {
    val bucket = listOf(alias, shortName, name)
        .filterNotNull()
        .joinToString(" ")
        .lowercase(Locale.ROOT)
        .replace(".", " ")
        .replace("_", " ")
    return when {
        bucket.contains("short film") ||
            bucket.contains("s films") ||
            bucket.contains("sfilms") ||
            bucket.contains("короткометраж") -> "S.Films"
        bucket.contains("ona") -> "ONA"
        bucket.contains("ova") -> "OVA"
        bucket.contains("special") || bucket.contains("спеш") -> "Special"
        bucket.contains("movie") || bucket.contains("film") || bucket.contains("фильм") -> "Movie"
        bucket.contains("tv") || bucket.contains("series") || bucket.contains("serial") || bucket.contains("сериал") -> "Serials"
        else -> null
    }
}

internal data class YummyProfileSnapshot(
    val watchTimeLabel: String,
    val siteWatchTimeLabel: String,
    val streakDays: Int,
    val streakLabel: String,
    val activeDaysCount: Int,
    val totalEpisodes: Int,
    val favoriteCount: Int,
    val favoriteHoursLabel: String,
    val favoriteDurationSeconds: Long,
    val libraryTotal: Int,
    val distributionSegments: List<DistributionSegment>,
    val siteWatchSegments: List<DurationSegment>,
    val durationSegments: List<DurationSegment>,
    val activityDays: List<ActivityDay>,
    val recentLibraryItems: List<RecentLibraryItem>,
    val onlineDaysLabel: String,
    val genreSegments: List<DistributionSegment>,
    val genreTrackedTitlesCount: Int,
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
private val genrePalette = listOf(
    Color(0xFF48D67B),
    Color(0xFFF7BC16),
    Color(0xFFA56CE3),
    Color(0xFFFF646B),
    Color(0xFFC24ED3),
    Color(0xFF737373),
)
