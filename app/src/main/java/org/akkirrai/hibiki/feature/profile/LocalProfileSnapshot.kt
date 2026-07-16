package org.akkirrai.hibiki.feature.profile

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.profile.LocalProfileData
import org.akkirrai.hibiki.core.source.LibraryCategory

internal fun buildProfileSnapshot(
    resources: Resources,
    localData: LocalProfileData,
): LocalProfileSnapshot {
    val activityByDate = localData.activity.associateBy { it.date }
    val today = LocalDate.now()
    val activityDays = (0 until ACTIVITY_HISTORY_DAYS).map { offset ->
        val date = today.minusDays((ACTIVITY_HISTORY_DAYS - 1 - offset).toLong())
        ActivityDay(
            date.format(ACTIVITY_DATE_FORMATTER),
            activityByDate[date]?.let { activity ->
                activity.completedEpisodes.takeIf { it > 0 } ?: if (activity.watchedMs > 0L) 1 else 0
            } ?: 0,
        )
    }
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
    val localRecentItems = localData.library
        .asSequence()
        .filter { it.addedAt != null && it.anime.title.isNotBlank() }
        .sortedByDescending { it.addedAt }
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
        }
        .distinctBy(RecentLibraryItem::title)
        .take(5)
        .toList()
    val favoriteItems = localData.library
        .asSequence()
        .filter { LibraryCategory.Favorite in it.categories && it.anime.title.isNotBlank() }
        .sortedByDescending { it.addedAt ?: 0L }
        .take(6)
        .map { item ->
            RecentLibraryItem(
                title = item.anime.title,
                posterUrl = item.anime.posterUrl,
                ratingLabel = item.anime.ratings.firstOrNull()?.value?.let(::formatRating),
                statusLabel = resources.getString(LibraryCategory.Favorite.labelResId),
                dateLabel = item.addedAt?.let { formatEpochDateShort(resources, it) }.orEmpty(),
                color = LibraryCategory.Favorite.color(),
            )
        }
        .toList()
    val allMetadata = localData.library.map { it.anime }.distinctBy(Anime::id)
    val genreSegments = allMetadata.flatMap { it.genres }.groupingBy { it.trim() }
        .eachCount()
        .filterKeys(String::isNotBlank)
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(6)
        .mapIndexed { index, entry ->
            DistributionSegment(entry.key, entry.value, genrePalette[index % genrePalette.size])
        }

    return LocalProfileSnapshot(
        watchTimeLabel = formatDurationLabel(resources, localData.activity.sumOf { it.watchedMs }),
        activeDaysCount = localData.activity.count { it.completedEpisodes > 0 || it.watchedMs > 0L },
        totalEpisodes = localData.activity.sumOf { it.completedEpisodes },
        libraryTotal = trackedLibrary.size,
        libraryStatusSegments = librarySegments,
        activityDays = activityDays,
        recentLibraryItems = localRecentItems,
        favoriteLibraryItems = favoriteItems,
        genreSegments = genreSegments,
        genreTrackedTitlesCount = allMetadata.count { it.genres.isNotEmpty() },
    )
}

private fun Set<LibraryCategory>.primaryCategory(): LibraryCategory =
    LibraryCategory.entries.firstOrNull { it != LibraryCategory.Saved && it in this } ?: LibraryCategory.Saved

private fun LibraryCategory.color(): Color = when (this) {
    LibraryCategory.Watching -> Color(0xFF3DDC84)
    LibraryCategory.Planned -> Color(0xFF5DA9FF)
    LibraryCategory.Completed -> Color(0xFFFFB84D)
    LibraryCategory.Dropped -> Color(0xFFFF6B6B)
    LibraryCategory.OnHold -> Color(0xFFC593FF)
    LibraryCategory.Favorite -> Color(0xFFFFB86A)
    LibraryCategory.Saved -> Color(0xFF9EA4B2)
}

internal fun normalizePosterUrl(rawUrl: String?): String? = rawUrl?.trim()?.takeIf {
    it.startsWith("http://", true) || it.startsWith("https://", true)
}

private fun epochToLocalDate(value: Long): LocalDate {
    val instant = if (value in 1 until 1_000_000_000_000L) Instant.ofEpochSecond(value) else Instant.ofEpochMilli(value)
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun formatEpochDateShort(resources: Resources, value: Long): String {
    val date = epochToLocalDate(value)
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    return when {
        daysAgo <= 0 -> resources.getString(R.string.local_profile_date_today)
        daysAgo == 1 -> resources.getString(R.string.local_profile_date_yesterday)
        daysAgo < 7 -> resources.getString(R.string.local_profile_date_days_ago_short, daysAgo)
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

internal fun formatDurationLabel(resources: Resources, durationMs: Long): String = resources.getString(
    R.string.local_profile_duration_hours_short,
    if (durationMs <= 0) "0" else String.format(Locale.US, "%.1f", durationMs / 3_600_000.0),
)

private fun formatRating(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

internal data class LocalProfileSnapshot(
    val watchTimeLabel: String,
    val activeDaysCount: Int,
    val totalEpisodes: Int,
    val libraryTotal: Int,
    val libraryStatusSegments: List<DistributionSegment>,
    val activityDays: List<ActivityDay>,
    val recentLibraryItems: List<RecentLibraryItem>,
    val favoriteLibraryItems: List<RecentLibraryItem>,
    val genreSegments: List<DistributionSegment>,
    val genreTrackedTitlesCount: Int,
)
internal data class DistributionSegment(val label: String, val count: Int, val color: Color)
internal data class ActivityDay(val dateLabel: String, val episodeCount: Int)
internal data class RecentLibraryItem(val title: String, val posterUrl: String?, val ratingLabel: String?, val statusLabel: String, val dateLabel: String, val color: Color)

private const val ACTIVITY_HISTORY_DAYS = 30
private val ACTIVITY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM")
private val genrePalette = listOf(Color(0xFF48D67B), Color(0xFFF7BC16), Color(0xFFA56CE3), Color(0xFFFF646B), Color(0xFFC24ED3), Color(0xFF737373))
private val PROFILE_LIBRARY_CATEGORIES = listOf(LibraryCategory.Watching, LibraryCategory.Planned, LibraryCategory.Completed, LibraryCategory.Dropped, LibraryCategory.OnHold, LibraryCategory.Favorite)
