package org.akkirrai.hibiki.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.Anime

class AnimeDescriptionResolverTest {
    @Test
    fun returnsOnlyMeaningfulDescription() {
        val base = Anime("1", "Title", "", "", "")
        assertEquals(" Description ", resolveAnimeDescription(base.copy(description = " Description ")))
        assertEquals("", resolveAnimeDescription(base.copy(description = "   ")))
        assertEquals("", resolveAnimeDescription(base))
    }
}
