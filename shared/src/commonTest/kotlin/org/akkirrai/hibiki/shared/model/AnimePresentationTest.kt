package org.akkirrai.hibiki.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AnimePresentationTest {
    @Test
    fun buildsLibraryMetadataFromSubtitleParts() {
        val anime = Anime("1", "Title", "TV • 2024", "", "")
        assertEquals("TV • 2024", anime.buildLibraryMeta())
        assertEquals("TV", anime.buildLibraryMeta(maxSubtitleParts = 1))
    }
}
