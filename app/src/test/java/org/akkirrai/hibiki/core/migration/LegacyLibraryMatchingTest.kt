package org.akkirrai.hibiki.core.migration

import org.akkirrai.beakokit.model.AnimeTitle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyLibraryMatchingTest {
    @Test
    fun `retired source ids are recognised, current ones are not`() {
        assertEquals("anichi", LegacyLibraryMatching.legacySourceIdOf("source:anichi:/anime/1"))
        assertEquals("ani-liberty", LegacyLibraryMatching.legacySourceIdOf("source:ANI_LIBERTY:42"))
        assertEquals("yummy-anime", LegacyLibraryMatching.legacySourceIdOf("12345"))
        assertNull(LegacyLibraryMatching.legacySourceIdOf("source:aniyomi-eu-kanade-tachiyomi-animeextension-en-anichi-jlerat-7rxy7ibxhnxm:/anime/1"))
        assertNull(LegacyLibraryMatching.legacySourceIdOf("something else"))
    }

    @Test
    fun `installed sources are suggested by name`() {
        val installed = listOf("Anichi", "Ani-Liberty", "AnimePahe")
        assertEquals("Ani-Liberty", LegacyLibraryMatching.suggestTarget("AniLiberty", installed) { it })
        assertEquals("AnimePahe", LegacyLibraryMatching.suggestTarget("animepahe", installed) { it })
        assertNull(LegacyLibraryMatching.suggestTarget("Mikai", installed) { it })
    }

    @Test
    fun `a saved entry becomes a probe and finds its title in the new source`() {
        val saved = JSONObject()
            .put("id", "source:anichi:old")
            .put("title", "Blue Lock Season 2")
            .put("alternativeTitles", JSONArray(listOf("Blue Lock 2")))
            .put("releaseDate", "Fall 2024")
        val probe = LegacyLibraryMatching.probeFor(saved)
        assertEquals(2024, probe.year)
        assertEquals(listOf("Blue Lock Season 2", "Blue Lock 2"), LegacyLibraryMatching.queriesFor(probe))

        val right = AnimeTitle(id = "source:new:/anime/blue-lock-season-2", originalName = "Blue Lock Season 2", year = 2024)
        val wrong = AnimeTitle(id = "source:new:/anime/naruto", originalName = "Naruto", year = 2002)
        assertEquals(right.id, LegacyLibraryMatching.bestMatch(probe, listOf(wrong, right))?.id)
        assertNull(LegacyLibraryMatching.bestMatch(probe, listOf(wrong)))
    }

    @Test
    fun `rewriting keeps the saved fields and swaps the id`() {
        val saved = JSONObject().put("id", "source:anichi:old").put("title", "T").put("posterUrl", "http://old/p.jpg")
        val match = AnimeTitle(id = "source:new:/x", originalName = "T", posterUrl = "http://new/p.jpg")
        val rewritten = LegacyLibraryMatching.rewritten(saved, match)
        assertEquals("source:new:/x", rewritten.getString("id"))
        assertEquals("T", rewritten.getString("title"))
        assertEquals("http://new/p.jpg", rewritten.getString("posterUrl"))
        assertNotNull(saved.getString("id"))
        assertEquals("source:anichi:old", saved.getString("id"))
    }
}
