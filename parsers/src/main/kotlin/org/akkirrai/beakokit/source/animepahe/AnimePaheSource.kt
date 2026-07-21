package org.akkirrai.beakokit.source.animepahe

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
import org.akkirrai.beakokit.api.track
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.beakokit.source.animepahe.internal.AnimePaheClient

object AnimePaheConfig {
    const val BASE_URL = "base_url"
}

@SourceEntry(id = "animepahe", order = 3)
class AnimePaheSource(
    context: SourceContext,
) : AnimeSource, LatestSource, PlaybackSource {
    private val health = context.sourceHealthReporter
    private val client = AnimePaheClient(
        client = context.httpClient,
        sessionProvider = context.challengeSessionProvider,
        baseUrl = context.config.value(AnimePaheConfig.BASE_URL) ?: DEFAULT_BASE_URL,
    )

    override val info: SourceInfo = INFO
    override val catalogCapabilities: CatalogCapabilities
        get() = client.capabilities

    override suspend fun search(query: String): List<AnimeTitle> =
        health.track(INFO.id) { client.search(AnimeSearchRequest(query = query)) }

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = health.track(INFO.id) { client.search(request) }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        AnimeSearchFilterCatalog(
            sortOptions = listOf(SearchFilterOption("relevance", "Relevance")),
            capabilities = client.capabilities,
        )

    override suspend fun getById(id: String): AnimeTitle = health.track(INFO.id) { client.getById(id) }

    override suspend fun latest(limit: Int): List<AnimeTitle> = health.track(INFO.id) { client.latest(limit) }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> = health.track(INFO.id) {
        val episodes = client.getEpisodes(title.id)
        if (episodes.isEmpty()) emptyList() else listOf(
            PlaybackGroup(id = title.id, title = "English dub", episodes = episodes),
        )
    }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = health.track(INFO.id) { client.getPlayerLinks(episode) }

    companion object {
        private const val DEFAULT_BASE_URL = "https://animepahetv.to"

        val INFO = SourceInfo(
            id = SourceId("animepahe"),
            name = "AnimePahe",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
            website = DEFAULT_BASE_URL,
            iconUrl = "$DEFAULT_BASE_URL/favicon.ico",
            capabilities = setOf(
                SourceCapability.LATEST_RELEASES,
                SourceCapability.PLAYBACK,
            ),
        )
    }
}
