package org.akkirrai.beakokit.source.aniliberty

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.akkirrai.animeresolver.model.MetadataSourceFeature
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AniLibertySourceTest {
    @Test
    fun `source owns its stable identity and capabilities`() {
        val client = HttpClient(MockEngine { error("Network must not be called") })

        try {
            val source = AniLibertySource(
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN),
                ),
            )

            assertEquals(SourceId("ani-liberty"), source.info.id)
            assertEquals("AniLiberty", source.name)
            assertEquals(setOf(SourceLanguage.RUSSIAN), source.info.languages)
            assertEquals("https://anilibria.top", source.info.website)
            assertTrue(MetadataSourceFeature.LATEST_RELEASES in source.capabilities.features)
            assertTrue(MetadataSourceFeature.SCHEDULE in source.capabilities.features)
        } finally {
            client.close()
        }
    }
}
