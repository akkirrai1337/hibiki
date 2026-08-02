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
import org.akkirrai.beakokit.api.SourceOperation
import org.akkirrai.beakokit.api.SourceCacheTtl
import org.akkirrai.beakokit.api.ConfigurableSource
import org.akkirrai.beakokit.api.SourceConfigField
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceConfigValueKind

object YummyAnimeConfig {
    const val APPLICATION_TOKEN = "application_token"
    const val BASE_URL = "base_url"
}

/** YummyAnime metadata wired exclusively through host-provided BeakoKit services. */
@SourceEntry(id = "yummy-anime", order = 0)
class YummyAnimeSource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource, ConfigurableSource {
    private val execution = context.sourceExecutionPolicy
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
    override val configSchema: SourceConfigSchema = CONFIG_SCHEMA
    override val catalogCapabilities: CatalogCapabilities
        get() = metadata.capabilities

    override suspend fun search(query: String): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.SEARCH, "query:$query", SourceCacheTtl.SEARCH_MILLIS) { metadata.search(query) }

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.SEARCH, "request:$request", SourceCacheTtl.SEARCH_MILLIS) { metadata.search(request) }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        execution.execute(INFO.id, SourceOperation.FILTER_CATALOG, "default", SourceCacheTtl.FILTER_CATALOG_MILLIS) { metadata.getSearchFilterCatalog() }

    override suspend fun latest(limit: Int): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.LATEST, "limit:$limit", SourceCacheTtl.LATEST_MILLIS) { metadata.latest(limit) }

    override suspend fun getById(id: String): AnimeTitle = execution.execute(INFO.id, SourceOperation.DETAILS, id, SourceCacheTtl.DETAILS_MILLIS) { metadata.getById(id) }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> = execution.execute(INFO.id, SourceOperation.PLAYBACK_GROUPS, title.id, SourceCacheTtl.PLAYBACK_GROUPS_MILLIS) {
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
    ): List<PlayerLink> = execution.execute(INFO.id, SourceOperation.PLAYER_LINKS) {
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

        val CONFIG_SCHEMA = SourceConfigSchema(
            listOf(
                SourceConfigField(YummyAnimeConfig.APPLICATION_TOKEN, SourceConfigValueKind.SECRET),
                SourceConfigField(YummyAnimeConfig.BASE_URL, SourceConfigValueKind.HTTPS_URL),
            ),
        )

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
