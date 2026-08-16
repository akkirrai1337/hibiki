package org.akkirrai.hibiki.shared.catalog
import org.akkirrai.hibiki.shared.catalog.model.AnimeStatus
import org.akkirrai.hibiki.shared.catalog.presentation.*
import org.akkirrai.hibiki.shared.catalog.sort.*

import kotlin.test.Test
import kotlin.test.assertEquals

class AnimeStatusTest {
    @Test
    fun resolvesAliasesCaseInsensitively() {
        assertEquals(AnimeStatus.Finished, AnimeStatus.fromAlias(" COMPLETED "))
        assertEquals(AnimeStatus.Releasing, AnimeStatus.fromAlias("airing"))
        assertEquals(AnimeStatus.NotYetReleased, AnimeStatus.fromAlias("not-yet-released"))
        assertEquals(AnimeStatus.Cancelled, AnimeStatus.fromAlias("canceled"))
        assertEquals(AnimeStatus.Hiatus, AnimeStatus.fromAlias("paused"))
    }

    @Test
    fun unknownAliasFallsBackToFinished() {
        assertEquals(AnimeStatus.Finished, AnimeStatus.fromAlias("unknown"))
    }
}
