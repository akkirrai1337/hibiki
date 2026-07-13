package org.akkirrai.hibiki.feature.account

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.akkirrai.hibiki.R
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.hibiki.core.design.yummyFavoriteListColor
import org.akkirrai.hibiki.core.profile.LocalProfileData
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.model.Anime

internal fun buildProfileSnapshot(
    resources: Resources,
    profile: YummyProfile,
    libraryMetadata: List<Anime>,
    localData: LocalProfileData,
): YummyProfileSnapshot {
    // Profile activity is deliberately remote-only. Local playback is still stored by
    // WatchStateRepository, but mixing it here would produce a statistic that exists nowhere.
    val activityCounts = profile.watches?.history.orEmpty()
        .filter { (it.duration ?: 0L) > 0L || (it.episodeCount ?: 0) > 0 }
        .mapNotNull { item -> item.date?.let(::epochToLocalDate)?.let { it to (item.episodeCount ?: 1).coerceAtLeast(1) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, counts) -> counts.sum() }
    val today = LocalDate.now()
    val activityDays = (0 until ACTIVITY_HISTORY_DAYS).map { offset ->
        val date = today.minusDays((ACTIVITY_HISTORY_DAYS - 1 - offset).toLong())
        ActivityDay(date.format(ACTIVITY_DATE_FORMATTER), activityCounts[date] ?: 0)
    }
    val sourceSegments = buildSiteWatchTypeSegments(resources, profile)
    val totalDuration = sourceSegments.sumOf(DurationSegment::value).takeIf { it > 0L }
        ?: profile.watches?.history.orEmpty().sumOf { it.duration ?: 0L }.coerceAtLeast(0L) * 1_000L
    val localRecentItems = localData.library
        .asSequence()
        .filter { it.addedAt != null && it.anime.title.isNotBlank() }
        .sortedByDescending { it.addedAt }
        .take(6)
        .map { item ->
            val category = item.categories.primaryCategory()
            RecentLibraryItem(
                title = item.anime.title,
                posterUrl = item.anime.posterUrl,
                ratingLabel = item.anime.ratings.firstOrNull()?.value?.let(::formatRating),
                statusLabel = resources.getString(category.labelResId),
                dateLabel = formatEpochDateShort(resources, requireNotNull(item.addedAt)),
                color = category.color(),
            )
        }.toList()
    val recentItems = localRecentItems.distinctBy { it.title }.take(6)
    val trackedLibrary = localData.library.filter { item ->
        item.categories.any(PROFILE_LIBRARY_CATEGORIES::contains)
    }
    val librarySegments = PROFILE_LIBRARY_CATEGORIES.map { category ->
        DistributionSegment(
            label = resources.getString(category.labelResId),
            count = trackedLibrary.count { category in it.categories },
            color = category.color(),
        )
    }
    val allMetadata = (libraryMetadata + localData.library.map { it.anime }).distinctBy(Anime::id)
    val genreCounts = allMetadata.flatMap { it.genres }.groupingBy { it.trim() }
        .eachCount().filterKeys(String::isNotBlank)
    val genreSegments = genreCounts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(6).mapIndexed { index, entry -> DistributionSegment(entry.key, entry.value, genrePalette[index % genrePalette.size]) }

    return YummyProfileSnapshot(
        watchTimeLabel = formatDurationLabel(resources, totalDuration),
        siteWatchTimeLabel = formatDurationLabel(resources, totalDuration),
        activeDaysCount = activityCounts.count { it.value > 0 },
        totalEpisodes = profile.watches?.history.orEmpty().sumOf { it.episodeCount ?: 0 },
        libraryTotal = trackedLibrary.size,
        siteWatchSegments = sourceSegments,
        libraryStatusSegments = librarySegments,
        activityDays = activityDays,
        recentLibraryItems = recentItems,
        genreSegments = genreSegments,
        genreTrackedTitlesCount = allMetadata.count { it.genres.isNotEmpty() },
    )
}

private fun buildSiteWatchTypeSegments(resources: Resources, profile: YummyProfile): List<DurationSegment> {
    return profile.watches?.sum.orEmpty().mapNotNull { item ->
        val alias = item.alias?.trim()?.lowercase(Locale.ROOT)
        val fallbackLabel = listOf(item.shortName, item.name, item.alias)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: return@mapNotNull null
        val label = when (alias) {
            "tv" -> resources.getString(R.string.yummy_account_watch_type_tv)
            "short-tv" -> resources.getString(R.string.yummy_account_watch_type_short_tv)
            "movie" -> resources.getString(R.string.yummy_account_watch_type_movie)
            "short-movie" -> resources.getString(R.string.yummy_account_watch_type_short_movie)
            "ona" -> "ONA"
            "ova" -> "OVA"
            "special" -> resources.getString(R.string.yummy_account_watch_type_special)
            else -> fallbackLabel
        }
        val duration = yummySpentTimeToMillis(item.spentTime ?: 0L)
        if (duration <= 0L) return@mapNotNull null
        DurationSegment(
            label = label,
            hoursLabel = if (duration < 3_600_000L) {
                resources.getString(R.string.yummy_account_duration_less_than_hour)
            } else {
                formatDurationLabel(resources, duration)
            },
            value = duration,
            color = watchTypeColor(alias),
        )
    }.sortedByDescending(DurationSegment::value)
}

