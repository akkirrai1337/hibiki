package org.akkirrai.hibiki.core.source.extension

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioCreditsTest {
    @Test
    fun `studios and producers in one author string are split`() {
        val credits = StudioCredits.parse(
            "Studio Pierrot, Studio Pierrot (**Producers:** Dentsu, Shueisha, Aniplex, TV Tokyo, Viz Media)",
        )
        assertEquals(listOf("Studio Pierrot"), credits.studios)
        assertEquals(listOf("Dentsu", "Shueisha", "Aniplex", "TV Tokyo", "Viz Media"), credits.producers)
    }

    @Test
    fun `other labels are dropped`() {
        val credits = StudioCredits.parse("MAPPA (**Producers:** Aniplex) (**Licensors:** Crunchyroll)")
        assertEquals(listOf("MAPPA"), credits.studios)
        assertEquals(listOf("Aniplex"), credits.producers)
    }

    @Test
    fun `a plain list of studios has no producers`() {
        val credits = StudioCredits.parse("Bones; Madhouse")
        assertEquals(listOf("Bones", "Madhouse"), credits.studios)
        assertEquals(emptyList<String>(), credits.producers)
    }

    @Test
    fun `studios printed as a footer line are split the same way`() {
        val parsed = SourceDescriptionFooter.parse("Story.\n\n**Studios:** Lerche, Lerche (**Producers:** Lantis)")
        val credits = StudioCredits.parse(FooterFacts.from(parsed.facts).studioText)
        assertEquals("Story.", parsed.text)
        assertEquals(listOf("Lerche"), credits.studios)
        assertEquals(listOf("Lantis"), credits.producers)
    }

    @Test
    fun `blank input is empty`() {
        assertEquals(StudioCredits.Credits(emptyList(), emptyList()), StudioCredits.parse(null))
        assertEquals(StudioCredits.Credits(emptyList(), emptyList()), StudioCredits.parse("  "))
    }
}
