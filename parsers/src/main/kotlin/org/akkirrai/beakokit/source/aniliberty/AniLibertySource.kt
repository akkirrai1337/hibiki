package org.akkirrai.beakokit.source.aniliberty

import org.akkirrai.animeresolver.metadata.AniLibertyMetadataSource
import org.akkirrai.animeresolver.metadata.AniLibertyScheduleEntry
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage

/** First source packaged around the BeakoKit contract instead of host-side registration metadata. */
class AniLibertySource(
    context: SourceContext,
) : AnimeSource {
    private val metadata = AniLibertyMetadataSource(context.httpClient)

    override val info: SourceInfo = INFO
    override val capabilities: MetadataSourceCapabilities
        get() = metadata.capabilities

    override suspend fun search(query: String): List<AnimeTitle> = metadata.search(query)

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = metadata.search(request)

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        metadata.getSearchFilterCatalog()

    override suspend fun latest(limit: Int): List<AnimeTitle> = metadata.latest(limit)

    override suspend fun getById(id: String): AnimeTitle = metadata.getById(id)

    suspend fun weeklySchedule(): List<AniLibertyScheduleEntry> = metadata.weeklySchedule()

    suspend fun currentSchedule(): List<AniLibertyScheduleEntry> = metadata.currentSchedule()

    companion object {
        val INFO = SourceInfo(
            id = SourceId("ani-liberty"),
            name = "AniLiberty",
            languages = setOf(SourceLanguage.RUSSIAN),
            website = "https://anilibria.top",
        )
    }
}
