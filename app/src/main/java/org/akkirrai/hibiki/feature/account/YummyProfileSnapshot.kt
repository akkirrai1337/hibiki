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
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserListWatchStat
import org.akkirrai.animeresolver.metadata.YummyUserList
import org.akkirrai.hibiki.core.design.yummyFavoriteListColor
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.profile.LocalProfileData
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.design.yummyListColor
import org.akkirrai.hibiki.core.design.yummyListLabel

internal fun buildProfileSnapshot(
    resources: Resources,
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
    listWatchStats: List<YummyUserListWatchStat>,
    libraryMetadata: List<Anime>,
    localData: LocalProfileData,
): YummyProfileSnapshot {
    val progress = localData.episodeProgress
    val localActivityCounts = progress
        .filter { it.positionMs > 0L }
        .groupBy { epochToLocalDate(it.updatedAt) }
        .mapValues { (_, items) -> items.distinctBy { "${it.titleId}:${it.episodeId}" }.size }
    val activityCounts = (localData.importedActivityCounts + localActivityCounts) + localData.activityOverrides
    val today = LocalDate.now()
    val activityDays = (0 until ACTIVITY_HISTORY_DAYS).map { offset ->
        val date = today.minusDays((ACTIVITY_HISTORY_DAYS - 1 - offset).toLong())
        ActivityDay(date.format(ACTIVITY_DATE_FORMATTER), activityCounts[date] ?: 0)
    }
    val remoteDuration = profile.watches?.sum.orEmpty().sumOf { it.spentTime ?: 0L }
        .takeIf { it > 0L } ?: profile.watches?.history.orEmpty().sumOf { it.duration ?: 0L }
    val localDuration = progress.sumOf { it.positionMs.coerceAtLeast(0L) }
    val totalDuration = maxOf(normalizeRemoteDuration(remoteDuration), localDuration)
    val sourceSegments = buildHybridSourceSegments(resources, listWatchStats, progress)
    val remoteRecentItems = libraryItems.asSequence()
        .filter { !it.title.isBlank() && it.addedAt != null }
        .map { item -> RecentLibraryItem(item.title, item.posterUrl, item.yummyRating?.let(::formatRating), item.list.yummyListLabel(resources), formatEpochDateShort(resources, requireNotNull(item.addedAt)), item.list.yummyListColor()) }
        .toList()
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
    val recentItems = (remoteRecentItems + localRecentItems).distinctBy { it.title }.take(6)
    val librarySegments = YummyUserList.entries.map { list ->
        DistributionSegment(list.yummyListLabel(resources), libraryItems.count { it.list == list }, list.yummyListColor())
    } + LibraryCategory.entries.map { category ->
        DistributionSegment(
            label = resources.getString(category.labelResId),
            count = localData.library.count { category in it.categories && it.anime.id !in libraryItems.map { remote -> remote.animeId.toString() } },
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
        activeDaysCount = activityCounts.size,
        totalEpisodes = maxOf(profile.watches?.history.orEmpty().sumOf { it.episodeCount ?: 0 }, progress.count { it.positionMs > 0L }),
        libraryTotal = (libraryItems.map { it.animeId.toString() } + localData.library.map { it.id }).distinct().size,
        siteWatchSegments = sourceSegments,
        libraryStatusSegments = librarySegments,
        activityDays = activityDays,
        recentLibraryItems = recentItems,
        genreSegments = genreSegments,
        genreTrackedTitlesCount = allMetadata.count { it.genres.isNotEmpty() },
    )
}

private fun buildHybridSourceSegments(resources: Resources, remoteStats: List<YummyUserListWatchStat>, progress: List<EpisodeWatchProgress>): List<DurationSegment> {
    val remote = YummyUserList.entries.map { list ->
        val seconds = remoteStats.filter { it.rawListId == list.value }.sumOf { it.seconds }
        DurationSegment(list.yummyListLabel(resources), formatDurationLabelForUi(seconds * 1_000L), seconds * 1_000L, list.yummyListColor())
    }
    val local = progress
    .groupBy { it.sourceTitle.ifBlank { it.sourceId } }
    .map { (source, items) -> DurationSegment(source, formatDurationLabelForUi(items.sumOf { it.positionMs }), items.sumOf { it.positionMs }, Color(0xFF5DA9FF)) }
    return (remote + local).filter { it.value > 0L }.sortedByDescending(DurationSegment::value)
}

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

private fun epochToLocalDate(value: Long): LocalDate = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
private fun formatEpochDateShort(resources: Resources, value: Long): String {
    val date = epochToLocalDate(value)
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    return when { daysAgo <= 0 -> resources.getString(R.string.yummy_account_date_today); daysAgo == 1 -> resources.getString(R.string.yummy_account_date_yesterday); daysAgo < 7 -> resources.getString(R.string.yummy_account_date_days_ago_short, daysAgo); else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }
}
internal fun formatEpochDateCompact(value: Long): String = epochToLocalDate(value).format(DateTimeFormatter.ofPattern("dd.MM.yy", Locale.getDefault()))
private fun formatDurationLabel(resources: Resources, durationMs: Long): String = resources.getString(R.string.yummy_account_duration_hours_short, if (durationMs <= 0) "0" else String.format(Locale.US, "%.1f", durationMs / 3_600_000.0))
private fun formatDurationLabelForUi(durationMs: Long): String = if (durationMs <= 0) "0 h" else String.format(Locale.US, "%.1f h", durationMs / 3_600_000.0)
private fun formatRating(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)
private fun normalizeRemoteDuration(value: Long): Long = if (value >= 10_000L) value * 1_000L else value * 60_000L

internal data class YummyProfileSnapshot(val watchTimeLabel: String, val siteWatchTimeLabel: String, val activeDaysCount: Int, val totalEpisodes: Int, val libraryTotal: Int, val siteWatchSegments: List<DurationSegment>, val libraryStatusSegments: List<DistributionSegment>, val activityDays: List<ActivityDay>, val recentLibraryItems: List<RecentLibraryItem>, val genreSegments: List<DistributionSegment>, val genreTrackedTitlesCount: Int)
internal data class DistributionSegment(val label: String, val count: Int, val color: Color)
internal data class DurationSegment(val label: String, val hoursLabel: String, val value: Long, val color: Color)
internal data class ActivityDay(val dateLabel: String, val episodeCount: Int)
internal data class RecentLibraryItem(val title: String, val posterUrl: String?, val ratingLabel: String?, val statusLabel: String, val dateLabel: String, val color: Color)

private const val ACTIVITY_HISTORY_DAYS = 90
private val ACTIVITY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM")
private val genrePalette = listOf(Color(0xFF48D67B), Color(0xFFF7BC16), Color(0xFFA56CE3), Color(0xFFFF646B), Color(0xFFC24ED3), Color(0xFF737373))
