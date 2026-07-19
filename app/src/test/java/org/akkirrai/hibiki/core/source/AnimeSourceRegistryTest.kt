package org.akkirrai.hibiki.core.source

import org.akkirrai.hibiki.R
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.source.animego.AnimeGoSource
import org.akkirrai.beakokit.source.aniliberty.AniLibertySource
import org.akkirrai.beakokit.source.yummy.YummyAnimeSource
import org.akkirrai.beakokit.source.animepahe.AnimePaheSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSourceRegistryTest {
    @Test
    fun `registered sources have unique ids and supported language`() {
        val sources = AnimeSourceRegistry.sources

        assertEquals(
            setOf(SourceId("yummy-anime"), SourceId("ani-liberty"), SourceId("animego"), SourceId("animepahe")),
            sources.map { it.id }.toSet(),
        )
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
        assertEquals(sources.map { it.info }, AnimeSourceRegistry.catalog.sources)
        assertEquals(
            setOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
            sources.map { it.language }.toSet(),
        )
        assertTrue(sources.all { it.supportsPlayback })
        assertEquals(
            AniLibertySource.INFO,
            AnimeSourceRegistry.descriptor(SourceId("ani-liberty")).info,
        )
        assertEquals(
            YummyAnimeSource.INFO,
            AnimeSourceRegistry.descriptor(SourceId("yummy-anime")).info,
        )
        assertEquals(
            AnimeGoSource.INFO,
            AnimeSourceRegistry.descriptor(SourceId("animego")).info,
        )
        assertEquals(
            AnimePaheSource.INFO,
            AnimeSourceRegistry.descriptor(SourceId("animepahe")).info,
        )
        assertEquals(
            R.drawable.source_animego,
            AnimeSourceRegistry.descriptor(SourceId("animego")).iconRes,
        )
        assertEquals(
            R.drawable.source_animepahe,
            AnimeSourceRegistry.descriptor(SourceId("animepahe")).iconRes,
        )
        assertEquals(
            setOf(SourceCapability.RELATED_TITLES, SourceCapability.SIMILAR_TITLES),
            AnimeSourceRegistry.descriptor(SourceId("yummy-anime")).contentFeatures,
        )
        assertTrue(AnimeSourceRegistry.descriptor(SourceId("ani-liberty")).contentFeatures.isEmpty())
    }
}
