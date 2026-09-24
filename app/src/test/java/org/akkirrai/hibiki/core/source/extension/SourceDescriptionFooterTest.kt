package org.akkirrai.hibiki.core.source.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDescriptionFooterTest {
    @Test
    fun `machine made footer is removed and the age rating is kept`() {
        val parsed = SourceDescriptionFooter.parse(
            "Second season.\n\n**Rating:** PG 13\n**Subtitles:** English, Deutsch\n[MAL](https://myanimelist.net/anime/1)",
        )
        assertEquals("Second season.", parsed.text)
        assertEquals("PG 13", parsed.ageRating)
    }

    @Test
    fun `a plain description is untouched`() {
        val parsed = SourceDescriptionFooter.parse("Just a story.\nWith two lines.")
        assertEquals("Just a story.\nWith two lines.", parsed.text)
        assertNull(parsed.ageRating)
    }

    @Test
    fun `bold text inside the story is not a footer`() {
        val text = "He said **no:** and left.\nThe end."
        assertEquals(text, SourceDescriptionFooter.parse(text).text)
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
