package org.akkirrai.beakokit.source.yummy

import org.akkirrai.animeresolver.metadata.YummyMetadataSource
import org.akkirrai.animeresolver.core.TitleMatcher
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.ProviderMatch
import org.akkirrai.animeresolver.provider.YummyAnimeProvider
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.api.SourceCapability

/** YummyAnime metadata wired exclusively through host-provided BeakoKit services. */
class YummyAnimeSource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource {
    private val applicationToken = context.config.secret(APPLICATION_TOKEN_KEY)
    private val baseUrl = context.config.value(BASE_URL_KEY) ?: DEFAULT_BASE_URL
    private val metadata = YummyMetadataSource(
        client = context.httpClient,
        applicationToken = applicationToken,
        baseUrl = baseUrl,
        debugLogger = { message ->
            context.logger.log(SourceLogLevel.DEBUG, message, null)
        },
        languageProvider = {
            context.preferredLanguages.firstOrNull()?.tag ?: SourceLanguage.RUSSIAN.tag
        },
    )
    private val playbackProvider = YummyAnimeProvider(
        client = context.httpClient,
        matcher = TitleMatcher(),
        applicationToken = applicationToken,
        baseUrl = baseUrl,
        debugLogger = { message ->
            context.logger.log(SourceLogLevel.DEBUG, message, null)
        },
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

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> {
        val match = title.toProviderMatch()
        return playbackProvider.getDubbingCatalog(match).map { dubbing ->
            PlaybackGroup(
                id = dubbing.title,
                title = dubbing.title,
                episodes = dubbing.episodes,
                qualityLabel = dubbing.qualityLabel,
            )
        }
    }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> {
        val allLinks = playbackProvider.getPlayerLinks(title.toProviderMatch(), episode)
        val matchingLinks = allLinks.filter { link ->
            link.translation.normalizedTitle() == group.title.normalizedTitle()
        }
        return matchingLinks.ifEmpty { allLinks }
    }

    private fun AnimeTitle.toProviderMatch() = ProviderMatch(
        providerId = playbackProvider.id,
        providerName = playbackProvider.name,
        mediaId = id,
        title = displayName,
        confidence = 1.0,
        year = year,
        type = type,
        episodeCount = episodeCount,
    )

    private fun String?.normalizedTitle(): String = orEmpty()
        .trim()
        .lowercase()
        .replace(Regex("""\s+"""), " ")

    companion object {
        const val APPLICATION_TOKEN_KEY = "application_token"
        const val BASE_URL_KEY = "base_url"

        private const val DEFAULT_BASE_URL = "https://api.yani.tv"

        val INFO = SourceInfo(
            id = SourceId("yummy-anime"),
            name = "YummyAnime",
            languages = setOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
            capabilities = setOf(
                SourceCapability.LATEST_RELEASES,
                SourceCapability.PLAYBACK,
                SourceCapability.RELATED_TITLES,
                SourceCapability.SIMILAR_TITLES,
            ),
        )
    }
}
