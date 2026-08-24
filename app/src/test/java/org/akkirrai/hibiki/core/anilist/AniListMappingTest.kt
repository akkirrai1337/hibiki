package org.akkirrai.hibiki.core.anilist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniListMappingTest {
    @Test
    fun `filters staff to director roles only, case-insensitively and deduplicated`() {
        val detail = AniListMediaDetail(
            staff = AniListStaffConnection(
                edges = listOf(
                    AniListStaffEdge(role = "Director", node = AniListPersonNode(name = AniListPersonName(full = "Naoko Yamada"))),
                    AniListStaffEdge(role = "Series Composition", node = AniListPersonNode(name = AniListPersonName(full = "Someone Else"))),
                    AniListStaffEdge(role = "Assistant Director", node = AniListPersonNode(name = AniListPersonName(full = "Naoko Yamada"))),
                ),
            ),
        )

        val enrichment = detail.toEnrichment(anilistId = 1)

        assertEquals(listOf("Naoko Yamada"), enrichment.directors)
    }

    @Test
    fun `maps characters with voice actor and falls back to native name`() {
        val detail = AniListMediaDetail(
            characters = AniListCharacterConnection(
                edges = listOf(
                    AniListCharacterEdge(
                        role = "MAIN",
                        node = AniListPersonNode(name = AniListPersonName(full = "Sakuta Azusagawa"), image = AniListImage(medium = "http://example/1.jpg")),
                        voiceActors = listOf(AniListPersonNode(name = AniListPersonName(full = "Kaito Ishikawa"))),
                    ),
                    AniListCharacterEdge(
                        role = "SUPPORTING",
                        node = AniListPersonNode(name = AniListPersonName(native = "花楓")),
                    ),
                ),
            ),
        )

        val enrichment = detail.toEnrichment(anilistId = 1)

        assertEquals(2, enrichment.characters.size)
        assertEquals("Sakuta Azusagawa", enrichment.characters[0].name)
        assertEquals("Kaito Ishikawa", enrichment.characters[0].voiceActorName)
        assertEquals("花楓", enrichment.characters[1].name)
        assertNull(enrichment.characters[1].voiceActorName)
    }

    @Test
    fun `skips characters with no usable name`() {
        val detail = AniListMediaDetail(
            characters = AniListCharacterConnection(
                edges = listOf(AniListCharacterEdge(role = "MAIN", node = AniListPersonNode(name = null))),
            ),
        )

        assertEquals(emptyList<Any>(), detail.toEnrichment(anilistId = 1).characters)
    }
}
