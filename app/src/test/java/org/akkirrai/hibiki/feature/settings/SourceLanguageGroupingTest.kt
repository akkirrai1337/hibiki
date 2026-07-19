package org.akkirrai.hibiki.feature.settings

import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceLanguageGroupingTest {
    @Test
    fun `sections use primary language without duplicating multilingual sources`() {
        val russian = descriptor("ru", setOf(SourceLanguage.RUSSIAN), SourceLanguage.RUSSIAN)
        val english = descriptor("en", setOf(SourceLanguage.ENGLISH), SourceLanguage.ENGLISH)
        val localizedRussian = descriptor(
            id = "ru-localized",
            languages = setOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.RUSSIAN,
        )

        val grouped = groupSourcesByLanguage(listOf(russian, english, localizedRussian))

        assertEquals(listOf(russian, localizedRussian), grouped[SourceLanguage.RUSSIAN])
        assertEquals(listOf(english), grouped[SourceLanguage.ENGLISH])
    }

    private fun descriptor(
        id: String,
        languages: Set<SourceLanguage>,
        primaryLanguage: SourceLanguage,
    ) = AnimeSourceDescriptor(
        info = SourceInfo(
            id = SourceId(id),
            name = id,
            languages = languages,
            primaryLanguage = primaryLanguage,
        ),
        iconRes = 0,
    )
}
