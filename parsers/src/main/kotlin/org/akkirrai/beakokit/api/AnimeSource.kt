package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.MetadataSourceCapabilities

/** Standalone BeakoKit metadata contract; legacy resolver interfaces stay implementation details. */
interface AnimeSource {
    val info: SourceInfo
    val capabilities: MetadataSourceCapabilities

    val name: String
        get() = info.name

    suspend fun search(query: String): List<AnimeTitle>

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = search(request.query)

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog()

    suspend fun getById(id: String): AnimeTitle
}
