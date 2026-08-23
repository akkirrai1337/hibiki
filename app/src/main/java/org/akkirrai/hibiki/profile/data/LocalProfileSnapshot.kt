package org.akkirrai.hibiki.profile

import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.library.LibraryCategory

data class LocalProfileSnapshot(
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

data class DistributionSegment(
    val label: String,
    val count: Int,
    val color: Color,
)

data class ActivityDay(
    val dateLabel: String,
    val episodeCount: Int,
)

data class ProfileActivitySummary(
    val activeDaysCount: Int,
    val totalEpisodes: Int,
    val watchedMs: Long,
)

data class RecentLibraryItem(
    val title: String,
    val posterUrl: String?,
    val ratingLabel: String?,
    val statusLabel: String,
    val dateLabel: String,
    val color: Color,
)

data class LocalProfileSnapshotLabels(
    val durationLabel: (Long) -> String,
    val categoryLabel: (LibraryCategory) -> String,
    val dateLabel: (Long) -> String,
    val activityDateLabel: (String) -> String,
)

fun LocalProfileData.activitySummary(): ProfileActivitySummary = ProfileActivitySummary(
    activeDaysCount = activity.count { it.completedEpisodes > 0 || it.watchedMs > 0L },
    totalEpisodes = activity.sumOf { it.completedEpisodes },
    watchedMs = activity.sumOf { it.watchedMs },
)

fun buildLocalProfileSnapshot(
    data: LocalProfileData,
    activityDateStrings: List<String>,
    labels: LocalProfileSnapshotLabels,
): LocalProfileSnapshot {
    val activitySummary = data.activitySummary()
    val trackedLibrary = data.trackedProfileLibraryItems()

    return LocalProfileSnapshot(
        watchTimeLabel = labels.durationLabel(activitySummary.watchedMs),
        activeDaysCount = activitySummary.activeDaysCount,
        totalEpisodes = activitySummary.totalEpisodes,
        libraryTotal = trackedLibrary.size,
        libraryStatusSegments = data.buildLibraryStatusSegments(labels.categoryLabel),
        activityDays = buildProfileActivityDays(
            activity = data.activity,
            dateStrings = activityDateStrings,
            dateLabel = labels.activityDateLabel,
        ),
        recentLibraryItems = data.buildRecentLibraryItems(
            statusLabel = labels.categoryLabel,
            dateLabel = labels.dateLabel,
        ),
        favoriteLibraryItems = data.buildFavoriteLibraryItems(
            statusLabel = labels.categoryLabel(LibraryCategory.Favorite),
            dateLabel = labels.dateLabel,
        ),
        genreSegments = data.buildGenreSegments(),
        genreTrackedTitlesCount = data.profileGenreTrackedTitlesCount(),
    )
}

fun LocalProfileData.buildRecentLibraryItems(
    statusLabel: (LibraryCategory) -> String,
    dateLabel: (Long) -> String,
): List<RecentLibraryItem> = library
    .asSequence()
    .filter { it.addedAt != null && it.anime.title.isNotBlank() }
    // Recent is an auto-assigned bookkeeping flag (set the moment playback starts), not a real
    // category the user chose -- an entry that's only Recent was never actually added to the
    // library and shouldn't show up here (it would otherwise fall through
    // primaryLibraryCategory()'s Recent/Saved exclusion and get mislabeled as "Saved").
    .filter { it.categories.any { category -> category != LibraryCategory.Recent } }
    .sortedByDescending { it.addedAt }
    .map { item ->
        val category = item.categories.primaryLibraryCategory()
        RecentLibraryItem(
            title = item.anime.title,
            posterUrl = item.anime.posterUrl,
            ratingLabel = item.anime.ratings.firstOrNull()?.value?.let(::formatProfileRating),
            statusLabel = statusLabel(category),
            dateLabel = dateLabel(requireNotNull(item.addedAt)),
            color = category.profileColor(),
        )
    }
    .distinctBy(RecentLibraryItem::title)
    .take(5)
    .toList()

fun LocalProfileData.buildFavoriteLibraryItems(
    statusLabel: String,
    dateLabel: (Long) -> String,
): List<RecentLibraryItem> = library
    .asSequence()
    .filter { LibraryCategory.Favorite in it.categories && it.anime.title.isNotBlank() }
    .sortedByDescending { it.addedAt ?: 0L }
    .take(6)
    .map { item ->
        RecentLibraryItem(
            title = item.anime.title,
            posterUrl = item.anime.posterUrl,
            ratingLabel = item.anime.ratings.firstOrNull()?.value?.let(::formatProfileRating),
            statusLabel = statusLabel,
            dateLabel = item.addedAt?.let(dateLabel).orEmpty(),
            color = LibraryCategory.Favorite.profileColor(),
        )
    }
    .toList()

fun buildProfileActivityDays(
    activity: List<DailyWatchActivity>,
    dateStrings: List<String>,
    dateLabel: (String) -> String,
): List<ActivityDay> {
    val activityByDate = activity.associateBy(DailyWatchActivity::date)
    return dateStrings.map { date ->
        ActivityDay(
            dateLabel = dateLabel(date),
            episodeCount = activityByDate[date]?.let { entry ->
                entry.completedEpisodes.takeIf { it > 0 } ?: if (entry.watchedMs > 0L) 1 else 0
            } ?: 0,
        )
    }
}

fun List<DistributionSegment>.toProfileGenreBarItems(): List<ProfileGenreBarItem> = map { segment ->
    ProfileGenreBarItem(
        label = segment.label,
        count = segment.count,
        color = segment.color,
    )
}

fun List<ActivityDay>.toProfileActivityBarItems(): List<ProfileActivityBarItem> = map { day ->
    ProfileActivityBarItem(
        dateLabel = day.dateLabel,
        episodeCount = day.episodeCount,
    )
}
