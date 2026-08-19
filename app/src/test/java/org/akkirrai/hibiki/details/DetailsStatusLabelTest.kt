package org.akkirrai.hibiki.details
import org.akkirrai.hibiki.details.data.*
import org.akkirrai.hibiki.details.model.*
import org.akkirrai.hibiki.details.screen.*
import org.akkirrai.hibiki.details.state.*

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
