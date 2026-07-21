package org.akkirrai.beakokit.source.aniliberty

import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.ProviderMatch
import org.akkirrai.beakokit.source.aniliberty.internal.AniLibertyCatalogClient
import org.akkirrai.beakokit.source.aniliberty.internal.AniLibertyPlaybackClient
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceEntry
import org.akkirrai.beakokit.api.SourceOperation
import org.akkirrai.beakokit.api.SourceCacheTtl
import org.akkirrai.beakokit.api.ConfigurableSource
import org.akkirrai.beakokit.api.SourceConfigField
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceConfigValueKind

/** First source packaged around the BeakoKit contract instead of host-side registration metadata. */
@SourceEntry(id = "ani-liberty", order = 1)
class AniLibertySource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource, ConfigurableSource {
    private val execution = context.sourceExecutionPolicy
    private val baseUrls = context.config.value(BASE_URLS_KEY)
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.takeIf(List<String>::isNotEmpty)
        ?: DEFAULT_BASE_URLS
    private val metadata = AniLibertyCatalogClient(context.httpClient, baseUrls, context.logger)
    private val playbackProvider = AniLibertyPlaybackClient(
        client = context.httpClient,
        matcher = TitleMatcher(),
        baseUrls = baseUrls,
        logger = context.logger,
    )

    override val info: SourceInfo = INFO
    override val configSchema: SourceConfigSchema = CONFIG_SCHEMA
    override val catalogCapabilities: CatalogCapabilities
        get() = metadata.capabilities

    override suspend fun search(query: String): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.SEARCH, "query:$query", SourceCacheTtl.SEARCH_MILLIS) { metadata.search(query) }

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.SEARCH, "request:$request", SourceCacheTtl.SEARCH_MILLIS) { metadata.search(request) }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        execution.execute(INFO.id, SourceOperation.FILTER_CATALOG, "default", SourceCacheTtl.FILTER_CATALOG_MILLIS) { metadata.getSearchFilterCatalog() }

    override suspend fun latest(limit: Int): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.LATEST, "limit:$limit", SourceCacheTtl.LATEST_MILLIS) { metadata.latest(limit) }

    override suspend fun getById(id: String): AnimeTitle = execution.execute(INFO.id, SourceOperation.DETAILS, id, SourceCacheTtl.DETAILS_MILLIS) { metadata.getById(id) }

    suspend fun weeklySchedule(): List<AniLibertyScheduleEntry> = execution.execute(INFO.id, SourceOperation.SCHEDULE, "weekly", SourceCacheTtl.SCHEDULE_MILLIS) { metadata.weeklySchedule() }

    suspend fun currentSchedule(): List<AniLibertyScheduleEntry> = execution.execute(INFO.id, SourceOperation.SCHEDULE, "current", SourceCacheTtl.SCHEDULE_MILLIS) { metadata.currentSchedule() }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> = execution.execute(INFO.id, SourceOperation.PLAYBACK_GROUPS, title.id, SourceCacheTtl.PLAYBACK_GROUPS_MILLIS) {
        val match = playbackProvider.search(title).maxByOrNull(ProviderMatch::confidence)
            ?: return@execute emptyList()
        val episodes = playbackProvider.getEpisodes(match)
        if (episodes.isEmpty()) emptyList() else listOf(
            PlaybackGroup(
                id = match.mediaId,
                title = playbackProvider.name,
                episodes = episodes,
                qualityLabel = "HLS",
            ),
        )
    }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = execution.execute(INFO.id, SourceOperation.PLAYER_LINKS) { playbackProvider.getPlayerLinks(
        match = ProviderMatch(
            providerId = playbackProvider.id,
            providerName = playbackProvider.name,
            mediaId = group.id,
            title = title.displayName,
            confidence = 1.0,
            year = title.year,
            type = title.type,
            episodeCount = title.episodeCount,
        ),
        episode = episode,
    ) }

    companion object {
        val CONFIG_SCHEMA = SourceConfigSchema(
            listOf(SourceConfigField(BASE_URLS_KEY, SourceConfigValueKind.TEXT)),
        )

        const val BASE_URLS_KEY = "api_base_urls"

        private val DEFAULT_BASE_URLS = listOf(
            "https://anilibria.top/api/v1",
            "https://api.anilibria.app/api/v1",
        )

        val INFO = SourceInfo(
            id = SourceId("ani-liberty"),
            name = "AniLiberty",
            languages = setOf(SourceLanguage.RUSSIAN),
            primaryLanguage = SourceLanguage.RUSSIAN,
            website = "https://anilibria.top",
            capabilities = setOf(
                SourceCapability.LATEST_RELEASES,
                SourceCapability.PLAYBACK,
            ),
        )
    }
}
