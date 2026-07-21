package org.akkirrai.beakokit.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AnimeReleaseStatusTest {
    @Test
    fun `normalizes common source status aliases`() {
        listOf("released", "finished", "finished airing", "вышел", "вышло", "завершён").forEach { status ->
            assertEquals(AnimeReleaseStatus.RELEASED, AnimeReleaseStatus.from(status))
        }
        listOf("ongoing", "airing", "currently airing", "онгоинг", "выходит").forEach { status ->
            assertEquals(AnimeReleaseStatus.ONGOING, AnimeReleaseStatus.from(status))
        }
        listOf("announcement", "announced", "not yet aired", "анонс", "анонсировано").forEach { status ->
            assertEquals(AnimeReleaseStatus.ANNOUNCEMENT, AnimeReleaseStatus.from(status))
        }
    }
}
