package org.akkirrai.beakokit.source.yummy

import org.akkirrai.animeresolver.metadata.YummyMetadataSource
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogLevel

/** YummyAnime metadata wired exclusively through host-provided BeakoKit services. */
class YummyAnimeSource(
    context: SourceContext,
) : AnimeSource {
    private val metadata = YummyMetadataSource(
        client = context.httpClient,
        applicationToken = context.config.secret(APPLICATION_TOKEN_KEY),
        baseUrl = context.config.value(BASE_URL_KEY) ?: DEFAULT_BASE_URL,
        debugLogger = { message ->
            context.logger.log(SourceLogLevel.DEBUG, message, null)
        },
        languageProvider = {
            context.preferredLanguages.firstOrNull()?.tag ?: SourceLanguage.RUSSIAN.tag
        },
    )

    override val info: SourceInfo = INFO
    override val capabilities: MetadataSourceCapabilities
        get() = metadata.capabilities

    override suspend fun search(query: String): List<AnimeTitle> = metadata.search(query)

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = metadata.search(request)

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        metadata.getSearchFilterCatalog()

    override suspend fun latest(limit: Int): List<AnimeTitle> = metadata.latest(limit)

    override suspend fun getById(id: String): AnimeTitle = metadata.getById(id)

    companion object {
        const val APPLICATION_TOKEN_KEY = "application_token"
        const val BASE_URL_KEY = "base_url"

        private const val DEFAULT_BASE_URL = "https://api.yani.tv"

        val INFO = SourceInfo(
            id = SourceId("yummy-anime"),
            name = "YummyAnime",
            languages = setOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
        )
    }
}
