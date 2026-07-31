package org.akkirrai.hibiki.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileDateFormatTest {
    @Test
    fun addedDateUsesTheSelectedAppLanguage() {
        val januaryFifth2026 = 1_767_571_200_000L

        assertEquals("5 Jan", profileAddedDateLabel(januaryFifth2026, "en"))
        assertEquals("5 янв.", profileAddedDateLabel(januaryFifth2026, "ru"))
    }

    @Test
    fun secondsAndMillisecondsUseTheSameFormattedDate() {
        val januaryFifth2026Millis = 1_767_571_200_000L

        assertEquals(
            profileAddedDateLabel(januaryFifth2026Millis, "en"),
            profileAddedDateLabel(januaryFifth2026Millis / 1_000L, "en"),
        )
    }
}
