package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities

/** Standalone BeakoKit catalog contract; legacy resolver interfaces stay implementation details. */
interface AnimeSource {
    val info: SourceInfo
    val catalogCapabilities: CatalogCapabilities

    val name: String
        get() = info.name

    suspend fun search(query: String): List<AnimeTitle>

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = search(request.query)

    suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog()

    suspend fun getById(id: String): AnimeTitle
}
