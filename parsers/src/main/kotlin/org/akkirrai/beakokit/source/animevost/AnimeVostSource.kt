package org.akkirrai.beakokit.source.animevost

import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.ConfigurableSource
import org.akkirrai.beakokit.api.HealthCheckSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceCacheTtl
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceConfigField
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceConfigValueKind
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceEntry
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceOperation
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.source.animevost.internal.AnimeVostCatalogClient
import org.akkirrai.beakokit.source.animevost.internal.AnimeVostPlaybackClient

object AnimeVostConfig {
    const val BASE_URL = "base_url"
    const val API_BASE_URL = "api_base_url"
}

/** AnimeVost HTML catalog with its public playlist endpoint for direct MP4 playback. */
@SourceEntry(id = "animevost", order = 5)
class AnimeVostSource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource, ConfigurableSource, HealthCheckSource {
    private val execution = context.sourceExecutionPolicy
    private val baseUrl = context.config.value(AnimeVostConfig.BASE_URL) ?: DEFAULT_BASE_URL
    private val catalog = AnimeVostCatalogClient(context.httpClient, baseUrl)
    private val playback = AnimeVostPlaybackClient(
        client = context.httpClient,
        baseUrl = baseUrl,
        apiBaseUrl = context.config.value(AnimeVostConfig.API_BASE_URL) ?: DEFAULT_API_BASE_URL,
    )

    override val info: SourceInfo = INFO
    override val configSchema: SourceConfigSchema = CONFIG_SCHEMA
    override val catalogCapabilities: CatalogCapabilities
        get() = catalog.capabilities

    override suspend fun checkHealth() {
        execution.execute(INFO.id, SourceOperation.HEALTH_CHECK) { catalog.latest(1) }
    }

    override suspend fun search(query: String): List<AnimeTitle> =
        execution.execute(INFO.id, SourceOperation.SEARCH, "query:$query", SourceCacheTtl.SEARCH_MILLIS) { catalog.search(AnimeSearchRequest(query = query)) }

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> =
        execution.execute(INFO.id, SourceOperation.SEARCH, "request:$request", SourceCacheTtl.SEARCH_MILLIS) { catalog.search(request) }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = catalog.filterCatalog()

    override suspend fun getById(id: String): AnimeTitle =
        execution.execute(INFO.id, SourceOperation.DETAILS, id, SourceCacheTtl.DETAILS_MILLIS) { catalog.getById(id) }

    override suspend fun latest(limit: Int): List<AnimeTitle> =
        execution.execute(INFO.id, SourceOperation.LATEST, "limit:$limit", SourceCacheTtl.LATEST_MILLIS) { catalog.latest(limit) }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> =
        execution.execute(INFO.id, SourceOperation.PLAYBACK_GROUPS, title.id, SourceCacheTtl.PLAYBACK_GROUPS_MILLIS) {
            playback.getEpisodes(title.id).takeIf(List<Episode>::isNotEmpty)?.let { episodes ->
                listOf(PlaybackGroup(id = title.id, title = INFO.name, episodes = episodes))
            }.orEmpty()
        }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = execution.execute(INFO.id, SourceOperation.PLAYER_LINKS) {
        playback.getPlayerLinks(episode)
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://animevost.org"
        private const val DEFAULT_API_BASE_URL = "https://api.animevost.org"

        val CONFIG_SCHEMA = SourceConfigSchema(
            listOf(
                SourceConfigField(AnimeVostConfig.BASE_URL, SourceConfigValueKind.HTTPS_URL),
                SourceConfigField(AnimeVostConfig.API_BASE_URL, SourceConfigValueKind.HTTPS_URL),
            ),
        )

        val INFO = SourceInfo(
            id = SourceId("animevost"),
            name = "AnimeVost",
            languages = setOf(SourceLanguage.RUSSIAN),
            primaryLanguage = SourceLanguage.RUSSIAN,
            website = DEFAULT_BASE_URL,
            iconUrl = "$DEFAULT_BASE_URL/favicon.ico",
            capabilities = setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
        )
    }
}
