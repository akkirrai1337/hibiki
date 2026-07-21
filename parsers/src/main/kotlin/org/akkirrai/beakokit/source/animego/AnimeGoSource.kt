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
) : AnimeSource, LatestSource, PlaybackSource {
    private val baseUrl = context.config.value(AnimeGoConfig.BASE_URL) ?: DEFAULT_BASE_URL
    private val catalog = AnimeGoCatalogClient(context.httpClient, baseUrl)
    private val playback = AnimeGoPlaybackClient(context.httpClient, baseUrl)

    override val info: SourceInfo = INFO
    override val catalogCapabilities: CatalogCapabilities
        get() = catalog.capabilities

    override suspend fun search(query: String): List<AnimeTitle> =
        catalog.search(AnimeSearchRequest(query = query))

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = catalog.search(request)

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = catalog.filterCatalog()

    override suspend fun getById(id: String): AnimeTitle = catalog.getById(id)

    override suspend fun latest(limit: Int): List<AnimeTitle> = catalog.latest(limit)

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> {
        val episodes = playback.getEpisodes(title.id)
        if (episodes.isEmpty()) return emptyList()
        return listOf(
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
    ): List<PlayerLink> = playback.getPlayerLinks(episode)

    companion object {
        private const val DEFAULT_BASE_URL = "https://animego.me"

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
