package org.akkirrai.beakokit.source.animego

import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceEntry
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceOperation
import org.akkirrai.beakokit.api.SourceCacheTtl
import org.akkirrai.beakokit.api.ConfigurableSource
import org.akkirrai.beakokit.api.SourceConfigField
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceConfigValueKind
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.source.animego.internal.AnimeGoCatalogClient
import org.akkirrai.beakokit.source.animego.internal.AnimeGoPlaybackClient

object AnimeGoConfig {
    const val BASE_URL = "base_url"
}

@SourceEntry(id = "animego", order = 2)
class AnimeGoSource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource, ConfigurableSource {
    private val execution = context.sourceExecutionPolicy
    private val baseUrl = context.config.value(AnimeGoConfig.BASE_URL) ?: DEFAULT_BASE_URL
    private val catalog = AnimeGoCatalogClient(context.httpClient, baseUrl)
    private val playback = AnimeGoPlaybackClient(context.httpClient, baseUrl)

    override val info: SourceInfo = INFO
    override val configSchema: SourceConfigSchema = CONFIG_SCHEMA
    override val catalogCapabilities: CatalogCapabilities
        get() = catalog.capabilities

    override suspend fun search(query: String): List<AnimeTitle> =
        execution.execute(INFO.id, SourceOperation.SEARCH, "query:$query", SourceCacheTtl.SEARCH_MILLIS) { catalog.search(AnimeSearchRequest(query = query)) }

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.SEARCH, "request:$request", SourceCacheTtl.SEARCH_MILLIS) { catalog.search(request) }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = catalog.filterCatalog()

    override suspend fun getById(id: String): AnimeTitle = execution.execute(INFO.id, SourceOperation.DETAILS, id, SourceCacheTtl.DETAILS_MILLIS) { catalog.getById(id) }

    override suspend fun latest(limit: Int): List<AnimeTitle> = execution.execute(INFO.id, SourceOperation.LATEST, "limit:$limit", SourceCacheTtl.LATEST_MILLIS) { catalog.latest(limit) }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> = execution.execute(INFO.id, SourceOperation.PLAYBACK_GROUPS, title.id, SourceCacheTtl.PLAYBACK_GROUPS_MILLIS) {
        val episodes = playback.getEpisodes(title.id)
        if (episodes.isEmpty()) emptyList() else listOf(
            PlaybackGroup(
                id = title.id,
                title = INFO.name,
                episodes = episodes,
            ),
        )
    }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = execution.execute(INFO.id, SourceOperation.PLAYER_LINKS) { playback.getPlayerLinks(episode) }

    companion object {
        private const val DEFAULT_BASE_URL = "https://animego.me"

        val CONFIG_SCHEMA = SourceConfigSchema(
            listOf(SourceConfigField(AnimeGoConfig.BASE_URL, SourceConfigValueKind.HTTPS_URL)),
        )

        val INFO = SourceInfo(
            id = SourceId("animego"),
            name = "AnimeGo",
            languages = setOf(SourceLanguage.RUSSIAN),
            primaryLanguage = SourceLanguage.RUSSIAN,
            website = DEFAULT_BASE_URL,
            iconUrl = "$DEFAULT_BASE_URL/favicon.ico",
            capabilities = setOf(
                SourceCapability.LATEST_RELEASES,
                SourceCapability.PLAYBACK,
            ),
        )
    }
}
