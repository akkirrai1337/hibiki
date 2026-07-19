package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceInfoTest {
    @Test
    fun `primary language can select one of multiple supported languages`() {
        val info = SourceInfo(
            id = SourceId("test"),
            name = "Test",
            languages = linkedSetOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.RUSSIAN,
        )

        assertEquals(SourceLanguage.RUSSIAN, info.primaryLanguage)
    }

    @Test
    fun `primary language must be supported by source`() {
        assertFailsWith<IllegalArgumentException> {
            SourceInfo(
                id = SourceId("test"),
                name = "Test",
                languages = setOf(SourceLanguage.RUSSIAN),
                primaryLanguage = SourceLanguage.ENGLISH,
            )
        }
    }
}
