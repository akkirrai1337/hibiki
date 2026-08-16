package org.akkirrai.hibiki.shared.profile

import androidx.compose.ui.graphics.Color

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
