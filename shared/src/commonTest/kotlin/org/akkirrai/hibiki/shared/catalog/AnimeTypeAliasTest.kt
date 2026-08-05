package org.akkirrai.hibiki.shared.catalog
import org.akkirrai.hibiki.shared.catalog.model.AnimeTypeAlias
import org.akkirrai.hibiki.shared.catalog.presentation.*
import org.akkirrai.hibiki.shared.catalog.sort.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnimeTypeAliasTest {
    @Test
    fun resolvesKnownAliases() {
        assertEquals(AnimeTypeAlias.Tv, AnimeTypeAlias.fromAlias(" TV "))
        assertEquals(AnimeTypeAlias.Movie, AnimeTypeAlias.fromAlias("movie"))
    }

    @Test
    fun unknownAliasIsNotSelected() {
        assertNull(AnimeTypeAlias.fromAlias("special"))
        assertNull(AnimeTypeAlias.fromAlias(null))
    }
}
