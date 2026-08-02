package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceLanguageSectionLabelTest {
    @Test
    fun usesAndroidLanguageCodesForKnownSourceLanguages() {
        assertEquals("RU", sourceLanguageSectionLabel("ru"))
        assertEquals("RU", sourceLanguageSectionLabel("RUSSIAN"))
        assertEquals("EN", sourceLanguageSectionLabel("en"))
        assertEquals("EN", sourceLanguageSectionLabel("english"))
    }
}
