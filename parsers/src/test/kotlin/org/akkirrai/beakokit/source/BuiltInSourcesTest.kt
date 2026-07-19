package org.akkirrai.beakokit.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.testkit.SourceTestKit
import kotlin.test.Test
import kotlin.test.assertEquals

class BuiltInSourcesTest {
    @Test
    fun `catalog creates every source without constructor io`() {
        val client = HttpClient(MockEngine { error("Source constructor must not perform network I/O") })
        val context = DefaultSourceContext(
            httpClient = client,
            preferredLanguages = listOf(SourceLanguage.RUSSIAN),
        )

        try {
            assertEquals(
                listOf(BuiltInSources.YUMMY_ANIME_ID, BuiltInSources.ANI_LIBERTY_ID),
                BuiltInSources.catalog.sources.map { it.id },
            )
            val sources = BuiltInSources.catalog.entries.map { entry ->
                BuiltInSources.catalog.create(entry.info.id, context).also { source ->
                    SourceTestKit.assertSourceContract(source, entry.info.id)
                }
            }

            assertEquals(BuiltInSources.catalog.sources, sources.map { it.info })
        } finally {
            client.close()
        }
    }
}
