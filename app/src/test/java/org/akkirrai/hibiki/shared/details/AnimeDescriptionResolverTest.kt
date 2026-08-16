package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.catalog.model.Anime

class AnimeDescriptionResolverTest {
    @Test
    fun returnsOnlyMeaningfulDescription() {
        val base = Anime("1", "Title", "", "", "")
        assertEquals(" Description ", resolveAnimeDescription(base.copy(description = " Description ")))
        assertEquals("", resolveAnimeDescription(base.copy(description = "   ")))
        assertEquals("", resolveAnimeDescription(base))
    }
}
