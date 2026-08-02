package org.akkirrai.hibiki.shared.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingSourceLanguageFilterTest {
    private data class Source(val id: String, val languages: Set<String>)

    @Test
    fun localeTagsUseTheRussianSourceGroup() {
        val sources = listOf(
            Source("ru", setOf("ru")),
            Source("en", setOf("en")),
        )

        assertEquals(
            listOf("ru"),
            filterOnboardingSourcesByLanguage(sources, "ru-RU", "ru", "en") { it.languages }
                .map(Source::id),
        )
        assertEquals(
            listOf("ru"),
            filterOnboardingSourcesByLanguage(sources, "uk-UA", "ru", "en") { it.languages }
                .map(Source::id),
        )
    }
}
