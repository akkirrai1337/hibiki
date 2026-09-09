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
import org.akkirrai.hibiki.core.profile.ProfileRules
import org.akkirrai.hibiki.core.source.LibraryCategory

internal fun buildProfileSnapshot(
    resources: Resources,
    localData: LocalProfileData,
): LocalProfileSnapshot {
    val activityByDate = localData.activity.associateBy { it.date }
    val today = LocalDate.now()
    val activityDays = (0 until ACTIVITY_HISTORY_DAYS).map { offset ->
        val date = today.minusDays((ACTIVITY_HISTORY_DAYS - 1 - offset).toLong())
        val activity = activityByDate[date]
        ActivityDay(
            dateLabel = date.format(ACTIVITY_DATE_FORMATTER),
            episodeCount = activity?.let {
                it.completedEpisodes.takeIf { count -> count > 0 } ?: if (it.watchedMs > 0L) 1 else 0
            } ?: 0,
            // Kept alongside the count for the day's detail popup, which reports minutes the way
            // the desktop's tooltip does. The bar height still comes from the episode count.
            watchedMs = activity?.watchedMs ?: 0L,
        )
    }
    // Achievements count every library entry, matching the desktop - including one saved but not
    // yet filed under a status. The tracked list below is a narrower thing: what the status
    // breakdown and its donut are about.
    val allLibrary = localData.library
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
                anime = item.anime,
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

    // XP, level, streak and achievements all come from ProfileRules, which is held to the same
    // generated vectors as the desktop implementation (see ProfileRulesTest) - so a given history
    // produces the same level on both. Nothing about them is stored: they are read off the library
    // and activity that already exist for the stat cards above.
    val lifetimeWatchedMs = localData.activity.sumOf { it.watchedMs }
    val ruleEntries = allLibrary.map { item ->
        ProfileRules.Entry(
            // The desktop's library rows carry one category each; here an item can sit in several,
            // and "completed" is the only one the rules ask about.
            category = if (LibraryCategory.Completed in item.categories) "completed" else "other",
            genres = item.anime.genres,
        )
    }
    // The same series the activity chart draws, as plain active/inactive days - the streak rules
    // care only whether a day happened, and reusing it keeps the badge and the chart from ever
    // telling different stories.
    val streakDays = activityDays.map { ProfileRules.ActivityDay(active = it.episodeCount > 0) }
    val streak = ProfileRules.computeStreaks(streakDays)
    // The badge shows the run inside the visible 30-day window; the achievement asks for the best
    // run ever, which is what the desktop feeds it. A month-long streak finished in spring has
    // been earned, and the chart scrolling past it does not unearn it.
    val lifetimeStreakDays = buildLifetimeActivityDays(localData, today)
    val bestStreakEver = maxOf(ProfileRules.computeStreaks(lifetimeStreakDays).best, streak.best)
    // Ordered by how far along each family is, not by the fixed order the rules declare them in:
    // what is nearly done, or done, is what the strip should lead with, and the seven families are
    // otherwise in an order that means nothing to anyone reading them. Sorted here rather than in
    // ProfileRules, whose order is part of what the shared vectors pin.
    val achievements = ProfileRules.computeAchievements(ruleEntries, lifetimeWatchedMs, bestStreakEver)
        .sortedWith(
            compareByDescending<ProfileRules.Achievement> { it.level }
                .thenByDescending { if (it.target > 0) it.current / it.target else 0.0 }
        )
    val level = ProfileRules.computeLevelProgress(ProfileRules.totalXpEarned(achievements, lifetimeWatchedMs))

    return LocalProfileSnapshot(
        level = level,
        streak = streak,
        achievements = achievements,
        watchTimeLabel = formatDurationLabel(resources, lifetimeWatchedMs),
        activeDaysCount = localData.activity.count { it.completedEpisodes > 0 || it.watchedMs > 0L },
        totalEpisodes = localData.activity.sumOf { it.completedEpisodes },
        libraryTotal = trackedLibrary.size,
        libraryStatusSegments = librarySegments,
        activityDays = activityDays,
        recentLibraryItems = localRecentItems,
        genreSegments = genreSegments,
        genreTrackedTitlesCount = allMetadata.count { it.genres.isNotEmpty() },
    )
}

/**
 * Every day from the first one ever recorded to today, active or not.
 *
 * The gaps have to be filled in: stored activity only has rows for days something happened, and
 * feeding those straight to the streak rules would let a year of silence read as one unbroken run.
 */
private fun buildLifetimeActivityDays(
    localData: LocalProfileData,
    today: LocalDate,
): List<ProfileRules.ActivityDay> {
    val active = localData.activity
        .filter { it.completedEpisodes > 0 || it.watchedMs > 0L }
        .map { it.date }
        .toSet()
    val firstDay = active.minOrNull() ?: return emptyList()
    val span = ChronoUnit.DAYS.between(firstDay, today)
    if (span < 0) return emptyList()
    return (0..span).map { offset ->
        ProfileRules.ActivityDay(active = firstDay.plusDays(offset) in active)
    }
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
    if (durationMs <= 0) "0" else String.format(Locale.US, "%.1f", durationMs / 3_600_000.0),
)

private fun formatRating(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

internal data class LocalProfileSnapshot(
    val level: ProfileRules.LevelProgress,
    val streak: ProfileRules.StreakInfo,
    val achievements: List<ProfileRules.Achievement>,
    val watchTimeLabel: String,
    val activeDaysCount: Int,
    val totalEpisodes: Int,
    val libraryTotal: Int,
    val libraryStatusSegments: List<DistributionSegment>,
    val activityDays: List<ActivityDay>,
    val recentLibraryItems: List<RecentLibraryItem>,
    val genreSegments: List<DistributionSegment>,
    val genreTrackedTitlesCount: Int,
)
internal data class DistributionSegment(val label: String, val count: Int, val color: Color)
internal data class ActivityDay(val dateLabel: String, val episodeCount: Int, val watchedMs: Long)
internal data class RecentLibraryItem(val anime: Anime, val title: String, val posterUrl: String?, val ratingLabel: String?, val statusLabel: String, val dateLabel: String, val color: Color)

private const val ACTIVITY_HISTORY_DAYS = 30
private val ACTIVITY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM")
private val genrePalette = listOf(Color(0xFF48D67B), Color(0xFFF7BC16), Color(0xFFA56CE3), Color(0xFFFF646B), Color(0xFFC24ED3), Color(0xFF737373))
private val PROFILE_LIBRARY_CATEGORIES = listOf(LibraryCategory.Watching, LibraryCategory.Planned, LibraryCategory.Completed, LibraryCategory.Dropped, LibraryCategory.OnHold, LibraryCategory.Favorite)
