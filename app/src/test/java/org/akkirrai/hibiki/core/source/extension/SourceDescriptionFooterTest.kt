package org.akkirrai.hibiki.core.source.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDescriptionFooterTest {
    @Test
    fun `markdown footer is removed and its facts are kept`() {
        val parsed = SourceDescriptionFooter.parse(
            "Second season.\n\n**Rating:** PG 13\n**Subtitles:** English, Deutsch\n[MAL](https://myanimelist.net/anime/1)",
        )
        assertEquals("Second season.", parsed.text)
        assertEquals("PG 13", parsed.ageRating)
        assertEquals("English, Deutsch", parsed.facts["subtitles"])
    }

    @Test
    fun `a pipe separated fact line is removed and parsed`() {
        val parsed = SourceDescriptionFooter.parse(
            "The story. [Written by MAL Rewrite]\n\nOther name: BLEACH - ブリーチ - | Synonyms: Bleach | Type: TV | " +
                "Aired: Oct 5, 2004 | Premiered: Fall 2004 | Duration: 24 min | Rating: PG-13 | Episodes: 366",
        )
        assertEquals("The story. [Written by MAL Rewrite]", parsed.text)
        val facts = FooterFacts.from(parsed.facts)
        assertEquals("PG-13", facts.ageRating)
        assertEquals(366, facts.episodeCount)
        assertEquals("TV", facts.type)
        assertEquals(2004, facts.year)
        assertEquals(4, facts.season)
        assertEquals(listOf("BLEACH - ブリーチ", "Bleach"), facts.synonyms)
    }

    @Test
    fun `the year falls back to the aired date`() {
        val facts = FooterFacts.from(mapOf("aired" to "Oct 5, 2004 to Mar 27, 2012", "episodes" to "?"))
        assertEquals(2004, facts.year)
        assertNull(facts.season)
        assertNull(facts.episodeCount)
    }

    @Test
    fun `a plain description is untouched`() {
        val parsed = SourceDescriptionFooter.parse("Just a story.\nWith two lines.")
        assertEquals("Just a story.\nWith two lines.", parsed.text)
        assertNull(parsed.ageRating)
    }

    @Test
    fun `bold text and a lone colon inside the story are not a footer`() {
        val bold = "He said **no:** and left.\nThe end."
        assertEquals(bold, SourceDescriptionFooter.parse(bold).text)
        val colon = "Note: this is prose | not facts, really"
        assertEquals(colon, SourceDescriptionFooter.parse(colon).text)
    }

    @Test
    fun `a footer only description becomes empty`() {
        val parsed = SourceDescriptionFooter.parse("**Rating:** R\n[MAL](https://x.y/1)")
        assertNull(parsed.text)
        assertEquals("R", parsed.ageRating)
    }

    @Test
    fun `blank or missing input passes through`() {
        assertNull(SourceDescriptionFooter.parse(null).text)
        assertEquals("", SourceDescriptionFooter.parse("").text)
    }
}
