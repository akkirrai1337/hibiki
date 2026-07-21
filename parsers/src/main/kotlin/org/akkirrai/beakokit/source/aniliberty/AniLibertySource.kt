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
import org.akkirrai.beakokit.api.track

/** First source packaged around the BeakoKit contract instead of host-side registration metadata. */
@SourceEntry(id = "ani-liberty", order = 1)
class AniLibertySource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource {
    private val health = context.sourceHealthReporter
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
    override val catalogCapabilities: CatalogCapabilities
        get() = metadata.capabilities

    override suspend fun search(query: String): List<AnimeTitle> = health.track(INFO.id) { metadata.search(query) }

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = health.track(INFO.id) { metadata.search(request) }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        health.track(INFO.id) { metadata.getSearchFilterCatalog() }

    override suspend fun latest(limit: Int): List<AnimeTitle> = health.track(INFO.id) { metadata.latest(limit) }

    override suspend fun getById(id: String): AnimeTitle = health.track(INFO.id) { metadata.getById(id) }

    suspend fun weeklySchedule(): List<AniLibertyScheduleEntry> = health.track(INFO.id) { metadata.weeklySchedule() }

    suspend fun currentSchedule(): List<AniLibertyScheduleEntry> = health.track(INFO.id) { metadata.currentSchedule() }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> = health.track(INFO.id) {
        val match = playbackProvider.search(title).maxByOrNull(ProviderMatch::confidence)
            ?: return@track emptyList()
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
    ): List<PlayerLink> = health.track(INFO.id) { playbackProvider.getPlayerLinks(
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
