package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities

class ExternalSourceRegistrationTest {
    @Test
    fun registrationCreatesRuntimeBackedSourceThroughCatalog() = runBlocking {
        val runtime = object : ExternalSourceRuntime {
            override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> = emptyList()

            override suspend fun details(id: String): AnimeTitle = title(id)
        }
        var receivedContext: SourceContext? = null
        val registration = ExternalSourceRegistration(
            info = sourceInfo(),
            catalogCapabilities = CatalogCapabilities.FULL,
            runtimeFactory = { context ->
                receivedContext = context
                runtime
            },
        )
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("Network is not expected in this test") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        )

        val source = externalSourceCatalog(listOf(registration))
            .create(SourceId("external-test"), context)

        assertSame(context, receivedContext)
        assertEquals("title-1", source.getById("title-1").id)
    }

    private fun sourceInfo() = SourceInfo(
        id = SourceId("external-test"),
        name = "External test source",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )

    private fun title(id: String) = AnimeTitle(
        id = id,
        russianName = null,
        englishName = "Test title",
        originalName = "Test title",
        japaneseName = null,
        synonyms = emptyList(),
        year = null,
        type = null,
        episodeCount = null,
        posterUrl = null,
        status = null,
        description = null,
    )
}
