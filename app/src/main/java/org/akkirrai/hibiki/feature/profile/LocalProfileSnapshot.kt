package org.akkirrai.hibiki.feature.profile

import android.content.res.Resources
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.formatDurationHours
import org.akkirrai.hibiki.shared.profile.ActivityDay
import org.akkirrai.hibiki.shared.profile.LocalProfileSnapshot
import org.akkirrai.hibiki.shared.profile.buildGenreSegments
import org.akkirrai.hibiki.shared.profile.profileGenreTrackedTitlesCount
import org.akkirrai.hibiki.shared.profile.buildLibraryStatusSegments
import org.akkirrai.hibiki.shared.profile.trackedProfileLibraryItems
import org.akkirrai.hibiki.shared.profile.buildFavoriteLibraryItems
import org.akkirrai.hibiki.shared.profile.buildRecentLibraryItems
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.labelResId

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
            activityByDate[date.toString()]?.let { activity ->
                activity.completedEpisodes.takeIf { it > 0 } ?: if (activity.watchedMs > 0L) 1 else 0
            } ?: 0,
        )
    }
    val trackedLibrary = localData.trackedProfileLibraryItems()
    val librarySegments = localData.buildLibraryStatusSegments { category ->
        resources.getString(category.labelResId)
    }
    val localRecentItems = localData.buildRecentLibraryItems(
        statusLabel = { category -> resources.getString(category.labelResId) },
        dateLabel = { value -> formatEpochDateShort(resources, value) },
    )
    val favoriteItems = localData.buildFavoriteLibraryItems(
        statusLabel = resources.getString(LibraryCategory.Favorite.labelResId),
        dateLabel = { value -> formatEpochDateShort(resources, value) },
    )
    val genreSegments = localData.buildGenreSegments()

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
        genreTrackedTitlesCount = localData.profileGenreTrackedTitlesCount(),
    )
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
        else -> date.format(
            DateTimeFormatter.ofPattern(
                "d MMM",
                resources.configuration.locales[0] ?: Locale.getDefault(),
            )
        )
    }
}

internal fun formatDurationLabel(resources: Resources, durationMs: Long): String = resources.getString(
    R.string.local_profile_duration_hours_short,
    formatDurationHours(durationMs),
)

private const val ACTIVITY_HISTORY_DAYS = 30
private val ACTIVITY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM")
