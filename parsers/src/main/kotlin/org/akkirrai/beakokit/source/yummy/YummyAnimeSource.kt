package org.akkirrai.beakokit.source.yummy

import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.ProviderMatch
import org.akkirrai.beakokit.source.yummy.internal.YummyCatalogClient
import org.akkirrai.beakokit.source.yummy.internal.YummyPlaybackClient
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
import org.akkirrai.beakokit.api.SourceEntry
import org.akkirrai.beakokit.api.track

object YummyAnimeConfig {
    const val APPLICATION_TOKEN = "application_token"
    const val BASE_URL = "base_url"
}

/** YummyAnime metadata wired exclusively through host-provided BeakoKit services. */
@SourceEntry(id = "yummy-anime", order = 0)
class YummyAnimeSource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource {
    private val health = context.sourceHealthReporter
    private val applicationToken = context.config.secret(YummyAnimeConfig.APPLICATION_TOKEN)
    private val baseUrl = context.config.value(YummyAnimeConfig.BASE_URL) ?: DEFAULT_BASE_URL
    private val metadata = YummyCatalogClient(
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
    private val playbackProvider = YummyPlaybackClient(
        client = context.httpClient,
        matcher = TitleMatcher(),
        applicationToken = applicationToken,
        baseUrl = baseUrl,
        debugLogger = { message ->
            context.logger.log(SourceLogLevel.DEBUG, message, null)
        },
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

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> = health.track(INFO.id) {
        val match = title.toProviderMatch()
        playbackProvider.getDubbingCatalog(match).map { dubbing ->
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
    ): List<PlayerLink> = health.track(INFO.id) {
        val allLinks = playbackProvider.getPlayerLinks(title.toProviderMatch(), episode)
        val matchingLinks = allLinks.filter { link ->
            link.translation.normalizedTitle() == group.title.normalizedTitle()
        }
        matchingLinks.ifEmpty { allLinks }
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
        private const val DEFAULT_BASE_URL = "https://api.yani.tv"

        val INFO = SourceInfo(
            id = SourceId("yummy-anime"),
            name = "YummyAnime",
            languages = setOf(SourceLanguage.RUSSIAN),
            primaryLanguage = SourceLanguage.RUSSIAN,
            capabilities = setOf(
                SourceCapability.LATEST_RELEASES,
                SourceCapability.PLAYBACK,
                SourceCapability.RELATED_TITLES,
                SourceCapability.SIMILAR_TITLES,
            ),
        )
    }
}
