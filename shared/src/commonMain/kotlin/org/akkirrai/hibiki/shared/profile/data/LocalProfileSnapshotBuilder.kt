package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryCategory

data class LocalProfileSnapshotLabels(
    val durationLabel: (Long) -> String,
    val categoryLabel: (LibraryCategory) -> String,
    val dateLabel: (Long) -> String,
    val activityDateLabel: (String) -> String,
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
