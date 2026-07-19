package org.akkirrai.beakokit.source.aniliberty

import org.akkirrai.animeresolver.core.TitleMatcher
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.ProviderMatch
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

/** First source packaged around the BeakoKit contract instead of host-side registration metadata. */
@SourceEntry(id = "ani-liberty", order = 1)
class AniLibertySource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource {
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
    override val capabilities: MetadataSourceCapabilities
        get() = metadata.capabilities

    override suspend fun search(query: String): List<AnimeTitle> = metadata.search(query)

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = metadata.search(request)

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        metadata.getSearchFilterCatalog()

    override suspend fun latest(limit: Int): List<AnimeTitle> = metadata.latest(limit)

    override suspend fun getById(id: String): AnimeTitle = metadata.getById(id)

    suspend fun weeklySchedule(): List<AniLibertyScheduleEntry> = metadata.weeklySchedule()

    suspend fun currentSchedule(): List<AniLibertyScheduleEntry> = metadata.currentSchedule()

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> {
        val match = playbackProvider.search(title).maxByOrNull(ProviderMatch::confidence)
            ?: return emptyList()
        val episodes = playbackProvider.getEpisodes(match)
        if (episodes.isEmpty()) return emptyList()
        return listOf(
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
    ): List<PlayerLink> = playbackProvider.getPlayerLinks(
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
    )

    companion object {
        const val BASE_URLS_KEY = "api_base_urls"

        private val DEFAULT_BASE_URLS = listOf(
            "https://anilibria.top/api/v1",
            "https://api.anilibria.app/api/v1",
        )

        val INFO = SourceInfo(
            id = SourceId("ani-liberty"),
            name = "AniLiberty",
            languages = setOf(SourceLanguage.RUSSIAN),
            website = "https://anilibria.top",
            capabilities = setOf(
                SourceCapability.LATEST_RELEASES,
                SourceCapability.PLAYBACK,
            ),
        )
    }
}
