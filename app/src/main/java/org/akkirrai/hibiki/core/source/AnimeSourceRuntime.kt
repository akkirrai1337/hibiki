package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.hibiki.app.settings.AppPreferences
import java.util.concurrent.ConcurrentHashMap

class AnimeSourceRuntime internal constructor(
    val descriptor: AnimeSourceDescriptor,
    val source: AnimeSource,
    private val localizeFilters: (AnimeSearchFilterCatalog, Boolean) -> AnimeSearchFilterCatalog,
    private val normalizeTitleId: (String) -> String,
) {
    private val latestSource = source as? LatestSource
    private val playbackSource = source as? PlaybackSource

    val supportsPlayback: Boolean
        get() = playbackSource != null

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> =
        source.search(source.capabilities.adapt(request)).map(::scopeTitle)

    suspend fun search(query: String): List<AnimeTitle> = source.search(query).map(::scopeTitle)

    suspend fun details(id: String): AnimeTitle = source.getById(nativeId(id)).let(::scopeTitle)

    fun normalizeId(id: String): String = scopedId(nativeId(id))

    suspend fun latest(limit: Int): List<AnimeTitle> {
        return latestSource?.latest(limit)?.map(::scopeTitle).orEmpty()
    }

    suspend fun filterCatalog(preferEnglish: Boolean): AnimeSearchFilterCatalog =
        localizeFilters(source.getSearchFilterCatalog(), preferEnglish).sanitized(preferEnglish)

    internal suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> =
        playbackSource?.getPlaybackGroups(title.copy(id = nativeId(title.id))).orEmpty()

    internal suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = playbackSource?.getPlayerLinks(
        title = title.copy(id = nativeId(title.id)),
        group = group,
        episode = episode,
    ).orEmpty()

    private fun nativeId(id: String): String {
        val scoped = AnimeKey.parse(id)
        if (scoped != null) {
            require(scoped.sourceId == descriptor.id) {
                "Title $id belongs to ${scoped.sourceId}, not ${descriptor.id}"
            }
            return scoped.nativeId
        }
        return normalizeTitleId(id)
    }

    private fun scopedId(nativeId: String): String = AnimeKey(descriptor.id, nativeId).value

    private fun scopeTitle(title: AnimeTitle): AnimeTitle = title.copy(
        id = scopedId(title.id),
        similarAnime = title.similarAnime.map { it.copy(id = scopedId(it.id)) },
        franchiseAnime = title.franchiseAnime.map { it.copy(id = scopedId(it.id)) },
        relatedAnime = title.relatedAnime.map { it.copy(id = scopedId(it.id)) },
    )
}

class AnimeSourceRuntimeManager(
    context: Context,
    private val client: HttpClient,
) {
    private val appContext = context.applicationContext
    private val runtimes = ConcurrentHashMap<SourceId, AnimeSourceRuntime>()

    val selectedId: SourceId
        get() = AppPreferences.readState(appContext).animeSource

    fun current(): AnimeSourceRuntime = runtime(selectedId)

    fun forTitle(titleId: String): AnimeSourceRuntime =
        AnimeKey.parse(titleId)?.sourceId?.let(::runtime)
            // Unscoped identifiers were persisted by versions which only supported YummyAnime.
            ?: runtime(AppPreferences.DEFAULT_ANIME_SOURCE_ID)

    fun runtime(sourceId: SourceId): AnimeSourceRuntime = runtimes.getOrPut(sourceId) {
        AnimeSourceRegistry.createRuntime(appContext, client, sourceId)
    }
}

private fun AnimeSearchFilterCatalog.sanitized(preferEnglish: Boolean): AnimeSearchFilterCatalog = copy(
    sortOptions = sortOptions.sanitizeOptions(preferEnglish),
    typeOptions = typeOptions.takeIf { capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.TYPE) }
        .orEmpty().sanitizeOptions(preferEnglish),
    statusOptions = statusOptions.takeIf { capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.STATUS) }
        .orEmpty().sanitizeOptions(preferEnglish, isStatus = true),
    genreOptions = genreOptions.takeIf {
        capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.INCLUDED_GENRES) ||
            capabilities.supports(org.akkirrai.beakokit.model.AnimeSearchFilter.EXCLUDED_GENRES)
    }.orEmpty().sanitizeOptions(preferEnglish),
)

private fun List<SearchFilterOption>.sanitizeOptions(
    preferEnglish: Boolean,
    isStatus: Boolean = false,
): List<SearchFilterOption> = mapNotNull { option ->
    val id = option.id.trim()
    if (id.isBlank()) return@mapNotNull null
    val rawTitle = option.title.trim()
    val title = when {
        isStatus -> canonicalStatusLabel(id, rawTitle, preferEnglish)
        rawTitle.isBlank() || rawTitle == id -> id.humanizedAlias()
        else -> rawTitle
    }
    // A numeric API identifier is not a user-facing label. A source with no label simply does
    // not expose that filter until its adapter supplies one.
    title.takeUnless { it.isBlank() || it.all(Char::isDigit) }
        ?.let { option.copy(id = id, title = it) }
}.distinctBy(SearchFilterOption::id)

private fun canonicalStatusLabel(id: String, title: String, preferEnglish: Boolean): String {
    return when (listOf(id, title).joinToString(" ").trim().lowercase()) {
        "ongoing", "is_ongoing" -> if (preferEnglish) "Ongoing" else "Онгоинг"
        "released", "completed", "is_not_ongoing" -> if (preferEnglish) "Released" else "Вышло"
        "announcement", "announced" -> if (preferEnglish) "Announcement" else "Анонс"
        else -> when (title.trim().lowercase()) {
            "ongoing", "is_ongoing" -> if (preferEnglish) "Ongoing" else "Онгоинг"
            "released", "completed", "is_not_ongoing" -> if (preferEnglish) "Released" else "Вышло"
            "announcement", "announced" -> if (preferEnglish) "Announcement" else "Анонс"
            else -> title.takeIf(String::isNotBlank) ?: id.humanizedAlias()
        }
    }
}

private fun String.humanizedAlias(): String =
    replace('-', ' ')
        .replace('_', ' ')
        .lowercase()
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