private fun watchTypeColor(alias: String?): Color = when (alias) {
    "tv" -> Color(0xFFA56CE3)
    "short-movie" -> Color(0xFFF7BC16)
    "ona" -> Color(0xFFFF9F1C)
    "ova" -> Color(0xFFFF646B)
    "movie" -> Color(0xFFC24ED3)
    "special" -> Color(0xFF737373)
    "short-tv" -> Color(0xFF5DA9FF)
    else -> Color(0xFF48D67B)
}

/** YummyAnime profile watch aggregates and daily history expose durations in seconds. */
internal fun yummySpentTimeToMillis(value: Long): Long =
    value.coerceAtLeast(0L) * 1_000L

private fun Set<LibraryCategory>.primaryCategory(): LibraryCategory =
    LibraryCategory.entries.firstOrNull { it != LibraryCategory.Saved && it in this } ?: LibraryCategory.Saved

private fun LibraryCategory.color(): Color = when (this) {
    LibraryCategory.Watching -> Color(0xFF3DDC84)
    LibraryCategory.Planned -> Color(0xFF5DA9FF)
    LibraryCategory.Completed -> Color(0xFFFFB84D)
    LibraryCategory.Dropped -> Color(0xFFFF6B6B)
    LibraryCategory.OnHold -> Color(0xFFC593FF)
    LibraryCategory.Favorite -> yummyFavoriteListColor()
    LibraryCategory.Saved -> Color(0xFF9EA4B2)
}

internal fun normalizeYummyAssetUrl(rawUrl: String?): String? {
    val value = rawUrl?.trim()?.trim('"')?.replace("\\/", "/")?.takeIf(String::isNotBlank) ?: return null
    return when {
        value.startsWith("http://", true) || value.startsWith("https://", true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://ru.yummyani.me$value"
        else -> "https://ru.yummyani.me/$value"
    }
}

internal fun YummyProfile.resolvedAvatarUrl(): String? = normalizeYummyAssetUrl(
    avatarUrl ?: avatars.full ?: avatars.big ?: avatars.small,
)

private fun epochToLocalDate(value: Long): LocalDate {
    val instant = if (value in 1 until 1_000_000_000_000L) Instant.ofEpochSecond(value) else Instant.ofEpochMilli(value)
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
}
private fun formatEpochDateShort(resources: Resources, value: Long): String {
    val date = epochToLocalDate(value)
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    return when { daysAgo <= 0 -> resources.getString(R.string.yummy_account_date_today); daysAgo == 1 -> resources.getString(R.string.yummy_account_date_yesterday); daysAgo < 7 -> resources.getString(R.string.yummy_account_date_days_ago_short, daysAgo); else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }
}
internal fun formatEpochDateCompact(value: Long): String = epochToLocalDate(value).format(DateTimeFormatter.ofPattern("dd.MM.yy", Locale.getDefault()))
private fun formatDurationLabel(resources: Resources, durationMs: Long): String = resources.getString(R.string.yummy_account_duration_hours_short, if (durationMs <= 0) "0" else String.format(Locale.US, "%.1f", durationMs / 3_600_000.0))
private fun formatRating(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

internal data class YummyProfileSnapshot(val watchTimeLabel: String, val siteWatchTimeLabel: String, val activeDaysCount: Int, val totalEpisodes: Int, val libraryTotal: Int, val siteWatchSegments: List<DurationSegment>, val libraryStatusSegments: List<DistributionSegment>, val activityDays: List<ActivityDay>, val recentLibraryItems: List<RecentLibraryItem>, val genreSegments: List<DistributionSegment>, val genreTrackedTitlesCount: Int)
internal data class DistributionSegment(val label: String, val count: Int, val color: Color)
internal data class DurationSegment(val label: String, val hoursLabel: String, val value: Long, val color: Color)
internal data class ActivityDay(val dateLabel: String, val episodeCount: Int)
internal data class RecentLibraryItem(val title: String, val posterUrl: String?, val ratingLabel: String?, val statusLabel: String, val dateLabel: String, val color: Color)

private const val ACTIVITY_HISTORY_DAYS = 90
private val ACTIVITY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM")
private val genrePalette = listOf(Color(0xFF48D67B), Color(0xFFF7BC16), Color(0xFFA56CE3), Color(0xFFFF646B), Color(0xFFC24ED3), Color(0xFF737373))
private val PROFILE_LIBRARY_CATEGORIES = listOf(
    LibraryCategory.Watching,
    LibraryCategory.Planned,
    LibraryCategory.Completed,
    LibraryCategory.Dropped,
    LibraryCategory.OnHold,
    LibraryCategory.Favorite,
)
