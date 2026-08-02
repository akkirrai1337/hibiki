package org.akkirrai.hibiki.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsStatusLabelTest {
    @Test
    fun localizesKnownEnglishStatuses() {
        assertEquals(
            "Онгоинг",
            resolveDetailsStatusLabel("ongoing", "Онгоинг", "Вышел", "Анонс"),
        )
        assertEquals(
            "Вышел",
            resolveDetailsStatusLabel("finished", "Онгоинг", "Вышел", "Анонс"),
        )
        assertEquals(
            "Анонс",
            resolveDetailsStatusLabel("announcement", "Онгоинг", "Вышел", "Анонс"),
        )
    }

    @Test
    fun preservesAlreadyLocalizedOrUnknownValues() {
        assertEquals("Онгоинг", resolveDetailsStatusLabel("Онгоинг", "Ongoing", "Released", "Announcement"))
        assertEquals("Custom", resolveDetailsStatusLabel("Custom", "Ongoing", "Released", "Announcement"))
    }
}
