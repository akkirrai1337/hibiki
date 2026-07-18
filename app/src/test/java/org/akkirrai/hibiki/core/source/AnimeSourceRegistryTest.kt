package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.api.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSourceRegistryTest {
    @Test
    fun `registered sources have unique ids and supported language`() {
        val sources = AnimeSourceRegistry.sources

        assertEquals(
            setOf(SourceId("yummy-anime"), SourceId("ani-liberty")),
            sources.map { it.id }.toSet(),
        )
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
        assertEquals(sources.map { it.info }, AnimeSourceRegistry.catalog.sources)
        assertTrue(sources.all { it.language == "RU" })
        assertTrue(sources.all { it.supportsPlayback })
        assertEquals(
            AnimeSourceContentFeature.entries.toSet(),
            AnimeSourceRegistry.descriptor(SourceId("yummy-anime")).contentFeatures,
        )
        assertTrue(AnimeSourceRegistry.descriptor(SourceId("ani-liberty")).contentFeatures.isEmpty())
    }
}
