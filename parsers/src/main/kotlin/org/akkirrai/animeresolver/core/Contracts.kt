package org.akkirrai.animeresolver.core

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.ProviderMatch

typealias SourceException = org.akkirrai.beakokit.api.SourceException

interface MetadataSource {
    val name: String
    val capabilities: CatalogCapabilities

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

@Deprecated("Use StreamExtractor from BeakoKit", ReplaceWith("StreamExtractor"))
typealias PlayerExtractor = org.akkirrai.beakokit.api.StreamExtractor

@Deprecated("Use StreamValidator from BeakoKit", ReplaceWith("StreamValidator"))
typealias StreamValidator = org.akkirrai.beakokit.api.StreamValidator
