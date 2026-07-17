package org.akkirrai.animeresolver.core

import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.ProviderMatch
import org.akkirrai.animeresolver.model.StreamValidationResult
import org.akkirrai.animeresolver.model.VideoStream

interface MetadataSource {
    val name: String
    val capabilities: MetadataSourceCapabilities

    suspend fun search(query: String): List<AnimeTitle>

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = search(request.query)

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog()

    /**
     * Titles ordered by their latest source-side update.
     * Sources advertising LATEST_RELEASES must implement this operation.
     */
    suspend fun latest(limit: Int): List<AnimeTitle> = emptyList()

    suspend fun getById(id: String): AnimeTitle
}

interface VideoProvider {
    val id: String
    val name: String

    suspend fun search(title: AnimeTitle): List<ProviderMatch>

    suspend fun getEpisodes(match: ProviderMatch): List<Episode>

    suspend fun getPlayerLinks(match: ProviderMatch, episode: Episode): List<PlayerLink>
}

interface PlayerExtractor {
    fun supports(link: PlayerLink): Boolean

    suspend fun extract(link: PlayerLink): VideoStream

    suspend fun extractVariants(link: PlayerLink): List<VideoStream> = listOf(extract(link))
}

interface StreamValidator {
    suspend fun validate(stream: VideoStream): StreamValidationResult
}

class SourceException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
