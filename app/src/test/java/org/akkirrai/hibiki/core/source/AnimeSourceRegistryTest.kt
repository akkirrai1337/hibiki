package org.akkirrai.hibiki.core.source

import org.akkirrai.hibiki.app.settings.AnimeSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSourceRegistryTest {
    @Test
    fun `registered sources have unique ids and supported language`() {
        val sources = AnimeSourceRegistry.sources

        assertEquals(AnimeSourceId.entries.toSet(), sources.map { it.id }.toSet())
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
        assertTrue(sources.all { it.language == "RU" })
    }
}
