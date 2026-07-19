package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.AnimeSearchSort
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class SourceContractValidatorTest {
    @Test
    fun `declared playback requires playback interface`() {
        val source = InvalidPlaybackSource()

        val violations = SourceContractValidator.violations(source)

        assertContains(violations, "PLAYBACK must match implementation of PlaybackSource")
        assertFailsWith<SourceContractException> {
            SourceContractValidator.requireValid(source)
        }
    }

    private class InvalidPlaybackSource : AnimeSource {
        override val info = SourceInfo(
            id = SourceId("invalid-source"),
            name = "Invalid",
            languages = setOf(SourceLanguage.ENGLISH),
            capabilities = setOf(SourceCapability.PLAYBACK),
        )
        override val catalogCapabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = emptySet(),
        )

        override suspend fun search(query: String): List<AnimeTitle> = emptyList()

        override suspend fun getById(id: String): AnimeTitle = error("Not used")
    }
}
