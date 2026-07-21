package org.akkirrai.hibiki.feature.onboarding

import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSourceSelectionTest {
    private val russian = descriptor(
        id = "russian",
        languages = setOf(SourceLanguage.RUSSIAN),
        primaryLanguage = SourceLanguage.RUSSIAN,
    )
    private val english = descriptor(
        id = "english",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )
    private val multilingual = descriptor(
        id = "multilingual",
        languages = setOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.RUSSIAN,
    )
    private val sources = listOf(russian, english, multilingual)

    @Test
    fun `russian ukrainian and belarusian locales show russian capable sources`() {
        listOf("ru", "uk", "be").forEach { language ->
            assertEquals(
                listOf(russian, multilingual),
                onboardingSourcesForSystemLanguage(sources, language),
            )
        }
    }

    @Test
    fun `other locales show english capable sources`() {
        listOf("en", "pl", "de", "").forEach { language ->
            assertEquals(
                listOf(english, multilingual),
                onboardingSourcesForSystemLanguage(sources, language),
            )
        }
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
